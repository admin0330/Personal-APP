package service

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"

	"emailbox/pkg/model"
)

type SyncHealthService struct {
	baseURL string
	client  *http.Client
}

func NewSyncHealthService(baseURL string) *SyncHealthService {
	baseURL = strings.TrimRight(strings.TrimSpace(baseURL), "/")
	if baseURL == "" {
		baseURL = "http://127.0.0.1:9090"
	}
	return &SyncHealthService{baseURL: baseURL, client: &http.Client{Timeout: 5 * time.Second}}
}

type mihomoProxy struct {
	Type string   `json:"type"`
	Now  string   `json:"now"`
	All  []string `json:"all"`
}

type mihomoProxies struct {
	Proxies map[string]mihomoProxy `json:"proxies"`
}

func (s *SyncHealthService) Status(ctx context.Context) (model.SyncHealth, error) {
	p, err := s.proxies(ctx)
	if err != nil {
		return model.SyncHealth{CheckedAt: time.Now().UTC()}, err
	}
	groups := make([]model.ProxyGroupStatus, 0)
	for name, v := range p.Proxies {
		if v.Type != "Selector" {
			continue
		}
		groups = append(groups, model.ProxyGroupStatus{Name: name, Type: v.Type, Current: v.Now, Choices: v.All})
	}
	sort.Slice(groups, func(i, j int) bool { return groups[i].Name < groups[j].Name })
	return model.SyncHealth{Healthy: true, CheckedAt: time.Now().UTC(), Groups: groups}, nil
}

func (s *SyncHealthService) Switch(ctx context.Context, group, node string) error {
	group, node = strings.TrimSpace(group), strings.TrimSpace(node)
	if group == "" || node == "" {
		return errors.New("代理组和节点不能为空")
	}
	p, err := s.proxies(ctx)
	if err != nil {
		return err
	}
	g, ok := p.Proxies[group]
	if !ok || g.Type != "Selector" {
		return errors.New("代理组不存在或不可手动切换")
	}
	allowed := false
	for _, candidate := range g.All {
		if candidate == node {
			allowed = true
			break
		}
	}
	if !allowed {
		return errors.New("节点不属于该代理组")
	}
	body, _ := json.Marshal(map[string]string{"name": node})
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, s.baseURL+"/proxies/"+url.PathEscape(group), bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := s.client.Do(req)
	if err != nil {
		return fmt.Errorf("代理控制器不可达: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("代理控制器切换失败（HTTP %d）", resp.StatusCode)
	}
	return nil
}

func (s *SyncHealthService) proxies(ctx context.Context) (mihomoProxies, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.baseURL+"/proxies", nil)
	if err != nil {
		return mihomoProxies{}, err
	}
	resp, err := s.client.Do(req)
	if err != nil {
		return mihomoProxies{}, fmt.Errorf("代理控制器不可达: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return mihomoProxies{}, fmt.Errorf("代理控制器异常（HTTP %d）", resp.StatusCode)
	}
	var result mihomoProxies
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return mihomoProxies{}, errors.New("代理控制器返回格式错误")
	}
	return result, nil
}
