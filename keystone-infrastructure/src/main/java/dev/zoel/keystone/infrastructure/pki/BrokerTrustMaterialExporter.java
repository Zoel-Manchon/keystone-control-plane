package dev.zoel.keystone.infrastructure.pki;

import dev.zoel.keystone.application.port.out.CertificateAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Writes the material Mosquitto needs onto the shared volume.
 *
 * The broker cannot ask Keystone for anything at boot, so the control plane pushes:
 * the CA chain it must trust, its own server certificate and key, and the CRL.
 *
 * The CRL is refreshed on a schedule, and that interval is a security parameter, not
 * a performance one. A revocation only takes effect once the broker reloads the list,
 * so the refresh period is the worst-case window during which a revoked device can
 * still publish.
 */
@Component
public class BrokerTrustMaterialExporter {

    private static final Logger log = LoggerFactory.getLogger(BrokerTrustMaterialExporter.class);

    private final CertificateAuthority ca;
    private final Path certificateDirectory;
    private final String brokerCommonName;

    BrokerTrustMaterialExporter(
            CertificateAuthority ca,
            @Value("${keystone.mqtt.certificate-path:docker/mosquitto/certs}") String certificatePath,
            @Value("${keystone.mqtt.broker-common-name:localhost}") String brokerCommonName) {
        this.ca = ca;
        this.certificateDirectory = Path.of(certificatePath);
        this.brokerCommonName = brokerCommonName;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void exportOnStartup() {
        try {
            Files.createDirectories(certificateDirectory);

            // The broker certificate is issued once and reused. Re-issuing on every
            // restart would force a broker reload each time for no benefit.
            if (!Files.exists(certificateDirectory.resolve("broker.crt"))) {
                CertificateAuthority.ServerCertificate broker = ca.issueBrokerCertificate(
                    brokerCommonName, List.of(brokerCommonName, "mosquitto", "127.0.0.1"));

                write("broker.crt", broker.certificatePem(), false);
                write("broker.key", broker.privateKeyPem(), true);
                log.info("issued a broker certificate for CN={}", brokerCommonName);
            }

            write("ca-chain.pem", ca.caChainPem(), false);
            exportRevocationList();

        } catch (Exception e) {
            // A failure here must not stop the control plane: the console and the
            // enrolment API are still useful with the broker down.
            log.error("could not export the broker trust material: {}", e.getMessage());
        }
    }

    /** Every five minutes: that is the revocation lag the fleet lives with. */
    @Scheduled(fixedDelayString = "${keystone.mqtt.crl-refresh-ms:300000}")
    public void exportRevocationList() {
        try {
            write("keystone.crl", ca.currentRevocationList(), false);
        } catch (Exception e) {
            log.error("could not refresh the CRL: {}", e.getMessage());
        }
    }

    private void write(String fileName, String content, boolean ownerOnly) throws Exception {
        Path target = certificateDirectory.resolve(fileName);
        Files.writeString(target, content, StandardCharsets.UTF_8);

        if (ownerOnly) {
            try {
                // POSIX only. On Windows this is a no-op and the file inherits the
                // directory ACL, which is why production runs of this belong on Linux.
                Files.setPosixFilePermissions(target,
                    Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                           java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                log.warn("{} could not be restricted to the owner on this filesystem", fileName);
            }
        }
    }
}
