package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceStatus;
import dev.zoel.keystone.domain.pki.CertificateFingerprint;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * View model for a single table row.
 *
 * Mapping status to a label and a CSS class happens HERE, not in the template:
 * presentation logic buried in template conditionals cannot be tested.
 */
public record DeviceRow(String id, String serialNumber, String model,
                        String statusLabel, String chipModifier,
                        String fingerprint, String certificateSummary,
                        String firmwareVersion) {

    /** Window within which a certificate is flagged as expiring soon. */
    private static final Duration EXPIRY_WARNING = Duration.ofDays(30);

    public static DeviceRow from(Device device, Instant now) {
        boolean expiringSoon = device.status() == DeviceStatus.ACTIVE
            && device.certificateExpiresWithin(EXPIRY_WARNING, now);

        return new DeviceRow(
            device.id().toString(),
            device.serialNumber(),
            device.model(),
            label(device.status(), expiringSoon),
            chipModifier(device.status(), expiringSoon),
            device.certificateFingerprint().map(CertificateFingerprint::humanReadable).orElse(null),
            certificateSummary(device, now),
            device.firmwareVersion().orElse(null)
        );
    }

    private static String label(DeviceStatus status, boolean expiringSoon) {
        if (status == DeviceStatus.ACTIVE && expiringSoon) {
            return "Caduca pronto";
        }
        return switch (status) {
            case PENDING_ENROLLMENT -> "Pendiente";
            case ACTIVE -> "Activo";
            case REVOKED -> "Revocado";
            case DECOMMISSIONED -> "Retirado";
        };
    }

    private static String chipModifier(DeviceStatus status, boolean expiringSoon) {
        if (status == DeviceStatus.ACTIVE && expiringSoon) {
            return "ks-chip--warn";
        }
        return switch (status) {
            case PENDING_ENROLLMENT -> "ks-chip--pending";
            case ACTIVE -> "ks-chip--ok";
            case REVOKED -> "ks-chip--danger";
            case DECOMMISSIONED -> "";
        };
    }

    private static String certificateSummary(Device device, Instant now) {
        return device.certificateExpiresAt()
            .map(expiry -> {
                long days = ChronoUnit.DAYS.between(now, expiry);
                return days < 0 ? "Caducado" : "Caduca en " + days + " d";
            })
            .orElse(null);
    }
}
