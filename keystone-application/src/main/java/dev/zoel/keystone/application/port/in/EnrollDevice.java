package dev.zoel.keystone.application.port.in;

/** Called by the device itself, not by an operator. */
public interface EnrollDevice {

    EnrollmentResult handle(EnrollDeviceCommand command);

    record EnrollDeviceCommand(String secret, String certificateSigningRequestPem) {}

    record EnrollmentResult(String certificatePem, String caChainPem, String fingerprint) {}
}
