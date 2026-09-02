package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.application.port.out.FirmwareRepository;
import dev.zoel.keystone.domain.firmware.FirmwareArtifact;
import dev.zoel.keystone.domain.firmware.FirmwareVersion;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaFirmwareRepositoryAdapter implements FirmwareRepository {

    private final FirmwareArtifactJpaRepository jpa;

    JpaFirmwareRepositoryAdapter(FirmwareArtifactJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public FirmwareArtifact save(FirmwareArtifact artifact) {
        jpa.save(new FirmwareArtifactEntity(artifact.id(), artifact.version().toString(),
            artifact.model(), artifact.sha256(), artifact.sizeBytes(), artifact.uploadedAt(),
            artifact.signatureBase64(), artifact.isWithdrawn()));
        return artifact;
    }

    @Override
    public Optional<FirmwareArtifact> findById(UUID id) {
        return jpa.findById(id).map(JpaFirmwareRepositoryAdapter::toDomain);
    }

    @Override
    public List<FirmwareArtifact> findAll() {
        return jpa.findAll().stream().map(JpaFirmwareRepositoryAdapter::toDomain).toList();
    }

    private static FirmwareArtifact toDomain(FirmwareArtifactEntity entity) {
        return FirmwareArtifact.rehydrate(entity.getId(), FirmwareVersion.parse(entity.getVersion()),
            entity.getModel(), entity.getSha256(), entity.getSizeBytes(), entity.getUploadedAt(),
            entity.getSignature(), entity.isWithdrawn());
    }
}
