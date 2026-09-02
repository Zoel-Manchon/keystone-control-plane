package dev.zoel.keystone.integration;

import dev.zoel.keystone.application.port.out.CertificateAuthority;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DeviceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CA itself, validated with the JDK's own path validator rather than by trusting
 * that BouncyCastle did the right thing. If the chain does not satisfy PKIX, it does
 * not matter how nice the certificate looks in the console.
 */
class CertificateAuthorityIT extends AbstractIntegrationTest {

    @Autowired
    private CertificateAuthority ca;

    @Autowired
    private DeviceRepository devices;

    /**
     * A certificate belongs to a device: issued_certificates carries a foreign key to
     * devices. Signing for an id that was never registered is not a shortcut, it is a
     * row the schema refuses — so these tests register the device first, exactly like
     * the enrolment path does.
     */
    private DeviceId registeredDevice(String serialNumber) {
        Device device = Device.register(serialNumber, "SIM-ESP32-S3", Instant.now());
        devices.save(device);
        return device.id();
    }

    /** Serial numbers are UNIQUE in the schema, so tests that do not care about the
     *  value still need distinct ones. */
    private static final AtomicInteger SERIAL_COUNTER = new AtomicInteger();

    private static String nextSerial() {
        return "SN-CA-" + SERIAL_COUNTER.incrementAndGet();
    }

    @Test
    @DisplayName("an issued certificate validates as a PKIX path up to the root")
    void issuedCertificateFormsAValidPath() throws Exception {
        DeviceId deviceId = registeredDevice("SN-CA-PATH");
        var issued = ca.signCertificateRequest(deviceId, TestCsr.generate().pem("device"));

        List<X509Certificate> chain = parseAll(ca.caChainPem());
        X509Certificate leaf = parse(issued.certificatePem());
        X509Certificate root = chain.get(chain.size() - 1);

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        var path = factory.generateCertPath(List.of(leaf, chain.get(0)));

        PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        // Revocation checking is exercised separately; here the question is only
        // whether the signatures and the constraints chain correctly.
        parameters.setRevocationEnabled(false);

        // Throws if the path is invalid, which is the assertion.
        CertPathValidator.getInstance("PKIX").validate(path, parameters);

        assertThat(leaf.getSubjectX500Principal().getName()).contains(deviceId.toString());
    }

    @Test
    @DisplayName("the issuing CA may not sign another CA")
    void issuingCaCannotSignAnotherCa() throws Exception {
        List<X509Certificate> chain = parseAll(ca.caChainPem());

        // pathLen 0 on the issuing CA: end-entity certificates and nothing else.
        assertThat(chain.get(0).getBasicConstraints()).isZero();
        // pathLen 1 on the root: it may sign one CA below it.
        assertThat(chain.get(chain.size() - 1).getBasicConstraints()).isEqualTo(1);
    }

    @Test
    @DisplayName("a revoked certificate appears in the CRL by serial")
    void revokedCertificateEntersTheCrl() throws Exception {
        var issued = ca.signCertificateRequest(registeredDevice(nextSerial()), TestCsr.generate().pem("device"));

        ca.revokeByFingerprint(issued.sha256Fingerprint(), Instant.now());

        X509CRL crl = (X509CRL) CertificateFactory.getInstance("X.509")
            .generateCRL(new ByteArrayInputStream(
                ca.currentRevocationList().getBytes(StandardCharsets.UTF_8)));

        assertThat(crl.isRevoked(parse(issued.certificatePem()))).isTrue();
    }

    @Test
    @DisplayName("a certificate that was never revoked stays out of the CRL")
    void healthyCertificateStaysOutOfTheCrl() throws Exception {
        var issued = ca.signCertificateRequest(registeredDevice(nextSerial()), TestCsr.generate().pem("device"));

        X509CRL crl = (X509CRL) CertificateFactory.getInstance("X.509")
            .generateCRL(new ByteArrayInputStream(
                ca.currentRevocationList().getBytes(StandardCharsets.UTF_8)));

        assertThat(crl.isRevoked(parse(issued.certificatePem()))).isFalse();
    }

    @Test
    @DisplayName("the broker certificate carries serverAuth and the right SANs")
    void brokerCertificateIsAServerCertificate() throws Exception {
        var broker = ca.issueBrokerCertificate("localhost", List.of("localhost", "mosquitto"));
        X509Certificate certificate = parse(broker.certificatePem());

        // serverAuth, not clientAuth: reusing a device certificate on the broker would
        // let any device impersonate it and harvest the whole fleet's traffic.
        assertThat(certificate.getExtendedKeyUsage()).containsExactly("1.3.6.1.5.5.7.3.1");
        assertThat(certificate.getSubjectAlternativeNames()).isNotEmpty();
        assertThat(certificate.getBasicConstraints()).isEqualTo(-1);
    }

    @Test
    @DisplayName("two certificates never share a serial number")
    void serialNumbersAreUnpredictableAndUnique() throws Exception {
        var first = ca.signCertificateRequest(registeredDevice("SN-CA-SERIAL-A"), TestCsr.generate().pem("a"));
        var second = ca.signCertificateRequest(registeredDevice("SN-CA-SERIAL-B"), TestCsr.generate().pem("b"));

        assertThat(first.serialNumber()).isNotEqualTo(second.serialNumber());
        // 128 bits of randomness: a sequential serial has enabled real attacks
        // against certificate systems.
        assertThat(first.serialNumber().length()).isGreaterThan(24);
    }

    private static X509Certificate parse(String pem) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<X509Certificate> parseAll(String pem) throws Exception {
        return CertificateFactory.getInstance("X.509")
            .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)))
            .stream().map(X509Certificate.class::cast).toList();
    }
}
