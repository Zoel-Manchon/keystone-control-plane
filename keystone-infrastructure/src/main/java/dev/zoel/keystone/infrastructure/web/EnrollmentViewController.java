package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.application.port.in.IssueEnrollmentToken;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.device.DeviceId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;

/**
 * Enrolment console.
 *
 * The issued secret travels back through a flash attribute, so it lives in the
 * session for exactly one redirect and never lands in a URL. A secret in a query
 * string ends up in browser history, in proxy logs and in the referer header.
 */
@Controller
@RequestMapping("/enrollment")
public class EnrollmentViewController {

    private final IssueEnrollmentToken issueToken;
    private final DeviceRepository devices;
    private final Clock clock;

    EnrollmentViewController(IssueEnrollmentToken issueToken, DeviceRepository devices, Clock clock) {
        this.issueToken = issueToken;
        this.devices = devices;
        this.clock = clock;
    }

    @GetMapping
    public String index(Model model) {
        Instant now = clock.now();
        model.addAttribute("pending", devices.findAll().stream()
            .filter(device -> device.certificateFingerprint().isEmpty())
            .map(device -> DeviceRow.from(device, now))
            .toList());
        return "enrollment/index";
    }

    @PostMapping("/{id}/token")
    public String issue(@PathVariable("id") String id, RedirectAttributes redirect) {
        IssueEnrollmentToken.IssuedToken issued = issueToken.handle(DeviceId.of(id));
        redirect.addFlashAttribute("issuedSecret", issued.secret());
        redirect.addFlashAttribute("issuedFor", id);
        redirect.addFlashAttribute("issuedExpiry", issued.expiresAt());
        return "redirect:/enrollment";
    }
}
