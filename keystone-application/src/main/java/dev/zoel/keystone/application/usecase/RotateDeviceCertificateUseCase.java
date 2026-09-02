package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.RotateDeviceCertificate;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.enrollment.InvalidEnrollmentException;
import dev.zoel.keystone.domain.pki.CertificateFingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Renews a device certificate.
 *
 * The fingerprint identifies the current certificate but does NOT authenticate the
 * caller. The device must also sign a canonical rotation payload with the CURRENT
 * private key. A stolen/public fingerprint therefore cannot be used to bind an
 * attacker-controlled CSR to the device identity. Revoked or expired devices cannot
 * renew their way back into the fleet.
 *
 * Every failure returns the same generic exception, for the same reason as enrolment:
 * a distinguishable error is a probe oracle.
 */
public class RotateDeviceCertificateUseCase implements RotateDeviceCertificate {

    private final DeviceRepository devices;
    private final CertificateAuthority ca;
    private final AuditTrail audit;
    private final DeviceEventPublisher events;
    private final Clock clock;

    public RotateDeviceCertificateUseCase(DeviceRepository devices, CertificateAuthority ca,
                                          AuditTrail audit, DeviceEventPublisher events, Clock clock) {
        this.devices = devices;
        this.ca = ca;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public RotationResult handle(RotateCommand command) {
        Instant now = clock.now();

        Device device = devices.findById(command.deviceId())
            .orElseThrow(InvalidEnrollmentException::new);

        // Revoked, expired or never enrolled: all three end here.
        if (!device.canPublish(now)) {
            audit.record(AuditAction.ROTATION_REJECTED, command.deviceId().toString(),
                "device cannot publish, status=" + device.status());
            throw new InvalidEnrollmentException();
        }

        String held = device.certificateFingerprint()
            .map(CertificateFingerprint::sha256Hex)
            .orElseThrow(InvalidEnrollmentException::new);

        // Constant-time comparison: a fingerprint check that leaks timing is a
        // fingerprint check an attacker can walk byte by byte.
        if (!java.security.MessageDigest.isEqual(
                held.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                normalise(command.currentFingerprint()).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            audit.record(AuditAction.ROTATION_REJECTED, command.deviceId().toString(),
                "presented fingerprint does not match the one on record");
            throw new InvalidEnrollmentException();
        }

        byte[] proofPayload = rotationProofPayload(device.id().toString(), held, command.csrPem());
        if (!ca.verifyProofOfPossession(device.id(), held, proofPayload, command.proofBase64())) {
            audit.record(AuditAction.ROTATION_REJECTED, command.deviceId().toString(),
                "proof of possession did not verify");
            throw new InvalidEnrollmentException();
        }

        CertificateAuthority.IssuedCertificate issued =
            ca.signCertificateRequest(device.id(), command.csrPem());

        // The superseded certificate is revoked, not merely forgotten. Leaving it
        // valid would mean every rotation doubles the number of keys that can
        // impersonate the device.
        ca.revokeByFingerprint(held, now);

        device.rotateCertificate(new CertificateFingerprint(issued.sha256Fingerprint()), issued.notAfter());
        devices.save(device);

        audit.record(AuditAction.CERTIFICATE_ROTATED, device.id().toString(),
            "serial=" + issued.serialNumber() + " supersedes=" + held.substring(0, 16) + "...");
        events.publish(new DeviceEventPublisher.FleetEvent(
            now, AuditAction.CERTIFICATE_ROTATED, device.serialNumber(), "certificate renewed"));

        return new RotationResult(issued.certificatePem(), ca.caChainPem(), issued.sha256Fingerprint());
    }

    /**
     * Canonical bytes that a device signs with its current private key.
     *
     * The CSR digest is calculated over DER, not over the PEM text. PEM is only a
     * transport representation and its line endings/trailing newline can change when
     * crossing Windows shells or JSON serializers. DER gives both sides one stable
     * byte representation of the exact PKCS#10 request.
     */
    public static byte[] rotationProofPayload(String deviceId, String fingerprint, String csrPem) {
        String csrSha256 = HexFormat.of().formatHex(sha256(csrDer(csrPem)));
        return ("keystone-rotation-v1\n" + deviceId + "\n" + fingerprint + "\n" + csrSha256)
            .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] csrDer(String csrPem) {
        if (csrPem == null) {
            throw new IllegalArgumentException("CSR must not be null");
        }

        String normalised = csrPem.replace("\r\n", "\n").replace('\r', '\n').trim();
        String begin = "-----BEGIN CERTIFICATE REQUEST-----";
        String end = "-----END CERTIFICATE REQUEST-----";
        if (!normalised.startsWith(begin) || !normalised.endsWith(end)) {
            throw new IllegalArgumentException("CSR must be PEM encoded");
        }

        String body = normalised.substring(begin.length(), normalised.length() - end.length())
            .replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException malformedBase64) {
            throw new IllegalArgumentException("CSR contains invalid Base64", malformedBase64);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String normalise(String fingerprint) {
        return fingerprint == null ? "" : fingerprint.replace(":", "").trim().toLowerCase();
    }
}
