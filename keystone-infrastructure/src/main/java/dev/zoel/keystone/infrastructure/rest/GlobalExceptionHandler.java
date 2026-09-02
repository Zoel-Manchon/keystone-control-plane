package dev.zoel.keystone.infrastructure.rest;

import dev.zoel.keystone.domain.device.DeviceNotFoundException;
import dev.zoel.keystone.domain.device.DuplicateSerialNumberException;
import dev.zoel.keystone.domain.device.IllegalDeviceStateException;
import dev.zoel.keystone.domain.enrollment.InvalidEnrollmentException;
import dev.zoel.keystone.domain.enrollment.TokenAlreadyConsumedException;
import dev.zoel.keystone.domain.enrollment.TokenExpiredException;
import dev.zoel.keystone.infrastructure.pki.CertificateAuthorityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into HTTP responses (RFC 9457).
 *
 * Security note: enrolment failures return a generic message. Distinguishing
 * "token expired" from "token already used" hands an attacker useful information.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    ProblemDetail handleNotFound(DeviceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateSerialNumberException.class)
    ProblemDetail handleDuplicate(DuplicateSerialNumberException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalDeviceStateException.class)
    ProblemDetail handleIllegalState(IllegalDeviceStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({TokenExpiredException.class, TokenAlreadyConsumedException.class,
                       InvalidEnrollmentException.class})
    ProblemDetail handleEnrollmentFailure(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "invalid enrolment token");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadInput(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * The CA itself failed: keystore unreadable, provider missing, storage rejected the
     * row. Genuinely a server error, so it must not be dressed up as a 4xx — but the
     * cause never reaches the caller, because it describes internal structure.
     */
    @ExceptionHandler(CertificateAuthorityException.class)
    ProblemDetail handleCertificateAuthorityFailure(CertificateAuthorityException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "the certificate authority could not complete the request");
    }
}
