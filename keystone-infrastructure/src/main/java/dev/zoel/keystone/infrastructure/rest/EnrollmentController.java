package dev.zoel.keystone.infrastructure.rest;

import dev.zoel.keystone.application.port.in.EnrollDevice;
import dev.zoel.keystone.application.port.out.CertificateAuthority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The device-facing endpoint. This is the only route a device may reach while it is
 * still anonymous, and it is guarded by the enrolment secret rather than by a session.
 *
 * Everything a device can send is bounded: a size cap on the CSR keeps a malformed or
 * hostile payload from becoming a memory problem before parsing even starts.
 */
@RestController
@RequestMapping("/api/v1/enrollment")
public class EnrollmentController {

    private final EnrollDevice enrollDevice;
    private final CertificateAuthority ca;

    EnrollmentController(EnrollDevice enrollDevice, CertificateAuthority ca) {
        this.enrollDevice = enrollDevice;
        this.ca = ca;
    }

    @PostMapping
    public EnrollmentResponse enroll(@Valid @RequestBody EnrollmentRequest request) {
        EnrollDevice.EnrollmentResult result = enrollDevice.handle(
            new EnrollDevice.EnrollDeviceCommand(request.secret(), request.csr()));
        return new EnrollmentResponse(result.certificatePem(), result.caChainPem(), result.fingerprint());
    }

    /** Public by design: a trust chain is not a secret, and devices need it to validate. */
    @GetMapping(value = "/ca-chain", produces = MediaType.TEXT_PLAIN_VALUE)
    public String caChain() {
        return ca.caChainPem();
    }

    /** Also public: a CRL is meant to be fetched by anyone verifying a certificate. */
    @GetMapping(value = "/crl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String revocationList() {
        return ca.currentRevocationList();
    }

    public record EnrollmentRequest(
        @NotBlank @Size(max = 128) String secret,
        @NotBlank @Size(max = 8192, message = "the CSR exceeds the accepted size") String csr) {}

    public record EnrollmentResponse(String certificatePem, String caChainPem, String fingerprint) {}
}
