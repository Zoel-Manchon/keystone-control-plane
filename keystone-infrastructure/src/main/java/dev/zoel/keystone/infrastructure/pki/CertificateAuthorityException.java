package dev.zoel.keystone.infrastructure.pki;

public class CertificateAuthorityException extends RuntimeException {

    public CertificateAuthorityException(String message) {
        super(message);
    }

    public CertificateAuthorityException(String message, Throwable cause) {
        super(message, cause);
    }
}
