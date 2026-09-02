package dev.zoel.keystone.infrastructure.pki;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/** The two CA identities held in memory once the keystore has been opened. */
record CaMaterial(PrivateKey rootKey, X509Certificate rootCertificate,
                  PrivateKey issuingKey, X509Certificate issuingCertificate) {
}
