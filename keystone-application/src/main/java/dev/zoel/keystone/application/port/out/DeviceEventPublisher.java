package dev.zoel.keystone.application.port.out;

import dev.zoel.keystone.domain.audit.AuditAction;

import java.time.Instant;

/** Pushes fleet events out to whatever is listening live (the console, today). */
public interface DeviceEventPublisher {

    void publish(FleetEvent event);

    record FleetEvent(Instant occurredAt, AuditAction action, String subject, String detail) {}
}
