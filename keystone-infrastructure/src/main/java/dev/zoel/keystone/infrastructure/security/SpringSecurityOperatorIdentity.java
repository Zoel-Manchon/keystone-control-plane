package dev.zoel.keystone.infrastructure.security;

import dev.zoel.keystone.application.port.out.OperatorIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Adapter that answers "who is acting" from the security context.
 *
 * "system" covers operations with no authenticated human behind them: a device
 * enrolling itself, or a scheduled job. Those must still be attributable.
 */
@Component
public class SpringSecurityOperatorIdentity implements OperatorIdentity {

    @Override
    public String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }
}
