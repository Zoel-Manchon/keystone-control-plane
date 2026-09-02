package dev.zoel.keystone.domain.audit;

import java.util.List;

/** Outcome of walking the chain from the genesis entry to the head. */
public record AuditTrailIntegrity(boolean intact, long entriesChecked, Long firstBrokenSequence) {

    public static AuditTrailIntegrity verify(List<AuditEntry> entriesInOrder) {
        AuditEntry previous = null;
        for (AuditEntry entry : entriesInOrder) {
            if (!entry.hasIntactHash() || !entry.follows(previous)) {
                return new AuditTrailIntegrity(false, entriesInOrder.size(), entry.sequence());
            }
            previous = entry;
        }
        return new AuditTrailIntegrity(true, entriesInOrder.size(), null);
    }
}
