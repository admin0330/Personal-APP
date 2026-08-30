package service_test

import (
	"context"
	"testing"
	"time"

	"emailbox/configs"
	"emailbox/pkg/crypto"
	"emailbox/pkg/model"
	"emailbox/pkg/service"
)

func ledgerTenant(t *testing.T) (*service.LedgerService, string) {
	t.Helper()
	configs.AppConfig = &configs.Config{Session: configs.SessionConfig{ExpireHour: 24}}
	store := testStore(t)
	auth, _, err := service.NewAuthService(store).Register(context.Background(), model.RegisterRequest{Username: "ledger-user", Password: "secret12"})
	if err != nil {
		t.Fatal(err)
	}
	return service.NewLedgerService(store), auth.Tenants[0].ID
}

func TestLedgerCreateIsIdempotentAndSummarizesByHongKongMonth(t *testing.T) {
	ledger, tenant := ledgerTenant(t)
	ctx := context.Background()
	request := model.CreateLedgerTransactionRequest{
		Type: model.LedgerExpense, AmountMinor: 1280, Currency: "HKD", Category: "餐饮",
		OccurredAt: time.Date(2026, 8, 31, 16, 30, 0, 0, time.UTC), Source: model.LedgerManual,
		ClientID: "d004b324-041b-4a28-834b-8760ef403b95",
	}
	first, err := ledger.Create(ctx, tenant, request)
	if err != nil {
		t.Fatal(err)
	}
	second, err := ledger.Create(ctx, tenant, request)
	if err != nil {
		t.Fatal(err)
	}
	if first.ID != second.ID {
		t.Fatalf("重复 client_id 应返回同一记录: %s != %s", first.ID, second.ID)
	}

	august, err := ledger.Summary(ctx, tenant, "2026-08")
	if err != nil {
		t.Fatal(err)
	}
	if len(august.Items) != 0 {
		t.Fatalf("UTC 8/31 16:30 在香港已是 9 月，不应计入 8 月: %#v", august.Items)
	}
	september, err := ledger.Summary(ctx, tenant, "2026-09")
	if err != nil {
		t.Fatal(err)
	}
	if len(september.Items) != 1 || september.Items[0].AmountMinor != 1280 {
		t.Fatalf("9 月汇总错误: %#v", september.Items)
	}
}

func TestLedgerRejectsInvalidMoney(t *testing.T) {
	ledger, tenant := ledgerTenant(t)
	_, err := ledger.Create(context.Background(), tenant, model.CreateLedgerTransactionRequest{
		Type: model.LedgerExpense, AmountMinor: 0, Currency: "CNY", Category: "餐饮",
		OccurredAt: time.Now(), Source: model.LedgerManual, ClientID: "d004b324-041b-4a28-834b-8760ef403b95",
	})
	if err == nil {
		t.Fatal("0 金额必须被拒绝")
	}
}

func TestAPIKeyScopesUsePersonalDefaultAndWriteIncludesRead(t *testing.T) {
	configs.AppConfig = &configs.Config{Session: configs.SessionConfig{ExpireHour: 24}}
	store := testStore(t)
	auth, _, err := service.NewAuthService(store).Register(context.Background(), model.RegisterRequest{Username: "scope-user", Password: "secret12"})
	if err != nil {
		t.Fatal(err)
	}
	keys := service.NewAPIKeyService(store, crypto.NewPlaintext())
	view, err := keys.Reset(context.Background(), auth.Tenants[0].ID)
	if err != nil {
		t.Fatal(err)
	}
	identity, err := keys.Authenticate(context.Background(), view.Token)
	if err != nil {
		t.Fatal(err)
	}
	if !identity.Scopes[model.APIKeyScopeLedgerRead] || !identity.Scopes[model.APIKeyScopeLedgerWrite] {
		t.Fatal("个人登录密钥应默认获得账本读写权限")
	}
	if _, err := keys.SetScopes(context.Background(), auth.Tenants[0].ID, []string{model.APIKeyScopeMailRead}); err != nil {
		t.Fatal(err)
	}
	identity, err = keys.Authenticate(context.Background(), view.Token)
	if err != nil {
		t.Fatal(err)
	}
	if identity.Scopes[model.APIKeyScopeLedgerRead] || identity.Scopes[model.APIKeyScopeLedgerWrite] {
		t.Fatal("显式收窄为邮件只读后不得保留账本权限")
	}
	if _, err := keys.SetScopes(context.Background(), auth.Tenants[0].ID, []string{model.APIKeyScopeLedgerWrite}); err != nil {
		t.Fatal(err)
	}
	identity, err = keys.Authenticate(context.Background(), view.Token)
	if err != nil {
		t.Fatal(err)
	}
	if !identity.Scopes[model.APIKeyScopeLedgerRead] || !identity.Scopes[model.APIKeyScopeLedgerWrite] {
		t.Fatal("ledger:write 必须显式包含 ledger:read")
	}
}
