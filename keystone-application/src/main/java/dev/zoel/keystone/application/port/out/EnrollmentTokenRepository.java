package dev.zoel.keystone.application.port.out;

import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.enrollment.EnrollmentToken;

import java.time.Instant;
import java.util.Optional;

public interface EnrollmentTokenRepository {

    EnrollmentToken save(EnrollmentToken token);

    Optional<EnrollmentToken> findByHash(String tokenHash);

    /**
     * Atomically marks a still-valid, still-unused token as consumed.
     *
     * @return true only for the single request that won the database race
     */
    boolean consumeIfUsable(String tokenHash, Instant consumedAt);

    Optional<EnrollmentToken> findActiveByDevice(DeviceId deviceId);
}
