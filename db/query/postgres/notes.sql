-- name: CreateNote :exec
INSERT INTO notes (id, tenant_id, title, content, is_pinned)
VALUES ($1, $2, $3, $4, $5);

-- name: ListNotes :many
SELECT * FROM notes WHERE tenant_id = $1
ORDER BY is_pinned DESC, updated_at DESC;

-- name: GetNote :one
SELECT * FROM notes WHERE tenant_id = $1 AND id = $2 LIMIT 1;

-- name: UpdateNote :execrows
UPDATE notes SET title = $1, content = $2, is_pinned = $3, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = $4 AND id = $5;

-- name: DeleteNote :execrows
DELETE FROM notes WHERE tenant_id = $1 AND id = $2;
