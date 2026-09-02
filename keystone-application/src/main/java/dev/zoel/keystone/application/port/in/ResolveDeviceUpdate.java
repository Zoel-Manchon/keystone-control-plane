package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.device.DeviceId;

/** What a device asks when it wakes up: "is there anything I should be running?" */
public interface ResolveDeviceUpdate {

    UpdateManifest handle(DeviceId deviceId);

    /**
     * The signed payload. The device verifies the signature over the canonical form
     * BEFORE downloading anything, so a hostile download URL never gets fetched.
     */
    record UpdateManifest(boolean updateAvailable, String deviceId, String version,
                          String artifactId, String sha256, long sizeBytes,
                          String downloadPath, String signature) {

        public static UpdateManifest none(String deviceId) {
            return new UpdateManifest(false, deviceId, null, null, null, 0, null, null);
        }

        /**
         * Canonical bytes to sign and verify. Field order and separator are fixed:
         * if the device and the server disagreed on either, every signature would
         * fail, which is the safe direction for that mistake to fall.
         */
        public byte[] canonicalPayload() {
            return String.join("|", deviceId, version, artifactId, sha256, Long.toString(sizeBytes))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
