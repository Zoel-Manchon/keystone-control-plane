package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.EnrollDevice;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.application.port.out.EnrollmentTokenRepository;
import dev.zoel.keystone.application.port.out.SecretGenerator;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.enrollment.EnrollmentToken;
import dev.zoel.keystone.domain.enrollment.InvalidEnrollmentException;
import dev.zoel.keystone.domain.pki.CertificateFingerprint;

import java.time.Instant;

/**
 * The security-critical path of the whole system.
 *
 * Token consumption is a database compare-and-set, not a read/modify/write sequence.
 * Exactly one concurrent request can change consumed_at from NULL while the token is
 * unexpired; every loser is rejected before any certificate is signed.
 */
public class EnrollDeviceUseCase implements EnrollDevice {

    private final DeviceRepository devices;
    private final EnrollmentTokenRepository tokens;
    private final CertificateAuthority ca;
    private final SecretGenerator secrets;
    private final AuditTrail audit;
    private final DeviceEventPublisher events;
    private final Clock clock;

    public EnrollDeviceUseCase(DeviceRepository devices, EnrollmentTokenRepository tokens,
                               CertificateAuthority ca, SecretGenerator secrets,
                               AuditTrail audit, DeviceEventPublisher events, Clock clock) {
        this.devices = devices;
        this.tokens = tokens;
        this.ca = ca;
        this.secrets = secrets;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public EnrollmentResult handle(EnrollDeviceCommand command) {
        Instant now = clock.now();

        // Look the token up by hash: the plaintext secret is never compared against
        // anything stored, because nothing stored is the plaintext.
        String tokenHash = secrets.hash(command.secret());
        EnrollmentToken token = tokens.findByHash(tokenHash)
            .orElseThrow(() -> {
                audit.record(AuditAction.ENROLLMENT_REJECTED, "unknown", "unrecognised token");
                return new InvalidEnrollmentException();
            });

        Device device = devices.findById(token.deviceId())
            .orElseThrow(InvalidEnrollmentException::new);

        // Atomic at the database: only one racing request can spend this token.
        if (!tokens.consumeIfUsable(tokenHash, now)) {
            audit.record(AuditAction.ENROLLMENT_REJECTED, device.id().toString(),
                "token expired, already used, or lost a concurrent consume race");
            throw new InvalidEnrollmentException();
        }

        CertificateAuthority.IssuedCertificate issued =
            ca.signCertificateRequest(device.id(), command.certificateSigningRequestPem());

        device.completeEnrollment(new CertificateFingerprint(issued.sha256Fingerprint()), issued.notAfter());
        devices.save(device);

        audit.record(AuditAction.CERTIFICATE_ISSUED, device.id().toString(),
            "serial=" + issued.serialNumber() + " notAfter=" + issued.notAfter());
        audit.record(AuditAction.ENROLLMENT_COMPLETED, device.id().toString(),
            "serial=" + device.serialNumber());

        events.publish(new DeviceEventPublisher.FleetEvent(
            now, AuditAction.ENROLLMENT_COMPLETED, device.serialNumber(), "certificate issued"));

        return new EnrollmentResult(issued.certificatePem(), ca.caChainPem(), issued.sha256Fingerprint());
    }
}
