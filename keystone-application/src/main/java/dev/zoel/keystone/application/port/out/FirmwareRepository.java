package dev.zoel.keystone.application.port.out;

import dev.zoel.keystone.domain.firmware.FirmwareArtifact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FirmwareRepository {

    FirmwareArtifact save(FirmwareArtifact artifact);

    Optional<FirmwareArtifact> findById(UUID id);

    List<FirmwareArtifact> findAll();
}
