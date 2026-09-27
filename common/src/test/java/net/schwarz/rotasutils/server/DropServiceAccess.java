package net.schwarz.rotasutils.server;

import net.minecraft.util.RandomSource;
import net.schwarz.rotasutils.level.SeasonRules;

/** Test door to DropService's package-private coin roll. */
public final class DropServiceAccess {
    private DropServiceAccess() {
    }

    public static long coins(int level, SeasonRules.RankDrop rule, RandomSource random) {
        return DropService.coins(level, rule, random);
    }
}
