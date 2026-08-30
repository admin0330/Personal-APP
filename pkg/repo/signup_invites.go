package repo

import (
	"context"

	postgresdb "emailbox/db/generated/postgres"
	sqlitedb "emailbox/db/generated/sqlite"
	"emailbox/pkg/model"
)

func (s *Store) CreateSignupInvite(ctx context.Context, v *model.SignupInvite) error {
	var err error
	if s.driver == "sqlite" {
		err = s.sqlite.CreateSignupInvite(ctx, sqlitedb.CreateSignupInviteParams{
			ID: v.ID, CodeHash: v.CodeHash, CreatedBy: v.CreatedBy, ExpiresAt: v.ExpiresAt,
		})
	} else {
		err = s.postgres.CreateSignupInvite(ctx, postgresdb.CreateSignupInviteParams{
			ID: v.ID, CodeHash: v.CodeHash, CreatedBy: v.CreatedBy, ExpiresAt: v.ExpiresAt,
		})
	}
	return normalize(err)
}

func mapSQLiteSignupInvite(v sqlitedb.SignupInvite) *model.SignupInvite {
	return &model.SignupInvite{
		ID: v.ID, CodeHash: v.CodeHash, CreatedBy: v.CreatedBy,
		ExpiresAt: v.ExpiresAt, RedeemedAt: timePtr(v.RedeemedAt), CreatedAt: v.CreatedAt,
	}
}

func mapPostgresSignupInvite(v postgresdb.SignupInvite) *model.SignupInvite {
	return &model.SignupInvite{
		ID: v.ID, CodeHash: v.CodeHash, CreatedBy: v.CreatedBy,
		ExpiresAt: v.ExpiresAt, RedeemedAt: timePtr(v.RedeemedAt), CreatedAt: v.CreatedAt,
	}
}

func (s *Store) GetActiveSignupInviteByHash(ctx context.Context, hash string) (*model.SignupInvite, error) {
	if s.driver == "sqlite" {
		v, err := s.sqlite.GetActiveSignupInviteByHash(ctx, hash)
		return mapSQLiteSignupInvite(v), normalize(err)
	}
	v, err := s.postgres.GetActiveSignupInviteByHash(ctx, hash)
	return mapPostgresSignupInvite(v), normalize(err)
}

func (s *Store) ListSignupInvites(ctx context.Context, limit int) ([]model.SignupInvite, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	items := make([]model.SignupInvite, 0, limit)
	if s.driver == "sqlite" {
		rows, err := s.sqlite.ListSignupInvites(ctx, sqlitedb.ListSignupInvitesParams{Limit: int64(limit)})
		if err != nil {
			return nil, err
		}
		for _, row := range rows {
			items = append(items, *mapSQLiteSignupInvite(row))
		}
		return items, nil
	}
	rows, err := s.postgres.ListSignupInvites(ctx, postgresdb.ListSignupInvitesParams{Limit: int32(limit)})
	if err != nil {
		return nil, err
	}
	for _, row := range rows {
		items = append(items, *mapPostgresSignupInvite(row))
	}
	return items, nil
}

func (s *Store) RedeemSignupInvite(ctx context.Context, id string) error {
	var n int64
	var err error
	if s.driver == "sqlite" {
		n, err = s.sqlite.RedeemSignupInvite(ctx, id)
	} else {
		n, err = s.postgres.RedeemSignupInvite(ctx, id)
	}
	return rowsAffected(n, err)
}

func (s *Store) DeleteSignupInvite(ctx context.Context, id string) error {
	var n int64
	var err error
	if s.driver == "sqlite" {
		n, err = s.sqlite.DeleteSignupInvite(ctx, id)
	} else {
		n, err = s.postgres.DeleteSignupInvite(ctx, id)
	}
	return rowsAffected(n, err)
}
