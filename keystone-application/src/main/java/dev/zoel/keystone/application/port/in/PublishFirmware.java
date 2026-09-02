package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.firmware.FirmwareArtifact;

public interface PublishFirmware {

    FirmwareArtifact handle(PublishFirmwareCommand command);

    record PublishFirmwareCommand(String version, String model, byte[] content) {}
}
