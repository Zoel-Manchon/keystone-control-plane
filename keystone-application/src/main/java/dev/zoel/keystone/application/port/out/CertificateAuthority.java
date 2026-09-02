package dev.zoel.keystone.application.port.out;

import dev.zoel.keystone.domain.device.DeviceId;

import java.time.Instant;

/**
 * Port towards the certificate authority. The BouncyCastle implementation lives in
 * infrastructure; here we only declare WHAT we need, never HOW it is done.
 */
public interface CertificateAuthority {

    /** Signs a PEM-encoded CSR and returns the issued certificate. */
    IssuedCertificate signCertificateRequest(DeviceId deviceId, String csrPem);

    /**
     * Verifies a detached signature using the public key in the currently issued
     * certificate. A fingerprint alone is public metadata and is not authentication.
     */
    boolean verifyProofOfPossession(DeviceId deviceId, String sha256Fingerprint,
                                    byte[] payload, String signatureBase64);

    /** Revokes the certificate carrying this fingerprint and refreshes the CRL. */
    void revokeByFingerprint(String sha256Fingerprint, Instant revokedAt);

    /** Current revocation list in PEM format, for the broker to consume. */
    String currentRevocationList();

    /** Issuing CA plus root, PEM-encoded, so the device can validate the chain. */
    String caChainPem();

    /**
     * Issues a server certificate for the MQTT broker, signed by the same issuing CA.
     *
     * The broker gets serverAuth, devices get clientAuth: one trust anchor, two
     * distinct roles. Reusing a device certificate on the broker would let any device
     * impersonate the broker and harvest the whole fleet's traffic.
     */
    ServerCertificate issueBrokerCertificate(String commonName, java.util.List<String> subjectAlternativeNames);

    /** What the console needs to show on the PKI screen. */
    CaStatus status();

    record IssuedCertificate(String certificatePem, String serialNumber,
                             String sha256Fingerprint, Instant notBefore, Instant notAfter) {}

    record ServerCertificate(String certificatePem, String privateKeyPem, String chainPem) {}

    record CaStatus(String rootSubject, String rootFingerprint, Instant rootNotAfter,
                    String issuingSubject, String issuingFingerprint, Instant issuingNotAfter,
                    long issuedCount, long revokedCount) {}
}
