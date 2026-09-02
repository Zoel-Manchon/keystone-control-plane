package dev.zoel.keystone.domain.firmware;

import java.util.Comparator;
import java.util.Objects;

/**
 * A semantic version. Comparable, because "is this newer than what the device runs"
 * is a question the system has to answer correctly, and string comparison gets it
 * wrong the moment you reach 1.10.0 versus 1.9.0.
 */
public record FirmwareVersion(int major, int minor, int patch) implements Comparable<FirmwareVersion> {

    private static final Comparator<FirmwareVersion> ORDER =
        Comparator.comparingInt(FirmwareVersion::major)
            .thenComparingInt(FirmwareVersion::minor)
            .thenComparingInt(FirmwareVersion::patch);

    public FirmwareVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version components must not be negative");
        }
    }

    public static FirmwareVersion parse(String raw) {
        Objects.requireNonNull(raw, "version must not be null");
        String[] parts = raw.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("expected MAJOR.MINOR.PATCH, got: " + raw);
        }
        try {
            return new FirmwareVersion(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("version components must be integers: " + raw, e);
        }
    }

    public boolean isNewerThan(FirmwareVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(FirmwareVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
