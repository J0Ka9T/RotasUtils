package net.schwarz.rotasutils.quest;

import net.minecraft.ChatFormatting;

/**
 * Quest danger ranks, F through SSS.
 *
 * <p>The values here are only the shipped defaults; the level manager UI overwrites
 * {@link net.schwarz.rotasutils.level.LevelConfig#rankLevel} and
 * {@link net.schwarz.rotasutils.level.LevelConfig#rankMultiplier} at runtime.
 */
public enum DangerRank {
    F("F", 1, 0.50f, ChatFormatting.GRAY, 0xFF9E9E9E),
    E("E", 3, 0.75f, ChatFormatting.WHITE, 0xFFE0E0E0),
    D("D", 5, 1.00f, ChatFormatting.GREEN, 0xFF55FF55),
    C("C", 10, 1.25f, ChatFormatting.AQUA, 0xFF55FFFF),
    B("B", 20, 1.75f, ChatFormatting.BLUE, 0xFF5577FF),
    A("A", 35, 2.50f, ChatFormatting.GOLD, 0xFFFFAA00),
    S("S", 50, 4.00f, ChatFormatting.RED, 0xFFFF5555),
    SS("SS", 70, 6.00f, ChatFormatting.LIGHT_PURPLE, 0xFFFF55FF),
    SSS("SSS", 90, 10.00f, ChatFormatting.DARK_PURPLE, 0xFFAA00AA);

    public static final DangerRank[] VALUES = values();

    private final String display;
    private final int defaultLevel;
    private final float defaultMultiplier;
    private final ChatFormatting color;
    private final int argb;

    DangerRank(String display, int defaultLevel, float defaultMultiplier, ChatFormatting color, int argb) {
        this.display = display;
        this.defaultLevel = defaultLevel;
        this.defaultMultiplier = defaultMultiplier;
        this.color = color;
        this.argb = argb;
    }

    public String display() {
        return display;
    }

    public int defaultLevel() {
        return defaultLevel;
    }

    public float defaultMultiplier() {
        return defaultMultiplier;
    }

    public ChatFormatting color() {
        return color;
    }

    public int argb() {
        return argb;
    }

    /** True when this rank needs the extra "are you sure" confirmation screen. */
    public boolean isHighDanger() {
        return ordinal() >= S.ordinal();
    }

    public static DangerRank byName(String name, DangerRank fallback) {
        for (DangerRank rank : VALUES) {
            if (rank.name().equalsIgnoreCase(name)) {
                return rank;
            }
        }
        return fallback;
    }
}
