package dev.zoel.keystone.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Top-level on purpose: Spring Data's repository scanning does not pick up
 * interfaces nested inside another class.
 */
public interface AuditEntryJpaRepository extends JpaRepository<AuditEntryEntity, Long> {

    Optional<AuditEntryEntity> findFirstByOrderBySequenceDesc();

    List<AuditEntryEntity> findAllByOrderBySequenceAsc();

    List<AuditEntryEntity> findAllByOrderBySequenceDesc(PageRequest page);
}
