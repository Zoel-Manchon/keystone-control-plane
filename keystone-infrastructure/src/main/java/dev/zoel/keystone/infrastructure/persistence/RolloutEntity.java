package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.domain.firmware.RolloutStage;
import dev.zoel.keystone.domain.firmware.RolloutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rollouts")
public class RolloutEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "target_model", nullable = false, length = 128)
    private String targetModel;

    @Column(name = "target_version", nullable = false, length = 32)
    private String targetVersion;

    @Column(name = "previous_version", length = 32)
    private String previousVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 16)
    private RolloutStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RolloutStatus status;

    @Column(name = "last_changed_at", nullable = false)
    private Instant lastChangedAt;

    protected RolloutEntity() {
        // required by JPA
    }

    public RolloutEntity(UUID id, UUID artifactId, String targetModel, String targetVersion,
                         String previousVersion, Instant startedAt, RolloutStage stage,
                         RolloutStatus status, Instant lastChangedAt) {
        this.id = id;
        this.artifactId = artifactId;
        this.targetModel = targetModel;
        this.targetVersion = targetVersion;
        this.previousVersion = previousVersion;
        this.startedAt = startedAt;
        this.stage = stage;
        this.status = status;
        this.lastChangedAt = lastChangedAt;
    }

    public UUID getId() { return id; }
    public UUID getArtifactId() { return artifactId; }
    public String getTargetModel() { return targetModel; }
    public String getTargetVersion() { return targetVersion; }
    public String getPreviousVersion() { return previousVersion; }
    public Instant getStartedAt() { return startedAt; }
    public RolloutStage getStage() { return stage; }
    public RolloutStatus getStatus() { return status; }
    public Instant getLastChangedAt() { return lastChangedAt; }
}
