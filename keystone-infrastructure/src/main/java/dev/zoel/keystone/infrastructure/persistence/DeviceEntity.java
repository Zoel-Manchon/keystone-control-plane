package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.domain.device.DeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity. A persistence detail: it lives here and NEVER escapes this package
 * into the domain. The mapper translates at the boundary.
 */
@Entity
@Table(name = "devices")
public class DeviceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 128)
    private String serialNumber;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeviceStatus status;

    @Column(name = "certificate_fingerprint", length = 64)
    private String certificateFingerprint;

    @Column(name = "certificate_expires_at")
    private Instant certificateExpiresAt;

    @Column(name = "firmware_version", length = 64)
    private String firmwareVersion;

    protected DeviceEntity() {
        // required by JPA
    }

    public DeviceEntity(UUID id, String serialNumber, String model, Instant registeredAt,
                        DeviceStatus status, String certificateFingerprint,
                        Instant certificateExpiresAt, String firmwareVersion) {
        this.id = id;
        this.serialNumber = serialNumber;
        this.model = model;
        this.registeredAt = registeredAt;
        this.status = status;
        this.certificateFingerprint = certificateFingerprint;
        this.certificateExpiresAt = certificateExpiresAt;
        this.firmwareVersion = firmwareVersion;
    }

    public UUID getId() { return id; }
    public String getSerialNumber() { return serialNumber; }
    public String getModel() { return model; }
    public Instant getRegisteredAt() { return registeredAt; }
    public DeviceStatus getStatus() { return status; }
    public String getCertificateFingerprint() { return certificateFingerprint; }
    public Instant getCertificateExpiresAt() { return certificateExpiresAt; }
    public String getFirmwareVersion() { return firmwareVersion; }
}
