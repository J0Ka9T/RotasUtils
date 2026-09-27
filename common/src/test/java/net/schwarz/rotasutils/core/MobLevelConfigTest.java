package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.MobLevelConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobLevelConfigTest {
    @Test void naturalMaximumCanNeverExceedOneHundred() {
        MobLevelConfig config = new MobLevelConfig();
        config.setMaxLevel(999);
        assertEquals(100, config.maxLevel());
    }
    @Test void tamedAnimalsAndNpcNamespacesAreNeverLeveled() {
        MobLevelConfig config = new MobLevelConfig();
        assertFalse(config.shouldLevel("minecraft:wolf", Set.of(), "creature", true));
        assertFalse(config.shouldLevel("easy_npc:humanoid", Set.of(), "misc", false));
        assertFalse(config.shouldLevel("easynpc:humanoid", Set.of(), "misc", false));
        assertTrue(config.shouldLevel("minecraft:zombie", Set.of(), "monster", false));
    }

    @Test void theCategoryFilterNarrowsTheSetWhenSet() {
        MobLevelConfig config = new MobLevelConfig();
        assertTrue(config.shouldLevel("minecraft:pig", Set.of(), "creature", false));
        config.toggleCategory("monster");
        assertTrue(config.shouldLevel("minecraft:zombie", Set.of(), "monster", false));
        assertFalse(config.shouldLevel("minecraft:pig", Set.of(), "creature", false));
    }

    @Test void entityAndTagExclusionsAreHonoured() {
        MobLevelConfig config = new MobLevelConfig();
        assertFalse(config.shouldLevel("minecraft:villager", Set.of(), "misc", false));
        config.toggleExcludedTag("rotasutils:no_level");
        assertFalse(config.shouldLevel("minecraft:zombie", Set.of("rotasutils:no_level"), "monster", false));
        assertTrue(config.shouldLevel("minecraft:zombie", Set.of(), "monster", false));
    }

    @Test void disablingTheFeatureStopsEveryLevel() {
        MobLevelConfig config = new MobLevelConfig();
        config.setEnabled(false);
        assertFalse(config.shouldLevel("minecraft:zombie", Set.of(), "monster", false));
    }

    @Test void theNameFormatMustCarryTheLevelPlaceholder() {
        MobLevelConfig config = new MobLevelConfig();
        config.setNameFormat("Lv {level} {name}");
        assertEquals("Lv 12 Zombie", config.formatName("Zombie", 12));
        assertThrows(IllegalArgumentException.class, () -> config.setNameFormat("Mystery mob"));
        assertThrows(IllegalArgumentException.class, () -> config.setNameFormat("   "));
    }

    @Test void numericBoundsClampRatherThanThrow() {
        MobLevelConfig config = new MobLevelConfig();
        config.setSpawnLevel(0);
        assertEquals(1, config.spawnLevel());
        config.setSpawnLevel(50);
        config.setMaxLevel(10);
        assertEquals(50, config.maxLevel());
        config.setHealthPerLevel(Double.NaN);
        assertEquals(0, config.healthPerLevel());
    }

    @Test void configRoundTripsThroughNbt() {
        MobLevelConfig config = new MobLevelConfig();
        config.setSpawnLevel(5);
        config.setLevelPerBlocks(128);
        config.setMaxLevel(60);
        config.setBandSpread(7);
        config.setNameFormat("Lv {level} - {name}");
        config.toggleCategory("monster");
        config.toggleExcludedEntity("minecraft:bat");
        config.toggleExcludedTag("rotasutils:no_level");
        MobLevelConfig loaded = MobLevelConfig.load(config.save());
        assertEquals(5, loaded.spawnLevel());
        assertEquals(128, loaded.levelPerBlocks());
        assertEquals(60, loaded.maxLevel());
        assertEquals(7, loaded.bandSpread());
        assertEquals(3, MobLevelConfig.load(new net.minecraft.nbt.CompoundTag()).bandSpread());
        config.setBandSpread(-4);
        assertEquals(0, config.bandSpread());
        assertEquals("Lv {level} - {name}", loaded.nameFormat());
        assertTrue(loaded.categories().contains("MONSTER"));
        assertTrue(loaded.excludeEntities().contains("minecraft:bat"));
        assertTrue(loaded.excludeTags().contains("rotasutils:no_level"));
        assertEquals("Lv 7 - Zombie", loaded.formatName("Zombie", 7));
    }

    @Test void aStoredBadNameFormatFallsBackToTheDefault() {
        MobLevelConfig config = new MobLevelConfig();
        var tag = config.save();
        tag.putString("name_format", "no placeholder");
        assertEquals("{name} [Lv {level}]", MobLevelConfig.load(tag).nameFormat());
    }
}
