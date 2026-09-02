package dev.zoel.keystone.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The plaintext secret has no column here, and that is the point. */
@Entity
@Table(name = "enrollment_tokens")
public class EnrollmentTokenEntity {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EnrollmentTokenEntity() {
        // required by JPA
    }

    public EnrollmentTokenEntity(String tokenHash, UUID deviceId, Instant issuedAt,
                                 Instant expiresAt, Instant consumedAt) {
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getDeviceId() { return deviceId; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
