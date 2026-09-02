package dev.zoel.keystone.domain.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tamper-detection tests. These are the ones that prove the audit log is worth
 * more than a text file: they demonstrate that an alteration is DETECTED.
 */
class AuditEntryTest {

    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");

    private static AuditEntry entry(long sequence, String subject, String previousHash) {
        return AuditEntry.link(sequence, NOW, "operator",
            AuditAction.DEVICE_REGISTERED, subject, "detail", previousHash);
    }

    @Test
    @DisplayName("a freshly linked entry has an intact hash")
    void freshEntryIsIntact() {
        assertThat(entry(1, "device-a", AuditEntry.GENESIS_HASH).hasIntactHash()).isTrue();
    }

    @Test
    @DisplayName("an unbroken chain verifies")
    void intactChainVerifies() {
        AuditEntry first = entry(1, "device-a", AuditEntry.GENESIS_HASH);
        AuditEntry second = entry(2, "device-b", first.entryHash());
        AuditEntry third = entry(3, "device-c", second.entryHash());

        AuditTrailIntegrity result = AuditTrailIntegrity.verify(List.of(first, second, third));

        assertThat(result.intact()).isTrue();
        assertThat(result.entriesChecked()).isEqualTo(3);
    }

    @Test
    @DisplayName("NEGATIVE: editing an entry's content breaks its own hash")
    void editedContentIsDetected() {
        AuditEntry original = entry(1, "device-a", AuditEntry.GENESIS_HASH);

        // Someone rewrites the subject in the database but keeps the stored hash.
        AuditEntry tampered = AuditEntry.rehydrate(1, NOW, "operator",
            AuditAction.DEVICE_REGISTERED, "device-ATTACKER", "detail",
            AuditEntry.GENESIS_HASH, original.entryHash());

        assertThat(tampered.hasIntactHash()).isFalse();
    }

    @Test
    @DisplayName("NEGATIVE: removing a middle entry breaks the chain")
    void removedEntryIsDetected() {
        AuditEntry first = entry(1, "device-a", AuditEntry.GENESIS_HASH);
        AuditEntry second = entry(2, "device-b", first.entryHash());
        AuditEntry third = entry(3, "device-c", second.entryHash());

        AuditTrailIntegrity result = AuditTrailIntegrity.verify(List.of(first, third));

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenSequence()).isEqualTo(3);
    }

    @Test
    @DisplayName("NEGATIVE: a chain that does not start at genesis is rejected")
    void chainMustStartAtGenesis() {
        AuditEntry orphan = entry(1, "device-a", "f".repeat(64));

        assertThat(AuditTrailIntegrity.verify(List.of(orphan)).intact()).isFalse();
    }

    @Test
    @DisplayName("field boundaries cannot be shifted without changing the hash")
    void fieldSeparatorPreventsCollisions() {
        AuditEntry a = AuditEntry.link(1, NOW, "operator", AuditAction.DEVICE_REGISTERED,
            "ab", "c", AuditEntry.GENESIS_HASH);
        AuditEntry b = AuditEntry.link(1, NOW, "operator", AuditAction.DEVICE_REGISTERED,
            "a", "bc", AuditEntry.GENESIS_HASH);

        assertThat(a.entryHash()).isNotEqualTo(b.entryHash());
    }
}
