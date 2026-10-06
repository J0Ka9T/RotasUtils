package net.schwarz.rotasutils.core;

import java.util.Locale;

public enum MonsterRank {
    NORMAL(0xF3EBDD, 0), VETERAN(0x9FD4FF, 1), ELITE(0xF2C14E, 2), CHAMPION(0xFF6B3D, 3),
    MINIBOSS(0xD46CFF, 3), BOSS(0xFF3B5C, 4), WORLD_BOSS(0xFF9EEA, 5);

    private final int rgb;
    private final int stars;

    MonsterRank(int rgb, int stars) {
        this.rgb = rgb;
        this.stars = stars;
    }

    public int rgb() {
        return rgb;
    }

    public int stars() {
        return stars;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MonsterRank byName(String name) {
        for (MonsterRank rank : values()) {
            if (rank.name().equalsIgnoreCase(name)) return rank;
        }
        return NORMAL;
    }

    public static MonsterRank infer(ContentId tier, boolean boss) {
        String text = tier == null ? "" : tier.value().toLowerCase(Locale.ROOT);
        for (MonsterRank rank : values()) {
            if (text.contains(rank.name().toLowerCase(Locale.ROOT))) return rank;
        }
        return boss ? BOSS : NORMAL;
    }
}
