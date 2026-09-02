package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.IssueEnrollmentToken;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.application.port.out.EnrollmentTokenRepository;
import dev.zoel.keystone.application.port.out.SecretGenerator;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.device.DeviceNotFoundException;
import dev.zoel.keystone.domain.device.DeviceStatus;
import dev.zoel.keystone.domain.device.IllegalDeviceStateException;
import dev.zoel.keystone.domain.enrollment.EnrollmentToken;

import java.time.Instant;

/**
 * Issues a single-use enrolment secret for a device awaiting enrolment.
 *
 * The secret is returned to the caller and never stored: the database only ever sees
 * its SHA-256. That is the same reasoning behind storing password hashes — a dump of
 * the enrollment_tokens table must not let anyone enrol a device.
 */
public class IssueEnrollmentTokenUseCase implements IssueEnrollmentToken {

    private final DeviceRepository devices;
    private final EnrollmentTokenRepository tokens;
    private final SecretGenerator secrets;
    private final AuditTrail audit;
    private final Clock clock;

    public IssueEnrollmentTokenUseCase(DeviceRepository devices, EnrollmentTokenRepository tokens,
                                       SecretGenerator secrets, AuditTrail audit, Clock clock) {
        this.devices = devices;
        this.tokens = tokens;
        this.secrets = secrets;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public IssuedToken handle(DeviceId deviceId) {
        Device device = devices.findById(deviceId)
            .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        if (device.status() != DeviceStatus.PENDING_ENROLLMENT) {
            throw new IllegalDeviceStateException(
                "an enrolment token is only issued to a device in PENDING_ENROLLMENT, not " + device.status());
        }

        Instant now = clock.now();
        String secret = secrets.generateSecret();

        tokens.save(EnrollmentToken.issue(deviceId, secrets.hash(secret), now, EnrollmentToken.DEFAULT_TTL));

        // The secret itself is deliberately absent from the audit detail.
        audit.record(AuditAction.ENROLLMENT_TOKEN_ISSUED, deviceId.toString(),
            "serial=" + device.serialNumber());

        return new IssuedToken(deviceId, secret, now.plus(EnrollmentToken.DEFAULT_TTL));
    }
}
