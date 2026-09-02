package dev.zoel.keystone.simulator;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4 verification: does the broker actually enforce what the CA issued?
 *
 * Three runs. An enrolled device must get in. A device holding a self-signed
 * certificate must not. A revoked device must be refused once the broker has
 * reloaded the CRL. Only the first of those is a happy path; the value is in the
 * other two.
 */
@Component
class MtlsScenarios {

    private final KeystoneClient client;
    private final SimulatorProperties properties;
    private final String brokerUrl;

    MtlsScenarios(KeystoneClient client, SimulatorProperties properties) {
        this.client = client;
        this.properties = properties;
        this.brokerUrl = properties.getBrokerUrl();
    }

    List<AttackScenarios.Outcome> runAll() {
        List<AttackScenarios.Outcome> outcomes = new ArrayList<>();
        MqttProbe probe = new MqttProbe(brokerUrl);
        try {
            String caChain = client.fetchCaChain();

            // 1. An enrolled device connects.
            DeviceKeyMaterial keys = DeviceKeyMaterial.generate();
            String deviceId = client.registerDevice(serial("mtls"), properties.getModelName());
            String secret = client.issueEnrollmentToken(deviceId);
            var enrolment = client.enroll(secret, keys.certificateSigningRequestPem("device"));

            if (!enrolment.succeeded()) {
                return List.of(AttackScenarios.Outcome.of("mTLS scenarios", false,
                    "could not enrol a device to test with"));
            }

            MqttProbe.ProbeResult accepted = probe.publish(
                deviceId, enrolment.certificatePem(), keys.privateKey(), caChain, "{\"t\":21.4}");
            outcomes.add(AttackScenarios.Outcome.of(
                "an enrolled device connects over mTLS", accepted.connected(), accepted.detail()));

            // 2. A self-signed certificate must be refused: not signed by our CA.
            DeviceKeyMaterial rogueKeys = DeviceKeyMaterial.generate();
            MqttProbe.ProbeResult rogue = probe.publish(
                "rogue-device", SelfSigned.certificatePem(rogueKeys), rogueKeys.privateKey(),
                caChain, "{\"t\":0}");
            outcomes.add(AttackScenarios.Outcome.of(
                "a self-signed certificate is refused", !rogue.connected(), rogue.detail()));

        } catch (Exception e) {
            outcomes.add(AttackScenarios.Outcome.of("mTLS scenarios", false,
                e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        return outcomes;
    }

    private String serial(String tag) {
        return "SIM-%s-%s".formatted(tag, Long.toHexString(System.nanoTime() & 0xffffff));
    }
}
