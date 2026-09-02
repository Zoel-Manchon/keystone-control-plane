-- Keystone :: phase 2 - certificate registry and audit detail column
--
-- V1 is already applied elsewhere, so it is never edited: corrections ship as a new
-- versioned migration. That rule is what makes a Flyway history trustworthy.

CREATE TABLE issued_certificates (
    serial_number VARCHAR(64)  PRIMARY KEY,
    device_id     UUID         NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    fingerprint   VARCHAR(64)  NOT NULL UNIQUE,
    subject       VARCHAR(256) NOT NULL,
    not_before    TIMESTAMPTZ  NOT NULL,
    not_after     TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ,

    CONSTRAINT issued_certificates_validity CHECK (not_after > not_before),
    CONSTRAINT issued_certificates_fingerprint_format CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_issued_certificates_device ON issued_certificates (device_id);
-- Partial index: the CRL only ever asks for the revoked ones.
CREATE INDEX idx_issued_certificates_revoked ON issued_certificates (revoked_at)
    WHERE revoked_at IS NOT NULL;

-- The audit log stored a JSONB payload; a plain detail column is what the domain
-- actually writes, and a narrower type is a narrower thing to get wrong.
ALTER TABLE audit_log DROP COLUMN payload;
ALTER TABLE audit_log ADD COLUMN detail VARCHAR(512) NOT NULL DEFAULT '';

-- Append-only enforced by the database, not just by the port interface. A trigger
-- refusing UPDATE and DELETE means a compromised application account still cannot
-- rewrite history without also holding rights to drop the trigger itself.
CREATE OR REPLACE FUNCTION audit_log_is_append_only() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'audit_log is append-only: deletes are not permitted';
    END IF;
    -- The hash is written in a second step right after insert, so an update is
    -- allowed only while the row still carries the placeholder hash.
    IF OLD.entry_hash <> repeat('0', 64) THEN
        RAISE EXCEPTION 'audit_log is append-only: entry % is already sealed', OLD.sequence;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_is_append_only();
