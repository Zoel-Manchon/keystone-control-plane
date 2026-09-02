package dev.zoel.keystone.integration;

import dev.zoel.keystone.application.port.in.ManageRollout;
import dev.zoel.keystone.application.port.in.PublishFirmware;
import dev.zoel.keystone.application.port.out.ArtifactSigner;
import dev.zoel.keystone.domain.firmware.FirmwareArtifact;
import dev.zoel.keystone.domain.firmware.RolloutStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Firmware publishing and rollout progression against the real signer and database. */
class FirmwareRolloutIT extends AbstractIntegrationTest {

    @Autowired
    private PublishFirmware publishFirmware;

    @Autowired
    private ManageRollout rollouts;

    @Autowired
    private ArtifactSigner signer;

    @Test
    @DisplayName("a published artifact is signed and its digest is computed by us")
    void publishingSignsTheArtifact() {
        byte[] image = "pretend this is a firmware image".getBytes(StandardCharsets.UTF_8);

        FirmwareArtifact artifact = publishFirmware.handle(
            new PublishFirmware.PublishFirmwareCommand("1.0.0", "ESP32-S3", image));

        assertThat(artifact.isDeployable()).isTrue();
        assertThat(artifact.sha256()).matches("[0-9a-f]{64}");
        // The signature covers the digest, so the device can verify before downloading.
        assertThat(signer.verify(artifact.sha256().getBytes(StandardCharsets.UTF_8),
            artifact.signatureBase64())).isTrue();
    }

    @Test
    @DisplayName("NEGATIVE: a tampered digest fails signature verification")
    void tamperedDigestFailsVerification() {
        FirmwareArtifact artifact = publishFirmware.handle(
            new PublishFirmware.PublishFirmwareCommand("1.0.1", "ESP32-S3",
                "image".getBytes(StandardCharsets.UTF_8)));

        String tampered = "b".repeat(64);

        assertThat(signer.verify(tampered.getBytes(StandardCharsets.UTF_8),
            artifact.signatureBase64())).isFalse();
    }

    @Test
    @DisplayName("a rollout walks canary to full and then completes")
    void rolloutProgresses() {
        FirmwareArtifact artifact = publishFirmware.handle(
            new PublishFirmware.PublishFirmwareCommand("2.0.0", "ESP32-S3",
                "image".getBytes(StandardCharsets.UTF_8)));

        var rollout = rollouts.start(artifact.id());
        assertThat(rollout.stage().percentage()).isEqualTo(5);

        assertThat(rollouts.advance(rollout.id()).stage().percentage()).isEqualTo(25);
        assertThat(rollouts.advance(rollout.id()).stage().percentage()).isEqualTo(100);
        assertThat(rollouts.advance(rollout.id()).status()).isEqualTo(RolloutStatus.COMPLETED);
    }

    @Test
    @DisplayName("rolling back sends devices to the previous version")
    void rollbackTargetsThePreviousVersion() {
        publishFirmware.handle(new PublishFirmware.PublishFirmwareCommand("3.0.0", "ESP32-S3",
            "old".getBytes(StandardCharsets.UTF_8)));
        FirmwareArtifact newer = publishFirmware.handle(
            new PublishFirmware.PublishFirmwareCommand("3.1.0", "ESP32-S3",
                "new".getBytes(StandardCharsets.UTF_8)));

        var rollout = rollouts.start(newer.id());
        var reverted = rollouts.rollBack(rollout.id(), "field failures");

        assertThat(reverted.status()).isEqualTo(RolloutStatus.ROLLED_BACK);
        assertThat(reverted.previousVersion().toString()).isEqualTo("3.0.0");
    }
}
