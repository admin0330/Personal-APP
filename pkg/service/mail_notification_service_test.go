package service

import (
	"testing"
	"time"

	"emailbox/pkg/mailer"
)

func TestMailNotificationIdentityAndCache(t *testing.T) {
	previous := messageIDSet([]mailer.Message{{Folder: mailer.FolderInbox, IDMode: "uid", ID: "1"}})
	current := []mailer.Message{
		{Folder: mailer.FolderInbox, IDMode: "uid", ID: "1"},
		{Folder: mailer.FolderInbox, IDMode: "uid", ID: "2"},
		{Folder: mailer.FolderJunk, IDMode: "uid", ID: "1"},
	}
	if got := newMessageCount(previous, current); got != 2 {
		t.Fatalf("newMessageCount=%d, want 2", got)
	}

	s := &MessageService{cache: make(map[messageCacheKey]messageCacheEntry)}
	result := &MessageListResult{Items: current, Channel: "imap"}
	s.storeListCache("tenant", "account", mailer.ListOptions{Folder: mailer.FolderInbox, Top: 25}, result)
	cached, ok := s.CachedList("tenant", "account", mailer.ListOptions{Folder: mailer.FolderInbox, Top: 2})
	if !ok || len(cached.Items) != 2 || cached.Channel != "imap" {
		t.Fatalf("unexpected cache result: ok=%v result=%+v", ok, cached)
	}
	key := messageCacheKey{tenantID: "tenant", accountID: "account", folder: mailer.FolderInbox}
	entry := s.cache[key]
	entry.fetchedAt = time.Now().Add(-messageCacheTTL - time.Second)
	s.cache[key] = entry
	if _, ok := s.CachedList("tenant", "account", mailer.ListOptions{Folder: mailer.FolderInbox, Top: 2}); ok {
		t.Fatal("expired cache must miss")
	}
}
