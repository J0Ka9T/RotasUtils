package net.schwarz.rotasutils.core;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/**
 * The runes a weapon can carry. Each one does one thing on a landed melee hit, and a second copy of
 * the same rune on the same weapon doubles its chance or strength, so a three-slot +10 can commit to
 * one idea or mix three.
 *
 * <p>The numbers are fixed here rather than in season rules so the tooltip, the altar and combat read
 * the same table on both sides without a sync.</p>
 */
public enum RuneType {
    /** Chance to set the target alight. */
    FIRE(ChatFormatting.RED, 0.20, 4),
    /** Chance to slow the target (Slowness II). */
    FROST(ChatFormatting.AQUA, 0.25, 3),
    /** Chance to poison the target (Poison I). */
    VENOM(ChatFormatting.DARK_GREEN, 0.20, 4),
    /** Heals the wielder for a share of the damage dealt; the per-rune value is that share. */
    LIFESTEAL(ChatFormatting.DARK_RED, 0.06, 0),
    /** Chance for a hit to deal {@link #FURY_MULTIPLIER} times its damage. */
    FURY(ChatFormatting.GOLD, 0.12, 0);

    public static final double FURY_MULTIPLIER = 1.5;
    /** Runes of one tier fused at the altar into one rune of the next. */
    public static final int FUSE_COUNT = 3;
    /** Gold to fuse tier 1 into 2, and tier 2 into 3. */
    private static final long[] FUSE_COST = {300, 1500};

    /** Gold to fuse runes of {@code tier} into the next tier; 0 when there is no next tier. */
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

    /** Registry path of this rune's item, e.g. {@code rune_fire}. */
    public String itemPath() {
        return "rune_" + id();
    }

    public ChatFormatting color() {
        return color;
    }

    /** Chance (or, for lifesteal, heal share) with {@code copies} of this rune on one weapon, at most 1. */
    public double strength(int copies) {
        return Math.min(1.0, perRune * Math.max(0, copies));
    }

    /** How long the effect lasts on the target; 0 for runes that act on the hit itself. */
    public int seconds() {
        return seconds;
    }

    /** The rune with this id, or null. */
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

    /**
     * How many rune slots a weapon at {@code refineLevel} has: one per threshold it has reached.
     * {@code {4, 7, 10}} gives slots at +4, +7 and +10.
     */
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
