package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"strings"
	"time"

	"emailbox/pkg/model"
	"emailbox/pkg/repo"

	"github.com/google/uuid"
)

// 设备接入的错误语义与 Android 侧的预案一一对应，
// 它们各自映射到一个 HTTP 状态码（见 pkg/handler/device_handler.go）。
var (
	// ErrDeviceCodeMalformed 是形状就不对（长度或字符集）。它和「查无此码」
	// 必须分开返回：形状错是客户端 bug（400，别重试），查无此码是业务结果
	// （401，提示重新生成）。混在一起会让 App 分不清该报哪个错。
	ErrDeviceCodeMalformed = errors.New("短码格式不正确，应为 8 位字母数字")
	// ErrDeviceCodeInvalid 覆盖「不存在」与「已用过」两种情况。
	// 合并是有意的：区分它们等于告诉攻击者「这个短码有效，只是被人抢先了」，
	// 而一次泄露的短码不需要再多送一条确认。
	ErrDeviceCodeInvalid = errors.New("短码无效或已被使用")
	ErrDeviceCodeExpired = errors.New("短码已过期，请回到网页端重新生成")
	// ErrDeviceLimitReached 是唯一必须由用户先做点什么才能继续的错误：
	// 先在设备列表里撤销一台，再重新生成短码。
	ErrDeviceLimitReached = errors.New("设备数量已达上限，请先在网页端撤销一台设备")
)

// DeviceService 管理一次性短码与设备令牌。
//
// 设备令牌的权限与租户 API Key 相同（只读、TenantRoleAPI），但它是**每台设备一把**：
// 手机丢了只撤销那一条，不必重置整个工作空间的 Key、连带让脚本和别的设备一起掉线。
type DeviceService struct {
	store *repo.Store
	audit *AuditService
	now   func() time.Time
}

func NewDeviceService(store *repo.Store, audit *AuditService) *DeviceService {
	return &DeviceService{store: store, audit: audit, now: time.Now}
}

// WithClock 换掉时间源，只给测试用：短码 8 分钟、令牌 180 天，
// 用例不能真的等那么久。
func (s *DeviceService) WithClock(f func() time.Time) *DeviceService {
	s.now = f
	return s
}

// DeviceIdentity 是设备令牌鉴权的结果。
type DeviceIdentity struct {
	TenantID string
	Scopes   map[string]bool
}

// MintCode 生成一个一次性短码。明文只在这一次返回值里出现，库里只有摘要。
func (s *DeviceService) MintCode(ctx context.Context, tenantID, userID string) (*model.DeviceCodeView, error) {
	code, err := randomDeviceCode()
	if err != nil {
		return nil, err
	}
	expiresAt := s.now().Add(model.DeviceCodeTTL)
	if err := s.store.CreateDeviceCode(ctx, &model.DeviceCode{
		ID: uuid.NewString(), TenantID: tenantID, CodeHash: TokenHash(code),
		CreatedBy: userID, ExpiresAt: expiresAt,
	}); err != nil {
		return nil, err
	}
	return &model.DeviceCodeView{
		Code: code, ExpiresAt: expiresAt, TTL: int(model.DeviceCodeTTL.Seconds()),
	}, nil
}

// Redeem 用短码换一台设备的令牌。
//
// 顺序是有讲究的：过期、已用、设备数上限都放在「把短码标记为已用」**之前**判。
// 前两者是终态（这个短码无论如何都废了），而设备数上限是用户能当场修好的——
// 撤销一台之后，同一个短码在剩余时间里还能再用，不必回网页重新生成。
func (s *DeviceService) Redeem(ctx context.Context, req model.RedeemDeviceCodeRequest, ip string) (*model.RedeemDeviceCodeResponse, error) {
	code := strings.ToUpper(strings.TrimSpace(req.Code))
	if !validDeviceCode(code) {
		return nil, ErrDeviceCodeMalformed
	}
	record, err := s.store.GetDeviceCodeByHash(ctx, TokenHash(code))
	if err != nil {
		return nil, ErrDeviceCodeInvalid
	}
	now := s.now()
	if !record.ExpiresAt.After(now) {
		return nil, ErrDeviceCodeExpired
	}
	if record.RedeemedAt != nil {
		return nil, ErrDeviceCodeInvalid
	}

	active, err := s.store.CountActiveDeviceTokens(ctx, record.TenantID)
	if err != nil {
		return nil, err
	}
	if active >= model.MaxActiveDeviceTokens {
		return nil, ErrDeviceLimitReached
	}

	// 一次性由 SQL 的 WHERE redeemed_at IS NULL 保证：并发提交同一个短码时
	// 只有一条 UPDATE 能命中，另一条拿到 0 行。
	if err := s.store.MarkDeviceCodeRedeemed(ctx, record.ID); err != nil {
		return nil, ErrDeviceCodeInvalid
	}

	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return nil, errors.New("生成设备令牌失败")
	}
	token := model.DeviceTokenPrefix + hex.EncodeToString(raw)
	expiresAt := now.Add(model.DeviceTokenTTL)
	if err := s.store.CreateDeviceToken(ctx, &model.DeviceToken{
		ID: uuid.NewString(), TenantID: record.TenantID,
		TokenHash: TokenHash(token), TokenHint: token[:len(model.DeviceTokenPrefix)+6] + "...",
		Label: cleanDeviceLabel(req.DeviceName),
		// 短码的创建者就是这次绑定的发起人：他在网页上点了「生成」，
		// 兑换只是他在另一台设备上完成这个动作。
		CreatedBy: record.CreatedBy, ExpiresAt: expiresAt,
	}); err != nil {
		return nil, err
	}

	// 兑换是唯一一个「操作者还没拿到凭据」的写操作：actor 留空，
	// 靠 IP 与所属租户定位。记它是因为「哪台设备在什么时候接入的」
	// 正是事后追责时要回答的第一个问题。
	s.audit.Record(ctx, Entry{
		TenantID: record.TenantID, ActorKind: model.ActorKindDevice,
		Action: model.AuditDeviceRedeem, ResourceType: "device", IP: ip,
		Details: map[string]any{"label": cleanDeviceLabel(req.DeviceName)},
	})
	return &model.RedeemDeviceCodeResponse{Token: token, TenantID: record.TenantID, ExpiresAt: expiresAt}, nil
}

// List 返回租户下的设备。已撤销的也列出——「这台我上周撤过」和
// 「从来没有这台」在排查时是两种不同的信息。
func (s *DeviceService) List(ctx context.Context, tenantID string) ([]model.DeviceView, error) {
	tokens, err := s.store.ListDeviceTokens(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	now := s.now()
	out := make([]model.DeviceView, 0, len(tokens))
	for _, t := range tokens {
		out = append(out, model.DeviceView{
			ID: t.ID, Label: t.Label, TokenHint: t.TokenHint,
			CreatedAt: t.CreatedAt, ExpiresAt: t.ExpiresAt, RevokedAt: t.RevokedAt,
			Expired: t.RevokedAt == nil && !t.ExpiresAt.After(now),
		})
	}
	return out, nil
}

// Revoke 撤销一台设备，下一个请求即生效（鉴权查询每次都判 revoked_at）。
func (s *DeviceService) Revoke(ctx context.Context, tenantID, deviceID string) error {
	return s.store.RevokeDeviceToken(ctx, tenantID, deviceID)
}

// Authenticate 校验 Authorization 头里的设备令牌。
//
// 只查摘要、不解密：与 API Key 同一条路径，撤销与过期都由 SQL 判。
// 返回的 scope 固定只有 mail:read——手机需要的是读邮件，
// 不需要（也不应该）碰个人账本的写权限。
func (s *DeviceService) Authenticate(ctx context.Context, token string) (*DeviceIdentity, error) {
	token = strings.TrimSpace(token)
	if token == "" || !strings.HasPrefix(token, model.DeviceTokenPrefix) {
		return nil, ErrAPIKeyInvalid
	}
	device, err := s.store.GetDeviceTokenByHash(ctx, TokenHash(token))
	if err != nil {
		return nil, ErrAPIKeyInvalid
	}
	return &DeviceIdentity{
		TenantID: device.TenantID,
		Scopes:   map[string]bool{model.APIKeyScopeMailRead: true},
	}, nil
}

// validDeviceCode 校验短码形状。先做形状检查再查库：
// 明显不是短码的输入不该产生一次数据库查询。
func validDeviceCode(code string) bool {
	if len(code) != model.DeviceCodeLen {
		return false
	}
	for _, r := range code {
		if !strings.ContainsRune(model.DeviceCodeAlphabet, r) {
			return false
		}
	}
	return true
}

// randomDeviceCode 生成 8 位短码。
//
// 直接用随机字节对字符集长度取模在这里是无偏的：256 能被 32 整除，
// 每个字符的概率完全相同。这不是小聪明——短码要靠人抄写，
// 谁也不希望某几个字符出现得更频繁（那会让猜测变得不均匀）。
func randomDeviceCode() (string, error) {
	buf := make([]byte, model.DeviceCodeLen)
	if _, err := rand.Read(buf); err != nil {
		return "", errors.New("生成短码失败")
	}
	out := make([]byte, model.DeviceCodeLen)
	for i, b := range buf {
		out[i] = model.DeviceCodeAlphabet[int(b)%len(model.DeviceCodeAlphabet)]
	}
	return string(out), nil
}

func cleanDeviceLabel(raw string) string {
	label := strings.TrimSpace(raw)
	if len([]rune(label)) > model.MaxDeviceLabelLen {
		label = string([]rune(label)[:model.MaxDeviceLabelLen])
	}
	return label
}
