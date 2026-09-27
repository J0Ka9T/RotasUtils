package net.schwarz.rotasutils.level;

import net.schwarz.rotasutils.progress.PlayerProgress;
import java.util.function.IntConsumer;

public final class ProgressionMath {
    private ProgressionMath() { }

    public static long add(long value, long amount) {
        if (value < 0 || amount < 0) { throw new IllegalArgumentException("XP cannot be negative"); }
        return amount > Long.MAX_VALUE - value ? Long.MAX_VALUE : value + amount;
    }

    public static long scale(long amount, double multiplier) {
        if (amount < 0 || !Double.isFinite(multiplier) || multiplier < 0) {
            throw new IllegalArgumentException("XP requires a finite nonnegative multiplier");
        }
        return Math.round(amount * multiplier);
    }

    public static int award(PlayerProgress progress, LevelCurve curve, long amount, IntConsumer levelReached) {
        if (amount <= 0) { return 0; }
        progress.setXp(add(progress.xp(), amount));
        progress.addTotalXp(amount);
        int gained = 0;
        while (progress.level() < curve.maxLevel()) {
            long needed = curve.xpToNext(progress.level());
            if (needed == Long.MAX_VALUE || progress.xp() < needed) { break; }
            progress.setXp(progress.xp() - needed);
            progress.setLevel(progress.level() + 1);
            gained++;
            levelReached.accept(progress.level());
            if (gained >= 10000) { throw new IllegalStateException("Level callback reversed progression repeatedly"); }
        }
        if (progress.level() >= curve.maxLevel()) { progress.setXp(0); }
        return gained;
    }
}
