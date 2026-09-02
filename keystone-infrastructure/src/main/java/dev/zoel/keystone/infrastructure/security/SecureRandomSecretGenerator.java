package dev.zoel.keystone.infrastructure.security;

import dev.zoel.keystone.application.port.out.SecretGenerator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Enrolment secrets.
 *
 * 256 bits from SecureRandom, base64url encoded. Not java.util.Random, which is
 * predictable from a handful of outputs and would make every future token guessable.
 *
 * The hash is a plain SHA-256 rather than BCrypt on purpose, and the reason is worth
 * stating: BCrypt exists to slow down guessing of LOW-entropy secrets that humans
 * choose. This secret has 256 bits of entropy and cannot be guessed at any speed, so
 * the deliberate slowness would only cost lookup time on a hot path.
 */
@Component
public class SecureRandomSecretGenerator implements SecretGenerator {

    private static final int SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
