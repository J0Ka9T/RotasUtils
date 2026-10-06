package net.schwarz.rotasutils.core;

public record RuleBool(boolean overridden, boolean value) {
    public static RuleBool inherit() { return new RuleBool(false, false); }
    public static RuleBool of(boolean value) { return new RuleBool(true, value); }
}
