package handler

import (
	"net/http"
	"strings"

	"emailbox/pkg/model"
	"emailbox/pkg/service"

	"github.com/labstack/echo/v5"
)

type APIKeyHandler struct {
	service *service.APIKeyService
	devices *service.DeviceService
}

func NewAPIKeyHandler(s *service.APIKeyService) *APIKeyHandler { return &APIKeyHandler{service: s} }

// WithDevices 接上设备令牌的校验能力。
//
// Session 这个端点必须同时认两种 Bearer 凭据：App 的绑定流程是「输入一把凭据 →
// 换回工作空间 ID」，而设备令牌兑换完之后走的正是这条路径（客户端不为此新增分支）。
func (h *APIKeyHandler) WithDevices(d *service.DeviceService) *APIKeyHandler {
	h.devices = d
	return h
}

// Session 校验 Authorization 里的只读凭据，并返回它所属的租户。
// 这是 Android 的单输入框登录入口；不创建 Cookie，也不返回用户或凭据明文。
func (h *APIKeyHandler) Session(c *echo.Context) error {
	const prefix = "Bearer "
	raw := c.Request().Header.Get(echo.HeaderAuthorization)
	if len(raw) <= len(prefix) || !strings.EqualFold(raw[:len(prefix)], prefix) {
		return failure(c, http.StatusUnauthorized, service.ErrAPIKeyInvalid)
	}
	token := strings.TrimSpace(raw[len(prefix):])
	if strings.HasPrefix(token, model.DeviceTokenPrefix) {
		if h.devices == nil {
			return failure(c, http.StatusUnauthorized, service.ErrAPIKeyInvalid)
		}
		identity, err := h.devices.Authenticate(c.Request().Context(), token)
		if err != nil {
			return failure(c, http.StatusUnauthorized, service.ErrAPIKeyInvalid)
		}
		return success(c, map[string]any{"tenant_id": identity.TenantID}, "验证成功")
	}
	identity, err := h.service.Authenticate(c.Request().Context(), token)
	if err != nil {
		return failure(c, http.StatusUnauthorized, service.ErrAPIKeyInvalid)
	}
	return success(c, map[string]any{"tenant_id": identity.TenantID, "scopes": identity.Scopes}, "验证成功")
}

type apiKeyScopesRequest struct {
	Scopes []string `json:"scopes"`
}

func (h *APIKeyHandler) SetScopes(c *echo.Context) error {
	var req apiKeyScopesRequest
	if err := c.Bind(&req); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	v, err := h.service.SetScopes(c.Request().Context(), c.Param("tenantID"), req.Scopes)
	if err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, v, "API Key 权限已更新")
}

// Get 回显当前 Key。还没生成时 data 为 null，页面据此显示「生成」而不是「重置」。
func (h *APIKeyHandler) Get(c *echo.Context) error {
	v, err := h.service.Get(c.Request().Context(), c.Param("tenantID"))
	if err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, v, "获取成功")
}

// Reset 生成新 Key 并覆盖旧的。这两个端点都要 tenant:update 权限，
// 而 API Key 自己的角色没有这一项——它读不到也重置不了自己。
func (h *APIKeyHandler) Reset(c *echo.Context) error {
	v, err := h.service.Reset(c.Request().Context(), c.Param("tenantID"))
	if err != nil {
		return failure(c, http.StatusInternalServerError, err)
	}
	return success(c, v, "已生成新的 API Key")
}
