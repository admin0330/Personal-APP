package model

import "time"

const (
	LedgerExpense = "expense"
	LedgerIncome  = "income"
	LedgerManual  = "manual"
	LedgerEmail   = "email"
)

var LedgerCurrencies = map[string]bool{"CNY": true, "USD": true, "HKD": true}

type LedgerTransaction struct {
	ID               string    `json:"id"`
	TenantID         string    `json:"tenant_id"`
	Type             string    `json:"type"`
	AmountMinor      int64     `json:"amount_minor"`
	Currency         string    `json:"currency"`
	Category         string    `json:"category"`
	OccurredAt       time.Time `json:"occurred_at"`
	Merchant         string    `json:"merchant"`
	Note             string    `json:"note"`
	Source           string    `json:"source"`
	SourceMessageKey *string   `json:"source_message_key,omitempty"`
	ClientID         string    `json:"client_id"`
	// Posted = 已入账。false 表示仅建议（如邮件识别的账单），不计入结余汇总。
	Posted    bool       `json:"posted"`
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
	DeletedAt *time.Time `json:"-"`
}

type LedgerSummaryItem struct {
	Type        string `json:"type"`
	Currency    string `json:"currency"`
	Category    string `json:"category"`
	AmountMinor int64  `json:"amount_minor"`
}

type LedgerSummary struct {
	Month string              `json:"month"`
	Items []LedgerSummaryItem `json:"items"`
}

type CreateLedgerTransactionRequest struct {
	Type             string    `json:"type"`
	AmountMinor      int64     `json:"amount_minor"`
	Currency         string    `json:"currency"`
	Category         string    `json:"category"`
	OccurredAt       time.Time `json:"occurred_at"`
	Merchant         string    `json:"merchant"`
	Note             string    `json:"note"`
	Source           string    `json:"source"`
	SourceMessageKey *string   `json:"source_message_key"`
	ClientID         string    `json:"client_id"`
	// 不传时按来源决定：manual 默认已入账，email 默认未入账。
	Posted *bool `json:"posted,omitempty"`
}

type UpdateLedgerTransactionRequest struct {
	Type        *string    `json:"type"`
	AmountMinor *int64     `json:"amount_minor"`
	Currency    *string    `json:"currency"`
	Category    *string    `json:"category"`
	OccurredAt  *time.Time `json:"occurred_at"`
	Merchant    *string    `json:"merchant"`
	Note        *string    `json:"note"`
	Posted      *bool      `json:"posted,omitempty"`
}
