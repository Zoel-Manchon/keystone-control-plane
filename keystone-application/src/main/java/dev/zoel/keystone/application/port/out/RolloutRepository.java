package dev.zoel.keystone.application.port.out;

import dev.zoel.keystone.domain.firmware.Rollout;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolloutRepository {

    Rollout save(Rollout rollout);

    Optional<Rollout> findById(UUID id);

    List<Rollout> findAll();

    /** Live rollouts for a model, newest first. Used to resolve what a device should run. */
    List<Rollout> findActiveForModel(String model);
}
