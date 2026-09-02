package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.EnrollDevice;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.application.port.out.EnrollmentTokenRepository;
import dev.zoel.keystone.application.port.out.SecretGenerator;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.audit.AuditEntry;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.device.DeviceStatus;
import dev.zoel.keystone.domain.enrollment.EnrollmentToken;
import dev.zoel.keystone.domain.enrollment.InvalidEnrollmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hand-written test doubles rather than a mocking framework. The point is that the
 * application layer has no framework dependency at all - not even in its tests.
 */
class EnrollDeviceUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");

    private final Map<DeviceId, Device> deviceStore = new HashMap<>();
    private final Map<String, EnrollmentToken> tokenStore = new HashMap<>();
    private final List<AuditAction> recorded = new ArrayList<>();

    private Instant now = NOW;
    private Device device;
    private EnrollDeviceUseCase useCase;

    private final DeviceRepository devices = new DeviceRepository() {
        @Override public Device save(Device d) { deviceStore.put(d.id(), d); return d; }
        @Override public Optional<Device> findById(DeviceId id) { return Optional.ofNullable(deviceStore.get(id)); }
        @Override public Optional<Device> findBySerialNumber(String s) { return Optional.empty(); }
        @Override public boolean existsBySerialNumber(String s) { return false; }
        @Override public List<Device> findAll() { return List.copyOf(deviceStore.values()); }
    };

    private final EnrollmentTokenRepository tokens = new EnrollmentTokenRepository() {
        @Override public EnrollmentToken save(EnrollmentToken t) { tokenStore.put(t.tokenHash(), t); return t; }
        @Override public Optional<EnrollmentToken> findByHash(String h) { return Optional.ofNullable(tokenStore.get(h)); }
        @Override public boolean consumeIfUsable(String h, Instant at) {
            EnrollmentToken token = tokenStore.get(h);
            if (token == null || !token.isUsable(at)) return false;
            token.consume(at);
            return true;
        }
        @Override public Optional<EnrollmentToken> findActiveByDevice(DeviceId id) { return Optional.empty(); }
    };

    /** Deterministic stub: the "hash" is a prefix, so tests stay readable. */
    private final SecretGenerator secrets = new SecretGenerator() {
        @Override public String generateSecret() { return "test-secret"; }
        @Override public String hash(String secret) { return "hashed:" + secret; }
    };

    private final CertificateAuthority ca = new CertificateAuthority() {
        @Override public IssuedCertificate signCertificateRequest(DeviceId id, String csr) {
            return new IssuedCertificate("-----BEGIN CERTIFICATE-----", "01ab",
                "a".repeat(64), NOW, NOW.plus(Duration.ofDays(90)));
        }
        @Override public boolean verifyProofOfPossession(DeviceId id, String fingerprint,
                                                         byte[] payload, String signatureBase64) { return true; }
        @Override public void revokeByFingerprint(String fingerprint, Instant at) { }
        @Override public String currentRevocationList() { return ""; }
        @Override public String caChainPem() { return "chain"; }
        @Override public CaStatus status() { return null; }
        @Override public ServerCertificate issueBrokerCertificate(String cn, List<String> sans) {
            // Not exercised here: enrolment never touches the broker certificate.
            // Throwing rather than returning null means that if a future change makes
            // enrolment depend on this, the test fails at the exact call site.
            throw new UnsupportedOperationException("not needed for enrolment tests");
        }
    };

    private final AuditTrail audit = new AuditTrail() {
        @Override public AuditEntry record(AuditAction action, String subject, String detail) {
            recorded.add(action);
            return AuditEntry.link(recorded.size(), NOW, "test", action, subject, detail,
                AuditEntry.GENESIS_HASH);
        }
        @Override public List<AuditEntry> findAllInOrder() { return List.of(); }
        @Override public List<AuditEntry> findLatest(int limit) { return List.of(); }
    };

    private final DeviceEventPublisher events = event -> { };

    @BeforeEach
    void setUp() {
        device = Device.register("SN-0001", "ESP32-S3", NOW);
        deviceStore.put(device.id(), device);
        tokenStore.put("hashed:test-secret",
            EnrollmentToken.issue(device.id(), "hashed:test-secret", NOW, EnrollmentToken.DEFAULT_TTL));
        useCase = new EnrollDeviceUseCase(devices, tokens, ca, secrets, audit, events, () -> now);
    }

    @Test
    @DisplayName("a valid secret enrols the device and issues a certificate")
    void validSecretEnrolls() {
        EnrollDevice.EnrollmentResult result = useCase.handle(
            new EnrollDevice.EnrollDeviceCommand("test-secret", "csr"));

        assertThat(result.fingerprint()).hasSize(64);
        assertThat(deviceStore.get(device.id()).status()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(recorded).contains(AuditAction.CERTIFICATE_ISSUED, AuditAction.ENROLLMENT_COMPLETED);
    }

    @Test
    @DisplayName("NEGATIVE: the same secret cannot enrol twice")
    void secretCannotBeReplayed() {
        useCase.handle(new EnrollDevice.EnrollDeviceCommand("test-secret", "csr"));

        assertThatThrownBy(() -> useCase.handle(new EnrollDevice.EnrollDeviceCommand("test-secret", "csr")))
            .isInstanceOf(InvalidEnrollmentException.class);
        assertThat(recorded).contains(AuditAction.ENROLLMENT_REJECTED);
    }

    @Test
    @DisplayName("NEGATIVE: an unknown secret is rejected and audited")
    void unknownSecretIsRejected() {
        assertThatThrownBy(() -> useCase.handle(new EnrollDevice.EnrollDeviceCommand("wrong", "csr")))
            .isInstanceOf(InvalidEnrollmentException.class);
        assertThat(recorded).containsExactly(AuditAction.ENROLLMENT_REJECTED);
    }

    @Test
    @DisplayName("NEGATIVE: an expired secret is rejected")
    void expiredSecretIsRejected() {
        now = NOW.plus(EnrollmentToken.DEFAULT_TTL).plusSeconds(1);

        assertThatThrownBy(() -> useCase.handle(new EnrollDevice.EnrollDeviceCommand("test-secret", "csr")))
            .isInstanceOf(InvalidEnrollmentException.class);
        assertThat(deviceStore.get(device.id()).status()).isEqualTo(DeviceStatus.PENDING_ENROLLMENT);
    }
}