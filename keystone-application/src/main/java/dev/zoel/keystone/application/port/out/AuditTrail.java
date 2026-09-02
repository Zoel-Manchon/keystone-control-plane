package dev.zoel.keystone.application.port.out;

import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.audit.AuditEntry;

import java.util.List;

/**
 * Append-only port. Note what is missing: there is no update and no delete.
 * The port shape itself encodes the guarantee.
 */
public interface AuditTrail {

    /** Appends an entry linked to the current head of the chain. */
    AuditEntry record(AuditAction action, String subject, String detail);

    /** Entries in chain order, oldest first. Used by the integrity check. */
    List<AuditEntry> findAllInOrder();

    /** Most recent entries first, for the console. */
    List<AuditEntry> findLatest(int limit);
}
