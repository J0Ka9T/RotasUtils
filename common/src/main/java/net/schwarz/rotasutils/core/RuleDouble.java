package net.schwarz.rotasutils.core;

/** A numeric zone rule that can either inherit or explicitly override a finite value. */
public record RuleDouble(boolean overridden, double value) {
    public RuleDouble {
        if (overridden && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException("Zone rule value must be finite and nonnegative");
        }
    }
    public static RuleDouble inherit() { return new RuleDouble(false, 0); }
    public static RuleDouble of(double value) { return new RuleDouble(true, value); }
}
