package dev.zoel.keystone.domain.device;

import dev.zoel.keystone.domain.pki.CertificateFingerprint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain tests: fast, no Spring, no database.
 * Note how many of them are NEGATIVE: that is what sets a security project apart.
 */
class DeviceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");
    private static final CertificateFingerprint FP = new CertificateFingerprint("a".repeat(64));

    @Test
    @DisplayName("a newly registered device has no certificate and cannot publish")
    void newDeviceCannotPublish() {
        Device device = Device.register("SN-0001", "ESP32-S3", NOW);

        assertThat(device.status()).isEqualTo(DeviceStatus.PENDING_ENROLLMENT);
        assertThat(device.certificateFingerprint()).isEmpty();
        assertThat(device.canPublish(NOW)).isFalse();
    }

    @Test
    @DisplayName("once enrolled, the device can publish")
    void enrolledDeviceCanPublish() {
        Device device = Device.register("SN-0001", "ESP32-S3", NOW);

        device.completeEnrollment(FP, NOW.plus(Duration.ofDays(90)));

        assertThat(device.status()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(device.canPublish(NOW)).isTrue();
    }

    @Test
    @DisplayName("NEGATIVE: a device cannot be enrolled twice")
    void cannotEnrollTwice() {
        Device device = Device.register("SN-0001", "ESP32-S3", NOW);
        device.completeEnrollment(FP, NOW.plus(Duration.ofDays(90)));

        assertThatThrownBy(() -> device.completeEnrollment(FP, NOW.plus(Duration.ofDays(90))))
            .isInstanceOf(IllegalDeviceStateException.class);
    }

    @Test
    @DisplayName("NEGATIVE: a revoked device cannot publish")
    void revokedDeviceCannotPublish() {
        Device device = Device.register("SN-0001", "ESP32-S3", NOW);
        device.completeEnrollment(FP, NOW.plus(Duration.ofDays(90)));

        device.revoke();

        assertThat(device.canPublish(NOW)).isFalse();
    }

    @Test
    @DisplayName("NEGATIVE: an expired certificate blocks publication")
    void expiredCertificateCannotPublish() {
        Device device = Device.register("SN-0001", "ESP32-S3", NOW);
        device.completeEnrollment(FP, NOW.plus(Duration.ofDays(1)));

        assertThat(device.canPublish(NOW.plus(Duration.ofDays(2)))).isFalse();
    }

    @Test
    @DisplayName("NEGATIVE: the certificate of a revoked device cannot be rotated")
    void cannotRotateRevokedDevice() {
        Device device = Device.register("SN-0001", "ESP32-S3", NOW);
        device.completeEnrollment(FP, NOW.plus(Duration.ofDays(90)));
        device.revoke();

        assertThatThrownBy(() -> device.rotateCertificate(FP, NOW.plus(Duration.ofDays(180))))
            .isInstanceOf(IllegalDeviceStateException.class);
    }

    @Test
    @DisplayName("NEGATIVE: a device without a serial number is rejected")
    void rejectsBlankSerial() {
        assertThatThrownBy(() -> Device.register("   ", "ESP32-S3", NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("flags certificates expiring inside the warning window")
    void detectsUpcomingExpiry() {
        Device device = Device.register("SN-0001", "ESP32-S3", NOW);
        device.completeEnrollment(FP, NOW.plus(Duration.ofDays(20)));

        assertThat(device.certificateExpiresWithin(Duration.ofDays(30), NOW)).isTrue();
        assertThat(device.certificateExpiresWithin(Duration.ofDays(10), NOW)).isFalse();
    }
}
