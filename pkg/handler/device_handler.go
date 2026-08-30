package handler

import (
	"errors"
	"net/http"

	"emailbox/pkg/middleware"
	"emailbox/pkg/model"
	"emailbox/pkg/service"

	"github.com/labstack/echo/v5"
)

// DeviceHandler 挂设备接入的三个管理端点与一个公开兑换端点。
type DeviceHandler struct{ service *service.DeviceService }

func NewDeviceHandler(s *service.DeviceService) *DeviceHandler {
	return &DeviceHandler{service: s}
}

// MintCode 生成一次性短码。需要 tenant:update（与 API Key 同档）：
// 它等价于「签发一把能读全部邮件的钥匙」，不能让只读成员发得出去。
func (h *DeviceHandler) MintCode(c *echo.Context) error {
	v, err := h.service.MintCode(c.Request().Context(), c.Param("tenantID"), middleware.UserID(c))
	if err != nil {
		return failure(c, http.StatusInternalServerError, err)
	}
	return success(c, v, "短码已生成")
}

// Redeem 用短码换设备令牌。
//
// 这是全平台唯一一个「未认证也能写库」的端点，所以它不复用 mailError：
// 那张映射表默认把认不出的错误落到 400，而这里每一种失败都有明确的语义，
// 少一个分支就会把「短码错了」和「请求体写错了」混成一句话。
func (h *DeviceHandler) Redeem(c *echo.Context) error {
	var req model.RedeemDeviceCodeRequest
	if err := c.Bind(&req); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	v, err := h.service.Redeem(c.Request().Context(), req, c.RealIP())
	switch {
	case err == nil:
		return success(c, v, "绑定成功")
	case errors.Is(err, service.ErrDeviceCodeMalformed):
		return failure(c, http.StatusBadRequest, err)
	case errors.Is(err, service.ErrDeviceCodeExpired):
		return failure(c, http.StatusGone, err)
	case errors.Is(err, service.ErrDeviceLimitReached):
		return failure(c, http.StatusForbidden, err)
	case errors.Is(err, service.ErrDeviceCodeInvalid):
		return failure(c, http.StatusUnauthorized, err)
	default:
		return failure(c, http.StatusInternalServerError, err)
	}
}

func (h *DeviceHandler) List(c *echo.Context) error {
	v, err := h.service.List(c.Request().Context(), c.Param("tenantID"))
	if err != nil {
		return failure(c, http.StatusInternalServerError, err)
	}
	return success(c, v, "获取成功")
}

// Revoke 撤销一台设备。跨租户的 deviceID 由 SQL 的 WHERE tenant_id 挡成 0 行，
// 落到 404——与账号、分组的口径一致。
func (h *DeviceHandler) Revoke(c *echo.Context) error {
	if err := h.service.Revoke(c.Request().Context(), c.Param("tenantID"), c.Param("deviceID")); err != nil {
		return mailError(c, err)
	}
	return success(c, nil, "已撤销该设备")
}
