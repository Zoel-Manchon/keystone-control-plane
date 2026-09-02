-- Keystone :: bind certificate rotation to possession of the CURRENT private key
--
-- Older rows remain nullable so this migration is safe on an existing development DB.
-- Certificates issued after this migration persist their public certificate and can
-- prove possession during rotation. Legacy rows must be re-enrolled before rotation.

ALTER TABLE issued_certificates ADD COLUMN certificate_pem TEXT;

ALTER TABLE issued_certificates ADD CONSTRAINT issued_certificates_pem_format CHECK (
    certificate_pem IS NULL OR certificate_pem LIKE '-----BEGIN CERTIFICATE-----%'
);
