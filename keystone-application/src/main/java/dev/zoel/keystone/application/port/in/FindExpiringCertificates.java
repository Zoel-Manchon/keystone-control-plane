package dev.zoel.keystone.application.port.in;

import dev.zoel.keystone.domain.device.Device;

import java.time.Duration;
import java.util.List;

/** Feeds the expiry warnings on the console and the scheduled sweep. */
public interface FindExpiringCertificates {
    List<Device> handle(Duration window);
}
