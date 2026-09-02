package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.pki.CertificateFingerprint;

/** Translates between the domain aggregate and the JPA entity. */
final class DeviceMapper {

    private DeviceMapper() {
    }

    static DeviceEntity toEntity(Device device) {
        return new DeviceEntity(
            device.id().value(),
            device.serialNumber(),
            device.model(),
            device.registeredAt(),
            device.status(),
            device.certificateFingerprint().map(CertificateFingerprint::sha256Hex).orElse(null),
            device.certificateExpiresAt().orElse(null),
            device.firmwareVersion().orElse(null)
        );
    }

    static Device toDomain(DeviceEntity entity) {
        return Device.rehydrate(
            new DeviceId(entity.getId()),
            entity.getSerialNumber(),
            entity.getModel(),
            entity.getRegisteredAt(),
            entity.getStatus(),
            entity.getCertificateFingerprint() == null
                ? null : new CertificateFingerprint(entity.getCertificateFingerprint()),
            entity.getCertificateExpiresAt(),
            entity.getFirmwareVersion()
        );
    }
}
