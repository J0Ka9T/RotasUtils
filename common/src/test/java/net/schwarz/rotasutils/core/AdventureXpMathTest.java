package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.AdventureXpConfig;
import net.schwarz.rotasutils.level.AdventureXpMath;
import net.schwarz.rotasutils.level.LevelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdventureXpMathTest {
    private final AdventureXpConfig config = new AdventureXpConfig();

    @Test void challengeRewardsDangerAndTapersTrivialKills() {
        assertEquals(0.10, AdventureXpMath.challengeMultiplier(50, 10, config), 0.001);
        assertEquals(1.0, AdventureXpMath.challengeMultiplier(20, 20, config), 0.001);
        assertEquals(1.75, AdventureXpMath.challengeMultiplier(1, 50, config), 0.001);
    }

    @Test void repetitionDecaysToABoundedFloor() {
        assertEquals(1.0, AdventureXpMath.repetitionMultiplier(0, config), 0.001);
        assertEquals(0.8, AdventureXpMath.repetitionMultiplier(1, config), 0.001);
        assertEquals(0.2, AdventureXpMath.repetitionMultiplier(100, config), 0.001);
    }

    @Test void rememberedKillsDecayWithPlayTimeNotForever() {
        assertEquals(4, AdventureXpMath.decayedKills(4, 1000, 1000, 6000));
        assertEquals(3, AdventureXpMath.decayedKills(4, 1000, 7000, 6000));
        assertEquals(0, AdventureXpMath.decayedKills(4, 0, 1_000_000, 6000));
        assertEquals(4, AdventureXpMath.decayedKills(4, 9000, 100, 6000), "a clock that went back forgives nothing");
        assertEquals(0, AdventureXpMath.decayedKills(0, 0, 0, 6000));
        LevelConfig legacy = LevelConfig.load(new net.minecraft.nbt.CompoundTag());
        assertEquals(6000, legacy.adventureXp().repetitionWindowTicks());
    }

    @Test void finalAwardSaturatesAtConfiguredMaximum() {
        assertEquals(5_000, AdventureXpMath.finalAward(10_000, 10, 1.75, 1, 1, 5_000));
        assertEquals(0, AdventureXpMath.finalAward(100, Double.NaN, 1, 1, 1, 5_000));
    }

    @Test void levelConfigPersistsAdventureTuningAndDefaultsLegacyData() {
        LevelConfig legacy = LevelConfig.load(new net.minecraft.nbt.CompoundTag());
        assertEquals(150, legacy.adventureXp().discoveryXp());
        LevelConfig loaded = LevelConfig.load(legacy.save());
        assertEquals(1000, loaded.adventureXp().distanceMilestoneBlocks());
    }
}
