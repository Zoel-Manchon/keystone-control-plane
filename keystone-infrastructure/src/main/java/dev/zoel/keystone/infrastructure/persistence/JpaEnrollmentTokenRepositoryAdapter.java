package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.application.port.out.EnrollmentTokenRepository;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.enrollment.EnrollmentToken;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaEnrollmentTokenRepositoryAdapter implements EnrollmentTokenRepository {

    private final EnrollmentTokenJpaRepository jpa;

    JpaEnrollmentTokenRepositoryAdapter(EnrollmentTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EnrollmentToken save(EnrollmentToken token) {
        jpa.save(new EnrollmentTokenEntity(
            token.tokenHash(), token.deviceId().value(),
            token.issuedAt(), token.expiresAt(), token.consumedAt()));
        return token;
    }

    @Override
    public Optional<EnrollmentToken> findByHash(String tokenHash) {
        return jpa.findById(tokenHash).map(JpaEnrollmentTokenRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public boolean consumeIfUsable(String tokenHash, Instant consumedAt) {
        return jpa.consumeIfUsable(tokenHash, consumedAt) == 1;
    }

    @Override
    public Optional<EnrollmentToken> findActiveByDevice(DeviceId deviceId) {
        return jpa.findFirstByDeviceIdAndConsumedAtIsNullOrderByIssuedAtDesc(deviceId.value())
            .map(JpaEnrollmentTokenRepositoryAdapter::toDomain)
            .filter(token -> token.isUsable(Instant.now()));
    }

    private static EnrollmentToken toDomain(EnrollmentTokenEntity entity) {
        return EnrollmentToken.rehydrate(
            new DeviceId(entity.getDeviceId()), entity.getTokenHash(),
            entity.getIssuedAt(), entity.getExpiresAt(), entity.getConsumedAt());
    }
}
