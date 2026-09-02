package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.ManageRollout;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import dev.zoel.keystone.application.port.out.FirmwareRepository;
import dev.zoel.keystone.application.port.out.RolloutRepository;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.firmware.FirmwareArtifact;
import dev.zoel.keystone.domain.firmware.FirmwareVersion;
import dev.zoel.keystone.domain.firmware.IllegalFirmwareStateException;
import dev.zoel.keystone.domain.firmware.Rollout;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

public class ManageRolloutUseCase implements ManageRollout {

    private final RolloutRepository rollouts;
    private final FirmwareRepository firmware;
    private final AuditTrail audit;
    private final DeviceEventPublisher events;
    private final Clock clock;

    public ManageRolloutUseCase(RolloutRepository rollouts, FirmwareRepository firmware,
                                AuditTrail audit, DeviceEventPublisher events, Clock clock) {
        this.rollouts = rollouts;
        this.firmware = firmware;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Rollout start(UUID artifactId) {
        FirmwareArtifact artifact = firmware.findById(artifactId)
            .orElseThrow(() -> new IllegalFirmwareStateException("no such artifact: " + artifactId));

        // The previous version is what a rollback would restore. Resolved now, while
        // the answer is unambiguous, rather than during an incident.
        FirmwareVersion previous = firmware.findAll().stream()
            .filter(candidate -> candidate.model().equals(artifact.model()))
            .filter(candidate -> candidate.version().compareTo(artifact.version()) < 0)
            .map(FirmwareArtifact::version)
            .max(Comparator.naturalOrder())
            .orElse(null);

        Rollout rollout = rollouts.save(Rollout.start(artifact, previous, clock.now()));
        audit.record(AuditAction.ROLLOUT_STARTED, rollout.id().toString(),
            "model=" + artifact.model() + " version=" + artifact.version() + " stage=CANARY");
        publish(rollout, "rollout started at 5%");
        return rollout;
    }

    @Override
    public Rollout advance(UUID rolloutId) {
        Rollout rollout = require(rolloutId);
        rollout.advance(clock.now());
        rollouts.save(rollout);
        audit.record(AuditAction.ROLLOUT_ADVANCED, rolloutId.toString(),
            "stage=" + rollout.stage() + " status=" + rollout.status());
        publish(rollout, "advanced to " + rollout.stage().percentage() + "%");
        return rollout;
    }

    @Override
    public Rollout pause(UUID rolloutId) {
        Rollout rollout = require(rolloutId);
        rollout.pause(clock.now());
        rollouts.save(rollout);
        audit.record(AuditAction.ROLLOUT_PAUSED, rolloutId.toString(), "paused by operator");
        publish(rollout, "paused");
        return rollout;
    }

    @Override
    public Rollout resume(UUID rolloutId) {
        Rollout rollout = require(rolloutId);
        rollout.resume(clock.now());
        rollouts.save(rollout);
        audit.record(AuditAction.ROLLOUT_ADVANCED, rolloutId.toString(), "resumed by operator");
        publish(rollout, "resumed");
        return rollout;
    }

    @Override
    public Rollout rollBack(UUID rolloutId, String reason) {
        Rollout rollout = require(rolloutId);
        rollout.rollBack(clock.now());
        rollouts.save(rollout);
        audit.record(AuditAction.ROLLOUT_ROLLED_BACK, rolloutId.toString(),
            "reason=" + reason + " revertingTo=" + rollout.previousVersion());
        publish(rollout, "rolled back to " + rollout.previousVersion());
        return rollout;
    }

    private Rollout require(UUID rolloutId) {
        return rollouts.findById(rolloutId)
            .orElseThrow(() -> new IllegalFirmwareStateException("no such rollout: " + rolloutId));
    }

    private void publish(Rollout rollout, String detail) {
        events.publish(new DeviceEventPublisher.FleetEvent(
            Instant.now(), AuditAction.ROLLOUT_ADVANCED, rollout.targetModel(), detail));
    }
}
