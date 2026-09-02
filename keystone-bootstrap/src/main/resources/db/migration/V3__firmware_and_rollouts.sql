-- Keystone :: phase 5 - signed firmware artifacts and staged rollouts

CREATE TABLE firmware_artifacts (
    id          UUID         PRIMARY KEY,
    version     VARCHAR(32)  NOT NULL,
    model       VARCHAR(128) NOT NULL,
    sha256      VARCHAR(64)  NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    uploaded_at TIMESTAMPTZ  NOT NULL,
    signature   VARCHAR(128),
    withdrawn   BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT firmware_artifacts_version_unique UNIQUE (model, version),
    CONSTRAINT firmware_artifacts_sha256_format CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT firmware_artifacts_size_positive CHECK (size_bytes > 0)
);

CREATE INDEX idx_firmware_artifacts_model ON firmware_artifacts (model);

CREATE TABLE rollouts (
    id               UUID         PRIMARY KEY,
    artifact_id      UUID         NOT NULL REFERENCES firmware_artifacts (id),
    target_model     VARCHAR(128) NOT NULL,
    target_version   VARCHAR(32)  NOT NULL,
    previous_version VARCHAR(32),
    started_at       TIMESTAMPTZ  NOT NULL,
    stage            VARCHAR(16)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    last_changed_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT rollouts_stage_valid CHECK (stage IN ('CANARY', 'EARLY', 'FULL')),
    CONSTRAINT rollouts_status_valid CHECK (
        status IN ('IN_PROGRESS', 'COMPLETED', 'PAUSED', 'ROLLED_BACK')
    ),
    -- A rollback needs somewhere to go. Enforcing it here means the invariant holds
    -- even for a row written by something other than the application.
    CONSTRAINT rollouts_rollback_needs_target CHECK (
        status <> 'ROLLED_BACK' OR previous_version IS NOT NULL
    )
);

CREATE INDEX idx_rollouts_model_status ON rollouts (target_model, status);
