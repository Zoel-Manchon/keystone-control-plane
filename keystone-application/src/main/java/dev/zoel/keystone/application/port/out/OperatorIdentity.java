package dev.zoel.keystone.application.port.out;

/**
 * Who is performing the current operation.
 *
 * The audit trail is worthless without an actor, and the use case must not reach
 * into Spring Security to find one: that would drag the framework into the core.
 */
@FunctionalInterface
public interface OperatorIdentity {
    String current();
}
