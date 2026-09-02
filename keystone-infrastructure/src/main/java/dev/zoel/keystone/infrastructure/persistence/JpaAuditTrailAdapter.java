package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.OperatorIdentity;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.audit.AuditEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Appends to the hash chain.
 *
 * Concurrent appends are serialized with a PostgreSQL transaction-scoped advisory
 * lock. SERIALIZABLE isolation alone would protect the chain by aborting one racing
 * transaction, but without an application retry that turns normal fleet concurrency
 * into avoidable 500 responses. The lock makes callers queue instead of fail.
 */
@Repository
public class JpaAuditTrailAdapter implements AuditTrail {

    private static final long AUDIT_APPEND_LOCK = 0x4B455953544F4E45L; // "KEYSTONE"

    private final AuditEntryJpaRepository jpa;
    private final Clock clock;
    private final OperatorIdentity operator;
    private final JdbcTemplate jdbc;

    JpaAuditTrailAdapter(AuditEntryJpaRepository jpa, Clock clock, OperatorIdentity operator,
                         JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.clock = clock;
        this.operator = operator;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public AuditEntry record(AuditAction action, String subject, String detail) {
        // Released automatically at transaction end; no unlock path can be forgotten.
        jdbc.execute("SELECT pg_advisory_xact_lock(" + AUDIT_APPEND_LOCK + ")");

        String previousHash = jpa.findFirstByOrderBySequenceDesc()
            .map(AuditEntryEntity::getEntryHash)
            .orElse(AuditEntry.GENESIS_HASH);

        // The sequence is assigned by the database, but the hash covers it, so the
        // row is saved once to obtain the sequence and then hashed and updated.
        AuditEntryEntity entity = jpa.save(new AuditEntryEntity(
            clock.now(), operator.current(), action, subject,
            truncate(detail), previousHash, AuditEntry.GENESIS_HASH));

        AuditEntry entry = AuditEntry.link(entity.getSequence(), entity.getOccurredAt(),
            entity.getActor(), action, subject, entity.getDetail(), previousHash);

        entity.applyHashes(previousHash, entry.entryHash());
        jpa.save(entity);
        return entry;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findAllInOrder() {
        return jpa.findAllByOrderBySequenceAsc().stream().map(JpaAuditTrailAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findLatest(int limit) {
        return jpa.findAllByOrderBySequenceDesc(PageRequest.of(0, limit)).stream()
            .map(JpaAuditTrailAdapter::toDomain).toList();
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }

    private static AuditEntry toDomain(AuditEntryEntity entity) {
        return AuditEntry.rehydrate(entity.getSequence(), entity.getOccurredAt(), entity.getActor(),
            entity.getAction(), entity.getSubject(), entity.getDetail(),
            entity.getPreviousHash(), entity.getEntryHash());
    }
}
