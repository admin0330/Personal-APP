package api_test

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"
)

func TestNotesLifecycleAndTenantIsolation(t *testing.T) {
	e := newTestServer(t)
	alice, aliceTenant := register(t, e, "alice", "alice@example.com")
	bob, bobTenant := register(t, e, "bobby", "bob@example.com")
	aliceBase := "/api/v1/tenants/" + aliceTenant + "/notes"
	bobBase := "/api/v1/tenants/" + bobTenant + "/notes"

	status, body := do(t, e, http.MethodPost, aliceBase, alice,
		`{"title":"第一条","content":"只属于 Alice","is_pinned":true}`)
	if status != http.StatusOK {
		t.Fatalf("创建笔记失败: %d %s", status, body)
	}
	var created struct {
		Data struct {
			ID string `json:"id"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(body), &created); err != nil {
		t.Fatal(err)
	}

	status, body = do(t, e, http.MethodGet, aliceBase, alice, "")
	if status != http.StatusOK || !strings.Contains(body, "第一条") {
		t.Fatalf("列表缺少笔记: %d %s", status, body)
	}

	status, body = do(t, e, http.MethodPatch, aliceBase+"/"+created.Data.ID, alice,
		`{"title":"已修改","is_pinned":false}`)
	if status != http.StatusOK || !strings.Contains(body, "已修改") {
		t.Fatalf("修改失败: %d %s", status, body)
	}

	if status, _ = do(t, e, http.MethodPatch, bobBase+"/"+created.Data.ID, bob,
		`{"title":"越权"}`); status != http.StatusNotFound {
		t.Fatalf("跨租户笔记 ID 应返回 404，实际 %d", status)
	}
	if status, _ = do(t, e, http.MethodGet, aliceBase, bob, ""); status != http.StatusForbidden {
		t.Fatalf("非成员读取别人笔记应返回 403，实际 %d", status)
	}

	if status, body = do(t, e, http.MethodDelete, aliceBase+"/"+created.Data.ID, alice, ""); status != http.StatusOK {
		t.Fatalf("删除失败: %d %s", status, body)
	}
}
