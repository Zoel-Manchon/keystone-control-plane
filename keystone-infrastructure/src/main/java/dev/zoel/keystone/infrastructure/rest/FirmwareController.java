package dev.zoel.keystone.infrastructure.rest;

import dev.zoel.keystone.application.port.in.ManageRollout;
import dev.zoel.keystone.application.port.in.PublishFirmware;
import dev.zoel.keystone.application.port.in.ResolveDeviceUpdate;
import dev.zoel.keystone.application.port.out.ArtifactSigner;
import dev.zoel.keystone.application.port.out.ArtifactStorage;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.firmware.FirmwareArtifact;
import dev.zoel.keystone.domain.firmware.Rollout;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Firmware and rollout API.
 *
 * The manifest and the artifact download are the two routes devices call. They are
 * separate because the device verifies the signed manifest FIRST and only downloads
 * the image if the signature checks out and the version is actually newer.
 */
@RestController
@RequestMapping("/api/v1/firmware")
public class FirmwareController {

    private final PublishFirmware publishFirmware;
    private final ManageRollout rollouts;
    private final ResolveDeviceUpdate resolveUpdate;
    private final ArtifactStorage storage;
    private final ArtifactSigner signer;

    FirmwareController(PublishFirmware publishFirmware, ManageRollout rollouts,
                       ResolveDeviceUpdate resolveUpdate, ArtifactStorage storage,
                       ArtifactSigner signer) {
        this.publishFirmware = publishFirmware;
        this.rollouts = rollouts;
        this.resolveUpdate = resolveUpdate;
        this.storage = storage;
        this.signer = signer;
    }

    @PostMapping
    public ArtifactResponse publish(@RequestParam("version") String version,
                                    @RequestParam("model") String model,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        FirmwareArtifact artifact = publishFirmware.handle(
            new PublishFirmware.PublishFirmwareCommand(version, model, file.getBytes()));
        return ArtifactResponse.from(artifact);
    }

    @PostMapping("/rollouts/{artifactId}")
    public RolloutResponse startRollout(@PathVariable("artifactId") String artifactId) {
        return RolloutResponse.from(rollouts.start(UUID.fromString(artifactId)));
    }

    @PostMapping("/rollouts/{rolloutId}/advance")
    public RolloutResponse advance(@PathVariable("rolloutId") String rolloutId) {
        return RolloutResponse.from(rollouts.advance(UUID.fromString(rolloutId)));
    }

    @PostMapping("/rollouts/{rolloutId}/rollback")
    public RolloutResponse rollBack(@PathVariable("rolloutId") String rolloutId,
                                    @RequestParam(value = "reason", defaultValue = "operator decision")
                                    String reason) {
        return RolloutResponse.from(rollouts.rollBack(UUID.fromString(rolloutId), reason));
    }

    /** Device-facing. Returns a manifest signed for THIS device and no other. */
    @GetMapping("/manifest/{deviceId}")
    public ResolveDeviceUpdate.UpdateManifest manifest(@PathVariable("deviceId") String deviceId) {
        return resolveUpdate.handle(DeviceId.of(deviceId));
    }

    /** Device-facing. The bytes are useless without a matching signed manifest. */
    @GetMapping("/{artifactId}/artifact")
    public ResponseEntity<Resource> download(@PathVariable("artifactId") String artifactId) {
        byte[] content = storage.load(UUID.fromString(artifactId));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifactId + ".bin\"")
            .body(new ByteArrayResource(content));
    }

    /** The public key devices pin. Public by definition: it verifies, it cannot sign. */
    @GetMapping(value = "/signing-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public String signingKey() {
        return signer.publicKeyBase64();
    }

    public record ArtifactResponse(String id, String version, String model, String sha256,
                                   long sizeBytes, String signature) {
        static ArtifactResponse from(FirmwareArtifact artifact) {
            return new ArtifactResponse(artifact.id().toString(), artifact.version().toString(),
                artifact.model(), artifact.sha256(), artifact.sizeBytes(), artifact.signatureBase64());
        }
    }

    public record RolloutResponse(String id, String model, String version, String previousVersion,
                                  String stage, int percentage, String status) {
        static RolloutResponse from(Rollout rollout) {
            return new RolloutResponse(rollout.id().toString(), rollout.targetModel(),
                rollout.targetVersion().toString(),
                rollout.previousVersion() == null ? null : rollout.previousVersion().toString(),
                rollout.stage().name(), rollout.stage().percentage(), rollout.status().name());
        }
    }
}
