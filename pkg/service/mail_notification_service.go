package service

import (
	"context"
	"log/slog"
	"sync"
	"sync/atomic"
	"time"

	"emailbox/pkg/mailer"
	"emailbox/pkg/model"
	"emailbox/pkg/repo"
)

// MailNotification 是发给个人 Android 客户端的最小事件。
// 不带主题、正文或发件人，锁屏通知不会意外泄露邮件内容。
type MailNotification struct {
	Seq       int64  `json:"-"`
	AccountID string `json:"account_id"`
	NewCount  int    `json:"new_count"`
}

type notificationSnapshotKey struct{ tenantID, accountID string }

// MailNotificationService 在服务器侧预取收件箱，并把“有几封新信”推给订阅客户端。
// 第一个客户端订阅某租户后启动一个 5 分钟轮询；服务重启后的首轮只建基线，不误报旧信。
type MailNotificationService struct {
	store    *repo.Store
	messages *MessageService
	interval time.Duration
	ctx      context.Context
	cancel   context.CancelFunc

	mu          sync.Mutex
	started     map[string]bool
	subscribers map[string]map[chan MailNotification]struct{}
	snapshots   map[notificationSnapshotKey]map[string]struct{}
	seq         atomic.Int64
}

func NewMailNotificationService(
	store *repo.Store, messages *MessageService, interval time.Duration,
) *MailNotificationService {
	ctx, cancel := context.WithCancel(context.Background())
	return &MailNotificationService{
		store: store, messages: messages, interval: interval, ctx: ctx, cancel: cancel,
		started: make(map[string]bool), subscribers: make(map[string]map[chan MailNotification]struct{}),
		snapshots: make(map[notificationSnapshotKey]map[string]struct{}),
	}
}

func (s *MailNotificationService) Close() { s.cancel() }

func (s *MailNotificationService) Subscribe(tenantID string) (<-chan MailNotification, func()) {
	ch := make(chan MailNotification, 8)
	s.mu.Lock()
	if s.subscribers[tenantID] == nil {
		s.subscribers[tenantID] = make(map[chan MailNotification]struct{})
	}
	s.subscribers[tenantID][ch] = struct{}{}
	if !s.started[tenantID] {
		s.started[tenantID] = true
		go s.runTenant(tenantID)
	}
	s.mu.Unlock()
	return ch, func() {
		s.mu.Lock()
		delete(s.subscribers[tenantID], ch)
		s.mu.Unlock()
	}
}

func (s *MailNotificationService) runTenant(tenantID string) {
	s.pollTenant(tenantID)
	ticker := time.NewTicker(s.interval)
	defer ticker.Stop()
	for {
		select {
		case <-s.ctx.Done():
			return
		case <-ticker.C:
			s.pollTenant(tenantID)
		}
	}
}

func (s *MailNotificationService) pollTenant(tenantID string) {
	accounts, err := s.activeAccounts(tenantID)
	if err != nil {
		slog.Warn("预取邮件账号列表失败", "tenant_id", tenantID, "error", err)
		return
	}
	// 慢账号不能阻塞后续账号的缓存和通知；每个租户最多两条上游请求。
	var workers sync.WaitGroup
	permits := make(chan struct{}, 2)
	for _, account := range accounts {
		select {
		case permits <- struct{}{}:
		case <-s.ctx.Done():
			workers.Wait()
			return
		}
		workers.Add(1)
		go func(account model.MailAccount) {
			defer workers.Done()
			defer func() { <-permits }()
			s.pollAccount(tenantID, account)
		}(account)
	}
	workers.Wait()
}

func (s *MailNotificationService) pollAccount(tenantID string, account model.MailAccount) {
	ctx, cancel := context.WithTimeout(s.ctx, 45*time.Second)
	result, err := s.messages.List(ctx, tenantID, account.ID, mailer.ListOptions{
		Folder: mailer.FolderInbox, Top: 25,
	})
	cancel()
	if err != nil {
		if s.ctx.Err() == nil {
			slog.Warn("预取收件箱失败", "tenant_id", tenantID, "account_id", account.ID, "error", err)
		}
		return
	}

	key := notificationSnapshotKey{tenantID: tenantID, accountID: account.ID}
	next := messageIDSet(result.Items)
	s.mu.Lock()
	previous, hadBaseline := s.snapshots[key]
	s.snapshots[key] = next
	s.mu.Unlock()
	if hadBaseline {
		if count := newMessageCount(previous, result.Items); count > 0 {
			s.broadcast(tenantID, MailNotification{
				Seq: s.seq.Add(1), AccountID: account.ID, NewCount: count,
			})
		}
	}
}

func (s *MailNotificationService) activeAccounts(tenantID string) ([]model.MailAccount, error) {
	var all []model.MailAccount
	for page := 1; ; page++ {
		rows, err := s.store.ListMailAccountsPage(s.ctx, tenantID, model.AccountFilter{
			Status: string(model.AccountStatusActive), Page: page, Limit: model.MaxAccountPageSize,
			Sort: "sort_order", Order: "asc",
		})
		if err != nil {
			return nil, err
		}
		all = append(all, rows...)
		if len(rows) < model.MaxAccountPageSize {
			return all, nil
		}
	}
}

func (s *MailNotificationService) broadcast(tenantID string, event MailNotification) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for ch := range s.subscribers[tenantID] {
		select {
		case ch <- event:
		default:
			// 客户端重连时会先预加载缓存；慢客户端不值得堵住服务器轮询。
		}
	}
}

func messageIDSet(items []mailer.Message) map[string]struct{} {
	set := make(map[string]struct{}, len(items))
	for _, item := range items {
		set[string(item.Folder)+"\x00"+item.IDMode+"\x00"+item.ID] = struct{}{}
	}
	return set
}

func newMessageCount(previous map[string]struct{}, current []mailer.Message) int {
	count := 0
	for key := range messageIDSet(current) {
		if _, ok := previous[key]; !ok {
			count++
		}
	}
	return count
}
