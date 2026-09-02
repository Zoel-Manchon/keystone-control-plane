package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.domain.firmware.RolloutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RolloutJpaRepository extends JpaRepository<RolloutEntity, UUID> {

    List<RolloutEntity> findByTargetModelAndStatusInOrderByLastChangedAtDesc(
        String targetModel, Collection<RolloutStatus> statuses);
}
