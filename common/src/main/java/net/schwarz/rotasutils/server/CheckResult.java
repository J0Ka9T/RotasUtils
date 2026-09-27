package net.schwarz.rotasutils.server;

/**
 * Outcome of one requirement check.
 *
 * <p>{@code label} is the line shown in the requirement list; {@code detail} is the
 * explanation shown underneath when the check failed ("Your current Heavy Armor
 * Training rank is 1").
 */
public record CheckResult(boolean pass, boolean blocking, String label, String detail) {

    public static CheckResult pass(String label) {
        return new CheckResult(true, true, label, "");
    }

    public static CheckResult fail(String label, String detail) {
        return new CheckResult(false, true, label, detail);
    }

    /** A recommendation: rendered, never blocking. */
    public static CheckResult advisory(boolean met, String label, String detail) {
        return new CheckResult(met, false, label, detail);
    }
}
