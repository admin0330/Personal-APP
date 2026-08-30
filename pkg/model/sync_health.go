package model

import "time"

type ProxyGroupStatus struct {
	Name    string   `json:"name"`
	Type    string   `json:"type"`
	Current string   `json:"current"`
	Choices []string `json:"choices"`
}

type SyncHealth struct {
	Healthy   bool               `json:"healthy"`
	CheckedAt time.Time          `json:"checked_at"`
	Groups    []ProxyGroupStatus `json:"groups"`
}

type SwitchProxyNodeRequest struct {
	Group string `json:"group"`
	Node  string `json:"node"`
}
