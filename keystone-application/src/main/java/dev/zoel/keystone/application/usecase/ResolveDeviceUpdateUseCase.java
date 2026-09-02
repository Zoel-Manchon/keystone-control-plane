package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.ResolveDeviceUpdate;
import dev.zoel.keystone.application.port.out.ArtifactSigner;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.application.port.out.FirmwareRepository;
import dev.zoel.keystone.application.port.out.RolloutRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.device.DeviceNotFoundException;
import dev.zoel.keystone.domain.firmware.FirmwareArtifact;
import dev.zoel.keystone.domain.firmware.FirmwareVersion;
import dev.zoel.keystone.domain.firmware.Rollout;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * Answers a device's update poll.
 *
 * A revoked or expired device gets nothing. That is the point where the PKI and the
 * OTA pipeline meet: revocation must cut off firmware delivery, not merely MQTT, or
 * a compromised device keeps receiving signed images it can study at leisure.
 */
public class ResolveDeviceUpdateUseCase implements ResolveDeviceUpdate {

    private final DeviceRepository devices;
    private final RolloutRepository rollouts;
    private final FirmwareRepository firmware;
    private final ArtifactSigner signer;

    public ResolveDeviceUpdateUseCase(DeviceRepository devices, RolloutRepository rollouts,
                                      FirmwareRepository firmware, ArtifactSigner signer) {
        this.devices = devices;
        this.rollouts = rollouts;
        this.firmware = firmware;
        this.signer = signer;
    }

    @Override
    public UpdateManifest handle(DeviceId deviceId) {
        Device device = devices.findById(deviceId)
            .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        if (!device.canPublish(Instant.now())) {
            return UpdateManifest.none(deviceId.toString());
        }

        Optional<Rollout> applicable = rollouts.findActiveForModel(device.model()).stream()
            .filter(rollout -> rollout.targets(deviceId, device.model()))
            .max(Comparator.comparing(Rollout::lastChangedAt));

        if (applicable.isEmpty()) {
            return UpdateManifest.none(deviceId.toString());
        }

        Rollout rollout = applicable.get();
        FirmwareVersion desired = rollout.desiredVersionFor(deviceId, device.model());
        if (desired == null || desired.toString().equals(device.firmwareVersion().orElse(null))) {
            return UpdateManifest.none(deviceId.toString());
        }

        Optional<FirmwareArtifact> artifact = firmware.findAll().stream()
            .filter(candidate -> candidate.model().equals(device.model()))
            .filter(candidate -> candidate.version().equals(desired))
            .filter(FirmwareArtifact::isDeployable)
            .findFirst();

        if (artifact.isEmpty()) {
            return UpdateManifest.none(deviceId.toString());
        }

        FirmwareArtifact target = artifact.get();
        UpdateManifest unsigned = new UpdateManifest(true, deviceId.toString(), desired.toString(),
            target.id().toString(), target.sha256(), target.sizeBytes(),
            "/api/v1/firmware/" + target.id() + "/artifact", null);

        // The manifest is signed per device: the device id is inside the signed
        // payload, so a manifest captured from one device cannot be replayed at another.
        String signature = signer.sign(unsigned.canonicalPayload());

        return new UpdateManifest(true, unsigned.deviceId(), unsigned.version(), unsigned.artifactId(),
            unsigned.sha256(), unsigned.sizeBytes(), unsigned.downloadPath(), signature);
    }
}
