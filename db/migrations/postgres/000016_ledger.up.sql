ALTER TABLE tenant_api_keys
ADD COLUMN scopes TEXT NOT NULL DEFAULT 'mail:read';

CREATE TABLE ledger_transactions (
    id                 TEXT PRIMARY KEY,
    tenant_id          TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type               TEXT NOT NULL CHECK (type IN ('expense', 'income')),
    amount_minor       BIGINT NOT NULL CHECK (amount_minor > 0),
    currency           TEXT NOT NULL CHECK (currency IN ('CNY', 'USD', 'HKD')),
    category           TEXT NOT NULL,
    occurred_at        TIMESTAMPTZ NOT NULL,
    merchant           TEXT NOT NULL DEFAULT '',
    note               TEXT NOT NULL DEFAULT '',
    source             TEXT NOT NULL CHECK (source IN ('manual', 'email')),
    source_message_key TEXT,
    client_id          TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMPTZ,
    UNIQUE (tenant_id, client_id)
);

CREATE UNIQUE INDEX ledger_source_message_unique
ON ledger_transactions (tenant_id, source_message_key)
WHERE source_message_key IS NOT NULL;

CREATE INDEX ledger_tenant_occurred
ON ledger_transactions (tenant_id, occurred_at DESC)
WHERE deleted_at IS NULL;
