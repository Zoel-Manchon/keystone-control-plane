package dev.zoel.keystone.infrastructure.pki;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Loads the CA key material, creating a fresh two-tier hierarchy on first run.
 *
 * Two tiers rather than one because that is how real PKIs are built: the root signs
 * the issuing CA and then stays out of daily operations, so compromising the online
 * component does not compromise the trust anchor. Here both keys sit in the same
 * PKCS#12 for a portfolio project — in production the root key belongs offline, and
 * that gap is documented rather than papered over.
 *
 * Keys are EC P-256: smaller signatures and cheaper verification than RSA, which
 * matters when the verifier is a microcontroller.
 */
final class CaKeyStore {

    private static final Logger log = LoggerFactory.getLogger(CaKeyStore.class);

    private static final String KEY_ALGORITHM = "EC";
    private static final String CURVE = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String ALIAS_ROOT = "keystone-root";
    private static final String ALIAS_ISSUING = "keystone-issuing";

    private final SecureRandom random = new SecureRandom();

    CaMaterial loadOrCreate(PkiProperties properties) {
        Path path = Path.of(properties.getKeystorePath());
        char[] password = properties.getKeystorePassword().toCharArray();
        try {
            return Files.exists(path) ? load(path, password) : create(path, password, properties);
        } catch (Exception e) {
            throw new IllegalStateException("could not initialise the CA keystore at " + path, e);
        }
    }

    private CaMaterial load(Path path, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            keyStore.load(in, password);
        }
        log.info("CA keystore loaded from {}", path);
        return new CaMaterial(
            (PrivateKey) keyStore.getKey(ALIAS_ROOT, password),
            (X509Certificate) keyStore.getCertificate(ALIAS_ROOT),
            (PrivateKey) keyStore.getKey(ALIAS_ISSUING, password),
            (X509Certificate) keyStore.getCertificate(ALIAS_ISSUING));
    }

    private CaMaterial create(Path path, char[] password, PkiProperties properties) throws Exception {
        log.warn("no CA keystore found at {} - generating a new hierarchy. "
            + "Every certificate issued by a previous hierarchy becomes untrusted.", path);

        KeyPair rootKeys = generateKeyPair();
        Instant now = Instant.now();

        X509Certificate root = selfSignedRoot(rootKeys, properties, now);

        KeyPair issuingKeys = generateKeyPair();
        X509Certificate issuing = signIntermediate(issuingKeys, rootKeys.getPrivate(), root, properties, now);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        keyStore.setKeyEntry(ALIAS_ROOT, rootKeys.getPrivate(), password, new X509Certificate[]{root});
        keyStore.setKeyEntry(ALIAS_ISSUING, issuingKeys.getPrivate(), password,
            new X509Certificate[]{issuing, root});

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            keyStore.store(out, password);
        }
        restrictOwnerOnly(path);
        log.info("CA hierarchy created and stored at {}", path);

        return new CaMaterial(rootKeys.getPrivate(), root, issuingKeys.getPrivate(), issuing);
    }

    private static void restrictOwnerOnly(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows uses ACLs rather than POSIX mode bits.
        }
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        generator.initialize(new ECGenParameterSpec(CURVE), random);
        return generator.generateKeyPair();
    }

    private X509Certificate selfSignedRoot(KeyPair keys, PkiProperties properties, Instant now)
            throws Exception {
        X500Name subject = new X500Name(properties.getRootSubject());
        Instant notAfter = now.plus(properties.getRootValidityYears() * 365L, ChronoUnit.DAYS);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            subject, newSerial(), Date.from(now), Date.from(notAfter), subject, keys.getPublic());

        JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        // pathLen 1: the root may sign one level of CA below it and no more.
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(1));
        builder.addExtension(Extension.keyUsage, true,
            new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
            utils.createSubjectKeyIdentifier(keys.getPublic()));

        return sign(builder, keys.getPrivate());
    }

    private X509Certificate signIntermediate(KeyPair issuingKeys, PrivateKey rootKey,
                                             X509Certificate root, PkiProperties properties,
                                             Instant now) throws Exception {
        Instant notAfter = now.plus(properties.getIssuingValidityYears() * 365L, ChronoUnit.DAYS);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            root, newSerial(), Date.from(now), Date.from(notAfter),
            new X500Name(properties.getIssuingSubject()), issuingKeys.getPublic());

        JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        // pathLen 0: this CA signs end-entity certificates only, never another CA.
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true,
            new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
            utils.createSubjectKeyIdentifier(issuingKeys.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
            utils.createAuthorityKeyIdentifier(root));

        return sign(builder, rootKey);
    }

    private X509Certificate sign(JcaX509v3CertificateBuilder builder, PrivateKey signingKey)
            throws OperatorCreationException, CertificateException, IOException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(signingKey);
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    /**
     * 128 bits of randomness in the serial. Predictable serials have enabled real
     * attacks against certificate systems, so this is not a place to use a counter.
     */
    private BigInteger newSerial() {
        return new BigInteger(128, random);
    }
}
