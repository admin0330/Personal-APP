package handler

import (
	"encoding/json"
	"time"

	"emailbox/pkg/service"

	"github.com/labstack/echo/v5"
)

type MailNotificationHandler struct {
	service *service.MailNotificationService
}

func NewMailNotificationHandler(s *service.MailNotificationService) *MailNotificationHandler {
	return &MailNotificationHandler{service: s}
}

// Stream 保持一条轻量 SSE 连接。事件只带 account_id 与新信数量；
// App 收到后从服务器缓存预加载邮件，不把邮件内容塞进锁屏通知。
func (h *MailNotificationHandler) Stream(c *echo.Context) error {
	writer, err := NewSSEWriter(c)
	if err != nil {
		c.Logger().Warn("通知 SSE 写超时未能取消", "error", err)
	}
	events, unsubscribe := h.service.Subscribe(c.Param("tenantID"))
	defer unsubscribe()
	if err := writer.Send(0, "ready", `{}`); err != nil {
		return nil
	}

	keepalive := time.NewTicker(SSEKeepaliveInterval)
	defer keepalive.Stop()
	for {
		select {
		case <-c.Request().Context().Done():
			return nil
		case event := <-events:
			// 事件只有字符串与整数两个字段，Marshal 不可能失败；
			// 真失败了也只跳过这一条，不影响 SSE 连接本身。
			data, err := json.Marshal(event)
			if err != nil {
				continue
			}
			if err := writer.Send(event.Seq, "mail", string(data)); err != nil {
				return nil
			}
		case <-keepalive.C:
			if err := writer.Keepalive(); err != nil {
				return nil
			}
		}
	}
}
