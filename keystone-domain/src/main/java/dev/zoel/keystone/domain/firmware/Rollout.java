package dev.zoel.keystone.domain.firmware;

import dev.zoel.keystone.domain.device.DeviceId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A staged deployment of one artifact.
 *
 * Cohort membership is computed, not stored: a device's bucket is derived from a hash
 * of the rollout id and the device id. That gives three properties worth having.
 * It is stable, so a device never flips in and out of the canary between polls. It is
 * uniform, so the canary is a real cross-section of the fleet rather than the first
 * five devices anyone registered. And it needs no per-device rows, so a rollout over
 * a hundred thousand devices costs the same as one over ten.
 */
public final class Rollout {

    private final UUID id;
    private final UUID artifactId;
    private final String targetModel;
    private final FirmwareVersion targetVersion;
    private final FirmwareVersion previousVersion;
    private final Instant startedAt;

    private RolloutStage stage;
    private RolloutStatus status;
    private Instant lastChangedAt;

    private Rollout(UUID id, UUID artifactId, String targetModel, FirmwareVersion targetVersion,
                    FirmwareVersion previousVersion, Instant startedAt) {
        this.id = Objects.requireNonNull(id);
        this.artifactId = Objects.requireNonNull(artifactId);
        this.targetModel = Objects.requireNonNull(targetModel);
        this.targetVersion = Objects.requireNonNull(targetVersion);
        this.previousVersion = previousVersion;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.stage = RolloutStage.CANARY;
        this.status = RolloutStatus.IN_PROGRESS;
        this.lastChangedAt = startedAt;
    }

    /** A rollout always opens at the canary stage. There is no "straight to 100%" path. */
    public static Rollout start(FirmwareArtifact artifact, FirmwareVersion previousVersion, Instant now) {
        if (!artifact.isDeployable()) {
            throw new IllegalFirmwareStateException(
                "an unsigned or withdrawn artifact cannot be rolled out");
        }
        return new Rollout(UUID.randomUUID(), artifact.id(), artifact.model(),
            artifact.version(), previousVersion, now);
    }

    public static Rollout rehydrate(UUID id, UUID artifactId, String targetModel,
                                    FirmwareVersion targetVersion, FirmwareVersion previousVersion,
                                    Instant startedAt, RolloutStage stage, RolloutStatus status,
                                    Instant lastChangedAt) {
        Rollout rollout = new Rollout(id, artifactId, targetModel, targetVersion, previousVersion, startedAt);
        rollout.stage = Objects.requireNonNull(stage);
        rollout.status = Objects.requireNonNull(status);
        rollout.lastChangedAt = lastChangedAt;
        return rollout;
    }

    public void advance(Instant now) {
        if (status != RolloutStatus.IN_PROGRESS) {
            throw new IllegalFirmwareStateException("only an in-progress rollout can advance, not " + status);
        }
        if (stage.isFinal()) {
            status = RolloutStatus.COMPLETED;
        } else {
            stage = stage.next();
        }
        lastChangedAt = now;
    }

    public void pause(Instant now) {
        if (status != RolloutStatus.IN_PROGRESS) {
            throw new IllegalFirmwareStateException("only an in-progress rollout can be paused");
        }
        status = RolloutStatus.PAUSED;
        lastChangedAt = now;
    }

    public void resume(Instant now) {
        if (status != RolloutStatus.PAUSED) {
            throw new IllegalFirmwareStateException("only a paused rollout can be resumed");
        }
        status = RolloutStatus.IN_PROGRESS;
        lastChangedAt = now;
    }

    /**
     * Rollback is allowed from any live state, including COMPLETED. That is
     * deliberate: the worst firmware bugs surface days after full deployment, and a
     * system that refuses to reverse a finished rollout is a system that turns a bug
     * into a field visit.
     */
    public void rollBack(Instant now) {
        if (status == RolloutStatus.ROLLED_BACK) {
            throw new IllegalFirmwareStateException("this rollout is already rolled back");
        }
        if (previousVersion == null) {
            throw new IllegalFirmwareStateException(
                "there is no previous version to roll back to for model " + targetModel);
        }
        status = RolloutStatus.ROLLED_BACK;
        lastChangedAt = now;
    }

    /** What this device should be running right now, or empty if the rollout does not touch it. */
    public boolean targets(DeviceId deviceId, String deviceModel) {
        if (!targetModel.equals(deviceModel)) {
            return false;
        }
        if (status == RolloutStatus.ROLLED_BACK) {
            // A rolled-back rollout still applies: it is what tells the device to
            // step down. Silently dropping it would leave the fleet on the bad build.
            return true;
        }
        if (status != RolloutStatus.IN_PROGRESS && status != RolloutStatus.COMPLETED) {
            return false;
        }
        return bucketOf(deviceId) < stage.percentage();
    }

    /** The version this device should converge on, given the rollout's current state. */
    public FirmwareVersion desiredVersionFor(DeviceId deviceId, String deviceModel) {
        if (!targets(deviceId, deviceModel)) {
            return null;
        }
        return status == RolloutStatus.ROLLED_BACK ? previousVersion : targetVersion;
    }

    /**
     * Stable bucket in [0, 100). Hashing the rollout id together with the device id
     * matters: without it, the same unlucky devices would be the canary for every
     * rollout forever.
     */
    int bucketOf(DeviceId deviceId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((id + ":" + deviceId).getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xff) << 8) | (digest[1] & 0xff);
            return value % 100;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    public UUID id() { return id; }
    public UUID artifactId() { return artifactId; }
    public String targetModel() { return targetModel; }
    public FirmwareVersion targetVersion() { return targetVersion; }
    public FirmwareVersion previousVersion() { return previousVersion; }
    public Instant startedAt() { return startedAt; }
    public RolloutStage stage() { return stage; }
    public RolloutStatus status() { return status; }
    public Instant lastChangedAt() { return lastChangedAt; }
}
