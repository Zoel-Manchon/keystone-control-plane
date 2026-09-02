package dev.zoel.keystone.domain.device;

public class DuplicateSerialNumberException extends RuntimeException {
    public DuplicateSerialNumberException(String serialNumber) {
        super("a device already exists with serial number " + serialNumber);
    }
}
