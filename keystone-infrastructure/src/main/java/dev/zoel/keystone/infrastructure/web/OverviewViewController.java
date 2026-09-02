package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.application.port.in.FindExpiringCertificates;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.application.port.out.RolloutRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceStatus;
import dev.zoel.keystone.domain.firmware.RolloutStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Fleet overview: the four numbers an operator checks before anything else. */
@Controller
public class OverviewViewController {

    private static final Duration EXPIRY_WINDOW = Duration.ofDays(30);

    private final DeviceRepository devices;
    private final RolloutRepository rollouts;
    private final CertificateAuthority ca;
    private final AuditTrail audit;
    private final FindExpiringCertificates expiring;
    private final Clock clock;

    OverviewViewController(DeviceRepository devices, RolloutRepository rollouts,
                           CertificateAuthority ca, AuditTrail audit,
                           FindExpiringCertificates expiring, Clock clock) {
        this.devices = devices;
        this.rollouts = rollouts;
        this.ca = ca;
        this.audit = audit;
        this.expiring = expiring;
        this.clock = clock;
    }

    @GetMapping("/")
    public String overview(Model model) {
        Instant now = clock.now();
        List<Device> all = devices.findAll();

        model.addAttribute("total", all.size());
        model.addAttribute("active", all.stream()
            .filter(device -> device.status() == DeviceStatus.ACTIVE).count());
        model.addAttribute("pending", all.stream()
            .filter(device -> device.status() == DeviceStatus.PENDING_ENROLLMENT).count());
        model.addAttribute("revoked", all.stream()
            .filter(device -> device.status() == DeviceStatus.REVOKED).count());
        model.addAttribute("expiring", expiring.handle(EXPIRY_WINDOW).size());
        model.addAttribute("activeRollouts", rollouts.findAll().stream()
            .filter(rollout -> rollout.status() == RolloutStatus.IN_PROGRESS).count());
        model.addAttribute("ca", ca.status());
        model.addAttribute("events", audit.findLatest(12));
        model.addAttribute("recentDevices", all.stream()
            .sorted((a, b) -> b.registeredAt().compareTo(a.registeredAt()))
            .limit(6)
            .map(device -> DeviceRow.from(device, now))
            .toList());
        return "overview";
    }
}
