package dev.zoel.keystone.integration;

import com.jayway.jsonpath.JsonPath;
import dev.zoel.keystone.application.port.in.VerifyAuditTrail;
import dev.zoel.keystone.application.usecase.RotateDeviceCertificateUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The enrolment lifecycle end to end, against a real CA and a real database.
 *
 * This is the test that would have caught every bug that mattered in this project.
 */
class EnrollmentFlowIT extends AbstractIntegrationTest {

    @Autowired
    private VerifyAuditTrail verifyAuditTrail;

    @Test
    @DisplayName("register, tokenise, enrol: the certificate chains to the Keystone CA")
    void fullEnrolmentIssuesATrustedCertificate() throws Exception {
        String deviceId = registerDevice("SN-FLOW-1");
        String secret = issueToken(deviceId);

        String response = enrol(secret, "whatever-the-device-asks-for")
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        X509Certificate certificate = parse(JsonPath.read(response, "$.certificatePem"));
        String subject = certificate.getSubjectX500Principal().getName();

        // The CA assigns the subject. If this ever starts echoing the CSR's CN, a
        // device can name itself anything and the topic ACL collapses with it.
        assertThat(subject).contains(deviceId);
        assertThat(subject).doesNotContain("whatever-the-device");

        // Not a CA, and client authentication only.
        assertThat(certificate.getBasicConstraints()).isEqualTo(-1);
        assertThat(certificate.getExtendedKeyUsage()).containsExactly("1.3.6.1.5.5.7.3.2");
    }

    @Test
    @DisplayName("NEGATIVE: the same secret cannot enrol twice")
    void secretIsSingleUse() throws Exception {
        String secret = issueToken(registerDevice("SN-FLOW-2"));

        enrol(secret, "device").andExpect(status().isOk());
        enrol(secret, "device").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("NEGATIVE: an invented secret gets the same generic refusal")
    void fabricatedSecretIsRefused() throws Exception {
        enrol("this-secret-never-existed", "device").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("NEGATIVE: a payload that is not a CSR never reaches the signer as one")
    void malformedCsrIsRejected() throws Exception {
        String secret = issueToken(registerDevice("SN-FLOW-3"));

        mockMvc.perform(post("/api/v1/enrollment")
                .contentType("application/json")
                .content(body(secret, "-----BEGIN CERTIFICATE REQUEST-----\nnot base64\n")))
            .andExpect(status().is4xxClientError());
    }


    @Test
    @DisplayName("rotation requires a signature from the current device private key")
    void rotationRequiresCurrentKeyProof() throws Exception {
        String deviceId = registerDevice("SN-ROTATE-POP");
        String secret = issueToken(deviceId);

        TestCsr currentIdentity = TestCsr.generate();
        String enrolment = mockMvc.perform(post("/api/v1/enrollment")
                .contentType("application/json")
                .content(body(secret, currentIdentity.pem("current-device"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String fingerprint = JsonPath.read(enrolment, "$.fingerprint");

        TestCsr replacementIdentity = TestCsr.generate();
        String replacementCsr = replacementIdentity.pem("replacement");
        byte[] proofPayload = RotateDeviceCertificateUseCase.rotationProofPayload(
            deviceId, fingerprint, replacementCsr);

        // Knowing the public fingerprint and owning the replacement key is insufficient.
        String attackerProof = replacementIdentity.signBase64(proofPayload);
        rotate(deviceId, fingerprint, replacementCsr, attackerProof)
            .andExpect(status().isForbidden());

        // The same CSR succeeds only when authorised by the currently enrolled key.
        String legitimateProof = currentIdentity.signBase64(proofPayload);
        String rotated = rotate(deviceId, fingerprint, replacementCsr, legitimateProof)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(rotated, "$.fingerprint")).isNotEqualTo(fingerprint);
    }

    @Test
    @DisplayName("the whole flow leaves an intact audit chain")
    void auditChainSurvivesTheFlow() throws Exception {
        String secret = issueToken(registerDevice("SN-FLOW-4"));
        enrol(secret, "device").andExpect(status().isOk());
        enrol(secret, "device").andExpect(status().isForbidden());   // adds a rejection entry

        var integrity = verifyAuditTrail.handle();

        assertThat(integrity.intact()).isTrue();
        // register + token + certificate + completion + rejection
        assertThat(integrity.entriesChecked()).isGreaterThanOrEqualTo(5);
    }

    private String registerDevice(String serialNumber) throws Exception {
        String response = mockMvc.perform(post("/api/v1/devices")
                .with(httpBasic(OPERATOR, OPERATOR_PASSWORD))
                .contentType("application/json")
                .content("{\"serialNumber\":\"" + serialNumber + "\",\"model\":\"ESP32-S3\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String issueToken(String deviceId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/devices/" + deviceId + "/enrollment-token")
                .with(httpBasic(OPERATOR, OPERATOR_PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.secret");
    }

    private ResultActions enrol(String secret, String requestedCommonName) throws Exception {
        return mockMvc.perform(post("/api/v1/enrollment")
            .contentType("application/json")
            .content(body(secret, TestCsr.generate().pem(requestedCommonName))));
    }


    private ResultActions rotate(String deviceId, String fingerprint, String csr, String proof) throws Exception {
        return mockMvc.perform(post("/api/v1/rotation/" + deviceId)
            .contentType("application/json")
            .content("{\"currentFingerprint\":\"" + fingerprint + "\",\"csr\":\""
                + jsonEscape(csr) + "\",\"proof\":\"" + proof + "\"}"));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }

    /** Hand-rolled JSON: the payload is two fields and the CSR needs newline escaping. */
    private static String body(String secret, String csr) {
        return "{\"secret\":\"" + secret + "\",\"csr\":\""
            + jsonEscape(csr) + "\"}";
    }

    private static X509Certificate parse(String pem) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }
}
