package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.device.DeviceId;

import java.time.Instant;

public interface IssueEnrollmentToken {

    IssuedToken handle(DeviceId deviceId);

    /**
     * The plaintext secret travels in this record and is never persisted. It is shown
     * to the operator once; after that only its hash exists anywhere in the system.
     */
    record IssuedToken(DeviceId deviceId, String secret, Instant expiresAt) {}
}
