package dev.zoel.keystone.domain.enrollment;

import dev.zoel.keystone.domain.device.DeviceId;

public class TokenAlreadyConsumedException extends RuntimeException {
    public TokenAlreadyConsumedException(DeviceId deviceId) {
        super("the enrolment token for device " + deviceId + " has already been used");
    }
}
