package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.RegisterDevice;
import dev.zoel.keystone.application.port.in.RegisterDeviceCommand;
import dev.zoel.keystone.application.port.out.AuditTrail;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.audit.AuditAction;
import dev.zoel.keystone.domain.device.DuplicateSerialNumberException;

/**
 * Use case. No @Service, no @Transactional: this class does not know Spring exists.
 * Wiring happens in keystone-infrastructure/config/UseCaseConfiguration.
 */
public class RegisterDeviceUseCase implements RegisterDevice {

    private final DeviceRepository devices;
    private final AuditTrail audit;
    private final Clock clock;

    public RegisterDeviceUseCase(DeviceRepository devices, AuditTrail audit, Clock clock) {
        this.devices = devices;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Device handle(RegisterDeviceCommand command) {
        if (devices.existsBySerialNumber(command.serialNumber())) {
            throw new DuplicateSerialNumberException(command.serialNumber());
        }
        Device device = devices.save(
            Device.register(command.serialNumber(), command.model(), clock.now()));
        audit.record(AuditAction.DEVICE_REGISTERED, device.id().toString(),
            "serial=" + device.serialNumber() + " model=" + device.model());
        return device;
    }
}
