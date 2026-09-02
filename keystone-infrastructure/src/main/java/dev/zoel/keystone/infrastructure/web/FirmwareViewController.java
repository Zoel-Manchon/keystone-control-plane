package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.application.port.in.ManageRollout;
import dev.zoel.keystone.application.port.out.FirmwareRepository;
import dev.zoel.keystone.application.port.out.RolloutRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.UUID;

@Controller
@RequestMapping("/firmware")
public class FirmwareViewController {

    private final FirmwareRepository firmware;
    private final RolloutRepository rollouts;
    private final ManageRollout manageRollout;

    FirmwareViewController(FirmwareRepository firmware, RolloutRepository rollouts,
                           ManageRollout manageRollout) {
        this.firmware = firmware;
        this.rollouts = rollouts;
        this.manageRollout = manageRollout;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("artifacts", firmware.findAll().stream()
            .sorted(Comparator.comparing(a -> a.version().toString()))
            .toList());
        model.addAttribute("rollouts", rollouts.findAll().stream()
            .sorted(Comparator.comparing(r -> r.startedAt(), Comparator.reverseOrder()))
            .toList());
        return "firmware/index";
    }

    @PostMapping("/rollouts/{artifactId}/start")
    public String start(@PathVariable("artifactId") String artifactId) {
        manageRollout.start(UUID.fromString(artifactId));
        return "redirect:/firmware";
    }

    @PostMapping("/rollouts/{rolloutId}/advance")
    public String advance(@PathVariable("rolloutId") String rolloutId) {
        manageRollout.advance(UUID.fromString(rolloutId));
        return "redirect:/firmware";
    }

    @PostMapping("/rollouts/{rolloutId}/rollback")
    public String rollBack(@PathVariable("rolloutId") String rolloutId) {
        manageRollout.rollBack(UUID.fromString(rolloutId), "rolled back from the console");
        return "redirect:/firmware";
    }
}
