package net.schwarz.rotasutils.core;

import java.util.Locale;

/**
 * How good a monster's drop is.
 *
 * <p>A grade is not an item the player has to open: it only decides which list of loot is rolled
 * when the monster dies. Ordinary ranks roll {@link #COMMON}, a miniboss {@link #COMMON} or
 * {@link #MEDIUM}, a boss {@link #RARE} or {@link #EPIC}; the rank rules in {@code season.json} set
 * that mapping.
 *
 * <p>Each grade also names the content-pack loot table an administrator may define to take its
 * contents over completely.
 */
public enum DropGrade {
    COMMON(0xFFB6A17C),
    MEDIUM(0xFF86C05C),
    RARE(0xFF5AA9E6),
    EPIC(0xFFB07CE8);

    private final int color;

    DropGrade(int color) {
        this.color = color;
    }

    /** Colour used when a grade is named in the interface. */
    public int color() {
        return color;
    }

    /** Lower-case name as it is written in {@code season.json} and in a content pack's table id. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The loot table id a pack may define to replace the configured contents of this grade. */
    public String tableId() {
        return "rotas:drop_" + key();
    }

    /** Translation key of the player-facing name. */
    public String nameKey() {
        return "rotasutils.drop.grade." + key();
    }

    /** Parses a name from a hand-edited config; anything unknown reads as {@link #COMMON}. */
    public static DropGrade byKey(String key) {
        if (key != null) {
            for (DropGrade grade : values()) {
                if (grade.key().equalsIgnoreCase(key)) {
                    return grade;
                }
            }
        }
        return COMMON;
    }
}
