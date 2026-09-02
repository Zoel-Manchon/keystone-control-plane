package dev.zoel.keystone.infrastructure.rest;

import dev.zoel.keystone.application.port.in.RotateDeviceCertificate;
import dev.zoel.keystone.domain.device.DeviceId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device-facing renewal.
 *
 * Unauthenticated at the HTTP layer for the same reason as enrolment - the device has
 * no session - but the use case demands a detached signature made by the private key
 * for the certificate it already holds. The fingerprint only selects that certificate;
 * it is public metadata and never treated as a credential.
 */
@RestController
@RequestMapping("/api/v1/rotation")
public class RotationController {

    private final RotateDeviceCertificate rotate;

    RotationController(RotateDeviceCertificate rotate) {
        this.rotate = rotate;
    }

    @PostMapping("/{deviceId}")
    public RotationResponse rotate(@PathVariable("deviceId") String deviceId,
                                   @Valid @RequestBody RotationRequest request) {
        RotateDeviceCertificate.RotationResult result = rotate.handle(
            new RotateDeviceCertificate.RotateCommand(
                DeviceId.of(deviceId), request.currentFingerprint(), request.csr(), request.proof()));

        return new RotationResponse(result.certificatePem(), result.caChainPem(), result.fingerprint());
    }

    public record RotationRequest(
        @NotBlank @Size(max = 128) String currentFingerprint,
        @NotBlank @Size(max = 8192) String csr,
        @NotBlank @Size(max = 512) String proof) {}

    public record RotationResponse(String certificatePem, String caChainPem, String fingerprint) {}
}
