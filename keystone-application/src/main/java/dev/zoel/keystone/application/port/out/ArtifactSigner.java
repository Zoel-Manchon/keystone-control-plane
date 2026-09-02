package dev.zoel.keystone.application.port.out;

/**
 * Signs firmware manifests.
 *
 * Ed25519 rather than RSA: 64-byte signatures and verification that a
 * microcontroller can do without breaking a sweat. The device carries only the
 * public key, so compromising a device never yields the ability to sign firmware.
 */
public interface ArtifactSigner {

    String sign(byte[] payload);

    boolean verify(byte[] payload, String signatureBase64);

    /** The public key devices embed, so they can verify without contacting anyone. */
    String publicKeyBase64();
}
