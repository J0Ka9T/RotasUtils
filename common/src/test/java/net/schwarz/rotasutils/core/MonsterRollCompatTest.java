package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.compat.MonsterRollCompat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterRollCompatTest {
    @SuppressWarnings("unused")
    private static final class RakothRollGoal {
    }

    @SuppressWarnings("unused")
    private static final class MeleeAttackGoal {
    }

    @Test void onlyMonsterExpansionMobsAreTouched() {
        assertTrue(MonsterRollCompat.handles("net.saksolm.monsterexpansion.entity.custom.RakothEntity"));
        assertFalse(MonsterRollCompat.handles("net.minecraft.world.entity.monster.Zombie"));
        assertFalse(MonsterRollCompat.handles("com.example.other.RakothEntity"));
        assertFalse(MonsterRollCompat.handles(null));
    }

    @Test void rollGoalsAreRecognisedAndOrdinaryGoalsAreNot() {
        assertTrue(MonsterRollCompat.isRollGoal(RakothRollGoal.class));
        assertFalse(MonsterRollCompat.isRollGoal(MeleeAttackGoal.class));
        assertFalse(MonsterRollCompat.isRollGoal(null));
    }
}
