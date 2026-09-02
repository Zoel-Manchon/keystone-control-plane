package dev.zoel.keystone.infrastructure.pki;

import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.infrastructure.persistence.IssuedCertificateEntity;
import dev.zoel.keystone.infrastructure.persistence.IssuedCertificateJpaRepository;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * The certificate authority.
 *
 * Every rule about what a device certificate may do is enforced here, at signing
 * time. A CSR is a REQUEST: the subject, the validity window and the extensions are
 * decided by this class and never copied from what the device asked for. Trusting
 * CSR-supplied extensions is how a device talks its way into a CA certificate.
 */
@Component
public class BouncyCastleCertificateAuthority implements CertificateAuthority {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final PkiProperties properties;
    private final IssuedCertificateJpaRepository certificates;
    private final SecureRandom random = new SecureRandom();

    private CaMaterial material;
    private volatile String cachedCrl;
    private volatile Instant cachedCrlAt = Instant.EPOCH;

    BouncyCastleCertificateAuthority(PkiProperties properties,
                                     IssuedCertificateJpaRepository certificates) {
        this.properties = properties;
        this.certificates = certificates;
    }

    @PostConstruct
    void initialise() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.material = new CaKeyStore().loadOrCreate(properties);
    }

    @Override
    public IssuedCertificate signCertificateRequest(DeviceId deviceId, String csrPem) {
        // Everything the CALLER controls is validated first, in its own block. A CSR
        // that does not parse or does not verify is bad input (400), never a CA
        // failure (500): this endpoint is reachable from the network and garbage
        // arriving at it is an expected condition, not an incident.
        PublicKey devicePublicKey;
        try {
            PKCS10CertificationRequest csr = parseCsr(csrPem);

            // Proof of possession: the CSR is self-signed with the private key that
            // matches the public key inside it. Skipping this check would let anyone
            // obtain a certificate over someone else's public key.
            JcaPKCS10CertificationRequest jcaCsr =
                new JcaPKCS10CertificationRequest(csr).setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (!csr.isSignatureValid(new JcaContentVerifierProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(jcaCsr.getPublicKey()))) {
                throw new InvalidCsrException("the CSR signature does not verify");
            }

            devicePublicKey = jcaCsr.getPublicKey();
        } catch (InvalidCsrException e) {
            throw e;
        } catch (Exception e) {
            // Generic on purpose: the parser's own message can describe internal
            // structure, and this text is returned to the caller.
            throw new InvalidCsrException("the certificate signing request is not valid");
        }

        try {

            Instant notBefore = Instant.now();
            Instant notAfter = notBefore.plus(properties.getDeviceCertificateValidityDays(), ChronoUnit.DAYS);
            BigInteger serial = new BigInteger(128, random);

            // The subject is ours, not theirs. The device id in the CN is what the
            // broker maps to a topic namespace, so it must be authoritative.
            X500Name subject = new X500Name("CN=" + deviceId + ", O=Keystone Devices");

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                material.issuingCertificate(), serial,
                Date.from(notBefore), Date.from(notAfter), subject, devicePublicKey);

            JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
            // Not a CA, and marked critical so no validator may ignore it.
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyAgreement));
            // clientAuth only: this certificate authenticates a client to the broker
            // and cannot be repurposed to impersonate a server.
            builder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                utils.createSubjectKeyIdentifier(devicePublicKey));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                utils.createAuthorityKeyIdentifier(material.issuingCertificate()));

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(material.issuingKey());
            X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));

            String fingerprint = PemCodec.fingerprint(certificate);
            String certificatePem = PemCodec.toPem(certificate);
            certificates.save(new IssuedCertificateEntity(
                serial.toString(16), deviceId.value(), fingerprint,
                subject.toString(), notBefore, notAfter, certificatePem));

            return new IssuedCertificate(certificatePem, serial.toString(16),
                fingerprint, notBefore, notAfter);

        } catch (CertificateAuthorityException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateAuthorityException("could not sign the certificate request", e);
        }
    }

    /**
     * Issues the broker's server certificate.
     *
     * Note the key is generated HERE and handed back: the broker is not a device and
     * has no enrolment flow. It also means the private key crosses a boundary, which
     * is why the file it lands in is written with owner-only permissions and never
     * leaves the host.
     */
    @Override
    public ServerCertificate issueBrokerCertificate(String commonName, List<String> subjectAlternativeNames) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"), random);
            java.security.KeyPair brokerKeys = generator.generateKeyPair();

            Instant notBefore = Instant.now();
            Instant notAfter = notBefore.plus(825, ChronoUnit.DAYS);
            BigInteger serial = new BigInteger(128, random);

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                material.issuingCertificate(), serial, Date.from(notBefore), Date.from(notAfter),
                new X500Name("CN=" + commonName + ", O=Keystone Infrastructure"),
                brokerKeys.getPublic());

            JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            builder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                utils.createSubjectKeyIdentifier(brokerKeys.getPublic()));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                utils.createAuthorityKeyIdentifier(material.issuingCertificate()));

            // Modern TLS clients ignore the CN and validate against the SAN. Without
            // these entries the handshake fails no matter how correct the chain is.
            if (subjectAlternativeNames != null && !subjectAlternativeNames.isEmpty()) {
                org.bouncycastle.asn1.x509.GeneralName[] names = subjectAlternativeNames.stream()
                    .map(name -> new org.bouncycastle.asn1.x509.GeneralName(
                        name.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                            ? org.bouncycastle.asn1.x509.GeneralName.iPAddress
                            : org.bouncycastle.asn1.x509.GeneralName.dNSName, name))
                    .toArray(org.bouncycastle.asn1.x509.GeneralName[]::new);
                builder.addExtension(Extension.subjectAlternativeName, false,
                    new org.bouncycastle.asn1.x509.GeneralNames(names));
            }

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(material.issuingKey());
            X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));

            return new ServerCertificate(
                PemCodec.toPem(certificate),
                PemCodec.toPem("PRIVATE KEY", brokerKeys.getPrivate().getEncoded()),
                caChainPem());
        } catch (Exception e) {
            throw new CertificateAuthorityException("could not issue the broker certificate", e);
        }
    }


    @Override
    public boolean verifyProofOfPossession(DeviceId deviceId, String sha256Fingerprint,
                                           byte[] payload, String signatureBase64) {
        try {
            IssuedCertificateEntity issued = certificates.findByFingerprint(sha256Fingerprint)
                .orElse(null);
            if (issued == null || issued.getRevokedAt() != null
                    || !issued.getDeviceId().equals(deviceId.value())
                    || issued.getCertificatePem() == null) {
                return false;
            }

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(issued.getCertificatePem()
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            certificate.checkValidity();
            if (!"EC".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())) {
                return false;
            }

            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(payload);
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception ignored) {
            // Deliberately collapse malformed proof, bad Base64 and lookup failures.
            return false;
        }
    }

    @Override
    public void revokeByFingerprint(String sha256Fingerprint, Instant revokedAt) {
        certificates.findByFingerprint(sha256Fingerprint).ifPresent(entity -> {
            entity.revoke(revokedAt);
            certificates.save(entity);
            cachedCrl = null;
        });
    }

    @Override
    public synchronized String currentRevocationList() {
        try {
            Instant now = Instant.now();
            // This endpoint is public by design, so do not perform a CA private-key
            // signature for every GET. Revocation invalidates the cache immediately.
            if (cachedCrl != null && now.isBefore(cachedCrlAt.plus(5, ChronoUnit.MINUTES))) {
                return cachedCrl;
            }
            JcaX509v2CRLBuilder builder =
                new JcaX509v2CRLBuilder(material.issuingCertificate().getSubjectX500Principal(),
                    Date.from(now));
            // Short next-update window: a CRL the broker refuses to refresh often is a
            // revocation that takes effect too late to matter.
            builder.setNextUpdate(Date.from(now.plus(1, ChronoUnit.DAYS)));

            for (IssuedCertificateEntity revoked : certificates.findByRevokedAtIsNotNull()) {
                builder.addCRLEntry(new BigInteger(revoked.getSerialNumber(), 16),
                    Date.from(revoked.getRevokedAt()), CRLReason.privilegeWithdrawn);
            }

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(material.issuingKey());
            cachedCrl = PemCodec.toPem("X509 CRL", builder.build(signer).getEncoded());
            cachedCrlAt = now;
            return cachedCrl;
        } catch (Exception e) {
            throw new CertificateAuthorityException("could not build the revocation list", e);
        }
    }

    @Override
    public String caChainPem() {
        // Issuing certificate first, root last: the order every TLS stack expects.
        return PemCodec.toPem(material.issuingCertificate()) + PemCodec.toPem(material.rootCertificate());
    }

    @Override
    public CaStatus status() {
        return new CaStatus(
            material.rootCertificate().getSubjectX500Principal().getName(),
            PemCodec.fingerprint(material.rootCertificate()),
            material.rootCertificate().getNotAfter().toInstant(),
            material.issuingCertificate().getSubjectX500Principal().getName(),
            PemCodec.fingerprint(material.issuingCertificate()),
            material.issuingCertificate().getNotAfter().toInstant(),
            certificates.count(),
            certificates.countByRevokedAtIsNotNull());
    }

    private PKCS10CertificationRequest parseCsr(String csrPem) throws Exception {
        try (org.bouncycastle.openssl.PEMParser parser =
                 new org.bouncycastle.openssl.PEMParser(new StringReader(csrPem))) {
            Object parsed = parser.readObject();
            if (parsed instanceof PKCS10CertificationRequest request) {
                return request;
            }
            throw new CertificateAuthorityException("the payload is not a PKCS#10 certificate request");
        }
    }

    /** Kept for symmetry with the port; the console reads the chain, not this list. */
    List<IssuedCertificateEntity> revokedCertificates() {
        return certificates.findByRevokedAtIsNotNull();
    }
}
