package dev.zoel.keystone.domain.firmware;

import dev.zoel.keystone.domain.device.DeviceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolloutTest {

    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");
    private static final String MODEL = "ESP32-S3";

    private static FirmwareArtifact signedArtifact(String version) {
        FirmwareArtifact artifact = FirmwareArtifact.upload(
            FirmwareVersion.parse(version), MODEL, "a".repeat(64), 1024, NOW);
        artifact.attachSignature("c2lnbmF0dXJl");
        return artifact;
    }

    @Test
    @DisplayName("NEGATIVE: an unsigned artifact cannot be rolled out")
    void unsignedArtifactCannotBeDeployed() {
        FirmwareArtifact unsigned = FirmwareArtifact.upload(
            FirmwareVersion.parse("1.0.0"), MODEL, "a".repeat(64), 1024, NOW);

        assertThatThrownBy(() -> Rollout.start(unsigned, null, NOW))
            .isInstanceOf(IllegalFirmwareStateException.class);
    }

    @Test
    @DisplayName("NEGATIVE: a withdrawn artifact cannot be rolled out")
    void withdrawnArtifactCannotBeDeployed() {
        FirmwareArtifact artifact = signedArtifact("1.0.0");
        artifact.withdraw();

        assertThatThrownBy(() -> Rollout.start(artifact, null, NOW))
            .isInstanceOf(IllegalFirmwareStateException.class);
    }

    @Test
    @DisplayName("a rollout always opens at the canary stage")
    void startsAtCanary() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);

        assertThat(rollout.stage()).isEqualTo(RolloutStage.CANARY);
        assertThat(rollout.stage().percentage()).isEqualTo(5);
    }

    @Test
    @DisplayName("advancing walks 5 -> 25 -> 100 and then completes")
    void advancesThroughStages() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);

        rollout.advance(NOW);
        assertThat(rollout.stage()).isEqualTo(RolloutStage.EARLY);
        rollout.advance(NOW);
        assertThat(rollout.stage()).isEqualTo(RolloutStage.FULL);
        rollout.advance(NOW);
        assertThat(rollout.status()).isEqualTo(RolloutStatus.COMPLETED);
    }

    @Test
    @DisplayName("cohort membership is stable across repeated evaluations")
    void cohortMembershipIsStable() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);
        DeviceId device = DeviceId.newId();

        boolean first = rollout.targets(device, MODEL);
        for (int i = 0; i < 100; i++) {
            assertThat(rollout.targets(device, MODEL)).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("the canary cohort is roughly 5% of a large fleet")
    void canaryCohortIsAboutFivePercent() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);

        long targeted = IntStream.range(0, 5000)
            .filter(i -> rollout.targets(DeviceId.newId(), MODEL))
            .count();

        // Uniform hashing, so a wide band: what this rules out is a bucket function
        // that lands everything in one place.
        assertThat(targeted).isBetween(150L, 400L);
    }

    @Test
    @DisplayName("a rollout does not touch devices of another model")
    void otherModelsAreUntouched() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);

        assertThat(rollout.targets(DeviceId.newId(), "SOME-OTHER-MODEL")).isFalse();
    }

    @Test
    @DisplayName("rollback is allowed even after the rollout completed")
    void rollbackAfterCompletionIsAllowed() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);
        rollout.advance(NOW);
        rollout.advance(NOW);
        rollout.advance(NOW);

        rollout.rollBack(NOW);

        assertThat(rollout.status()).isEqualTo(RolloutStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("NEGATIVE: rollback is refused when there is no previous version")
    void rollbackNeedsAPreviousVersion() {
        Rollout rollout = Rollout.start(signedArtifact("1.0.0"), null, NOW);

        assertThatThrownBy(() -> rollout.rollBack(NOW))
            .isInstanceOf(IllegalFirmwareStateException.class);
    }

    @Test
    @DisplayName("after a rollback the targeted device is told to go back")
    void rolledBackRolloutSendsDevicesToPreviousVersion() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);
        rollout.advance(NOW);
        rollout.advance(NOW);   // FULL: everyone is in scope
        rollout.rollBack(NOW);

        DeviceId device = DeviceId.newId();
        assertThat(rollout.desiredVersionFor(device, MODEL)).isEqualTo(FirmwareVersion.parse("1.0.0"));
    }

    @Test
    @DisplayName("NEGATIVE: a paused rollout stops targeting devices")
    void pausedRolloutTargetsNobody() {
        Rollout rollout = Rollout.start(signedArtifact("1.1.0"), FirmwareVersion.parse("1.0.0"), NOW);
        rollout.advance(NOW);
        rollout.advance(NOW);
        rollout.pause(NOW);

        assertThat(rollout.targets(DeviceId.newId(), MODEL)).isFalse();
    }
}
