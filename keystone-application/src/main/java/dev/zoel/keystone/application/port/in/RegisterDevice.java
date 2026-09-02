package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.device.Device;

/** Inbound port: what the outside world may ask the application to do. */
public interface RegisterDevice {
    Device handle(RegisterDeviceCommand command);
}
