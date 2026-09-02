package dev.zoel.keystone.application.port.out;

/**
 * Generates enrolment secrets and hashes them.
 *
 * A port rather than a utility class so the use case can be tested with a
 * deterministic stub. Implementations MUST use a cryptographically secure source.
 */
public interface SecretGenerator {

    /** A fresh high-entropy secret, in the form handed to the operator once. */
    String generateSecret();

    /** SHA-256 of the secret, lowercase hex. Only this ever reaches the database. */
    String hash(String secret);
}
