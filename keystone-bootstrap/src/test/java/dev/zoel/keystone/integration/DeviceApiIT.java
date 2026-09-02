package dev.zoel.keystone.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The REST surface an operator or a provisioning script touches. */
class DeviceApiIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("NEGATIVE: the API is closed to anonymous callers")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("registering a device returns 201 with a Location header")
    void registersDevice() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                .with(httpBasic(OPERATOR, OPERATOR_PASSWORD))
                .contentType("application/json")
                .content("{\"serialNumber\":\"SN-0001\",\"model\":\"ESP32-S3\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("PENDING_ENROLLMENT"))
            // A brand-new device must not come back carrying a certificate.
            .andExpect(jsonPath("$.certificateFingerprint").doesNotExist());
    }

    @Test
    @DisplayName("NEGATIVE: a duplicate serial number is a 409, not a second device")
    void rejectsDuplicateSerial() throws Exception {
        registerDevice("SN-DUP");

        mockMvc.perform(post("/api/v1/devices")
                .with(httpBasic(OPERATOR, OPERATOR_PASSWORD))
                .contentType("application/json")
                .content("{\"serialNumber\":\"SN-DUP\",\"model\":\"ESP32-S3\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("NEGATIVE: a serial number with injection characters is refused at the boundary")
    void rejectsHostileSerialNumber() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                .with(httpBasic(OPERATOR, OPERATOR_PASSWORD))
                .contentType("application/json")
                .content("{\"serialNumber\":\"SN'; DROP TABLE devices;--\",\"model\":\"ESP32-S3\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("NEGATIVE: bad credentials do not get in")
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/devices").with(httpBasic(OPERATOR, "not-the-password")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown device id is a 404, not a 500")
    void unknownDeviceIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/devices/1c1a2f8e-0000-0000-0000-000000000000")
                .with(httpBasic(OPERATOR, OPERATOR_PASSWORD)))
            .andExpect(status().isNotFound());
    }

    private void registerDevice(String serialNumber) throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                .with(httpBasic(OPERATOR, OPERATOR_PASSWORD))
                .contentType("application/json")
                .content("{\"serialNumber\":\"" + serialNumber + "\",\"model\":\"ESP32-S3\"}"))
            .andExpect(status().isCreated());
    }
}
