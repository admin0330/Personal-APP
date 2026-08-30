-- name: CreateNote :exec
INSERT INTO notes (id, tenant_id, title, content, is_pinned)
VALUES (?, ?, ?, ?, ?);

-- name: ListNotes :many
SELECT * FROM notes WHERE tenant_id = ?
ORDER BY is_pinned DESC, updated_at DESC;

-- name: GetNote :one
SELECT * FROM notes WHERE tenant_id = ? AND id = ? LIMIT 1;

-- name: UpdateNote :execrows
UPDATE notes SET title = ?, content = ?, is_pinned = ?, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = ? AND id = ?;

-- name: DeleteNote :execrows
DELETE FROM notes WHERE tenant_id = ? AND id = ?;
