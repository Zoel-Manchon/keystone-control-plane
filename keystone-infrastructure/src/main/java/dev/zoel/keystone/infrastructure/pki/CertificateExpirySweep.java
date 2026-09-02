package dev.zoel.keystone.infrastructure.pki;

import dev.zoel.keystone.application.port.in.FindExpiringCertificates;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.device.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Daily sweep for certificates about to expire.
 *
 * Short-lived certificates are only a good idea if something notices before they
 * lapse. Otherwise "90 days" just means "an outage in 90 days".
 */
@Component
public class CertificateExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(CertificateExpirySweep.class);
    private static final Duration WARNING_WINDOW = Duration.ofDays(30);

    private final FindExpiringCertificates expiring;
    private final AuditTrail audit;
    private final DeviceEventPublisher events;

    CertificateExpirySweep(FindExpiringCertificates expiring, AuditTrail audit,
                           DeviceEventPublisher events) {
        this.expiring = expiring;
        this.audit = audit;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${keystone.pki.expiry-sweep-ms:86400000}", initialDelay = 60_000)
    public void sweep() {
        List<Device> devices = expiring.handle(WARNING_WINDOW);
        if (devices.isEmpty()) {
            return;
        }

        log.warn("{} device certificates expire within {} days", devices.size(), WARNING_WINDOW.toDays());

        // One audit entry for the sweep, not one per device: a thousand expiring
        // certificates would otherwise bury every other event in the trail.
        audit.record(AuditAction.CERTIFICATE_EXPIRING, "fleet",
            "count=" + devices.size() + " window=" + WARNING_WINDOW.toDays() + "d");

        events.publish(new DeviceEventPublisher.FleetEvent(Instant.now(),
            AuditAction.CERTIFICATE_EXPIRING, "fleet",
            devices.size() + " certificados caducan en menos de 30 días"));
    }
}
