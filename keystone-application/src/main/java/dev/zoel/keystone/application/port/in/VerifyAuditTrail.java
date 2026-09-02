package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.audit.AuditTrailIntegrity;

public interface VerifyAuditTrail {
    AuditTrailIntegrity handle();
}
