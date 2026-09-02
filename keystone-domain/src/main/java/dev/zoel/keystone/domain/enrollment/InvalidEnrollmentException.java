package dev.zoel.keystone.domain.enrollment;

/**
 * Deliberately uninformative.
 *
 * Unknown token, expired token, already-used token and unknown device all collapse
 * into this one exception, so the response cannot be used to probe which secrets
 * exist. The specific reason goes to the audit trail, where only operators see it.
 */
public class InvalidEnrollmentException extends RuntimeException {
    public InvalidEnrollmentException() {
        super("invalid enrolment request");
    }
}
