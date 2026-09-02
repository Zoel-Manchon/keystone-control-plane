package dev.zoel.keystone.domain.firmware;

public class IllegalFirmwareStateException extends RuntimeException {
    public IllegalFirmwareStateException(String message) {
        super(message);
    }
}
