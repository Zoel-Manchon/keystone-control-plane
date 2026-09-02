package dev.zoel.keystone.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FirmwareArtifactJpaRepository extends JpaRepository<FirmwareArtifactEntity, UUID> {
}
