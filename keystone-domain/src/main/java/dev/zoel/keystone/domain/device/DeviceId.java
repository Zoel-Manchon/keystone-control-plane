package dev.zoel.keystone.domain.device;

import java.util.Objects;
import java.util.UUID;

/**
 * Device identity. Value object: immutable and self-validating.
 * An invalid DeviceId can never be constructed.
 */
public record DeviceId(UUID value) {

    public DeviceId {
        Objects.requireNonNull(value, "device id must not be null");
    }

    public static DeviceId newId() {
        return new DeviceId(UUID.randomUUID());
    }

    public static DeviceId of(String raw) {
        try {
            return new DeviceId(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed device id: " + raw, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
