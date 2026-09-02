package dev.zoel.keystone.simulator;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Builds a perfectly well-formed certificate that no one trusts.
 *
 * The point of the rogue scenario: the certificate is valid X.509, the key is real,
 * the signature verifies against itself. It fails for the only reason that matters -
 * it does not chain to the Keystone CA.
 */
final class SelfSigned {

    private SelfSigned() {
    }

    static String certificatePem(DeviceKeyMaterial keys) {
        try {
            Instant now = Instant.now();
            X500Name subject = new X500Name("CN=rogue-device");

            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()),
                    Date.from(now), Date.from(now.plus(1, ChronoUnit.DAYS)), subject, keys.publicKey())
                    .build(new JcaContentSignerBuilder("SHA256withECDSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(keys.privateKey())));

            try (StringWriter out = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(out)) {
                writer.writeObject(certificate);
                writer.flush();
                return out.toString();
            }
        } catch (Exception e) {
            throw new SimulationException("could not build a self-signed certificate: " + e.getMessage());
        }
    }
}
