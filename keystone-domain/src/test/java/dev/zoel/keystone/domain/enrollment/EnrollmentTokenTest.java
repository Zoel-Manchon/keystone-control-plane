package dev.zoel.keystone.domain.enrollment;

import dev.zoel.keystone.domain.device.DeviceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTokenTest {

    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");

    @Test
    @DisplayName("a freshly issued token is usable")
    void freshTokenIsUsable() {
        EnrollmentToken token = EnrollmentToken.issue(DeviceId.newId(), "hash", NOW, EnrollmentToken.DEFAULT_TTL);

        assertThat(token.isUsable(NOW)).isTrue();
    }

    @Test
    @DisplayName("NEGATIVE: a single-use token cannot be replayed")
    void tokenCannotBeReplayed() {
        EnrollmentToken token = EnrollmentToken.issue(DeviceId.newId(), "hash", NOW, EnrollmentToken.DEFAULT_TTL);
        token.consume(NOW);

        assertThatThrownBy(() -> token.consume(NOW.plusSeconds(1)))
            .isInstanceOf(TokenAlreadyConsumedException.class);
    }

    @Test
    @DisplayName("NEGATIVE: an expired token cannot be consumed")
    void expiredTokenIsRejected() {
        EnrollmentToken token = EnrollmentToken.issue(DeviceId.newId(), "hash", NOW, Duration.ofMinutes(5));

        assertThatThrownBy(() -> token.consume(NOW.plus(Duration.ofMinutes(6))))
            .isInstanceOf(TokenExpiredException.class);
    }
}
