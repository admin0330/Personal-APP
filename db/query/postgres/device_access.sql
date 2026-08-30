-- NOTE: keep this file ASCII-only. sqlc miscomputes query boundaries when a
-- .sql file contains multi-byte characters and silently truncates the SQL.
-- Explanations live in pkg/repo/devices.go instead.

-- name: CreateDeviceCode :exec
INSERT INTO device_codes (
    id, tenant_id, code_hash, created_by, expires_at
) VALUES ($1, $2, $3, $4, $5);

-- name: GetDeviceCodeByHash :one
SELECT * FROM device_codes WHERE code_hash = $1 LIMIT 1;

-- Redeeming is the one place where "one-time" is enforced, so the guard lives in
-- the WHERE clause rather than in Go: two devices racing with the same code must
-- not both win, and a read-then-write pair would let them.
-- name: MarkDeviceCodeRedeemed :execrows
UPDATE device_codes
SET redeemed_at = CURRENT_TIMESTAMP
WHERE id = $1 AND redeemed_at IS NULL AND expires_at > CURRENT_TIMESTAMP;

-- name: CreateDeviceToken :exec
INSERT INTO device_tokens (
    id, tenant_id, token_hash, token_hint, label, created_by, expires_at
) VALUES ($1, $2, $3, $4, $5, $6, $7);

-- Revocation and expiry are checked here on every request instead of being
-- cached anywhere: "revoke now" that lingers for a minute is not revocation.
-- name: GetDeviceTokenByHash :one
SELECT * FROM device_tokens
WHERE token_hash = $1
  AND revoked_at IS NULL
  AND expires_at > CURRENT_TIMESTAMP
LIMIT 1;

-- name: ListDeviceTokens :many
SELECT * FROM device_tokens WHERE tenant_id = $1 ORDER BY created_at DESC;

-- name: CountActiveDeviceTokens :one
SELECT COUNT(*) FROM device_tokens
WHERE tenant_id = $1 AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP;

-- name: RevokeDeviceToken :execrows
UPDATE device_tokens
SET revoked_at = CURRENT_TIMESTAMP
WHERE tenant_id = $1 AND id = $2 AND revoked_at IS NULL;
