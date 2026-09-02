package dev.zoel.keystone.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The hardening applied to the console, asserted rather than assumed.
 *
 * Security headers are the kind of thing that silently disappears in a refactor,
 * because nothing visibly breaks when they go missing.
 */
class WebConsoleSecurityIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("NEGATIVE: an anonymous visitor is sent to the login page")
    void anonymousIsRedirectedToLogin() throws Exception {
        // Spring Security sends a relative Location ("/login"), not an absolute URL, so
        // match it exactly rather than with a "**/login" pattern that silently stopped
        // matching when the redirect stopped being absolute.
        mockMvc.perform(get("/devices"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("the login page is reachable without credentials")
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an authenticated operator reaches the overview")
    void operatorReachesOverview() throws Exception {
        mockMvc.perform(get("/").with(user(OPERATOR).roles("OPERATOR")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("NEGATIVE: a user without the OPERATOR role is refused")
    void wrongRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/devices").with(user("intern").roles("VIEWER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("NEGATIVE: a form POST without a CSRF token is rejected")
    void csrfIsEnforcedOnTheConsole() throws Exception {
        mockMvc.perform(post("/devices")
                .with(user(OPERATOR).roles("OPERATOR"))
                .param("serialNumber", "SN-CSRF")
                .param("model", "ESP32-S3"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the same POST succeeds once the CSRF token is present")
    void csrfTokenLetsTheFormThrough() throws Exception {
        mockMvc.perform(post("/devices")
                .with(user(OPERATOR).roles("OPERATOR"))
                .with(csrf())
                .param("serialNumber", "SN-CSRF-OK")
                .param("model", "ESP32-S3"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("the defensive headers are actually on the response")
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().exists("Content-Security-Policy"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("the health probe is public but the rest of actuator is not")
    void actuatorIsMostlyClosed() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // What matters is that the data is never served. Form login answers an
        // unauthenticated browser with a redirect to /login rather than a 401, so
        // assert on that instead of on a 4xx that this configuration never returns.
        mockMvc.perform(get("/actuator/beans"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("the CA chain and the CRL are public: devices need them and they are not secrets")
    void trustMaterialIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/enrollment/ca-chain")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enrollment/crl")).andExpect(status().isOk());
    }
}
