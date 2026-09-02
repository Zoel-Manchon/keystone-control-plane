package dev.zoel.keystone.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Records authentication outcomes.
 *
 * An unauthenticated attempt that leaves no trace is an attempt nobody can
 * investigate. Repeated failures against one account are the first visible sign of
 * credential stuffing, and this listener is what makes them visible.
 *
 * Deliberately absent from the log line: the submitted password, and any hint about
 * whether the username exists. Logs get shipped to systems with a wider audience
 * than the database, so they are treated as untrusted storage.
 */
@Component
public class AuthenticationAuditListener {

    private static final Logger log = LoggerFactory.getLogger("keystone.security.audit");

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        log.info("authentication succeeded: principal={}", event.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        // The principal is logged; the reason is the exception class, never the
        // message, so an internal detail cannot leak into an operator-visible log.
        log.warn("authentication failed: principal={} reason={}",
            event.getAuthentication().getName(),
            event.getException().getClass().getSimpleName());
    }
}
