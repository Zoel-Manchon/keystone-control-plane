package dev.zoel.keystone.domain.device;

public enum DeviceStatus {
    /** Registered in the inventory but not yet issued a certificate. */
    PENDING_ENROLLMENT,
    /** Enrolled with a valid certificate. Allowed to publish. */
    ACTIVE,
    /** Certificate revoked. Rejected by the broker. */
    REVOKED,
    /** Permanently withdrawn from service. */
    DECOMMISSIONED
}
