package dev.zoel.keystone.simulator;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * One simulated device, start to finish: generate keys, get registered, obtain a
 * secret, enrol, and check what came back.
 *
 * Runs on its own virtual thread. The work is almost entirely blocking I/O, which is
 * precisely the workload virtual threads exist for: a platform-thread pool of 200
 * would spend its life parked on sockets.
 */
record SimulatedDevice(int index, KeystoneClient client, SimulatorProperties properties) {

    Result run() {
        String serialNumber = "SIM-%05d-%s".formatted(index, Long.toHexString(System.nanoTime() & 0xffff));
        try {
            DeviceKeyMaterial keys = DeviceKeyMaterial.generate();

            String deviceId = client.registerDevice(serialNumber, properties.getModelName());
            String secret = client.issueEnrollmentToken(deviceId);

            // The CSR asks for CN=device-boot. Keystone will ignore it and use the
            // device id instead, which the assertion below verifies.
            KeystoneClient.EnrollmentOutcome outcome =
                client.enroll(secret, keys.certificateSigningRequestPem("device-boot"));

            if (!outcome.succeeded()) {
                return Result.failed(serialNumber, "enrolment returned HTTP " + outcome.statusCode());
            }

            X509Certificate certificate = parse(outcome.certificatePem());
            String subject = certificate.getSubjectX500Principal().getName();

            if (!subject.contains(deviceId)) {
                return Result.failed(serialNumber,
                    "the CA honoured the CSR subject instead of assigning its own: " + subject);
            }
            if (certificate.getBasicConstraints() != -1) {
                return Result.failed(serialNumber, "the device certificate is marked as a CA");
            }

            return Result.enrolled(serialNumber, deviceId, outcome.fingerprint(),
                certificate.getNotAfter().toInstant().toString());

        } catch (Exception e) {
            return Result.failed(serialNumber, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static X509Certificate parse(String pem) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }

    record Result(boolean success, String serialNumber, String deviceId,
                  String fingerprint, String notAfter, String failure) {

        static Result enrolled(String serial, String deviceId, String fingerprint, String notAfter) {
            return new Result(true, serial, deviceId, fingerprint, notAfter, null);
        }

        static Result failed(String serial, String failure) {
            return new Result(false, serial, null, null, null, failure);
        }
    }
}
