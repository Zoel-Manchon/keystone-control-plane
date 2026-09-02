package dev.zoel.keystone.domain.firmware;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirmwareVersionTest {

    @Test
    @DisplayName("1.10.0 is newer than 1.9.0 - the case string comparison gets wrong")
    void comparesNumericallyNotLexically() {
        assertThat(FirmwareVersion.parse("1.10.0").isNewerThan(FirmwareVersion.parse("1.9.0"))).isTrue();
    }

    @Test
    @DisplayName("NEGATIVE: a malformed version is rejected at the boundary")
    void rejectsMalformedVersions() {
        assertThatThrownBy(() -> FirmwareVersion.parse("1.2"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirmwareVersion.parse("1.2.x"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
