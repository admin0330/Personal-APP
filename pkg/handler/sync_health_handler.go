package handler

import (
	"net/http"

	"emailbox/pkg/model"
	"emailbox/pkg/service"

	"github.com/labstack/echo/v5"
)

type SyncHealthHandler struct{ service *service.SyncHealthService }

func NewSyncHealthHandler(s *service.SyncHealthService) *SyncHealthHandler {
	return &SyncHealthHandler{service: s}
}

func (h *SyncHealthHandler) Get(c *echo.Context) error {
	status, err := h.service.Status(c.Request().Context())
	if err != nil {
		return failure(c, http.StatusBadGateway, err)
	}
	return success(c, status, "同步链路正常")
}

func (h *SyncHealthHandler) Switch(c *echo.Context) error {
	var req model.SwitchProxyNodeRequest
	if err := c.Bind(&req); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	if err := h.service.Switch(c.Request().Context(), req.Group, req.Node); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, nil, "节点已切换")
}
