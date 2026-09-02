package dev.zoel.keystone.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {

    Optional<DeviceEntity> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);
}
