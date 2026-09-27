package net.schwarz.rotasutils.level;

/** Pure reward multipliers shared by combat previews and server awards. */
public final class AdventureXpMath {
    private AdventureXpMath() {
    }

    public static double challengeMultiplier(int playerLevel, int monsterLevel, AdventureXpConfig config) {
        int difference = monsterLevel - Math.max(1, playerLevel);
        if (difference >= 10) return config.dangerousCap();
        if (difference > 0) return 1.0 + (config.dangerousCap() - 1.0) * difference / 10.0;
        if (difference <= -10) return config.trivialFloor();
        return 1.0 - (1.0 - config.trivialFloor()) * -difference / 10.0;
    }

    public static double repetitionMultiplier(int recentKills, AdventureXpConfig config) {
        if (recentKills <= 0) return 1.0;
        return Math.max(config.repetitionFloor(), 1.0 - recentKills * config.repetitionStep());
    }

    /**
     * Remembered kills after decay: one kill is forgiven per {@code windowTicks} of play time since
     * the last kill. A clock that went backwards forgives nothing rather than everything.
     */
    public static int decayedKills(int count, long lastTick, long nowTick, long windowTicks) {
        if (count <= 0) return 0;
        if (windowTicks <= 0 || nowTick <= lastTick) return count;
        long forgiven = (nowTick - lastTick) / windowTicks;
        return (int) Math.max(0, count - Math.min(count, forgiven));
    }

    public static long finalAward(long baseXp, double zone, double challenge, double contribution,
                                  double repetition, long maximum) {
        if (baseXp <= 0 || maximum <= 0 || !finite(zone, challenge, contribution, repetition)) return 0;
        double calculated = baseXp * Math.max(0, zone) * Math.max(0, challenge)
                * Math.max(0, Math.min(1, contribution)) * Math.max(0, repetition);
        if (!Double.isFinite(calculated) || calculated >= maximum) return maximum;
        return Math.max(0, Math.round(calculated));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
