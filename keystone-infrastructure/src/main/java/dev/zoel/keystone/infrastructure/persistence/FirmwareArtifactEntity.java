package dev.zoel.keystone.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "firmware_artifacts")
public class FirmwareArtifactEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "signature", length = 128)
    private String signature;

    @Column(name = "withdrawn", nullable = false)
    private boolean withdrawn;

    protected FirmwareArtifactEntity() {
        // required by JPA
    }

    public FirmwareArtifactEntity(UUID id, String version, String model, String sha256, long sizeBytes,
                                  Instant uploadedAt, String signature, boolean withdrawn) {
        this.id = id;
        this.version = version;
        this.model = model;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
        this.signature = signature;
        this.withdrawn = withdrawn;
    }

    public UUID getId() { return id; }
    public String getVersion() { return version; }
    public String getModel() { return model; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getSignature() { return signature; }
    public boolean isWithdrawn() { return withdrawn; }
}
