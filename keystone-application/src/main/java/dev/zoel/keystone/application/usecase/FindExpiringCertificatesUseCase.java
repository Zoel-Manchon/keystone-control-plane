package dev.zoel.keystone.application.usecase;

import dev.zoel.keystone.application.port.in.FindExpiringCertificates;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class FindExpiringCertificatesUseCase implements FindExpiringCertificates {

    private final DeviceRepository devices;
    private final Clock clock;

    public FindExpiringCertificatesUseCase(DeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    @Override
    public List<Device> handle(Duration window) {
        Instant now = clock.now();
        return devices.findAll().stream()
            .filter(device -> device.status() == DeviceStatus.ACTIVE)
            .filter(device -> device.certificateExpiresWithin(window, now))
            .sorted(Comparator.comparing(device -> device.certificateExpiresAt().orElse(Instant.MAX)))
            .toList();
    }
}
