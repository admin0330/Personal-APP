package service

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSyncHealthStatusAndSwitch(t *testing.T) {
	current := "HK"
	controller := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/proxies":
			_ = json.NewEncoder(w).Encode(map[string]any{"proxies": map[string]any{
				"Google": map[string]any{"type": "Selector", "now": current, "all": []string{"HK", "JP"}},
				"Auto":   map[string]any{"type": "URLTest", "now": "JP", "all": []string{"HK", "JP"}},
			}})
		case r.Method == http.MethodPut && r.URL.Path == "/proxies/Google":
			var body map[string]string
			_ = json.NewDecoder(r.Body).Decode(&body)
			current = body["name"]
			w.WriteHeader(http.StatusNoContent)
		default:
			http.NotFound(w, r)
		}
	}))
	defer controller.Close()

	s := NewSyncHealthService(controller.URL)
	status, err := s.Status(t.Context())
	if err != nil || !status.Healthy || len(status.Groups) != 1 || status.Groups[0].Current != "HK" {
		t.Fatalf("unexpected status: %#v, %v", status, err)
	}
	if err := s.Switch(t.Context(), "Google", "JP"); err != nil || current != "JP" {
		t.Fatalf("switch failed: current=%q err=%v", current, err)
	}
	if err := s.Switch(t.Context(), "Google", "unknown"); err == nil {
		t.Fatal("unknown node must be rejected")
	}
}
