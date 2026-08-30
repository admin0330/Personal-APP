-- name: CreateLedgerTransaction :one
INSERT INTO ledger_transactions (
    id, tenant_id, type, amount_minor, currency, category, occurred_at,
    merchant, note, source, source_message_key, client_id, posted
) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
ON CONFLICT (tenant_id, client_id) DO UPDATE SET client_id = EXCLUDED.client_id
RETURNING *;

-- name: GetLedgerTransaction :one
SELECT * FROM ledger_transactions
WHERE tenant_id = $1 AND id = $2 AND deleted_at IS NULL
LIMIT 1;

-- name: GetLedgerTransactionByClientID :one
SELECT * FROM ledger_transactions
WHERE tenant_id = $1 AND client_id = $2 AND deleted_at IS NULL
LIMIT 1;

-- name: ListLedgerTransactions :many
SELECT * FROM ledger_transactions
WHERE tenant_id = $1 AND occurred_at >= $2 AND occurred_at < $3 AND deleted_at IS NULL
ORDER BY occurred_at DESC, created_at DESC
LIMIT $4 OFFSET $5;

-- name: UpdateLedgerTransaction :one
UPDATE ledger_transactions SET
    type = $1, amount_minor = $2, currency = $3, category = $4, occurred_at = $5,
    merchant = $6, note = $7, posted = $8, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = $9 AND id = $10 AND deleted_at IS NULL
RETURNING *;

-- name: DeleteLedgerTransaction :execrows
UPDATE ledger_transactions SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = $1 AND id = $2 AND deleted_at IS NULL;

-- name: SummarizeLedgerTransactions :many
SELECT type, currency, category, SUM(amount_minor)::BIGINT AS amount_minor
FROM ledger_transactions
WHERE tenant_id = $1 AND occurred_at >= $2 AND occurred_at < $3 AND deleted_at IS NULL AND posted
GROUP BY type, currency, category
ORDER BY currency, type, amount_minor DESC;
