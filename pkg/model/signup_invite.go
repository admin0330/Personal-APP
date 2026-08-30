package model

import "time"

type SignupInvite struct {
	ID         string     `json:"id"`
	CodeHash   string     `json:"-"`
	CreatedBy  string     `json:"created_by"`
	ExpiresAt  time.Time  `json:"expires_at"`
	RedeemedAt *time.Time `json:"redeemed_at,omitempty"`
	CreatedAt  time.Time  `json:"created_at"`
}

type SignupInviteCreated struct {
	SignupInvite
	// Code 只在创建响应中出现一次，数据库只保存 SHA-256。
	Code string `json:"code"`
}
