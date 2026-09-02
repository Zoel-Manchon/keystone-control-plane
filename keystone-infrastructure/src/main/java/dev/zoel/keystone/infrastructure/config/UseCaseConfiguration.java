package dev.zoel.keystone.infrastructure.config;

import dev.zoel.keystone.application.port.in.EnrollDevice;
import dev.zoel.keystone.application.port.in.IssueEnrollmentToken;
import dev.zoel.keystone.application.port.in.ManageRollout;
import dev.zoel.keystone.application.port.in.PublishFirmware;
import dev.zoel.keystone.application.port.in.RegisterDevice;
import dev.zoel.keystone.application.port.in.ResolveDeviceUpdate;
import dev.zoel.keystone.application.port.in.FindExpiringCertificates;
import dev.zoel.keystone.application.port.in.RevokeDeviceCertificate;
import dev.zoel.keystone.application.port.in.RotateDeviceCertificate;
import dev.zoel.keystone.application.port.in.VerifyAuditTrail;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.application.port.out.EnrollmentTokenRepository;
import dev.zoel.keystone.application.port.out.ArtifactSigner;
import dev.zoel.keystone.application.port.out.ArtifactStorage;
import dev.zoel.keystone.application.port.out.FirmwareRepository;
import dev.zoel.keystone.application.port.out.RolloutRepository;
import dev.zoel.keystone.application.port.out.SecretGenerator;
import dev.zoel.keystone.application.usecase.EnrollDeviceUseCase;
import dev.zoel.keystone.application.usecase.IssueEnrollmentTokenUseCase;
import dev.zoel.keystone.application.usecase.ManageRolloutUseCase;
import dev.zoel.keystone.application.usecase.PublishFirmwareUseCase;
import dev.zoel.keystone.application.usecase.RegisterDeviceUseCase;
import dev.zoel.keystone.application.usecase.ResolveDeviceUpdateUseCase;
import dev.zoel.keystone.application.usecase.FindExpiringCertificatesUseCase;
import dev.zoel.keystone.application.usecase.RevokeDeviceCertificateUseCase;
import dev.zoel.keystone.application.usecase.RotateDeviceCertificateUseCase;
import dev.zoel.keystone.application.usecase.VerifyAuditTrailUseCase;
import dev.zoel.keystone.infrastructure.pki.PkiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Here, and only here, use cases are wired into Spring.
 * That is what keeps the application layer free of a single framework annotation.
 */
@Configuration
@EnableConfigurationProperties(PkiProperties.class)
public class UseCaseConfiguration {

    @Bean
    Clock systemClock() {
        return Instant::now;
    }

    @Bean
    RegisterDevice registerDevice(DeviceRepository devices, AuditTrail audit, Clock clock) {
        return new RegisterDeviceUseCase(devices, audit, clock);
    }

    @Bean
    IssueEnrollmentToken issueEnrollmentToken(DeviceRepository devices,
                                              EnrollmentTokenRepository tokens,
                                              SecretGenerator secrets,
                                              AuditTrail audit,
                                              Clock clock) {
        return new IssueEnrollmentTokenUseCase(devices, tokens, secrets, audit, clock);
    }

    @Bean
    EnrollDevice enrollDevice(DeviceRepository devices, EnrollmentTokenRepository tokens,
                              CertificateAuthority ca, SecretGenerator secrets,
                              AuditTrail audit, DeviceEventPublisher events, Clock clock) {
        return new EnrollDeviceUseCase(devices, tokens, ca, secrets, audit, events, clock);
    }

    @Bean
    RevokeDeviceCertificate revokeDeviceCertificate(DeviceRepository devices, CertificateAuthority ca,
                                                    AuditTrail audit, DeviceEventPublisher events,
                                                    Clock clock) {
        return new RevokeDeviceCertificateUseCase(devices, ca, audit, events, clock);
    }

    @Bean
    RotateDeviceCertificate rotateDeviceCertificate(DeviceRepository devices, CertificateAuthority ca,
                                                    AuditTrail audit, DeviceEventPublisher events,
                                                    Clock clock) {
        return new RotateDeviceCertificateUseCase(devices, ca, audit, events, clock);
    }

    @Bean
    FindExpiringCertificates findExpiringCertificates(DeviceRepository devices, Clock clock) {
        return new FindExpiringCertificatesUseCase(devices, clock);
    }

    @Bean
    PublishFirmware publishFirmware(FirmwareRepository firmware, ArtifactStorage storage,
                                    ArtifactSigner signer, AuditTrail audit, Clock clock) {
        return new PublishFirmwareUseCase(firmware, storage, signer, audit, clock);
    }

    @Bean
    ManageRollout manageRollout(RolloutRepository rollouts, FirmwareRepository firmware,
                                AuditTrail audit, DeviceEventPublisher events, Clock clock) {
        return new ManageRolloutUseCase(rollouts, firmware, audit, events, clock);
    }

    @Bean
    ResolveDeviceUpdate resolveDeviceUpdate(DeviceRepository devices, RolloutRepository rollouts,
                                            FirmwareRepository firmware, ArtifactSigner signer) {
        return new ResolveDeviceUpdateUseCase(devices, rollouts, firmware, signer);
    }

    @Bean
    VerifyAuditTrail verifyAuditTrail(AuditTrail audit) {
        return new VerifyAuditTrailUseCase(audit);
    }
}
