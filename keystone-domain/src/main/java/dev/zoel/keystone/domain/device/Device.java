package dev.zoel.keystone.domain.device;

import dev.zoel.keystone.domain.pki.CertificateFingerprint;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root.
 *
 * Every device state transition goes through this class, so the rules cannot be
 * bypassed from a service or a controller.
 */
public final class Device {

    private final DeviceId id;
    private final String serialNumber;
    private final String model;
    private final Instant registeredAt;

    private DeviceStatus status;
    private CertificateFingerprint certificateFingerprint;
    private Instant certificateExpiresAt;
    private String firmwareVersion;

    private Device(DeviceId id, String serialNumber, String model, Instant registeredAt) {
        this.id = Objects.requireNonNull(id);
        this.serialNumber = requireText(serialNumber, "serialNumber");
        this.model = requireText(model, "model");
        this.registeredAt = Objects.requireNonNull(registeredAt);
        this.status = DeviceStatus.PENDING_ENROLLMENT;
    }

    /** Adds the device to the inventory. It starts WITHOUT a certificate: enrolment is a separate step. */
    public static Device register(String serialNumber, String model, Instant now) {
        return new Device(DeviceId.newId(), serialNumber, model, now);
    }

    /** Rehydration from persistence. For adapters only. */
    public static Device rehydrate(DeviceId id, String serialNumber, String model,
                                   Instant registeredAt, DeviceStatus status,
                                   CertificateFingerprint fingerprint, Instant certificateExpiresAt,
                                   String firmwareVersion) {
        Device device = new Device(id, serialNumber, model, registeredAt);
        device.status = Objects.requireNonNull(status);
        device.certificateFingerprint = fingerprint;
        device.certificateExpiresAt = certificateExpiresAt;
        device.firmwareVersion = firmwareVersion;
        return device;
    }

    /** A device can only be enrolled once; rotating the certificate is a different operation. */
    public void completeEnrollment(CertificateFingerprint fingerprint, Instant expiresAt) {
        if (status != DeviceStatus.PENDING_ENROLLMENT) {
            throw new IllegalDeviceStateException(
                "only a device in PENDING_ENROLLMENT can be enrolled, current status: " + status);
        }
        this.certificateFingerprint = Objects.requireNonNull(fingerprint);
        this.certificateExpiresAt = Objects.requireNonNull(expiresAt);
        this.status = DeviceStatus.ACTIVE;
    }

    public void rotateCertificate(CertificateFingerprint fingerprint, Instant expiresAt) {
        if (status != DeviceStatus.ACTIVE) {
            throw new IllegalDeviceStateException("cannot rotate the certificate of a device in status " + status);
        }
        this.certificateFingerprint = Objects.requireNonNull(fingerprint);
        this.certificateExpiresAt = Objects.requireNonNull(expiresAt);
    }

    public void revoke() {
        if (status == DeviceStatus.DECOMMISSIONED) {
            throw new IllegalDeviceStateException("a decommissioned device can no longer be revoked");
        }
        this.status = DeviceStatus.REVOKED;
    }

    public void decommission() {
        this.status = DeviceStatus.DECOMMISSIONED;
    }

    public void reportFirmware(String version) {
        this.firmwareVersion = requireText(version, "firmwareVersion");
    }

    /** Core rule: this is what gets checked before accepting a publication from the device. */
    public boolean canPublish(Instant now) {
        return status == DeviceStatus.ACTIVE
            && certificateExpiresAt != null
            && certificateExpiresAt.isAfter(now);
    }

    public boolean certificateExpiresWithin(Duration window, Instant now) {
        return certificateExpiresAt != null && certificateExpiresAt.isBefore(now.plus(window));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public DeviceId id() { return id; }
    public String serialNumber() { return serialNumber; }
    public String model() { return model; }
    public Instant registeredAt() { return registeredAt; }
    public DeviceStatus status() { return status; }
    public Optional<CertificateFingerprint> certificateFingerprint() { return Optional.ofNullable(certificateFingerprint); }
    public Optional<Instant> certificateExpiresAt() { return Optional.ofNullable(certificateExpiresAt); }
    public Optional<String> firmwareVersion() { return Optional.ofNullable(firmwareVersion); }
}
