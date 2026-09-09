package service

import (
	"context"
	"errors"
	"strings"

	"emailbox/pkg/model"
	"emailbox/pkg/repo"

	"github.com/google/uuid"
)

type NoteService struct{ store *repo.Store }

func NewNoteService(store *repo.Store) *NoteService { return &NoteService{store: store} }

func validateNote(title, content string) error {
	if strings.TrimSpace(title) == "" {
		return errors.New("标题不能为空")
	}
	if len(title) > 120 {
		return errors.New("标题不能超过 120 个字符")
	}
	if len(content) > 50000 {
		return errors.New("内容不能超过 50000 个字符")
	}
	return nil
}

func (s *NoteService) List(ctx context.Context, tenantID string) ([]model.Note, error) {
	return s.store.ListNotes(ctx, tenantID)
}

func (s *NoteService) Create(ctx context.Context, tenantID string, req model.CreateNoteRequest) (*model.Note, error) {
	req.Title = strings.TrimSpace(req.Title)
	if err := validateNote(req.Title, req.Content); err != nil {
		return nil, err
	}
	v := &model.Note{ID: uuid.NewString(), TenantID: tenantID, Title: req.Title, Content: req.Content, IsPinned: req.IsPinned, IsCompleted: req.IsCompleted}
	if err := s.store.CreateNote(ctx, v); err != nil {
		return nil, err
	}
	return s.store.GetNote(ctx, tenantID, v.ID)
}

func (s *NoteService) Update(ctx context.Context, tenantID, id string, req model.UpdateNoteRequest) (*model.Note, error) {
	v, err := s.store.GetNote(ctx, tenantID, id)
	if err != nil {
		return nil, err
	}
	if req.Title != nil {
		v.Title = strings.TrimSpace(*req.Title)
	}
	if req.Content != nil {
		v.Content = *req.Content
	}
	if req.IsPinned != nil {
		v.IsPinned = *req.IsPinned
	}
	if req.IsCompleted != nil {
		v.IsCompleted = *req.IsCompleted
	}
	if err := validateNote(v.Title, v.Content); err != nil {
		return nil, err
	}
	if err := s.store.UpdateNote(ctx, v); err != nil {
		return nil, err
	}
	return s.store.GetNote(ctx, tenantID, id)
}

func (s *NoteService) Delete(ctx context.Context, tenantID, id string) error {
	return s.store.DeleteNote(ctx, tenantID, id)
}
