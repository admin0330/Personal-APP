-- NOTE: keep this file ASCII-only. sqlc miscomputes query boundaries when a
-- .sql file contains multi-byte characters and silently truncates the SQL.
-- Explanations live in pkg/repo/api_keys.go instead.

-- name: GetAPIKeyByTenant :one
SELECT * FROM tenant_api_keys WHERE tenant_id = $1 LIMIT 1;

-- name: GetAPIKeyByHash :one
SELECT * FROM tenant_api_keys WHERE token_hash = $1 LIMIT 1;

-- name: UpsertAPIKey :exec
INSERT INTO tenant_api_keys (tenant_id, token_hash, token_enc, scopes)
VALUES ($1, $2, $3, $4)
ON CONFLICT (tenant_id) DO UPDATE
SET token_hash = EXCLUDED.token_hash,
    token_enc = EXCLUDED.token_enc,
    scopes = EXCLUDED.scopes,
    updated_at = CURRENT_TIMESTAMP;

-- name: SetAPIKeyScopes :execrows
UPDATE tenant_api_keys SET scopes = $1, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = $2;
