package handler

import (
	"net/http"

	"emailbox/pkg/model"
	"emailbox/pkg/repo"
	"emailbox/pkg/service"

	"github.com/labstack/echo/v5"
)

type LedgerHandler struct{ service *service.LedgerService }

func NewLedgerHandler(s *service.LedgerService) *LedgerHandler { return &LedgerHandler{service: s} }

func (h *LedgerHandler) List(c *echo.Context) error {
	limit := atoiOrZero(c.QueryParam("limit"))
	offset := atoiOrZero(c.QueryParam("offset"))
	v, err := h.service.List(c.Request().Context(), c.Param("tenantID"), c.QueryParam("month"), limit, offset)
	if err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, v, "获取成功")
}

func (h *LedgerHandler) Create(c *echo.Context) error {
	var req model.CreateLedgerTransactionRequest
	if err := c.Bind(&req); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	v, err := h.service.Create(c.Request().Context(), c.Param("tenantID"), req)
	if err != nil {
		if err == repo.ErrConflict {
			return failure(c, http.StatusConflict, err)
		}
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, v, "记账成功")
}

func (h *LedgerHandler) Update(c *echo.Context) error {
	var req model.UpdateLedgerTransactionRequest
	if err := c.Bind(&req); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	v, err := h.service.Update(c.Request().Context(), c.Param("tenantID"), c.Param("id"), req)
	if err != nil {
		if err == repo.ErrNotFound {
			return failure(c, http.StatusNotFound, err)
		}
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, v, "更新成功")
}

func (h *LedgerHandler) Delete(c *echo.Context) error {
	if err := h.service.Delete(c.Request().Context(), c.Param("tenantID"), c.Param("id")); err != nil {
		if err == repo.ErrNotFound {
			return failure(c, http.StatusNotFound, err)
		}
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, nil, "删除成功")
}

func (h *LedgerHandler) Summary(c *echo.Context) error {
	v, err := h.service.Summary(c.Request().Context(), c.Param("tenantID"), c.QueryParam("month"))
	if err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, v, "获取成功")
}
