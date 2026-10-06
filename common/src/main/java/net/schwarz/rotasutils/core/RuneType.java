package net.schwarz.rotasutils.core;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum RuneType {
    FIRE(ChatFormatting.RED, 0.20, 4),
    FROST(ChatFormatting.AQUA, 0.25, 3),
    VENOM(ChatFormatting.DARK_GREEN, 0.20, 4),
    LIFESTEAL(ChatFormatting.DARK_RED, 0.06, 0),
    FURY(ChatFormatting.GOLD, 0.12, 0);

    public static final double FURY_MULTIPLIER = 1.5;
    public static final int FUSE_COUNT = 3;
    private static final long[] FUSE_COST = {300, 1500};

    public static long fuseCost(int tier) {
        return tier >= 1 && tier <= FUSE_COST.length ? FUSE_COST[tier - 1] : 0;
    }

    private final ChatFormatting color;
    private final double perRune;
    private final int seconds;

    RuneType(ChatFormatting color, double perRune, int seconds) {
        this.color = color;
        this.perRune = perRune;
        this.seconds = seconds;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String itemPath() {
        return "rune_" + id();
    }

    public ChatFormatting color() {
        return color;
    }

    public double strength(int copies) {
        return Math.min(1.0, perRune * Math.max(0, copies));
    }

    public int seconds() {
        return seconds;
    }

    public static RuneType byId(String id) {
        if (id == null) {
            return null;
        }
        for (RuneType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static int slots(int refineLevel, int[] thresholds) {
        int slots = 0;
        if (thresholds != null) {
            for (int threshold : thresholds) {
                if (refineLevel >= threshold) {
                    slots++;
                }
            }
        }
        return slots;
    }
}
