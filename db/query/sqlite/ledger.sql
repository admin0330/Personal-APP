-- name: CreateLedgerTransaction :one
INSERT INTO ledger_transactions (
    id, tenant_id, type, amount_minor, currency, category, occurred_at,
    merchant, note, source, source_message_key, client_id, posted
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (tenant_id, client_id) DO UPDATE SET client_id = excluded.client_id
RETURNING *;

-- name: GetLedgerTransaction :one
SELECT * FROM ledger_transactions
WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
LIMIT 1;

-- name: GetLedgerTransactionByClientID :one
SELECT * FROM ledger_transactions
WHERE tenant_id = ? AND client_id = ? AND deleted_at IS NULL
LIMIT 1;

-- name: ListLedgerTransactions :many
SELECT * FROM ledger_transactions
WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ? AND deleted_at IS NULL
ORDER BY occurred_at DESC, created_at DESC
LIMIT ? OFFSET ?;

-- name: UpdateLedgerTransaction :one
UPDATE ledger_transactions SET
    type = ?, amount_minor = ?, currency = ?, category = ?, occurred_at = ?,
    merchant = ?, note = ?, posted = ?, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
RETURNING *;

-- name: DeleteLedgerTransaction :execrows
UPDATE ledger_transactions SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL;

-- name: SummarizeLedgerTransactions :many
SELECT type, currency, category, CAST(SUM(amount_minor) AS INTEGER) AS amount_minor
FROM ledger_transactions
WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ? AND deleted_at IS NULL AND posted = 1
GROUP BY type, currency, category
ORDER BY currency, type, amount_minor DESC;
