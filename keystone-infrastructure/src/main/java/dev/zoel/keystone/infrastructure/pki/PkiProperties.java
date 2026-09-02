package dev.zoel.keystone.infrastructure.pki;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PKI settings.
 *
 * The keystore password is a configuration value on purpose: it must come from the
 * environment in any real deployment, never from a constant in the source tree.
 */
@ConfigurationProperties(prefix = "keystone.pki")
public class PkiProperties {

    /** Where the PKCS#12 keystore holding the CA private keys lives. */
    private String keystorePath = "data/keystone-ca.p12";

    private String keystorePassword = "changeit";

    private String rootSubject = "CN=Keystone Root CA, O=Keystone, C=ES";

    private String issuingSubject = "CN=Keystone Issuing CA, O=Keystone, C=ES";

    private int rootValidityYears = 10;

    private int issuingValidityYears = 5;

    /** Device certificates are short-lived on purpose: rotation limits the blast radius. */
    private int deviceCertificateValidityDays = 90;

    public String getKeystorePath() { return keystorePath; }
    public void setKeystorePath(String keystorePath) { this.keystorePath = keystorePath; }

    public String getKeystorePassword() { return keystorePassword; }
    public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }

    public String getRootSubject() { return rootSubject; }
    public void setRootSubject(String rootSubject) { this.rootSubject = rootSubject; }

    public String getIssuingSubject() { return issuingSubject; }
    public void setIssuingSubject(String issuingSubject) { this.issuingSubject = issuingSubject; }

    public int getRootValidityYears() { return rootValidityYears; }
    public void setRootValidityYears(int rootValidityYears) { this.rootValidityYears = rootValidityYears; }

    public int getIssuingValidityYears() { return issuingValidityYears; }
    public void setIssuingValidityYears(int issuingValidityYears) { this.issuingValidityYears = issuingValidityYears; }

    public int getDeviceCertificateValidityDays() { return deviceCertificateValidityDays; }
    public void setDeviceCertificateValidityDays(int days) { this.deviceCertificateValidityDays = days; }
}
