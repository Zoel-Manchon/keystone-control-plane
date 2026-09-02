package dev.zoel.keystone.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Top-level for the same reason as AuditEntryJpaRepository: scanning finds it here. */
public interface EnrollmentTokenJpaRepository extends JpaRepository<EnrollmentTokenEntity, String> {

    Optional<EnrollmentTokenEntity> findFirstByDeviceIdAndConsumedAtIsNullOrderByIssuedAtDesc(UUID deviceId);

    /**
     * Compare-and-set consumption performed by PostgreSQL in one statement. This is
     * what prevents two concurrent enrolment requests from spending the same token.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update EnrollmentTokenEntity t
           set t.consumedAt = :consumedAt
         where t.tokenHash = :tokenHash
           and t.consumedAt is null
           and t.expiresAt > :consumedAt
        """)
    int consumeIfUsable(@Param("tokenHash") String tokenHash,
                        @Param("consumedAt") Instant consumedAt);
}
