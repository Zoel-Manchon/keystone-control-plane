package dev.zoel.keystone.infrastructure.firmware;

import dev.zoel.keystone.application.port.out.ArtifactSigner;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.EnumSet;

/**
 * Ed25519 signing for firmware manifests, using the JDK's own provider (Java 15+).
 *
 * The signing key is separate from the CA key material on purpose. They protect
 * different things and would be rotated on different schedules, and in a real
 * deployment the firmware key belongs in an HSM or a build-server enclave that the
 * control plane never sees. Keeping them in one keystore would quietly couple the
 * blast radius of two very different compromises.
 */
@Component
public class Ed25519ArtifactSigner implements ArtifactSigner {

    private static final Logger log = LoggerFactory.getLogger(Ed25519ArtifactSigner.class);
    private static final String ALGORITHM = "Ed25519";

    private final Path privateKeyPath;
    private final Path publicKeyPath;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    Ed25519ArtifactSigner(@Value("${keystone.firmware.signing-key-path:data/firmware-signing.key}")
                          String keyPath) {
        this.privateKeyPath = Path.of(keyPath);
        this.publicKeyPath = Path.of(keyPath + ".pub");
    }

    @PostConstruct
    void initialise() {
        try {
            if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
                KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
                privateKey = factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Files.readAllBytes(privateKeyPath)));
                publicKey = factory.generatePublic(
                    new X509EncodedKeySpec(Files.readAllBytes(publicKeyPath)));
                log.info("firmware signing key loaded from {}", privateKeyPath);
            } else {
                KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();

                if (privateKeyPath.getParent() != null) {
                    Files.createDirectories(privateKeyPath.getParent());
                }
                Files.write(privateKeyPath, privateKey.getEncoded());
                restrictOwnerOnly(privateKeyPath);
                Files.write(publicKeyPath, publicKey.getEncoded());
                log.warn("generated a new firmware signing key at {} - devices holding the "
                    + "previous public key will reject every manifest signed from now on",
                    privateKeyPath);
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not initialise the firmware signing key", e);
        }
    }

    private static void restrictOwnerOnly(Path path) throws java.io.IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows uses ACLs rather than POSIX mode bits.
        }
    }

    @Override
    public String sign(byte[] payload) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payload);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("could not sign the payload", e);
        }
    }

    @Override
    public boolean verify(byte[] payload, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload);
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            // A malformed signature is a verification failure, not a server error.
            return false;
        }
    }

    @Override
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
