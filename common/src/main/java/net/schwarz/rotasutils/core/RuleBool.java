package net.schwarz.rotasutils.core;

/** A boolean zone rule that can either inherit or explicitly override a value. */
public record RuleBool(boolean overridden, boolean value) {
    public static RuleBool inherit() { return new RuleBool(false, false); }
    public static RuleBool of(boolean value) { return new RuleBool(true, value); }
}
