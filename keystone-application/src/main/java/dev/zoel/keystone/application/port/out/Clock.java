package dev.zoel.keystone.application.port.out;

import java.time.Instant;

/**
 * Time is a dependency, not a global constant.
 * Injecting it makes use cases testable without waiting 24 hours.
 */
@FunctionalInterface
public interface Clock {
    Instant now();
}
