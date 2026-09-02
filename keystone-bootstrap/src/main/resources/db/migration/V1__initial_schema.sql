-- Keystone :: initial schema
-- Flyway is the single source of truth for the schema. Hibernate only validates it.

CREATE TABLE devices (
    id                      UUID         PRIMARY KEY,
    serial_number           VARCHAR(128) NOT NULL UNIQUE,
    model                   VARCHAR(128) NOT NULL,
    registered_at           TIMESTAMPTZ  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    certificate_fingerprint VARCHAR(64),
    certificate_expires_at  TIMESTAMPTZ,
    firmware_version        VARCHAR(64),

    CONSTRAINT devices_status_valid CHECK (
        status IN ('PENDING_ENROLLMENT', 'ACTIVE', 'REVOKED', 'DECOMMISSIONED')
    ),
    -- Invariant mirrored in the database: if the device is ACTIVE it has a certificate.
    CONSTRAINT devices_active_requires_certificate CHECK (
        status <> 'ACTIVE' OR (certificate_fingerprint IS NOT NULL AND certificate_expires_at IS NOT NULL)
    ),
    -- Same rule the CertificateFingerprint value object enforces, restated at the
    -- storage layer: defence in depth against a write that bypasses the application.
    CONSTRAINT devices_fingerprint_format CHECK (
        certificate_fingerprint IS NULL OR certificate_fingerprint ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_devices_status ON devices (status);
CREATE INDEX idx_devices_cert_expiry ON devices (certificate_expires_at)
    WHERE certificate_expires_at IS NOT NULL;

CREATE TABLE enrollment_tokens (
    token_hash  VARCHAR(64) PRIMARY KEY,   -- SHA-256 of the token; the plaintext is never stored
    device_id   UUID        NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,

    CONSTRAINT enrollment_tokens_expiry_after_issue CHECK (expires_at > issued_at)
);

CREATE INDEX idx_enrollment_tokens_device ON enrollment_tokens (device_id);

-- Hash-chained audit log: each row carries the hash of the previous one, so deleting
-- or altering a row breaks the chain in a detectable way.
CREATE TABLE audit_log (
    sequence      BIGSERIAL   PRIMARY KEY,
    occurred_at   TIMESTAMPTZ NOT NULL,
    actor         VARCHAR(128) NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    subject       VARCHAR(128) NOT NULL,
    payload       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    previous_hash VARCHAR(64)  NOT NULL,
    entry_hash    VARCHAR(64)  NOT NULL UNIQUE
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_subject ON audit_log (subject);
