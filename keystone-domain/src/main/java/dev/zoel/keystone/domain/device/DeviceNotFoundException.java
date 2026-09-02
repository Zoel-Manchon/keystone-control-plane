package dev.zoel.keystone.domain.device;

public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException(DeviceId id) {
        super("no device exists with id " + id);
    }
}
