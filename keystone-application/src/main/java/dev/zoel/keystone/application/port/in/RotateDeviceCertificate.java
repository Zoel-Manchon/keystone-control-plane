package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.device.DeviceId;

/**
 * Renewal. Without this, a fleet on 90-day certificates goes dark on day 91.
 *
 * Rotation is authenticated by the certificate the device already holds, not by a new
 * enrolment token: a device that can still prove possession of its current key does
 * not need an operator in the loop to renew. That is what makes renewal automatable
 * and therefore what makes short-lived certificates practical.
 */
public interface RotateDeviceCertificate {

    RotationResult handle(RotateCommand command);

    record RotateCommand(DeviceId deviceId, String currentFingerprint, String csrPem,
                         String proofBase64) {}

    record RotationResult(String certificatePem, String caChainPem, String fingerprint) {}
}
