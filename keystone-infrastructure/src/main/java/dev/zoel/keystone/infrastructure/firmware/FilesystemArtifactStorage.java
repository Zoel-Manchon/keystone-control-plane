package dev.zoel.keystone.infrastructure.firmware;

import dev.zoel.keystone.application.port.out.ArtifactStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Firmware images on the local filesystem.
 *
 * The file name is the artifact UUID and nothing else. Deriving it from anything the
 * uploader supplies would open a path traversal, and firmware upload is the last
 * place you want one.
 */
@Component
public class FilesystemArtifactStorage implements ArtifactStorage {

    private final Path root;

    FilesystemArtifactStorage(@Value("${keystone.firmware.storage-path:data/firmware}") String storagePath) {
        this.root = Path.of(storagePath);
    }

    @Override
    public StoredArtifact store(UUID artifactId, byte[] content) {
        try {
            Files.createDirectories(root);
            Path target = root.resolve(artifactId + ".bin");

            // Write to a temporary file and rename: a crash mid-write must never leave
            // a truncated image sitting where a device could download it.
            Path temporary = root.resolve(artifactId + ".part");
            Files.write(temporary, content);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return new StoredArtifact(HexFormat.of().formatHex(digest), content.length);
        } catch (Exception e) {
            throw new IllegalStateException("could not store the firmware artifact", e);
        }
    }

    @Override
    public byte[] load(UUID artifactId) {
        try {
            return Files.readAllBytes(root.resolve(artifactId + ".bin"));
        } catch (Exception e) {
            throw new IllegalStateException("could not read the firmware artifact " + artifactId, e);
        }
    }
}
