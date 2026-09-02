package dev.zoel.keystone.domain.firmware;

/**
 * Progressive exposure. A bad image reaching 5% of the fleet is an incident you
 * recover from; the same image reaching 100% is a truck roll to every site.
 */
public enum RolloutStage {

    CANARY(5),
    EARLY(25),
    FULL(100);

    private final int percentage;

    RolloutStage(int percentage) {
        this.percentage = percentage;
    }

    public int percentage() {
        return percentage;
    }

    public RolloutStage next() {
        return switch (this) {
            case CANARY -> EARLY;
            case EARLY -> FULL;
            case FULL -> throw new IllegalFirmwareStateException("FULL is the last stage");
        };
    }

    public boolean isFinal() {
        return this == FULL;
    }
}
