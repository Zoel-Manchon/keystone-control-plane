package dev.zoel.keystone.simulator;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The runs that are supposed to FAIL.
 *
 * A simulator that only walks the happy path proves the system works when nobody is
 * attacking it, which is the easy half. Each scenario here asserts that Keystone
 * refuses something, and a "pass" means the refusal happened.
 */
@Component
class AttackScenarios {

    private final KeystoneClient client;
    private final SimulatorProperties properties;

    AttackScenarios(KeystoneClient client, SimulatorProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    List<Outcome> runAll() throws Exception {
        return List.of(
            fabricatedSecretIsRejected(),
            replayedSecretIsRejected(),
            stolenSecretCannotBeBoundToAnotherKey(),
            concurrentReplayYieldsExactlyOneCertificate());
    }

    /** A guessed secret must not enrol anything. */
    private Outcome fabricatedSecretIsRejected() throws Exception {
        var outcome = client.enroll("not-a-real-secret-at-all",
            DeviceKeyMaterial.generate().certificateSigningRequestPem("attacker"));
        return Outcome.of("fabricated secret rejected", !outcome.succeeded(),
            "HTTP " + outcome.statusCode());
    }

    /** The defining property of a single-use token. */
    private Outcome replayedSecretIsRejected() throws Exception {
        String deviceId = client.registerDevice(serial("replay"), properties.getModelName());
        String secret = client.issueEnrollmentToken(deviceId);

        var first = client.enroll(secret, DeviceKeyMaterial.generate().certificateSigningRequestPem("d"));
        var second = client.enroll(secret, DeviceKeyMaterial.generate().certificateSigningRequestPem("d"));

        return Outcome.of("replayed secret rejected",
            first.succeeded() && !second.succeeded(),
            "first HTTP " + first.statusCode() + ", replay HTTP " + second.statusCode());
    }

    /**
     * Someone intercepts the secret in transit and races the real device with their
     * own keypair. Whoever arrives first wins the certificate; the loser gets nothing.
     * The property under test is that the secret cannot serve BOTH.
     */
    private Outcome stolenSecretCannotBeBoundToAnotherKey() throws Exception {
        String deviceId = client.registerDevice(serial("steal"), properties.getModelName());
        String secret = client.issueEnrollmentToken(deviceId);

        var attacker = client.enroll(secret,
            DeviceKeyMaterial.generate().certificateSigningRequestPem("attacker"));
        var legitimate = client.enroll(secret,
            DeviceKeyMaterial.generate().certificateSigningRequestPem("legit"));

        return Outcome.of("a stolen secret yields at most one certificate",
            attacker.succeeded() ^ legitimate.succeeded(),
            "attacker HTTP " + attacker.statusCode() + ", device HTTP " + legitimate.statusCode());
    }

    /**
     * The race the use case was written to survive: many requests firing the same
     * secret at the same instant. Consuming the token before signing is what makes
     * this come out at exactly one.
     */
    private Outcome concurrentReplayYieldsExactlyOneCertificate() throws Exception {
        String deviceId = client.registerDevice(serial("race"), properties.getModelName());
        String secret = client.issueEnrollmentToken(deviceId);

        int attempts = 16;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, attempts)
                .<Callable<Boolean>>mapToObj(i -> () -> client.enroll(secret,
                    DeviceKeyMaterial.generate().certificateSigningRequestPem("racer-" + i)).succeeded())
                .toList();

            long successes = 0;
            for (Future<Boolean> future : executor.invokeAll(tasks)) {
                try {
                    if (future.get()) {
                        successes++;
                    }
                } catch (Exception ignored) {
                    // A failed attempt is the expected outcome for all but one.
                }
            }
            return Outcome.of("%d concurrent replays yield exactly one certificate".formatted(attempts),
                successes == 1, successes + " succeeded");
        }
    }

    private String serial(String tag) {
        return "SIM-ATTACK-%s-%s".formatted(tag, Long.toHexString(System.nanoTime() & 0xffffff));
    }

    record Outcome(String scenario, boolean passed, String detail) {
        static Outcome of(String scenario, boolean passed, String detail) {
            return new Outcome(scenario, passed, detail);
        }
    }
}
