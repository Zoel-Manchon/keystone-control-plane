package dev.zoel.keystone.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IssuedCertificateJpaRepository extends JpaRepository<IssuedCertificateEntity, String> {

    Optional<IssuedCertificateEntity> findByFingerprint(String fingerprint);

    List<IssuedCertificateEntity> findByRevokedAtIsNotNull();

    long countByRevokedAtIsNotNull();
}
