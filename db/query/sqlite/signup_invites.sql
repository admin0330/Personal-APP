-- name: CreateSignupInvite :exec
INSERT INTO signup_invites (id, code_hash, created_by, expires_at)
VALUES (?, ?, ?, ?);

-- name: GetActiveSignupInviteByHash :one
SELECT * FROM signup_invites
WHERE code_hash = ? AND redeemed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
LIMIT 1;

-- name: ListSignupInvites :many
SELECT * FROM signup_invites ORDER BY created_at DESC LIMIT ? OFFSET ?;

-- name: RedeemSignupInvite :execrows
UPDATE signup_invites SET redeemed_at = CURRENT_TIMESTAMP
WHERE id = ? AND redeemed_at IS NULL AND expires_at > CURRENT_TIMESTAMP;

-- name: DeleteSignupInvite :execrows
DELETE FROM signup_invites WHERE id = ? AND redeemed_at IS NULL;
