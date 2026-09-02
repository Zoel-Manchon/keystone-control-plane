package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.VerifyAuditTrail;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.domain.audit.AuditTrailIntegrity;

/** Walks the whole chain. Cheap at portfolio scale; would need checkpoints at millions of rows. */
public class VerifyAuditTrailUseCase implements VerifyAuditTrail {

    private final AuditTrail audit;

    public VerifyAuditTrailUseCase(AuditTrail audit) {
        this.audit = audit;
    }

    @Override
    public AuditTrailIntegrity handle() {
        return AuditTrailIntegrity.verify(audit.findAllInOrder());
    }
}
