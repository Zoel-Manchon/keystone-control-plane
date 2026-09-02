package dev.zoel.keystone.application.port.out;

import java.util.UUID;

/** Where the firmware bytes live. A filesystem today; object storage would slot straight in. */
public interface ArtifactStorage {

    /** Stores the bytes and returns their SHA-256, computed while writing. */
    StoredArtifact store(UUID artifactId, byte[] content);

    byte[] load(UUID artifactId);

    record StoredArtifact(String sha256, long sizeBytes) {}
}
