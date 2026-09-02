package dev.zoel.keystone.domain.pki;

import java.util.HexFormat;
import java.util.Objects;

/** SHA-256 fingerprint of an X.509 certificate, normalised to lowercase hex. */
public record CertificateFingerprint(String sha256Hex) {

    private static final int SHA256_HEX_LENGTH = 64;

    public CertificateFingerprint {
        Objects.requireNonNull(sha256Hex, "fingerprint must not be null");
        sha256Hex = sha256Hex.replace(":", "").trim().toLowerCase();
        if (sha256Hex.length() != SHA256_HEX_LENGTH) {
            throw new IllegalArgumentException(
                "a SHA-256 fingerprint has " + SHA256_HEX_LENGTH + " hex characters, not " + sha256Hex.length());
        }
        if (!sha256Hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint contains non-hexadecimal characters");
        }
    }

    public static CertificateFingerprint ofBytes(byte[] digest) {
        return new CertificateFingerprint(HexFormat.of().formatHex(digest));
    }

    /** Human-readable form for the UI: AB:CD:EF:... */
    public String humanReadable() {
        StringBuilder sb = new StringBuilder(SHA256_HEX_LENGTH + 31);
        for (int i = 0; i < sha256Hex.length(); i += 2) {
            if (i > 0) sb.append(':');
            sb.append(Character.toUpperCase(sha256Hex.charAt(i)))
              .append(Character.toUpperCase(sha256Hex.charAt(i + 1)));
        }
        return sb.toString();
    }
}
