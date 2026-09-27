package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.ServerSettings;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.level.MobLevelConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the save paths that used to lose settings silently.
 *
 * <p>The admin screens save by shipping a config's NBT to the server, which reloads it and stores
 * the result. Any field missing from that round trip is editable in the UI and then quietly
 * discarded - which is exactly what happened to the waystone costs and the monster-drop toggle.</p>
 */
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
        // A new field added to save() but not to load() (or the reverse) loses its value on save.
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
        // A villager is MobCategory.MISC, so the summon/golem rule still covers it until that is
        // turned off too - the point here is only that the entity exclusion itself stayed removed.
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

    @Test void summonsAndPetsAreNeverLeveled() {
        MobLevelConfig config = new MobLevelConfig();
        // MISC covers summoned weapons, golems and other non-creature entities.
        assertFalse(config.shouldLevel("efn:sin_summoned_sword", Set.of(), "misc", false));
        assertTrue(config.shouldLevel("minecraft:zombie", Set.of(), "monster", false));
    }
}
