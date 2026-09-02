package dev.zoel.keystone.integration;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.util.Base64;
import java.security.spec.ECGenParameterSpec;

/** Builds genuine PKCS#10 requests. A hand-written fake would only test the fake. */
final class TestCsr {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final KeyPair keyPair;

    private TestCsr(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    static TestCsr generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        return new TestCsr(generator.generateKeyPair());
    }

    String signBase64(byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    String pem(String commonName) throws Exception {
        var csr = new JcaPKCS10CertificationRequestBuilder(
            new X500Name("CN=" + commonName), keyPair.getPublic())
            .build(new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate()));

        try (StringWriter out = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(out)) {
            writer.writeObject(csr);
            writer.flush();
            return out.toString();
        }
    }
}
