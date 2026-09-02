package dev.zoel.keystone.simulator;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

/**
 * What a real device does on first boot: generate a keypair and a CSR.
 *
 * The private key never leaves this object, exactly as it would never leave the
 * secure element of an ESP32-S3. Keystone only ever sees the CSR.
 */
final class DeviceKeyMaterial {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final KeyPair keyPair;

    private DeviceKeyMaterial(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * Exposed only so the MQTT probe can build an in-memory keystore. In real
     * firmware this key would live behind a secure element and never be readable.
     */
    java.security.PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    java.security.PublicKey publicKey() {
        return keyPair.getPublic();
    }

    static DeviceKeyMaterial generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            return new DeviceKeyMaterial(generator.generateKeyPair());
        } catch (Exception e) {
            throw new IllegalStateException("could not generate device key material", e);
        }
    }

    /**
     * The CN asked for here is intentionally wrong-ish: Keystone overwrites it with
     * the device id it assigned. Running the simulator proves that in practice.
     */
    String certificateSigningRequestPem(String requestedCommonName) {
        try {
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

            PKCS10CertificationRequest csr = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=" + requestedCommonName), keyPair.getPublic()).build(signer);

            try (StringWriter out = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(out)) {
                writer.writeObject(csr);
                writer.flush();
                return out.toString();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not build the certificate signing request", e);
        }
    }
}
