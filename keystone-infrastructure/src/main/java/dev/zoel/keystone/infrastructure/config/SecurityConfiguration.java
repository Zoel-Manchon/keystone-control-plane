package dev.zoel.keystone.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.time.Duration;

/**
 * Two separate filter chains, on purpose:
 *
 *   /api/**  -> stateless API. HTTP Basic for now; JWT in phase 2.
 *   anything else -> web console with a session and a login form.
 *
 * Merging them would force CSRF to be disabled everywhere, and CSRF protection is
 * exactly what guards the console's forms.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize on use-case entry points
public class SecurityConfiguration {

    /** Every console route requires this role. Roles come from the database in phase 2. */
    static final String OPERATOR = "OPERATOR";

    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            // No session means no CSRF vector. This is the ONLY place CSRF may be off.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // The device-facing enrolment endpoints are unauthenticated by
                // necessity: a device has no credentials until it is enrolled. They
                // are guarded by the single-use secret, and the CA chain and CRL are
                // public information by definition.
                .requestMatchers(HttpMethod.POST, "/api/v1/enrollment").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/enrollment/ca-chain").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/enrollment/crl").permitAll()
                // Renewal is HTTP-public but cryptographically authenticated: the
                // current certificate fingerprint selects the identity and the device
                // must sign the rotation payload with the corresponding private key.
                .requestMatchers(HttpMethod.POST, "/api/v1/rotation/**").permitAll()
                .anyRequest().hasRole(OPERATOR))
            .httpBasic(Customizer.withDefaults())   // temporary: replaced by JWT in phase 2
            .headers(SecurityConfiguration::hardenHeaders)
            .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers("/login").permitAll()
                // The error dispatch MUST be permitted. Spring Security filters the
                // ERROR dispatch too, so without this every unhandled exception is
                // masked as a 403 and the real cause never reaches the logs or you.
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                // Actuator beyond the health probe is operational surface: lock it down.
                .requestMatchers("/actuator/**").hasRole(OPERATOR)
                .anyRequest().hasRole(OPERATOR))

            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/devices", true)
                .failureUrl("/login?error")
                .permitAll())

            .logout(logout -> logout
                // logoutUrl already requires POST while CSRF protection is on.
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll())

            .sessionManagement(session -> session
                // A new session id is issued on login, so a session id captured
                // beforehand is worthless. This is session fixation defence.
                .sessionFixation(fixation -> fixation.migrateSession())
                // One live session per operator; a second login evicts the first.
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false))

            .headers(SecurityConfiguration::hardenHeaders)
            .build();
    }

    /**
     * Response headers applied to both chains. These are cheap, they cost nothing at
     * runtime, and they close off whole families of client-side attacks.
     */
    private static void hardenHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
            // Clickjacking: the console must never be embeddable.
            .frameOptions(frame -> frame.deny())
            // Do not leak the console URLs (which contain device ids) to third parties.
            .referrerPolicy(referrer -> referrer.policy(
                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            // Force HTTPS once a certificate is in front of this. Harmless over
            // plain HTTP on localhost: browsers ignore HSTS on non-secure origins.
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                + "font-src https://fonts.gstatic.com; "
                + "img-src 'self' data:; "
                + "form-action 'self'; "
                + "base-uri 'none'; "
                + "object-src 'none'; "
                + "frame-ancestors 'none'"));
    }

    /**
     * Development operator. This is a placeholder: in phase 2 operators are loaded
     * from the database with roles and a second factor.
     *
     * The credential is intentionally required from configuration. There is no
     * repository default password that can accidentally escape into a deployment.
     */
    @Bean
    UserDetailsService operators(
            PasswordEncoder encoder,
            @Value("${keystone.security.dev-operator-password}") String devPassword) {
        return new InMemoryUserDetailsManager(
            User.withUsername("operator")
                .password(encoder.encode(devPassword))
                .roles(OPERATOR)
                .build());
    }

    /**
     * Without this publisher the servlet container never tells Spring Security that a
     * session ended, so the concurrent-session registry fills up with dead entries and
     * maximumSessions(1) starts locking out a legitimate operator.
     */
    @Bean
    ServletListenerRegistrationBean<HttpSessionEventPublisher> sessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    /**
     * BCrypt with cost 12. Higher than the default 10 on purpose: this hash guards
     * console access, and the extra milliseconds per login are worth the added cost
     * to anyone brute-forcing a leaked hash offline.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
