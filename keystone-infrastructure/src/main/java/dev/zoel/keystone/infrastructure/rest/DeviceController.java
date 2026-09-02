package dev.zoel.keystone.infrastructure.rest;

import dev.zoel.keystone.application.port.in.IssueEnrollmentToken;
import dev.zoel.keystone.application.port.in.RegisterDevice;
import dev.zoel.keystone.application.port.in.RegisterDeviceCommand;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.device.DeviceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Primary adapter. Thin on purpose: it translates HTTP into commands and nothing else.
 * If business logic starts appearing here, it belongs somewhere else.
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final RegisterDevice registerDevice;
    private final IssueEnrollmentToken issueEnrollmentToken;
    private final DeviceRepository devices;

    DeviceController(RegisterDevice registerDevice, IssueEnrollmentToken issueEnrollmentToken,
                     DeviceRepository devices) {
        this.registerDevice = registerDevice;
        this.issueEnrollmentToken = issueEnrollmentToken;
        this.devices = devices;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> register(@Valid @RequestBody RegisterDeviceRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        Device device = registerDevice.handle(
            new RegisterDeviceCommand(request.serialNumber(), request.model()));

        var location = uriBuilder.path("/api/v1/devices/{id}").build(device.id().toString());
        return ResponseEntity.created(location).body(DeviceResponse.from(device));
    }

    /**
     * Issues an enrolment secret for automation (provisioning scripts, the fleet
     * simulator). The plaintext secret is in the response body and NOWHERE else:
     * it is not logged, not persisted, and not retrievable a second time.
     *
     * Operator-authenticated, unlike the enrolment endpoint the device calls.
     */
    @PostMapping("/{id}/enrollment-token")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentTokenResponse issueEnrollmentToken(@PathVariable("id") String id) {
        IssueEnrollmentToken.IssuedToken issued = issueEnrollmentToken.handle(DeviceId.of(id));
        return new EnrollmentTokenResponse(
            issued.deviceId().toString(), issued.secret(), issued.expiresAt());
    }

    public record EnrollmentTokenResponse(String deviceId, String secret, java.time.Instant expiresAt) {}

    @GetMapping
    public List<DeviceResponse> list() {
        return devices.findAll().stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable("id") String id) {
        DeviceId deviceId = DeviceId.of(id);
        return devices.findById(deviceId)
            .map(DeviceResponse::from)
            .orElseThrow(() -> new DeviceNotFoundException(deviceId));
    }
}
