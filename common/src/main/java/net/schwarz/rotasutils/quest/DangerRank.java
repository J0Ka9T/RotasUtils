package net.schwarz.rotasutils.quest;

import net.minecraft.ChatFormatting;

public enum DangerRank {
    F("F", 1, ChatFormatting.GRAY, 0xFF9E9E9E),
    E("E", 3, ChatFormatting.WHITE, 0xFFE0E0E0),
    D("D", 5, ChatFormatting.GREEN, 0xFF55FF55),
    C("C", 10, ChatFormatting.AQUA, 0xFF55FFFF),
    B("B", 20, ChatFormatting.BLUE, 0xFF5577FF),
    A("A", 35, ChatFormatting.GOLD, 0xFFFFAA00),
    S("S", 50, ChatFormatting.RED, 0xFFFF5555),
    SS("SS", 70, ChatFormatting.LIGHT_PURPLE, 0xFFFF55FF),
    SSS("SSS", 90, ChatFormatting.DARK_PURPLE, 0xFFAA00AA);

    public static final DangerRank[] VALUES = values();

    private final String display;
    private final int defaultLevel;
    private final ChatFormatting color;
    private final int argb;

    DangerRank(String display, int defaultLevel, ChatFormatting color, int argb) {
        this.display = display;
        this.defaultLevel = defaultLevel;
        this.color = color;
        this.argb = argb;
    }

    public String display() {
        return display;
    }

    public int defaultLevel() {
        return defaultLevel;
    }

    public ChatFormatting color() {
        return color;
    }

    public int argb() {
        return argb;
    }

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
