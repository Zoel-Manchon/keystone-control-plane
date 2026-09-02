package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.device.DeviceId;

public interface RevokeDeviceCertificate {
    void handle(DeviceId deviceId, String reason);
}
