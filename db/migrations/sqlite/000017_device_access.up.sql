-- Short-lived one-time codes: a logged-in user mints one in the web UI and types
-- it into a device. Only the SHA-256 digest is stored -- the plaintext lives for
-- minutes and is shown once, so a database dump must not hand out live codes.
CREATE TABLE device_codes (
    id            TEXT PRIMARY KEY,
    tenant_id     TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code_hash     TEXT NOT NULL UNIQUE,
    created_by    TEXT REFERENCES users(id) ON DELETE SET NULL,
    expires_at    DATETIME NOT NULL,
    redeemed_at   DATETIME,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX device_codes_tenant ON device_codes (tenant_id, created_at DESC);

-- Device tokens carry the same read-only powers as the tenant API Key, but each
-- device gets its own: revoking one phone must not log out the others, and the
-- revocation has to take effect on the very next request.
--
-- Unlike tenant_api_keys there is no token_enc column. The plaintext is returned
-- once at redemption and lives in the device's Keystore. There is no legitimate
-- "show it to me again", and a re-displayable copy would double the value of a
-- database leak for no benefit.
CREATE TABLE device_tokens (
    id          TEXT PRIMARY KEY,
    tenant_id   TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    token_hint  TEXT NOT NULL,
    label       TEXT NOT NULL DEFAULT '',
    created_by  TEXT REFERENCES users(id) ON DELETE SET NULL,
    expires_at  DATETIME NOT NULL,
    revoked_at  DATETIME,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX device_tokens_tenant ON device_tokens (tenant_id, created_at DESC);
