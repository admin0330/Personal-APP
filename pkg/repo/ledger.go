package repo

import (
	"context"
	"time"

	postgresdb "emailbox/db/generated/postgres"
	sqlitedb "emailbox/db/generated/sqlite"
	"emailbox/pkg/model"
)

func (s *Store) CreateLedgerTransaction(ctx context.Context, v *model.LedgerTransaction) (*model.LedgerTransaction, error) {
	if s.driver == "sqlite" {
		r, err := s.sqlite.CreateLedgerTransaction(ctx, sqlitedb.CreateLedgerTransactionParams{
			ID: v.ID, TenantID: v.TenantID, Type: v.Type, AmountMinor: v.AmountMinor,
			Currency: v.Currency, Category: v.Category, OccurredAt: v.OccurredAt,
			Merchant: v.Merchant, Note: v.Note, Source: v.Source, Posted: boolToInt64(v.Posted),
			SourceMessageKey: nullableString(v.SourceMessageKey), ClientID: v.ClientID,
		})
		if err != nil {
			return nil, normalize(err)
		}
		return mapSQLiteLedger(r), nil
	}
	r, err := s.postgres.CreateLedgerTransaction(ctx, postgresdb.CreateLedgerTransactionParams{
		ID: v.ID, TenantID: v.TenantID, Type: v.Type, AmountMinor: v.AmountMinor,
		Currency: v.Currency, Category: v.Category, OccurredAt: v.OccurredAt,
		Merchant: v.Merchant, Note: v.Note, Source: v.Source, Posted: v.Posted,
		SourceMessageKey: nullableString(v.SourceMessageKey), ClientID: v.ClientID,
	})
	if err != nil {
		return nil, normalize(err)
	}
	return mapPostgresLedger(r), nil
}

func (s *Store) GetLedgerTransaction(ctx context.Context, tenantID, id string) (*model.LedgerTransaction, error) {
	if s.driver == "sqlite" {
		r, err := s.sqlite.GetLedgerTransaction(ctx, sqlitedb.GetLedgerTransactionParams{TenantID: tenantID, ID: id})
		if err != nil {
			return nil, normalize(err)
		}
		return mapSQLiteLedger(r), nil
	}
	r, err := s.postgres.GetLedgerTransaction(ctx, postgresdb.GetLedgerTransactionParams{TenantID: tenantID, ID: id})
	if err != nil {
		return nil, normalize(err)
	}
	return mapPostgresLedger(r), nil
}

func (s *Store) ListLedgerTransactions(ctx context.Context, tenantID string, start, end time.Time, limit, offset int) ([]model.LedgerTransaction, error) {
	if s.driver == "sqlite" {
		rows, err := s.sqlite.ListLedgerTransactions(ctx, sqlitedb.ListLedgerTransactionsParams{
			TenantID: tenantID, OccurredAt: start, OccurredAt_2: end, Limit: int64(limit), Offset: int64(offset),
		})
		if err != nil {
			return nil, normalize(err)
		}
		out := make([]model.LedgerTransaction, 0, len(rows))
		for _, row := range rows {
			out = append(out, *mapSQLiteLedger(row))
		}
		return out, nil
	}
	rows, err := s.postgres.ListLedgerTransactions(ctx, postgresdb.ListLedgerTransactionsParams{
		TenantID: tenantID, OccurredAt: start, OccurredAt_2: end, Limit: int32(limit), Offset: int32(offset),
	})
	if err != nil {
		return nil, normalize(err)
	}
	out := make([]model.LedgerTransaction, 0, len(rows))
	for _, row := range rows {
		out = append(out, *mapPostgresLedger(row))
	}
	return out, nil
}

func (s *Store) UpdateLedgerTransaction(ctx context.Context, v *model.LedgerTransaction) (*model.LedgerTransaction, error) {
	if s.driver == "sqlite" {
		r, err := s.sqlite.UpdateLedgerTransaction(ctx, sqlitedb.UpdateLedgerTransactionParams{
			Type: v.Type, AmountMinor: v.AmountMinor, Currency: v.Currency, Category: v.Category,
			OccurredAt: v.OccurredAt, Merchant: v.Merchant, Note: v.Note, Posted: boolToInt64(v.Posted), TenantID: v.TenantID, ID: v.ID,
		})
		if err != nil {
			return nil, normalize(err)
		}
		return mapSQLiteLedger(r), nil
	}
	r, err := s.postgres.UpdateLedgerTransaction(ctx, postgresdb.UpdateLedgerTransactionParams{
		Type: v.Type, AmountMinor: v.AmountMinor, Currency: v.Currency, Category: v.Category,
		OccurredAt: v.OccurredAt, Merchant: v.Merchant, Note: v.Note, Posted: v.Posted, TenantID: v.TenantID, ID: v.ID,
	})
	if err != nil {
		return nil, normalize(err)
	}
	return mapPostgresLedger(r), nil
}

func (s *Store) DeleteLedgerTransaction(ctx context.Context, tenantID, id string) error {
	if s.driver == "sqlite" {
		n, err := s.sqlite.DeleteLedgerTransaction(ctx, sqlitedb.DeleteLedgerTransactionParams{TenantID: tenantID, ID: id})
		return rowsAffected(n, err)
	}
	n, err := s.postgres.DeleteLedgerTransaction(ctx, postgresdb.DeleteLedgerTransactionParams{TenantID: tenantID, ID: id})
	return rowsAffected(n, err)
}

func (s *Store) SummarizeLedgerTransactions(ctx context.Context, tenantID string, start, end time.Time) ([]model.LedgerSummaryItem, error) {
	if s.driver == "sqlite" {
		rows, err := s.sqlite.SummarizeLedgerTransactions(ctx, sqlitedb.SummarizeLedgerTransactionsParams{TenantID: tenantID, OccurredAt: start, OccurredAt_2: end})
		if err != nil {
			return nil, normalize(err)
		}
		out := make([]model.LedgerSummaryItem, 0, len(rows))
		for _, r := range rows {
			out = append(out, model.LedgerSummaryItem{Type: r.Type, Currency: r.Currency, Category: r.Category, AmountMinor: r.AmountMinor})
		}
		return out, nil
	}
	rows, err := s.postgres.SummarizeLedgerTransactions(ctx, postgresdb.SummarizeLedgerTransactionsParams{TenantID: tenantID, OccurredAt: start, OccurredAt_2: end})
	if err != nil {
		return nil, normalize(err)
	}
	out := make([]model.LedgerSummaryItem, 0, len(rows))
	for _, r := range rows {
		out = append(out, model.LedgerSummaryItem{Type: r.Type, Currency: r.Currency, Category: r.Category, AmountMinor: r.AmountMinor})
	}
	return out, nil
}

func mapSQLiteLedger(v sqlitedb.LedgerTransaction) *model.LedgerTransaction {
	return &model.LedgerTransaction{ID: v.ID, TenantID: v.TenantID, Type: v.Type, AmountMinor: v.AmountMinor,
		Currency: v.Currency, Category: v.Category, OccurredAt: v.OccurredAt, Merchant: v.Merchant,
		Note: v.Note, Source: v.Source, SourceMessageKey: stringPtr(v.SourceMessageKey), ClientID: v.ClientID,
		Posted:    v.Posted != 0,
		CreatedAt: v.CreatedAt, UpdatedAt: v.UpdatedAt, DeletedAt: timePtr(v.DeletedAt)}
}

func mapPostgresLedger(v postgresdb.LedgerTransaction) *model.LedgerTransaction {
	return &model.LedgerTransaction{ID: v.ID, TenantID: v.TenantID, Type: v.Type, AmountMinor: v.AmountMinor,
		Currency: v.Currency, Category: v.Category, OccurredAt: v.OccurredAt, Merchant: v.Merchant,
		Note: v.Note, Source: v.Source, SourceMessageKey: stringPtr(v.SourceMessageKey), ClientID: v.ClientID,
		Posted:    v.Posted,
		CreatedAt: v.CreatedAt, UpdatedAt: v.UpdatedAt, DeletedAt: timePtr(v.DeletedAt)}
}
