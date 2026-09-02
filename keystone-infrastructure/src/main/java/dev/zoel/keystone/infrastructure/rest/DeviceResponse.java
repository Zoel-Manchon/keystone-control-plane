package dev.zoel.keystone.infrastructure.rest;

import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.pki.CertificateFingerprint;

import java.time.Instant;

/** Outbound DTO. Controls EXACTLY what is exposed; never serialise the aggregate. */
public record DeviceResponse(String id, String serialNumber, String model, String status,
                             Instant registeredAt, String certificateFingerprint,
                             Instant certificateExpiresAt, String firmwareVersion) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
            device.id().toString(),
            device.serialNumber(),
            device.model(),
            device.status().name(),
            device.registeredAt(),
            device.certificateFingerprint().map(CertificateFingerprint::humanReadable).orElse(null),
            device.certificateExpiresAt().orElse(null),
            device.firmwareVersion().orElse(null)
        );
    }
}
