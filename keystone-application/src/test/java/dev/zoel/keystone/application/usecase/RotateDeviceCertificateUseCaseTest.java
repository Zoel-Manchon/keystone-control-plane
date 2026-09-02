package dev.zoel.keystone.application.usecase;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RotateDeviceCertificateUseCaseTest {

    @Test
    void rotationProofIsIndependentOfPemLineEndings() {
        String lf = "-----BEGIN CERTIFICATE REQUEST-----\nAQIDBA==\n-----END CERTIFICATE REQUEST-----\n";
        String crlf = lf.replace("\n", "\r\n");

        byte[] linuxPayload = RotateDeviceCertificateUseCase.rotationProofPayload(
            "11111111-1111-1111-1111-111111111111",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            lf);
        byte[] windowsPayload = RotateDeviceCertificateUseCase.rotationProofPayload(
            "11111111-1111-1111-1111-111111111111",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            crlf);

        assertThat(windowsPayload).isEqualTo(linuxPayload);
    }
}
