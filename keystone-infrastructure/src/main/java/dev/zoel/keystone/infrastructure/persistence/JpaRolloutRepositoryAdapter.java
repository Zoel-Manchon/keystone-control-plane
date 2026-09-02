package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.application.port.out.RolloutRepository;
import dev.zoel.keystone.domain.firmware.FirmwareVersion;
import dev.zoel.keystone.domain.firmware.Rollout;
import dev.zoel.keystone.domain.firmware.RolloutStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaRolloutRepositoryAdapter implements RolloutRepository {

    private final RolloutJpaRepository jpa;

    JpaRolloutRepositoryAdapter(RolloutJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Rollout save(Rollout rollout) {
        jpa.save(new RolloutEntity(rollout.id(), rollout.artifactId(), rollout.targetModel(),
            rollout.targetVersion().toString(),
            rollout.previousVersion() == null ? null : rollout.previousVersion().toString(),
            rollout.startedAt(), rollout.stage(), rollout.status(), rollout.lastChangedAt()));
        return rollout;
    }

    @Override
    public Optional<Rollout> findById(UUID id) {
        return jpa.findById(id).map(JpaRolloutRepositoryAdapter::toDomain);
    }

    @Override
    public List<Rollout> findAll() {
        return jpa.findAll().stream().map(JpaRolloutRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<Rollout> findActiveForModel(String model) {
        // PAUSED is excluded: a paused rollout must stop handing out manifests.
        // ROLLED_BACK is included, because it is what tells devices to step down.
        return jpa.findByTargetModelAndStatusInOrderByLastChangedAtDesc(model,
                List.of(RolloutStatus.IN_PROGRESS, RolloutStatus.COMPLETED, RolloutStatus.ROLLED_BACK))
            .stream().map(JpaRolloutRepositoryAdapter::toDomain).toList();
    }

    private static Rollout toDomain(RolloutEntity entity) {
        return Rollout.rehydrate(entity.getId(), entity.getArtifactId(), entity.getTargetModel(),
            FirmwareVersion.parse(entity.getTargetVersion()),
            entity.getPreviousVersion() == null ? null : FirmwareVersion.parse(entity.getPreviousVersion()),
            entity.getStartedAt(), entity.getStage(), entity.getStatus(), entity.getLastChangedAt());
    }
}
