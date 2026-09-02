package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.RevokeDeviceCertificate;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.device.DeviceNotFoundException;

import java.time.Instant;

/**
 * Revocation must take effect at the broker, not just in our database, which is why
 * the CA is told as well: it is what regenerates the CRL Mosquitto reads.
 */
public class RevokeDeviceCertificateUseCase implements RevokeDeviceCertificate {

    private final DeviceRepository devices;
    private final CertificateAuthority ca;
    private final AuditTrail audit;
    private final DeviceEventPublisher events;
    private final Clock clock;

    public RevokeDeviceCertificateUseCase(DeviceRepository devices, CertificateAuthority ca,
                                          AuditTrail audit, DeviceEventPublisher events, Clock clock) {
        this.devices = devices;
        this.ca = ca;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void handle(DeviceId deviceId, String reason) {
        Device device = devices.findById(deviceId)
            .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        Instant now = clock.now();
        device.certificateFingerprint()
            .ifPresent(fingerprint -> ca.revokeByFingerprint(fingerprint.sha256Hex(), now));

        device.revoke();
        devices.save(device);

        audit.record(AuditAction.CERTIFICATE_REVOKED, deviceId.toString(), "reason=" + reason);
        events.publish(new DeviceEventPublisher.FleetEvent(
            now, AuditAction.CERTIFICATE_REVOKED, device.serialNumber(), reason));
    }
}
