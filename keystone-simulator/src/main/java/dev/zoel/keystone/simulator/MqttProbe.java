package dev.zoel.keystone.simulator;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Connects to Mosquitto with the certificate Keystone issued, over mutual TLS.
 *
 * This is what makes the PKI real. Up to here Keystone was issuing certificates that
 * nothing enforced; here a broker refuses the connection unless the chain validates
 * and the certificate is absent from the CRL.
 *
 * The keystore is assembled in memory. Writing a private key to a temporary file just
 * to hand it to a TLS stack is a habit worth not acquiring.
 */
final class MqttProbe {

    private final String brokerUrl;

    MqttProbe(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    /**
     * Attempts a connect-and-publish. Returns the outcome rather than throwing,
     * because for a revoked device a REFUSED connection is the passing result.
     */
    ProbeResult publish(String deviceId, String certificatePem, PrivateKey privateKey,
                        String caChainPem, String payload) {
        // Paho's MqttClient is not AutoCloseable, so the lifecycle is managed by hand.
        MqttClient client = null;
        try {
            SSLContext ssl = buildContext(certificatePem, privateKey, caChainPem);

            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setSocketFactory(ssl.getSocketFactory());
            options.setCleanStart(true);
            options.setConnectionTimeout(10);
            options.setAutomaticReconnect(false);

            client = new MqttClient(brokerUrl, deviceId, new MemoryPersistence());
            client.connect(options);
            // The ACL confines the device to devices/<its own id>/#. Publishing
            // anywhere else is refused even though the certificate is valid.
            client.publish("devices/" + deviceId + "/telemetry",
                new MqttMessage(payload.getBytes(StandardCharsets.UTF_8), 1, false, null));
            client.disconnect();

            return new ProbeResult(true, "connected and published");

        } catch (Exception e) {
            return new ProbeResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());

        } finally {
            // close() releases the persistence layer and Paho's internal threads.
            // Skipping it leaks a thread per probe: invisible with three runs, very
            // visible with a few hundred.
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                    // Closing a client that never connected is not worth reporting.
                }
            }
        }
    }

    private SSLContext buildContext(String certificatePem, PrivateKey privateKey, String caChainPem)
            throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        X509Certificate deviceCertificate = (X509Certificate) factory.generateCertificate(
            new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.UTF_8)));

        List<X509Certificate> chain = factory.generateCertificates(
                new ByteArrayInputStream(caChainPem.getBytes(StandardCharsets.UTF_8)))
            .stream().map(X509Certificate.class::cast).toList();

        // Trust store: only the Keystone CA. Not the JDK default trust store, which
        // would accept any public CA and defeat the point of running our own.
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        for (int i = 0; i < chain.size(); i++) {
            trust.setCertificateEntry("ca-" + i, chain.get(i));
        }

        KeyStore identity = KeyStore.getInstance("PKCS12");
        identity.load(null, null);
        char[] password = "in-memory".toCharArray();
        identity.setKeyEntry("device", privateKey, password, new X509Certificate[]{deviceCertificate});

        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(identity, password);

        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trust);

        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return context;
    }

    record ProbeResult(boolean connected, String detail) {}
}