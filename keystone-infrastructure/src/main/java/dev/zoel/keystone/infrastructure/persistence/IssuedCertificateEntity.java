package dev.zoel.keystone.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registry of everything the CA has ever signed.
 *
 * A CA that cannot enumerate what it issued cannot revoke reliably, and cannot answer
 * "what is currently trusted?" after an incident. The serial is the primary key
 * because that is what a CRL entry refers to.
 */
@Entity
@Table(name = "issued_certificates")
public class IssuedCertificateEntity {

    @Id
    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    @Column(name = "device_id", nullable = false)
    private java.util.UUID deviceId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "subject", nullable = false, length = 256)
    private String subject;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Column(name = "not_after", nullable = false)
    private Instant notAfter;

    @Column(name = "certificate_pem", columnDefinition = "TEXT")
    private String certificatePem;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected IssuedCertificateEntity() {
        // required by JPA
    }

    public IssuedCertificateEntity(String serialNumber, java.util.UUID deviceId, String fingerprint,
                                   String subject, Instant notBefore, Instant notAfter,
                                   String certificatePem) {
        this.serialNumber = serialNumber;
        this.deviceId = deviceId;
        this.fingerprint = fingerprint;
        this.subject = subject;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.certificatePem = certificatePem;
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }

    public String getSerialNumber() { return serialNumber; }
    public java.util.UUID getDeviceId() { return deviceId; }
    public String getFingerprint() { return fingerprint; }
    public String getSubject() { return subject; }
    public Instant getNotBefore() { return notBefore; }
    public Instant getNotAfter() { return notAfter; }
    public String getCertificatePem() { return certificatePem; }
    public Instant getRevokedAt() { return revokedAt; }
}
