package dev.zoel.keystone.domain.enrollment;

import dev.zoel.keystone.domain.device.DeviceId;

import java.time.Instant;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(DeviceId deviceId, Instant expiredAt) {
        super("the enrolment token for device " + deviceId + " expired on " + expiredAt);
    }
}
