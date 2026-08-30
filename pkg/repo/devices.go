package repo

import (
	"context"
	"database/sql"

	postgresdb "emailbox/db/generated/postgres"
	sqlitedb "emailbox/db/generated/sqlite"
	"emailbox/pkg/model"
)

// 设备接入：短码（一次性、短命）与设备令牌（长命、可撤销）两张表。
//
// 两张表都只存摘要。明文短码在屏幕上停留 8 分钟，明文令牌只在兑换响应里
// 出现一次——这两样东西一旦落盘，一次数据库泄露就等于把凭据直接交出去。

func (s *Store) CreateDeviceCode(ctx context.Context, c *model.DeviceCode) error {
	if s.driver == "sqlite" {
		return normalize(s.sqlite.CreateDeviceCode(ctx, sqlitedb.CreateDeviceCodeParams{
			ID: c.ID, TenantID: c.TenantID, CodeHash: c.CodeHash,
			CreatedBy: nullString(c.CreatedBy), ExpiresAt: c.ExpiresAt,
		}))
	}
	return normalize(s.postgres.CreateDeviceCode(ctx, postgresdb.CreateDeviceCodeParams{
		ID: c.ID, TenantID: c.TenantID, CodeHash: c.CodeHash,
		CreatedBy: nullString(c.CreatedBy), ExpiresAt: c.ExpiresAt,
	}))
}

func (s *Store) GetDeviceCodeByHash(ctx context.Context, hash string) (*model.DeviceCode, error) {
	if s.driver == "sqlite" {
		v, e := s.sqlite.GetDeviceCodeByHash(ctx, hash)
		if e != nil {
			return nil, normalize(e)
		}
		return mapSQLiteDeviceCode(v), nil
	}
	v, e := s.postgres.GetDeviceCodeByHash(ctx, hash)
	if e != nil {
		return nil, normalize(e)
	}
	return mapPostgresDeviceCode(v), nil
}

// MarkDeviceCodeRedeemed 把短码标记为已用，0 行表示它已被用过或已过期。
// 「一次性」由 SQL 的 WHERE 保证而不是由调用方先查后写——
// 两台设备同时提交同一个短码时，先读后写会让它们都通过。
func (s *Store) MarkDeviceCodeRedeemed(ctx context.Context, id string) error {
	if s.driver == "sqlite" {
		n, err := s.sqlite.MarkDeviceCodeRedeemed(ctx, id)
		return rowsAffected(n, err)
	}
	n, err := s.postgres.MarkDeviceCodeRedeemed(ctx, id)
	return rowsAffected(n, err)
}

func (s *Store) CreateDeviceToken(ctx context.Context, t *model.DeviceToken) error {
	if s.driver == "sqlite" {
		return normalize(s.sqlite.CreateDeviceToken(ctx, sqlitedb.CreateDeviceTokenParams{
			ID: t.ID, TenantID: t.TenantID, TokenHash: t.TokenHash, TokenHint: t.TokenHint,
			Label: t.Label, CreatedBy: nullString(t.CreatedBy), ExpiresAt: t.ExpiresAt,
		}))
	}
	return normalize(s.postgres.CreateDeviceToken(ctx, postgresdb.CreateDeviceTokenParams{
		ID: t.ID, TenantID: t.TenantID, TokenHash: t.TokenHash, TokenHint: t.TokenHint,
		Label: t.Label, CreatedBy: nullString(t.CreatedBy), ExpiresAt: t.ExpiresAt,
	}))
}

// GetDeviceTokenByHash 是设备令牌的鉴权查询。撤销与过期的判定都在这条 SQL 里，
// 不缓存：撤销之后还让它生效一分钟，那不叫撤销。
func (s *Store) GetDeviceTokenByHash(ctx context.Context, hash string) (*model.DeviceToken, error) {
	if s.driver == "sqlite" {
		v, e := s.sqlite.GetDeviceTokenByHash(ctx, hash)
		if e != nil {
			return nil, normalize(e)
		}
		return mapSQLiteDeviceToken(v), nil
	}
	v, e := s.postgres.GetDeviceTokenByHash(ctx, hash)
	if e != nil {
		return nil, normalize(e)
	}
	return mapPostgresDeviceToken(v), nil
}

func (s *Store) ListDeviceTokens(ctx context.Context, tenantID string) ([]model.DeviceToken, error) {
	out := []model.DeviceToken{}
	if s.driver == "sqlite" {
		rows, err := s.sqlite.ListDeviceTokens(ctx, tenantID)
		if err != nil {
			return nil, err
		}
		for _, r := range rows {
			out = append(out, *mapSQLiteDeviceToken(r))
		}
		return out, nil
	}
	rows, err := s.postgres.ListDeviceTokens(ctx, tenantID)
	if err != nil {
		return nil, err
	}
	for _, r := range rows {
		out = append(out, *mapPostgresDeviceToken(r))
	}
	return out, nil
}

func (s *Store) CountActiveDeviceTokens(ctx context.Context, tenantID string) (int, error) {
	if s.driver == "sqlite" {
		n, e := s.sqlite.CountActiveDeviceTokens(ctx, tenantID)
		return int(n), e
	}
	n, e := s.postgres.CountActiveDeviceTokens(ctx, tenantID)
	return int(n), e
}

// RevokeDeviceToken 撤销一台设备。0 行表示它不属于本租户或已被撤销，
// 两者都返回 ErrNotFound——不区分是为了不泄露「这个 ID 确实存在」。
func (s *Store) RevokeDeviceToken(ctx context.Context, tenantID, id string) error {
	if s.driver == "sqlite" {
		n, err := s.sqlite.RevokeDeviceToken(ctx, sqlitedb.RevokeDeviceTokenParams{TenantID: tenantID, ID: id})
		return rowsAffected(n, err)
	}
	n, err := s.postgres.RevokeDeviceToken(ctx, postgresdb.RevokeDeviceTokenParams{TenantID: tenantID, ID: id})
	return rowsAffected(n, err)
}

func nullString(v string) sql.NullString {
	return sql.NullString{String: v, Valid: v != ""}
}

func mapSQLiteDeviceCode(v sqlitedb.DeviceCode) *model.DeviceCode {
	return &model.DeviceCode{
		ID: v.ID, TenantID: v.TenantID, CodeHash: v.CodeHash, CreatedBy: v.CreatedBy.String,
		ExpiresAt: v.ExpiresAt, RedeemedAt: timePtr(v.RedeemedAt),
		CreatedAt: v.CreatedAt,
	}
}

func mapPostgresDeviceCode(v postgresdb.DeviceCode) *model.DeviceCode {
	return &model.DeviceCode{
		ID: v.ID, TenantID: v.TenantID, CodeHash: v.CodeHash, CreatedBy: v.CreatedBy.String,
		ExpiresAt: v.ExpiresAt, RedeemedAt: timePtr(v.RedeemedAt),
		CreatedAt: v.CreatedAt,
	}
}

func mapSQLiteDeviceToken(v sqlitedb.DeviceToken) *model.DeviceToken {
	return &model.DeviceToken{
		ID: v.ID, TenantID: v.TenantID, TokenHash: v.TokenHash, TokenHint: v.TokenHint,
		Label: v.Label, CreatedBy: v.CreatedBy.String,
		ExpiresAt: v.ExpiresAt, RevokedAt: timePtr(v.RevokedAt), CreatedAt: v.CreatedAt,
	}
}

func mapPostgresDeviceToken(v postgresdb.DeviceToken) *model.DeviceToken {
	return &model.DeviceToken{
		ID: v.ID, TenantID: v.TenantID, TokenHash: v.TokenHash, TokenHint: v.TokenHint,
		Label: v.Label, CreatedBy: v.CreatedBy.String,
		ExpiresAt: v.ExpiresAt, RevokedAt: timePtr(v.RevokedAt), CreatedAt: v.CreatedAt,
	}
}
