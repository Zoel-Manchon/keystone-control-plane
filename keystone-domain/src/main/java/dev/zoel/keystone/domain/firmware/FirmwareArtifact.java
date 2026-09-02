package dev.zoel.keystone.domain.firmware;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A firmware image plus everything a device needs to decide whether to trust it.
 *
 * The digest and the signature are separate on purpose and answer different
 * questions. The digest answers "did this image arrive intact"; the signature answers
 * "did Keystone actually authorise this image". A digest alone is worthless against
 * an attacker who can replace both the file and its published hash.
 */
public final class FirmwareArtifact {

    private final UUID id;
    private final FirmwareVersion version;
    private final String model;
    private final String sha256;
    private final long sizeBytes;
    private final Instant uploadedAt;

    private String signatureBase64;
    private boolean withdrawn;

    private FirmwareArtifact(UUID id, FirmwareVersion version, String model, String sha256,
                             long sizeBytes, Instant uploadedAt) {
        this.id = Objects.requireNonNull(id);
        this.version = Objects.requireNonNull(version);
        this.model = requireText(model);
        this.sha256 = requireDigest(sha256);
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("a firmware artifact cannot be empty");
        }
        this.sizeBytes = sizeBytes;
        this.uploadedAt = Objects.requireNonNull(uploadedAt);
    }

    public static FirmwareArtifact upload(FirmwareVersion version, String model, String sha256,
                                          long sizeBytes, Instant now) {
        return new FirmwareArtifact(UUID.randomUUID(), version, model, sha256, sizeBytes, now);
    }

    public static FirmwareArtifact rehydrate(UUID id, FirmwareVersion version, String model,
                                             String sha256, long sizeBytes, Instant uploadedAt,
                                             String signatureBase64, boolean withdrawn) {
        FirmwareArtifact artifact = new FirmwareArtifact(id, version, model, sha256, sizeBytes, uploadedAt);
        artifact.signatureBase64 = signatureBase64;
        artifact.withdrawn = withdrawn;
        return artifact;
    }

    public void attachSignature(String signatureBase64) {
        if (this.signatureBase64 != null) {
            throw new IllegalFirmwareStateException("this artifact is already signed");
        }
        this.signatureBase64 = Objects.requireNonNull(signatureBase64);
    }

    /**
     * The single gate every rollout has to pass. An unsigned artifact is not
     * deployable at any stage, to any cohort, for any reason.
     */
    public boolean isDeployable() {
        return signatureBase64 != null && !withdrawn;
    }

    public void withdraw() {
        this.withdrawn = true;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        return value.trim();
    }

    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        return value;
    }

    public UUID id() { return id; }
    public FirmwareVersion version() { return version; }
    public String model() { return model; }
    public String sha256() { return sha256; }
    public long sizeBytes() { return sizeBytes; }
    public Instant uploadedAt() { return uploadedAt; }
    public String signatureBase64() { return signatureBase64; }
    public boolean isWithdrawn() { return withdrawn; }
}
