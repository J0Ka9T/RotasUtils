package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.level.LevelCurve;
import net.schwarz.rotasutils.level.ProgressionMath;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.progress.RpgProfile;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    @Test void multiLevelGainHonorsEveryCostAndCallbackAndCap() {
        var curve = new LevelCurve();
        curve.set(100, 0, 5);
        var player = new PlayerProgress(UUID.randomUUID());
        List<Integer> reached = new ArrayList<>();
        assertEquals(3, ProgressionMath.award(player, curve, 350, reached::add));
        assertEquals(List.of(2, 3, 4), reached);
        assertEquals(50, player.xp());
        assertEquals(350, player.totalXp());
        assertEquals(1, ProgressionMath.award(player, curve, Long.MAX_VALUE, reached::add));
        assertEquals(5, player.level()); assertEquals(0, player.xp());
        assertEquals(Long.MAX_VALUE, player.totalXp());
        assertEquals(0, ProgressionMath.award(player, curve, 20, reached::add));
    }

    @Test void saturationAndInvalidXpNeverWrapOrMintExperience() {
        assertEquals(Long.MAX_VALUE, ProgressionMath.add(Long.MAX_VALUE - 1, 10));
        for (double multiplier : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> ProgressionMath.scale(100, multiplier));
        }
        assertEquals(0, ProgressionMath.scale(100, 0));
        var player = new PlayerProgress(UUID.randomUUID());
        assertEquals(0, ProgressionMath.award(player, new LevelCurve(), -10, n -> fail()));
        player.addSkillPoints(Integer.MAX_VALUE); player.addSkillPoints(1);
        assertEquals(Integer.MAX_VALUE, player.skillPoints());
        assertEquals(Integer.MAX_VALUE, player.totalPointsEarned());
    }

    @Test void cumulativeCurveCannotWrapAndMalformedNumbersAreClamped() {
        var curve = new LevelCurve();
        curve.set(1e9, 4, Integer.MAX_VALUE);
        assertEquals(10000, curve.maxLevel());
        assertEquals(Long.MAX_VALUE, curve.totalXpTo(10000));
        curve.set(Double.NaN, Double.NaN, 100);
        assertEquals(25, curve.xpToNext(1));
    }

    @Test void profileMutationIsBoundedAndRejectsInsufficientFunds() {
        var profile = new RpgProfile(() -> { });
        profile.currency("rotas:gold", 100);
        assertThrows(IllegalArgumentException.class, () -> profile.currency("rotas:gold", -101));
        assertEquals(100, profile.currency("rotas:gold"));
        assertThrows(IllegalArgumentException.class, () -> profile.currency("rotas:gold", Long.MAX_VALUE - 100));
        profile.reputation("rotas:empire", -50);
        assertEquals(-50, profile.reputation("rotas:empire"));
        profile.addStatPoints(3); profile.allocate("rotas:strength", 2);
        assertEquals(1, profile.statPoints()); assertEquals(2, profile.stats().get("rotas:strength"));
        assertThrows(IllegalArgumentException.class, () -> profile.allocate("rotas:strength", 2));
        assertThrows(IllegalArgumentException.class, () -> profile.stat("rotas:strength", Double.NaN));
    }

    @Test void oldProfileMigrationPreservesIdentityUnknownFieldsAndNewState() {
        var player = new PlayerProgress(UUID.randomUUID());
        player.setLevel(20); player.setXp(42);
        var old = player.save(); old.putInt("data_version", 1); old.remove("rpg");
        old.putString("addon_field", "keep");
        var migrated = PlayerProgress.load(old);
        assertEquals(player.playerId(), migrated.playerId());
        assertEquals(20, migrated.level()); assertEquals(42, migrated.xp());
        assertEquals(2, migrated.dataVersion());
        migrated.rpg().currency("rotas:gold", 1234);
        migrated.rpg().masteryXp("rotas:sword", 123);
        migrated.rpg().reputation("rotas:empire", -30);
        var restored = PlayerProgress.load(migrated.save());
        assertEquals(1234, restored.rpg().currency("rotas:gold"));
        assertEquals(123, restored.rpg().masteryXp("rotas:sword"));
        assertEquals(-30, restored.reputation("rotas:empire"));
        assertEquals("keep", restored.save().getString("addon_field"));
    }

    @Test void invalidStoredWalletAndFutureSchemaAreRejectedWithoutSourceMutation() {
        var profile = new RpgProfile(() -> { });
        var saved = profile.save();
        saved.getCompound("currencies").putDouble("rotas:gold", 1.9);
        assertThrows(IllegalArgumentException.class, () -> RpgProfile.load(saved, () -> { }));
        assertEquals(1.9, saved.getCompound("currencies").getDouble("rotas:gold"));
        saved.putInt("schema", 99);
        assertThrows(IllegalArgumentException.class, () -> RpgProfile.load(saved, () -> { }));
    }

    @Test void unknownProfileFieldsSurviveSavesWithoutLeakingToClient() {
        var original = new PlayerProgress(UUID.randomUUID()).save();
        original.putString("addon_private", "root secret");
        original.getCompound("rpg").putString("addon_private", "profile secret");
        var restored = PlayerProgress.load(original);
        restored.rpg().currency("rotas:gold", 5);
        assertEquals("root secret", restored.save().getString("addon_private"));
        assertEquals("profile secret", restored.save().getCompound("rpg").getString("addon_private"));
        assertFalse(restored.clientSnapshot().contains("addon_private"));
        assertFalse(restored.clientSnapshot().getCompound("rpg").contains("addon_private"));
        assertEquals(5, restored.clientSnapshot().getCompound("rpg").getCompound("currencies").getLong("rotas:gold"));
    }
}
