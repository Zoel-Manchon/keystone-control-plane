package dev.zoel.keystone.integration;

import dev.zoel.keystone.KeystoneApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

/**
 * Base for every integration test.
 *
 * The database is a real Postgres, not H2. That is not pedantry: half of what these
 * tests verify - CHECK constraints, the append-only trigger, SERIALIZABLE isolation,
 * partial indexes - simply does not exist in an embedded database, so an H2 suite
 * would pass while the production schema misbehaved.
 *
 * Every path that writes to disk is redirected into a temporary directory, so a test
 * run never touches the CA keystore or the firmware key of the developer's machine.
 */
@SpringBootTest(classes = KeystoneApplication.class)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    /*
     * Singleton container, started once for the whole JVM and never stopped explicitly.
     *
     * Deliberately NOT @Testcontainers + @Container: that extension owns the lifecycle
     * per test class, so the first IT to finish would stop this shared container while
     * Spring still has a cached context bound to its now-dead port. Every subsequent
     * class then failed with "connection refused" against the old mapping.
     *
     * Ryuk reaps the container when the JVM exits, so nothing leaks.
     */
    // Testcontainers 2.x dropped the self-referential type parameter, so no <?> here.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void isolateOnDiskState(DynamicPropertyRegistry registry) {
        Path scratch = Path.of(System.getProperty("java.io.tmpdir"), "keystone-it-" + System.nanoTime());
        registry.add("keystone.pki.keystore-path", () -> scratch.resolve("ca.p12").toString());
        registry.add("keystone.firmware.signing-key-path", () -> scratch.resolve("fw.key").toString());
        registry.add("keystone.firmware.storage-path", () -> scratch.resolve("firmware").toString());
        registry.add("keystone.mqtt.certificate-path", () -> scratch.resolve("certs").toString());
        registry.add("keystone.pki.keystore-password", () -> "test-ca-password-32-bytes-minimum");
        registry.add("keystone.security.dev-operator-password", () -> OPERATOR_PASSWORD);
        // The sweeps would fire mid-test and pollute the audit trail assertions.
        registry.add("keystone.mqtt.crl-refresh-ms", () -> "3600000");
        registry.add("keystone.pki.expiry-sweep-ms", () -> "3600000");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The container is shared across the whole suite for speed, so each test starts
     * from a clean slate instead of inheriting the previous one's rows. TRUNCATE
     * bypasses the append-only trigger, which is the only legitimate way to reset the
     * audit table - and it needs direct database access, not the application account.
     */
    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE devices, issued_certificates, enrollment_tokens, audit_log, "
            + "rollouts, firmware_artifacts RESTART IDENTITY CASCADE");
    }

    protected static final String OPERATOR = "operator";
    protected static final String OPERATOR_PASSWORD = "integration-test-only-password";
}