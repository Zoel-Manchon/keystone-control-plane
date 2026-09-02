package dev.zoel.keystone.integration;

import dev.zoel.keystone.application.port.in.VerifyAuditTrail;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.domain.audit.AuditAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audit trail against the real database.
 *
 * The domain tests already prove the hash chain detects tampering. What they cannot
 * prove is that Postgres refuses the tampering in the first place - that lives in a
 * trigger, and a trigger is only ever tested against a real Postgres.
 */
class AuditTrailIT extends AbstractIntegrationTest {

    @Autowired
    private AuditTrail audit;

    @Autowired
    private VerifyAuditTrail verify;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("appended entries form a chain that verifies")
    void appendedEntriesChain() {
        audit.record(AuditAction.DEVICE_REGISTERED, "device-a", "first");
        audit.record(AuditAction.ENROLLMENT_TOKEN_ISSUED, "device-a", "second");
        audit.record(AuditAction.CERTIFICATE_ISSUED, "device-a", "third");

        var integrity = verify.handle();

        assertThat(integrity.intact()).isTrue();
        assertThat(integrity.entriesChecked()).isEqualTo(3);
    }

    @Test
    @DisplayName("NEGATIVE: the database refuses to update a sealed entry")
    void sealedEntriesCannotBeUpdated() {
        audit.record(AuditAction.CERTIFICATE_REVOKED, "device-b", "sensitive detail");

        // Someone with the application's own credentials tries to rewrite history.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE audit_log SET detail = 'nothing to see here' WHERE sequence = 1"))
            .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("NEGATIVE: the database refuses to delete an entry")
    void entriesCannotBeDeleted() {
        audit.record(AuditAction.CERTIFICATE_REVOKED, "device-c", "detail");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE sequence = 1"))
            .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("each entry links to the hash of the one before it")
    void eachEntryLinksToItsPredecessor() {
        var first = audit.record(AuditAction.DEVICE_REGISTERED, "device-d", "one");
        var second = audit.record(AuditAction.DEVICE_REGISTERED, "device-e", "two");

        assertThat(second.previousHash()).isEqualTo(first.entryHash());
    }


    @Test
    @DisplayName("concurrent audit appends queue without forking or failing")
    void concurrentAppendsStayLinear() throws Exception {
        int writers = 16;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < writers; i++) {
                int n = i;
                futures.add(executor.submit(() ->
                    audit.record(AuditAction.DEVICE_REGISTERED, "concurrent-" + n, "append")));
            }
            for (var future : futures) {
                future.get();
            }
        }

        var integrity = verify.handle();
        assertThat(integrity.intact()).isTrue();
        assertThat(integrity.entriesChecked()).isEqualTo(writers);
    }

    @Test
    @DisplayName("the enrolment secret never reaches the audit trail")
    void secretsAreNotAudited() {
        audit.record(AuditAction.ENROLLMENT_TOKEN_ISSUED, "device-f", "serial=SN-1");

        // A regression here would put a live credential into a log that gets shipped
        // to systems with a much wider audience than the database.
        Integer leaks = jdbc.queryForObject(
            "SELECT count(*) FROM audit_log WHERE detail ILIKE '%secret%' OR detail ILIKE '%token=%'",
            Integer.class);

        assertThat(leaks).isZero();
    }
}
