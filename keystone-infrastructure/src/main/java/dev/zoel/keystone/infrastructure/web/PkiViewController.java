package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.application.port.out.CertificateAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PkiViewController {

    private final CertificateAuthority ca;

    PkiViewController(CertificateAuthority ca) {
        this.ca = ca;
    }

    @GetMapping("/pki")
    public String pki(Model model) {
        model.addAttribute("ca", ca.status());
        model.addAttribute("chain", ca.caChainPem());
        return "pki/index";
    }
}
