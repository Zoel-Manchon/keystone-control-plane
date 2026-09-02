package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.firmware.Rollout;

import java.util.UUID;

public interface ManageRollout {

    Rollout start(UUID artifactId);

    Rollout advance(UUID rolloutId);

    Rollout pause(UUID rolloutId);

    Rollout resume(UUID rolloutId);

    Rollout rollBack(UUID rolloutId, String reason);
}
