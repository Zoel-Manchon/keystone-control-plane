package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Secondary adapter: implements the port declared by the application layer. */
@Repository
public class JpaDeviceRepositoryAdapter implements DeviceRepository {

    private final DeviceJpaRepository jpa;

    JpaDeviceRepositoryAdapter(DeviceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Device save(Device device) {
        return DeviceMapper.toDomain(jpa.save(DeviceMapper.toEntity(device)));
    }

    @Override
    public Optional<Device> findById(DeviceId id) {
        return jpa.findById(id.value()).map(DeviceMapper::toDomain);
    }

    @Override
    public Optional<Device> findBySerialNumber(String serialNumber) {
        return jpa.findBySerialNumber(serialNumber).map(DeviceMapper::toDomain);
    }

    @Override
    public boolean existsBySerialNumber(String serialNumber) {
        return jpa.existsBySerialNumber(serialNumber);
    }

    @Override
    public List<Device> findAll() {
        return jpa.findAll().stream().map(DeviceMapper::toDomain).toList();
    }
}
