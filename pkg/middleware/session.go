package middleware

import (
	"emailbox/pkg/model"
	"emailbox/pkg/service"
	"net/http"
	"strings"

	"github.com/labstack/echo/v5"
)

const SessionCookie = "session_token"

// bearerPrefix 是 API Key 的传递方式：`Authorization: Bearer ebx_xxx`。
const bearerPrefix = "Bearer "

// tenantScopePrefix 限定 API Key 只在租户作用域下有效。
//
// Key 不属于任何用户，一旦放它进 /user/profile、/tenants、/auth/logout 这类
// 以 user_id 为主语的端点，那些 handler 会拿着一个空 user_id 继续往下走——
// 具体表现取决于每个 handler 恰好怎么写的。与其依赖那种巧合，
// 不如在这里把作用域一次说清楚：Key 只能进 /api/v1/tenants/<id>/**。
const tenantScopePrefix = "/api/v1/tenants/"

type AuthMiddleware struct {
	auth    *service.AuthService
	keys    *service.APIKeyService
	devices *service.DeviceService
}

func NewAuthMiddleware(a *service.AuthService, k *service.APIKeyService, d *service.DeviceService) *AuthMiddleware {
	return &AuthMiddleware{auth: a, keys: k, devices: d}
}

// Require 认证调用方。三条入口，Cookie 优先：
//
//   - 浏览器：HttpOnly 的 session_token Cookie
//   - 脚本 / Agent：Authorization: Bearer ebx_...（租户 API Key）
//   - 手机 App：Authorization: Bearer ebxd_...（设备令牌）
//
// Cookie 优先是有意的：一个人在浏览器里开着页面、同时手边脚本带着 Key 的场景下，
// 他在界面上做的事应当以他自己的身份留痕，而不是记成 api_key。
//
// 两种 Bearer 凭据靠前缀区分而不是「两张表都查一遍」：它们权限相同、
// 走同一份路由，但每次请求多一次落空的查询没有理由。
func (m *AuthMiddleware) Require(next echo.HandlerFunc) echo.HandlerFunc {
	return func(c *echo.Context) error {
		if cookie, e := c.Cookie(SessionCookie); e == nil && cookie.Value != "" {
			_, session, e := m.auth.Session(c.Request().Context(), cookie.Value)
			if e != nil {
				return echo.NewHTTPError(http.StatusUnauthorized, "Session已失效")
			}
			c.Set("user_id", session.UserID)
			c.Set("session", session)
			return next(c)
		}
		if token := bearerToken(c); token != "" {
			if !strings.HasPrefix(c.Request().URL.Path, tenantScopePrefix) {
				return echo.NewHTTPError(http.StatusUnauthorized, "API Key 只能访问工作空间下的接口")
			}
			// Key 与设备令牌都不属于任何用户：user_id 留空，身份由 actor_kind 表达。
			if strings.HasPrefix(token, model.DeviceTokenPrefix) {
				if m.devices == nil {
					return echo.NewHTTPError(http.StatusUnauthorized, "设备令牌无效")
				}
				identity, e := m.devices.Authenticate(c.Request().Context(), token)
				if e != nil {
					return echo.NewHTTPError(http.StatusUnauthorized, "设备令牌无效或已被撤销")
				}
				c.Set("actor_kind", ActorKindDevice)
				c.Set("api_key_tenant_id", identity.TenantID)
				c.Set("api_key_scopes", identity.Scopes)
				return next(c)
			}
			if m.keys == nil {
				return echo.NewHTTPError(http.StatusUnauthorized, "API Key 无效")
			}
			identity, e := m.keys.Authenticate(c.Request().Context(), token)
			if e != nil {
				return echo.NewHTTPError(http.StatusUnauthorized, "API Key 无效")
			}
			c.Set("actor_kind", ActorKindAPIKey)
			c.Set("api_key_tenant_id", identity.TenantID)
			c.Set("api_key_scopes", identity.Scopes)
			return next(c)
		}
		return echo.NewHTTPError(http.StatusUnauthorized, "用户未认证")
	}
}

func APIKeyHasScope(c *echo.Context, scope string) bool {
	v, ok := c.Get("api_key_scopes").(map[string]bool)
	return ok && v[scope]
}

func bearerToken(c *echo.Context) string {
	v := c.Request().Header.Get(echo.HeaderAuthorization)
	if len(v) <= len(bearerPrefix) || !strings.EqualFold(v[:len(bearerPrefix)], bearerPrefix) {
		return ""
	}
	return strings.TrimSpace(v[len(bearerPrefix):])
}

// APIKeyTenant 返回本次请求所用 API Key 绑定的租户；不是 Key 调用时返回空串。
func APIKeyTenant(c *echo.Context) string {
	v, ok := c.Get("api_key_tenant_id").(string)
	if !ok {
		return ""
	}
	return v
}

func UserID(c *echo.Context) string {
	v, ok := c.Get("user_id").(string)
	if !ok {
		return ""
	}

	return v
}

func Session(c *echo.Context) *model.Session {
	v, ok := c.Get("session").(*model.Session)
	if !ok {
		return nil
	}

	return v
}
