package api_test

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestDeviceCodeLifecycle(t *testing.T) {
	e, _, _, clock := newTestServerWithStore(t)
	session, tenantID := register(t, e, "device-owner", "device@example.com")
	base := "/api/v1/tenants/" + tenantID

	status, body := do(t, e, http.MethodPost, base+"/device-codes", session, "")
	if status != http.StatusOK {
		t.Fatalf("生成短码失败: %d %s", status, body)
	}
	var minted struct {
		Data struct {
			Code string `json:"code"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(body), &minted); err != nil {
		t.Fatal(err)
	}
	if len(minted.Data.Code) != 8 {
		t.Fatalf("短码应为 8 位，实际 %q", minted.Data.Code)
	}

	status, body = do(t, e, http.MethodPost, "/api/v1/auth/device-code/redeem", "",
		`{"code":"`+strings.ToLower(minted.Data.Code)+`","device_name":"Ym1r 手机"}`)
	if status != http.StatusOK {
		t.Fatalf("兑换短码失败: %d %s", status, body)
	}
	var redeemed struct {
		Data struct {
			Token    string `json:"token"`
			TenantID string `json:"tenant_id"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(body), &redeemed); err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(redeemed.Data.Token, "ebxd_") || redeemed.Data.TenantID != tenantID {
		t.Fatalf("设备令牌响应异常: %s", body)
	}
	deviceToken := redeemed.Data.Token

	if status, body = doKey(t, e, http.MethodGet, "/api/v1/auth/key-session", deviceToken); status != http.StatusOK {
		t.Fatalf("设备令牌应能完成单密钥登录: %d %s", status, body)
	}
	if status, body = doKey(t, e, http.MethodGet, base+"/mail/groups", deviceToken); status != http.StatusOK {
		t.Fatalf("设备令牌应能读取邮件: %d %s", status, body)
	}
	if status, _ = doKey(t, e, http.MethodGet, base+"/ledger/summary?month=2026-08", deviceToken); status != http.StatusForbidden {
		t.Fatalf("设备令牌不得继承账本权限，实际 %d", status)
	}
	if status, _ = do(t, e, http.MethodPost, "/api/v1/auth/device-code/redeem", "",
		`{"code":"`+minted.Data.Code+`"}`); status != http.StatusUnauthorized {
		t.Fatalf("短码二次兑换应 401，实际 %d", status)
	}

	status, body = do(t, e, http.MethodGet, base+"/devices", session, "")
	if status != http.StatusOK || !strings.Contains(body, `"label":"Ym1r 手机"`) || strings.Contains(body, deviceToken) {
		t.Fatalf("设备列表必须可识别设备且不得泄露令牌: %d %s", status, body)
	}
	var listed struct {
		Data []struct {
			ID string `json:"id"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(body), &listed); err != nil || len(listed.Data) != 1 {
		t.Fatalf("设备列表响应异常: %v %s", err, body)
	}
	if status, body = do(t, e, http.MethodPost, base+"/devices/"+listed.Data[0].ID+"/revoke", session, ""); status != http.StatusOK {
		t.Fatalf("撤销设备失败: %d %s", status, body)
	}
	if status, _ = doKey(t, e, http.MethodGet, "/api/v1/auth/key-session", deviceToken); status != http.StatusUnauthorized {
		t.Fatalf("撤销后令牌必须立即 401，实际 %d", status)
	}

	status, body = do(t, e, http.MethodPost, base+"/device-codes", session, "")
	if status != http.StatusOK {
		t.Fatalf("再次生成短码失败: %d %s", status, body)
	}
	if err := json.Unmarshal([]byte(body), &minted); err != nil {
		t.Fatal(err)
	}
	clock.Advance(9 * time.Minute)
	if status, _ = do(t, e, http.MethodPost, "/api/v1/auth/device-code/redeem", "",
		`{"code":"`+minted.Data.Code+`"}`); status != http.StatusGone {
		t.Fatalf("过期短码应 410，实际 %d", status)
	}
}
