package api_test

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"emailbox/configs"
)

func TestClosedRegistrationAllowsManagedUsersAndOneTimeInvites(t *testing.T) {
	e, store, _, _ := newTestServerWithStore(t)
	adminToken, _ := register(t, e, "root", "root@example.com")
	promoteToAdmin(t, store, "root")
	configs.AppConfig.SaaS.RegistrationMode = configs.RegistrationClosed

	if status, _ := do(t, e, http.MethodPost, "/api/v1/auth/register", "",
		`{"username":"public","password":"secret12"}`); status != http.StatusBadRequest {
		t.Fatalf("关闭注册后公开入口应返回 400，实际 %d", status)
	}

	status, body := do(t, e, http.MethodPost, "/api/v1/admin/users", adminToken,
		`{"username":"managed","password":"secret12"}`)
	if status != http.StatusOK || !strings.Contains(body, `"username":"managed"`) {
		t.Fatalf("管理员建号失败: %d %s", status, body)
	}

	status, body = do(t, e, http.MethodPost, "/api/v1/admin/invites", adminToken,
		`{"valid_hours":24}`)
	if status != http.StatusOK {
		t.Fatalf("生成邀请码失败: %d %s", status, body)
	}
	var minted struct {
		Data struct{ ID, Code string } `json:"data"`
	}
	if err := json.Unmarshal([]byte(body), &minted); err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(minted.Data.Code, "YM1R-") {
		t.Fatalf("邀请码格式异常: %s", body)
	}

	status, body = do(t, e, http.MethodGet, "/api/v1/admin/invites", adminToken, "")
	if status != http.StatusOK || strings.Contains(body, "code_hash") || strings.Contains(body, minted.Data.Code) {
		t.Fatalf("邀请码列表不得回显明文或哈希: %d %s", status, body)
	}

	status, body = do(t, e, http.MethodPost, "/api/v1/auth/invite/redeem", "",
		`{"code":"`+strings.ToLower(minted.Data.Code)+`","username":"invited","password":"secret12"}`)
	if status != http.StatusOK || !strings.Contains(body, `"username":"invited"`) {
		t.Fatalf("邀请码兑换失败: %d %s", status, body)
	}
	if status, _ = do(t, e, http.MethodPost, "/api/v1/auth/invite/redeem", "",
		`{"code":"`+minted.Data.Code+`","username":"second","password":"secret12"}`); status != http.StatusBadRequest {
		t.Fatalf("邀请码二次兑换应失败，实际 %d", status)
	}
}
