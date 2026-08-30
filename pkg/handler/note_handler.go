package handler

import (
	"emailbox/pkg/model"
	"emailbox/pkg/service"
	"net/http"

	"github.com/labstack/echo/v5"
)

type NoteHandler struct{ service *service.NoteService }

func NewNoteHandler(s *service.NoteService) *NoteHandler { return &NoteHandler{service: s} }

func (h *NoteHandler) List(c *echo.Context) error {
	v, err := h.service.List(c.Request().Context(), c.Param("tenantID"))
	if err != nil {
		return failure(c, http.StatusInternalServerError, err)
	}
	return success(c, v, "获取成功")
}
func (h *NoteHandler) Create(c *echo.Context) error {
	var req model.CreateNoteRequest
	if err := c.Bind(&req); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	v, err := h.service.Create(c.Request().Context(), c.Param("tenantID"), req)
	if err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	return success(c, v, "创建成功")
}
func (h *NoteHandler) Update(c *echo.Context) error {
	var req model.UpdateNoteRequest
	if err := c.Bind(&req); err != nil {
		return failure(c, http.StatusBadRequest, err)
	}
	v, err := h.service.Update(c.Request().Context(), c.Param("tenantID"), c.Param("noteID"), req)
	if err != nil {
		return adminError(c, err)
	}
	return success(c, v, "保存成功")
}
func (h *NoteHandler) Delete(c *echo.Context) error {
	if err := h.service.Delete(c.Request().Context(), c.Param("tenantID"), c.Param("noteID")); err != nil {
		return adminError(c, err)
	}
	return success(c, nil, "删除成功")
}
