package repo

import (
	"context"

	postgresdb "emailbox/db/generated/postgres"
	sqlitedb "emailbox/db/generated/sqlite"
	"emailbox/pkg/model"
)

func mapSQLiteNote(v sqlitedb.Note) *model.Note {
	return &model.Note{ID: v.ID, TenantID: v.TenantID, Title: v.Title, Content: v.Content, IsPinned: v.IsPinned != 0, IsCompleted: v.IsCompleted != 0, CreatedAt: v.CreatedAt, UpdatedAt: v.UpdatedAt}
}
func mapPostgresNote(v postgresdb.Note) *model.Note {
	return &model.Note{ID: v.ID, TenantID: v.TenantID, Title: v.Title, Content: v.Content, IsPinned: v.IsPinned, IsCompleted: v.IsCompleted, CreatedAt: v.CreatedAt, UpdatedAt: v.UpdatedAt}
}

func (s *Store) CreateNote(ctx context.Context, v *model.Note) error {
	if s.driver == "sqlite" {
		return normalize(s.sqlite.CreateNote(ctx, sqlitedb.CreateNoteParams{ID: v.ID, TenantID: v.TenantID, Title: v.Title, Content: v.Content, IsPinned: boolToInt64(v.IsPinned), IsCompleted: boolToInt64(v.IsCompleted)}))
	}
	return normalize(s.postgres.CreateNote(ctx, postgresdb.CreateNoteParams{ID: v.ID, TenantID: v.TenantID, Title: v.Title, Content: v.Content, IsPinned: v.IsPinned, IsCompleted: v.IsCompleted}))
}

func (s *Store) ListNotes(ctx context.Context, tenantID string) ([]model.Note, error) {
	var out []model.Note
	if s.driver == "sqlite" {
		rows, err := s.sqlite.ListNotes(ctx, tenantID)
		if err != nil {
			return nil, err
		}
		for _, row := range rows {
			out = append(out, *mapSQLiteNote(row))
		}
	} else {
		rows, err := s.postgres.ListNotes(ctx, tenantID)
		if err != nil {
			return nil, err
		}
		for _, row := range rows {
			out = append(out, *mapPostgresNote(row))
		}
	}
	if out == nil {
		out = []model.Note{}
	}
	return out, nil
}

func (s *Store) GetNote(ctx context.Context, tenantID, id string) (*model.Note, error) {
	if s.driver == "sqlite" {
		v, err := s.sqlite.GetNote(ctx, sqlitedb.GetNoteParams{TenantID: tenantID, ID: id})
		return mapSQLiteNote(v), normalize(err)
	}
	v, err := s.postgres.GetNote(ctx, postgresdb.GetNoteParams{TenantID: tenantID, ID: id})
	return mapPostgresNote(v), normalize(err)
}

func (s *Store) UpdateNote(ctx context.Context, v *model.Note) error {
	var n int64
	var err error
	if s.driver == "sqlite" {
		n, err = s.sqlite.UpdateNote(ctx, sqlitedb.UpdateNoteParams{Title: v.Title, Content: v.Content, IsPinned: boolToInt64(v.IsPinned), IsCompleted: boolToInt64(v.IsCompleted), TenantID: v.TenantID, ID: v.ID})
	} else {
		n, err = s.postgres.UpdateNote(ctx, postgresdb.UpdateNoteParams{Title: v.Title, Content: v.Content, IsPinned: v.IsPinned, IsCompleted: v.IsCompleted, TenantID: v.TenantID, ID: v.ID})
	}
	return rowsAffected(n, err)
}

func (s *Store) DeleteNote(ctx context.Context, tenantID, id string) error {
	var n int64
	var err error
	if s.driver == "sqlite" {
		n, err = s.sqlite.DeleteNote(ctx, sqlitedb.DeleteNoteParams{TenantID: tenantID, ID: id})
	} else {
		n, err = s.postgres.DeleteNote(ctx, postgresdb.DeleteNoteParams{TenantID: tenantID, ID: id})
	}
	return rowsAffected(n, err)
}
