package dev.zoel.keystone.simulator;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * HTTP client for the control plane.
 *
 * java.net.http.HttpClient rather than a Spring client because it is JDK-native and,
 * since Java 21, its blocking calls are virtual-thread friendly: a thread parked on a
 * socket unmounts from its carrier instead of pinning it. That property is the whole
 * reason a few hundred simulated devices fit on a laptop.
 */
@Component
class KeystoneClient {

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final SimulatorProperties properties;
    private final String operatorAuthorization;

    KeystoneClient(SimulatorProperties properties) {
        this.properties = properties;
        this.operatorAuthorization = "Basic " + Base64.getEncoder().encodeToString(
            (properties.getOperatorUsername() + ":" + properties.getOperatorPassword())
                .getBytes(StandardCharsets.UTF_8));
    }

    /** Operator action: puts the device in the inventory. */
    String registerDevice(String serialNumber, String model) throws Exception {
        HttpResponse<String> response = send(request("/api/v1/devices")
            .header("Authorization", operatorAuthorization)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                Json.object("serialNumber", serialNumber, "model", model))));

        if (response.statusCode() != 201) {
            throw new SimulationException("device registration returned HTTP " + response.statusCode());
        }
        return Json.stringField(response.body(), "id");
    }

    /** Operator action: issues the single-use secret. */
    String issueEnrollmentToken(String deviceId) throws Exception {
        HttpResponse<String> response = send(
            request("/api/v1/devices/" + deviceId + "/enrollment-token")
                .header("Authorization", operatorAuthorization)
                .POST(HttpRequest.BodyPublishers.noBody()));

        if (response.statusCode() != 201 && response.statusCode() != 200) {
            throw new SimulationException("token issuance returned HTTP " + response.statusCode());
        }
        return Json.stringField(response.body(), "secret");
    }

    /** Device action: unauthenticated, guarded only by the secret. */
    EnrollmentOutcome enroll(String secret, String csrPem) throws Exception {
        HttpResponse<String> response = send(request("/api/v1/enrollment")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                Json.object("secret", secret, "csr", csrPem))));

        if (response.statusCode() != 200) {
            return new EnrollmentOutcome(response.statusCode(), null, null);
        }
        return new EnrollmentOutcome(200,
            Json.stringField(response.body(), "certificatePem"),
            Json.stringField(response.body(), "fingerprint"));
    }

    String fetchCaChain() throws Exception {
        return send(request("/api/v1/enrollment/ca-chain").GET()).body();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + path))
            .timeout(Duration.ofSeconds(30));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The status code is kept explicitly: the failure cases are what the attack runs assert on. */
    record EnrollmentOutcome(int statusCode, String certificatePem, String fingerprint) {
        boolean succeeded() {
            return statusCode == 200;
        }
    }
}
