package dev.zoel.keystone.domain.device;

/** A state transition the domain does not allow was attempted. */
public class IllegalDeviceStateException extends RuntimeException {
    public IllegalDeviceStateException(String message) {
        super(message);
    }
}
