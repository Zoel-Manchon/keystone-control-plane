package dev.zoel.keystone.domain.firmware;

public enum RolloutStatus {
    IN_PROGRESS,
    COMPLETED,
    /** Halted deliberately. Devices keep whatever they are running. */
    PAUSED,
    /** Reverted. Devices are told to go back to the previous version. */
    ROLLED_BACK
}
