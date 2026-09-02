package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.application.port.in.VerifyAuditTrail;
import dev.zoel.keystone.application.port.out.AuditTrail;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditViewController {

    private static final int PAGE_SIZE = 100;

    private final AuditTrail audit;
    private final VerifyAuditTrail verify;

    AuditViewController(AuditTrail audit, VerifyAuditTrail verify) {
        this.audit = audit;
        this.verify = verify;
    }

    @GetMapping("/audit")
    public String audit(Model model) {
        model.addAttribute("entries", audit.findLatest(PAGE_SIZE));
        // The integrity check runs on every view on purpose: an integrity guarantee
        // nobody ever evaluates is a guarantee in name only.
        model.addAttribute("integrity", verify.handle());
        return "audit/index";
    }
}
