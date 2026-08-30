package service

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"emailbox/pkg/model"
	"emailbox/pkg/repo"

	"github.com/google/uuid"
)

type LedgerService struct{ store *repo.Store }

func NewLedgerService(store *repo.Store) *LedgerService { return &LedgerService{store: store} }

func (s *LedgerService) Create(ctx context.Context, tenantID string, req model.CreateLedgerTransactionRequest) (*model.LedgerTransaction, error) {
	v := &model.LedgerTransaction{
		ID: uuid.NewString(), TenantID: tenantID, Type: req.Type, AmountMinor: req.AmountMinor,
		Currency: strings.ToUpper(strings.TrimSpace(req.Currency)), Category: strings.TrimSpace(req.Category),
		OccurredAt: req.OccurredAt.UTC(), Merchant: strings.TrimSpace(req.Merchant), Note: strings.TrimSpace(req.Note),
		Source: req.Source, SourceMessageKey: trimString(req.SourceMessageKey), ClientID: strings.TrimSpace(req.ClientID),
	}
	if v.Source == "" {
		v.Source = model.LedgerManual
	}
	// 入账默认值按来源：手动记录视为已入账；邮件识别的账单只是建议，默认未入账。
	if req.Posted != nil {
		v.Posted = *req.Posted
	} else {
		v.Posted = v.Source == model.LedgerManual
	}
	if err := validateLedger(v); err != nil {
		return nil, err
	}
	return s.store.CreateLedgerTransaction(ctx, v)
}

func (s *LedgerService) List(ctx context.Context, tenantID, month string, limit, offset int) ([]model.LedgerTransaction, error) {
	start, end, err := ledgerMonthRange(month)
	if err != nil {
		return nil, err
	}
	if limit <= 0 || limit > 200 {
		limit = 100
	}
	if offset < 0 {
		offset = 0
	}
	return s.store.ListLedgerTransactions(ctx, tenantID, start, end, limit, offset)
}

func (s *LedgerService) Update(ctx context.Context, tenantID, id string, req model.UpdateLedgerTransactionRequest) (*model.LedgerTransaction, error) {
	v, err := s.store.GetLedgerTransaction(ctx, tenantID, id)
	if err != nil {
		return nil, err
	}
	if req.Type != nil {
		v.Type = *req.Type
	}
	if req.AmountMinor != nil {
		v.AmountMinor = *req.AmountMinor
	}
	if req.Currency != nil {
		v.Currency = strings.ToUpper(strings.TrimSpace(*req.Currency))
	}
	if req.Category != nil {
		v.Category = strings.TrimSpace(*req.Category)
	}
	if req.OccurredAt != nil {
		v.OccurredAt = req.OccurredAt.UTC()
	}
	if req.Merchant != nil {
		v.Merchant = strings.TrimSpace(*req.Merchant)
	}
	if req.Note != nil {
		v.Note = strings.TrimSpace(*req.Note)
	}
	if req.Posted != nil {
		v.Posted = *req.Posted
	}
	if err := validateLedger(v); err != nil {
		return nil, err
	}
	return s.store.UpdateLedgerTransaction(ctx, v)
}

func (s *LedgerService) Delete(ctx context.Context, tenantID, id string) error {
	return s.store.DeleteLedgerTransaction(ctx, tenantID, id)
}

func (s *LedgerService) Summary(ctx context.Context, tenantID, month string) (*model.LedgerSummary, error) {
	start, end, err := ledgerMonthRange(month)
	if err != nil {
		return nil, err
	}
	items, err := s.store.SummarizeLedgerTransactions(ctx, tenantID, start, end)
	if err != nil {
		return nil, err
	}
	return &model.LedgerSummary{Month: start.In(hongKong).Format("2006-01"), Items: items}, nil
}

func validateLedger(v *model.LedgerTransaction) error {
	if v.Type != model.LedgerExpense && v.Type != model.LedgerIncome {
		return errors.New("账目类型必须是 expense 或 income")
	}
	if v.AmountMinor <= 0 {
		return errors.New("金额必须大于 0")
	}
	if !model.LedgerCurrencies[v.Currency] {
		return errors.New("币种仅支持 CNY、USD、HKD")
	}
	if v.Category == "" || len([]rune(v.Category)) > 40 {
		return errors.New("分类不能为空且不能超过 40 个字符")
	}
	if v.OccurredAt.IsZero() {
		return errors.New("发生时间不能为空")
	}
	if len([]rune(v.Merchant)) > 100 || len([]rune(v.Note)) > 500 {
		return errors.New("商户或备注过长")
	}
	if v.Source != model.LedgerManual && v.Source != model.LedgerEmail {
		return errors.New("账目来源必须是 manual 或 email")
	}
	if v.Source == model.LedgerEmail && v.SourceMessageKey == nil {
		return errors.New("邮件账单必须包含 source_message_key")
	}
	if _, err := uuid.Parse(v.ClientID); err != nil {
		return errors.New("client_id 必须是 UUID")
	}
	return nil
}

var hongKong = time.FixedZone("Asia/Hong_Kong", 8*60*60)

func ledgerMonthRange(month string) (time.Time, time.Time, error) {
	month = strings.TrimSpace(month)
	if month == "" {
		month = time.Now().In(hongKong).Format("2006-01")
	}
	startLocal, err := time.ParseInLocation("2006-01", month, hongKong)
	if err != nil || startLocal.Format("2006-01") != month {
		return time.Time{}, time.Time{}, fmt.Errorf("month 必须是 YYYY-MM")
	}
	return startLocal.UTC(), startLocal.AddDate(0, 1, 0).UTC(), nil
}

func trimString(v *string) *string {
	if v == nil {
		return nil
	}
	s := strings.TrimSpace(*v)
	if s == "" {
		return nil
	}
	return &s
}
