package dev.zoel.keystone.application.port.out;

import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port. Declared by the application layer, implemented by infrastructure.
 * That inversion is precisely what keeps the core independent of the database.
 */
public interface DeviceRepository {

    Device save(Device device);

    Optional<Device> findById(DeviceId id);

    Optional<Device> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);

    List<Device> findAll();
}
