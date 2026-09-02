package dev.zoel.keystone.infrastructure.pki;

/**
 * The caller sent a certificate signing request that cannot be honoured: unparseable,
 * truncated, or carrying a signature that does not verify against its own public key.
 *
 * Separate from {@link CertificateAuthorityException} on purpose. That one means the CA
 * itself failed and is a 500; this one is bad input and must be a 400. Collapsing the
 * two turned every malformed CSR into a server error, which is both wrong and noisy:
 * garbage from the network is an expected condition for this endpoint, not an incident.
 *
 * Extends IllegalArgumentException so the existing bad-input handler maps it without a
 * special case. Messages are deliberately generic — they are returned to the caller.
 */
public class InvalidCsrException extends IllegalArgumentException {

    public InvalidCsrException(String message) {
        super(message);
    }
}
