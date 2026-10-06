package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.ServerSettings;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.level.MobLevelConfig;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.reward.RewardType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigRoundTripTest {
    @Test void everyServerSettingSurvivesTheSaveRoundTrip() {
        ServerSettings edited = new ServerSettings();
        edited.setWaystonesEnabled(false);
        edited.setWaystoneDiscoverCost(777);
        edited.setWaystoneWarpCost(321);
        edited.setWaystoneWarpCostPerThousandBlocks(99);
        edited.setMonsterDropsEnabled(false);
        edited.setMaxPartySize(7);

        ServerSettings stored = ServerSettings.load(edited.save());

        assertFalse(stored.waystonesEnabled(), "waystone toggle must survive a save");
        assertEquals(777, stored.waystoneDiscoverCost());
        assertEquals(321, stored.waystoneWarpCost());
        assertEquals(99, stored.waystoneWarpCostPerThousandBlocks());
        assertFalse(stored.monsterDropsEnabled(), "monster drop toggle must survive a save");
        assertEquals(7, stored.maxPartySize());
    }

    @Test void serverSettingsSaveAndLoadCoverTheSameKeys() {
        CompoundTag fresh = new ServerSettings().save();
        CompoundTag reloaded = ServerSettings.load(fresh).save();
        assertEquals(fresh.getAllKeys(), reloaded.getAllKeys());
        for (String key : fresh.getAllKeys()) {
            assertEquals(fresh.get(key), reloaded.get(key), "setting changed across a round trip: " + key);
        }
    }

    @Test void levelConfigClampsXpValuesComingFromASavePacket() {
        CompoundTag hostile = new CompoundTag();
        hostile.putDouble("monster_xp_scale", -5.0);
        hostile.putDouble("boss_xp_mult", 1e9);
        hostile.putDouble("modded_mob_xp_mult", Double.NaN);

        LevelConfig config = LevelConfig.load(hostile);

        assertTrue(config.monsterXpScale() >= 0.01, "negative XP scale must be clamped");
        assertTrue(config.bossXpMultiplier() <= 100.0, "boss multiplier must be clamped");
        assertTrue(Double.isFinite(config.moddedMobXpMultiplier()), "NaN must never reach the XP economy");
    }

    @Test void removingADefaultExclusionSticksAcrossASave() {
        MobLevelConfig config = new MobLevelConfig();
        assertTrue(config.excludeEntities().contains("minecraft:villager"));
        config.toggleExcludedEntity("minecraft:villager");

        MobLevelConfig reloaded = MobLevelConfig.load(config.save());

        assertFalse(reloaded.excludeEntities().contains("minecraft:villager"),
                "a removed default must not come back on the next load");
        reloaded.setSkipMisc(false);
        assertTrue(reloaded.shouldLevel("minecraft:villager", Set.of(), "misc", false));
    }

    @Test void namespaceExclusionsAreConfigurable() {
        MobLevelConfig config = new MobLevelConfig();
        assertFalse(config.shouldLevel("easy_npc:humanoid", Set.of(), "misc", false));
        config.toggleExcludedNamespace("easy_npc");
        config.setSkipMisc(false);
        assertTrue(config.shouldLevel("easy_npc:humanoid", Set.of(), "misc", false));

        config.toggleExcludedNamespace("mymod");
        assertFalse(MobLevelConfig.load(config.save())
                .shouldLevel("mymod:guard", Set.of(), "monster", false));
    }

    @Test void monsterXpWeightsDriveTheThreatScore() {
        net.schwarz.rotasutils.level.MonsterXpWeights weights =
                new net.schwarz.rotasutils.level.MonsterXpWeights();
        double base = weights.threat(20, 3, 0, 0, 0.2, 0);
        weights.setDamage(weights.damage() * 2);
        assertTrue(weights.threat(20, 3, 0, 0, 0.2, 0) > base,
                "raising the damage weight must raise the score");

        weights.setHealth(Double.NaN);
        assertTrue(Double.isFinite(weights.health()), "NaN must never reach the threat score");

        var reloaded = net.schwarz.rotasutils.level.MonsterXpWeights.load(weights.save());
        assertEquals(weights.damage(), reloaded.damage());
        assertEquals(weights.tierThreeMultiplier(), reloaded.tierThreeMultiplier());
    }

    @Test void oldNbtWithRankMultKeyLoadsWithoutErrorAndRankLevelSurvives() {
        CompoundTag ranks = new CompoundTag();
        for (DangerRank rank : DangerRank.VALUES) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("level", rank.defaultLevel() + 5);
            entry.putFloat("mult", 999f);
            entry.putInt("quests", 3);
            entry.putString("promotion", "");
            entry.putBoolean("auto", true);
            ranks.put(rank.name(), entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("ranks", ranks);

        LevelConfig config = LevelConfig.load(tag);

        assertEquals(DangerRank.S.defaultLevel() + 5, config.rankLevel(DangerRank.S),
                "rank level must survive a load from old NBT that contains a stale 'mult' key");
    }

    @Test void rotasXpRewardTypeHasNoScaleWithRankParam() {
        boolean hasScaleWithRank = RewardType.ROTAS_XP.specs().stream()
                .anyMatch(spec -> spec.key().equals("scale_with_rank"));
        assertFalse(hasScaleWithRank,
                "ROTAS_XP must not expose a scale_with_rank param — rank no longer multiplies XP");
    }

    @Test void summonsAndPetsAreNeverLeveled() {
        MobLevelConfig config = new MobLevelConfig();
        assertFalse(config.shouldLevel("efn:sin_summoned_sword", Set.of(), "misc", false));
        assertTrue(config.shouldLevel("minecraft:zombie", Set.of(), "monster", false));
    }
}
