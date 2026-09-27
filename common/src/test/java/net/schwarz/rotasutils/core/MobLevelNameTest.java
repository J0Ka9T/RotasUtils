package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.hud.MobLevelName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobLevelNameTest {
    @Test void readsTheLevelFromTheDefaultTemplate() {
        assertEquals(12, MobLevelName.levelFrom("Zombie [Lv 12]", "{name} [Lv {level}]"));
        assertEquals(1, MobLevelName.levelFrom("Zombie [Lv 1]", "{name} [Lv {level}]"));
        assertEquals(10000, MobLevelName.levelFrom("Zombie [Lv 10000]", "{name} [Lv {level}]"));
    }

    @Test void readsTheLevelFromOtherTemplates() {
        assertEquals(7, MobLevelName.levelFrom("Lv 7 Skeleton", "Lv {level} {name}"));
        assertEquals(30, MobLevelName.levelFrom("Elite Warden Lv 30", "{tier} {name} Lv {level}"));
    }

    @Test void aNameWithoutALevelIsIgnored() {
        assertEquals(-1, MobLevelName.levelFrom("Zombie", "{name} [Lv {level}]"));
        assertEquals(-1, MobLevelName.levelFrom("", "{name} [Lv {level}]"));
        assertEquals(-1, MobLevelName.levelFrom("[Lv 5]", "{name} unlabeled"));
    }

    @Test void theDifficultyRampRunsGreyToRed() {
        assertEquals(MobLevelName.colorFor(-20), MobLevelName.colorFor(-8));
        assertTrue(MobLevelName.colorFor(0) != MobLevelName.colorFor(15));
        assertTrue(MobLevelName.colorFor(20) != MobLevelName.colorFor(4));
        assertTrue(MobLevelName.colorFor(10) != MobLevelName.colorFor(50));
    }

    @Test void healthFormattingStaysReadableAtBossScale() {
        assertEquals("950", MobLevelName.compact(950));
        assertEquals("12.4K", MobLevelName.compact(12_400));
        assertEquals("1.25M", MobLevelName.compact(1_250_000));
        assertEquals("12.4K / 15K", MobLevelName.health(12_400, 15_000));
    }
}
