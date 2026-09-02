package dev.zoel.keystone.infrastructure.pki;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.IOException;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

/** PEM encoding and fingerprinting helpers. Pure functions, no state. */
final class PemCodec {

    private PemCodec() {
    }

    static String toPem(X509Certificate certificate) {
        try (StringWriter out = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(out)) {
            writer.writeObject(certificate);
            writer.flush();
            return out.toString();
        } catch (IOException e) {
            throw new IllegalStateException("could not PEM-encode certificate", e);
        }
    }

    static String toPem(String type, byte[] der) {
        try (StringWriter out = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(out)) {
            writer.writeObject(new PemObject(type, der));
            writer.flush();
            return out.toString();
        } catch (IOException e) {
            throw new IllegalStateException("could not PEM-encode " + type, e);
        }
    }

    /**
     * SHA-256 over the DER encoding of the certificate. This is the same value
     * openssl prints with -fingerprint -sha256, which matters: an operator has to be
     * able to compare what the console shows against what the device reports.
     */
    static String fingerprint(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new IllegalStateException("could not fingerprint certificate", e);
        }
    }
}
