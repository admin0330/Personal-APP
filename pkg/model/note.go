package model

import "time"

type Note struct {
	ID          string    `json:"id"`
	TenantID    string    `json:"tenant_id"`
	Title       string    `json:"title"`
	Content     string    `json:"content"`
	IsPinned    bool      `json:"is_pinned"`
	IsCompleted bool      `json:"is_completed"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type CreateNoteRequest struct {
	Title       string `json:"title"`
	Content     string `json:"content"`
	IsPinned    bool   `json:"is_pinned"`
	IsCompleted bool   `json:"is_completed"`
}

type UpdateNoteRequest struct {
	Title       *string `json:"title"`
	Content     *string `json:"content"`
	IsPinned    *bool   `json:"is_pinned"`
	IsCompleted *bool   `json:"is_completed"`
}
