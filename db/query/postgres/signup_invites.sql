-- name: CreateSignupInvite :exec
INSERT INTO signup_invites (id, code_hash, created_by, expires_at)
VALUES ($1, $2, $3, $4);

-- name: GetActiveSignupInviteByHash :one
SELECT * FROM signup_invites
WHERE code_hash = $1 AND redeemed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
LIMIT 1;

-- name: ListSignupInvites :many
SELECT * FROM signup_invites ORDER BY created_at DESC LIMIT $1 OFFSET $2;

-- name: RedeemSignupInvite :execrows
UPDATE signup_invites SET redeemed_at = CURRENT_TIMESTAMP
WHERE id = $1 AND redeemed_at IS NULL AND expires_at > CURRENT_TIMESTAMP;

-- name: DeleteSignupInvite :execrows
DELETE FROM signup_invites WHERE id = $1 AND redeemed_at IS NULL;
