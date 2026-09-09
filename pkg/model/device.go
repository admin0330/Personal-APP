package model

import "time"

// DeviceTokenPrefix 是设备令牌的固定前缀。与 API Key 的 ebx_ 区分开，
// 是为了让鉴权路径能按前缀一次判定去查哪张表，也让人一眼看出
// 「这串东西绑在一台设备上，不是工作空间的主 Key」。
const DeviceTokenPrefix = "ebxd_"

// 设备接入的两个时间窗口。
const (
	// DeviceCodeTTL 是短码的有效期。取预案给的 5–10 分钟窗口的中段：
	// 短到用户还没从网页切到手机就过期、长到短码在肩窥后仍能兑换，两头都要防。
	DeviceCodeTTL = 8 * time.Minute
	// DeviceTokenTTL 是设备令牌的有效期。到期之后设备必须重新绑定，
	// 这样「换手机、旧手机丢了」这类事件即使没人主动撤销也有上限。
	DeviceTokenTTL = 180 * 24 * time.Hour
)

// 设备接入的两道数量上限。
const (
	// MaxActiveDeviceTokens 是单个租户同时有效的设备令牌数。
	// 超过之后兑换返回 403：让用户先撤销一台，而不是无限发放无人认领的凭据。
	MaxActiveDeviceTokens = 10
	// MaxDeviceLabelLen 是设备名的长度上限，防止把整段 UA 塞进来。
	MaxDeviceLabelLen = 64
)

// DeviceCodeAlphabet 是短码的字符集。剔除了 0/O 与 1/I/L：
// 短码是要被**人从屏幕上抄到另一台设备**的，形近字符造成的失败
// 会被用户当成「接口坏了」，而它恰好也是抄写类验证码最经典的返工来源。
const DeviceCodeAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

// DeviceCodeLen 是短码长度。8 位 × 32 字符集 ≈ 1.1e12 种，
// 配合一次性、8 分钟有效期与限流，穷举不是现实威胁。
const DeviceCodeLen = 8

// DeviceCode 是一次性短码的内部形态。库里只有摘要，明文不落盘。
type DeviceCode struct {
	ID         string
	TenantID   string
	CodeHash   string
	CreatedBy  string
	ExpiresAt  time.Time
	RedeemedAt *time.Time
	CreatedAt  time.Time
}

// DeviceToken 是一台设备持有的只读凭据。
//
// 与 TenantAPIKey 的关键差别：没有密文列。明文只在兑换响应里出现一次，
// 之后只存在于设备的 Keystore。设备丢了重新绑一台即可，
// 而「随时能再显示一次」的能力只会让一次数据库泄露的收益翻倍。
type DeviceToken struct {
	ID        string
	TenantID  string
	TokenHash string
	TokenHint string
	Label     string
	CreatedBy string
	ExpiresAt time.Time
	RevokedAt *time.Time
	CreatedAt time.Time
}

// DeviceView 是设备列表里的一行。token_hash 永不出现在响应里——
// 列表的作用是「让人认出哪一台该撤销」，辨认靠 hint 与 label 就够了。
type DeviceView struct {
	ID        string     `json:"id"`
	Label     string     `json:"label"`
	TokenHint string     `json:"token_hint"`
	CreatedAt time.Time  `json:"created_at"`
	ExpiresAt time.Time  `json:"expires_at"`
	RevokedAt *time.Time `json:"revoked_at"`
	// Expired 是「已过期但还没被撤销」。列表要能区分这两种不可用，
	// 否则用户会以为撤销失败了。
	Expired bool `json:"expired"`
}

// DeviceCodeView 是短码的生成结果。明文只在这一次响应里出现。
type DeviceCodeView struct {
	Code      string    `json:"code"`
	ExpiresAt time.Time `json:"expires_at"`
	TTL       int       `json:"ttl_seconds"`
}

// RedeemDeviceCodeRequest 是兑换请求。device_name 可选：
// 没有它，设备列表就是几行无法分辨的令牌，「撤销哪一台」变成猜谜。
type RedeemDeviceCodeRequest struct {
	Code       string `json:"code"`
	DeviceName string `json:"device_name"`
}

// RedeemDeviceCodeResponse 是兑换结果。App 把 token 走既有 Keystore 通道存起来，
// 之后当 API Key 用；tenant_id 直接给出，省掉第二次解析请求。
type RedeemDeviceCodeResponse struct {
	Token     string    `json:"token"`
	TenantID  string    `json:"tenant_id"`
	ExpiresAt time.Time `json:"expires_at"`
}
