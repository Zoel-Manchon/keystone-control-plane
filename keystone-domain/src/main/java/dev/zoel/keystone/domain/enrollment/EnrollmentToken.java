package dev.zoel.keystone.domain.enrollment;

import dev.zoel.keystone.domain.device.DeviceId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Single-use token for enrolling a device.
 *
 * ONLY the hash is stored in the database: the plaintext value is shown to the
 * operator once and can never be retrieved again. Same model as an API key.
 */
public final class EnrollmentToken {

    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final DeviceId deviceId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant consumedAt;

    private EnrollmentToken(DeviceId deviceId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.deviceId = Objects.requireNonNull(deviceId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiry must be after issuance");
        }
    }

    public static EnrollmentToken issue(DeviceId deviceId, String tokenHash, Instant now, Duration ttl) {
        return new EnrollmentToken(deviceId, tokenHash, now, now.plus(ttl));
    }

    public static EnrollmentToken rehydrate(DeviceId deviceId, String tokenHash,
                                            Instant issuedAt, Instant expiresAt, Instant consumedAt) {
        EnrollmentToken token = new EnrollmentToken(deviceId, tokenHash, issuedAt, expiresAt);
        token.consumedAt = consumedAt;
        return token;
    }

    /** An expired or already-used token is invalid. Half of the security of this flow lives here. */
    public void consume(Instant now) {
        if (consumedAt != null) {
            throw new TokenAlreadyConsumedException(deviceId);
        }
        if (!now.isBefore(expiresAt)) {
            throw new TokenExpiredException(deviceId, expiresAt);
        }
        this.consumedAt = now;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public DeviceId deviceId() { return deviceId; }
    public String tokenHash() { return tokenHash; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant consumedAt() { return consumedAt; }
}
