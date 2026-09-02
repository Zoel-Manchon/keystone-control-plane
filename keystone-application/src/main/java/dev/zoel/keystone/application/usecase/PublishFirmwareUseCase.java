package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.PublishFirmware;
import dev.zoel.keystone.application.port.out.ArtifactSigner;
import dev.zoel.keystone.application.port.out.ArtifactStorage;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.FirmwareRepository;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.firmware.FirmwareArtifact;
import dev.zoel.keystone.domain.firmware.FirmwareVersion;

/**
 * Stores an image and signs it in the same operation.
 *
 * There is no path that leaves a stored-but-unsigned artifact reachable by a rollout:
 * the artifact is only persisted once the signature is attached. An "upload now, sign
 * later" workflow is exactly where an unsigned build slips into production.
 */
public class PublishFirmwareUseCase implements PublishFirmware {

    private final FirmwareRepository firmware;
    private final ArtifactStorage storage;
    private final ArtifactSigner signer;
    private final AuditTrail audit;
    private final Clock clock;

    public PublishFirmwareUseCase(FirmwareRepository firmware, ArtifactStorage storage,
                                  ArtifactSigner signer, AuditTrail audit, Clock clock) {
        this.firmware = firmware;
        this.storage = storage;
        this.signer = signer;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public FirmwareArtifact handle(PublishFirmwareCommand command) {
        FirmwareVersion version = FirmwareVersion.parse(command.version());

        FirmwareArtifact artifact = FirmwareArtifact.upload(
            version, command.model(), "0".repeat(64), Math.max(command.content().length, 1), clock.now());

        // The digest is computed from the bytes as they are written, never taken from
        // whoever uploaded them: a caller-supplied hash verifies nothing.
        ArtifactStorage.StoredArtifact stored = storage.store(artifact.id(), command.content());

        FirmwareArtifact persisted = FirmwareArtifact.rehydrate(artifact.id(), version,
            command.model(), stored.sha256(), stored.sizeBytes(), artifact.uploadedAt(), null, false);

        // The signature covers the digest, not the bytes: the device checks the
        // signature first and only then bothers to download and hash the image.
        persisted.attachSignature(signer.sign(stored.sha256().getBytes(
            java.nio.charset.StandardCharsets.UTF_8)));

        FirmwareArtifact saved = firmware.save(persisted);
        audit.record(AuditAction.FIRMWARE_PUBLISHED, saved.id().toString(),
            "version=" + version + " model=" + command.model() + " sha256=" + stored.sha256());
        return saved;
    }
}
