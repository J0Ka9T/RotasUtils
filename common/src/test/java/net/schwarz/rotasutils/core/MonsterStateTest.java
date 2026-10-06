package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MonsterStateTest {
    private static MonsterState state() {
        return new MonsterState(new ContentId("rotas:monster/zombie"), new ContentId("rotas:tier/elite"), 12, 340, 2.5, true,
                List.of(new ContentId("rotas:affix/strong"), new ContentId("rotas:affix/swift")),
                Map.of("minecraft:generic.max_health", new MonsterDefinitions.DerivedScale(2.0, 4.0)),
                new ContentId("rotas:reward/elite"), "Elite Zombie", "{\"text\":\"Bob\"}");
    }

    @Test void persistedStateSurvivesAnExactSaveAndLoadRoundTrip() {
        MonsterState original = state();
        CompoundTag runtime = new CompoundTag();
        runtime.putLong("cooldown:rotas:affix/strong:HURT", 4096L);
        runtime.putString("var:rpg.rage", "3");
        original.runtime(runtime);
        original.rewarded(true);

        MonsterState loaded = MonsterState.load(original.save());
        assertEquals(original.profile(), loaded.profile());
        assertEquals(original.tier(), loaded.tier());
        assertEquals(12, loaded.level());
        assertEquals(340, loaded.xp());
        assertEquals(2.5, loaded.lootMultiplier());
        assertTrue(loaded.boss());
        assertTrue(loaded.rewarded());
        assertEquals(original.affixes(), loaded.affixes());
        assertEquals(original.reward(), loaded.reward());
        assertEquals("Elite Zombie", loaded.name());
        assertEquals("{\"text\":\"Bob\"}", loaded.originalName());
        assertEquals(2.0, loaded.attributes().get("minecraft:generic.max_health").multiplier());
        assertEquals(4.0, loaded.attributes().get("minecraft:generic.max_health").add());
        assertEquals(4096L, loaded.runtime().getLong("cooldown:rotas:affix/strong:HURT"));
        assertEquals("3", loaded.runtime().getString("var:rpg.rage"));
        MonsterState rewardless = new MonsterState(new ContentId("rotas:monster/zombie"), new ContentId("rotas:tier/normal"), 1, 0, 1, false,
                List.of(), Map.of(), null, "", "");
        assertNull(MonsterState.load(rewardless.save()).reward());
    }

    @Test void unsupportedOrCorruptPersistedStateFailsExplicitly() {
        CompoundTag future = state().save();
        future.putInt("schema", 3);
        assertThrows(IllegalArgumentException.class, () -> MonsterState.load(future));

        CompoundTag missing = state().save();
        missing.remove("attributes");
        assertThrows(IllegalArgumentException.class, () -> MonsterState.load(missing));

        CompoundTag wrongType = state().save();
        wrongType.putString("xp", "many");
        assertThrows(IllegalArgumentException.class, () -> MonsterState.load(wrongType));

        CompoundTag badAffix = state().save();
        ListTag affixes = new ListTag();
        affixes.add(StringTag.valueOf("not an ID"));
        badAffix.put("affixes", affixes);
        assertThrows(IllegalArgumentException.class, () -> MonsterState.load(badAffix));

        CompoundTag badAttribute = state().save();
        badAttribute.getCompound("attributes").put("minecraft:generic.max_health", new CompoundTag());
        assertThrows(IllegalArgumentException.class, () -> MonsterState.load(badAttribute));
    }

    @Test void schemaOneMigratesWithoutRerollingAndSchemaTwoStoresRpgIdentity() {
        CompoundTag legacy = state().save();
        legacy.putInt("schema", 1);
        legacy.remove("source");
        legacy.remove("rank");
        legacy.remove("monster_type");
        legacy.remove("custom_id");
        MonsterState migrated = MonsterState.load(legacy);
        assertEquals(12, migrated.level());
        assertEquals(MonsterAssignmentSource.CONFIGURED, migrated.source());
        assertEquals(MonsterRank.ELITE, migrated.rank());
        assertEquals(MonsterType.UNKNOWN, migrated.monsterType());

        MonsterState restored = MonsterState.load(migrated.save());
        assertEquals(2, migrated.save().getInt("schema"));
        assertEquals(migrated.source(), restored.source());
        assertEquals(migrated.rank(), restored.rank());
    }

    @Test void naturalAndConfiguredLevelsUseDifferentHardLimits() {
        assertEquals(100, MonsterLevels.natural(700, 100));
        assertEquals(40, MonsterLevels.natural(40, 100));
        assertEquals(999, MonsterLevels.configured(1200));
        assertEquals(450, MonsterLevels.configured(450));
        assertThrows(IllegalArgumentException.class, () -> MonsterLevels.requireNatural(101, 100));
        assertThrows(IllegalArgumentException.class, () -> MonsterLevels.requireConfigured(1000));
    }

    @Test void constructorAndRuntimeStorageRejectOutOfBoundsContent() {
        assertThrows(IllegalArgumentException.class, () -> new MonsterState(new ContentId("rotas:m"), new ContentId("rotas:t"), 0, 0, 1, false,
                List.of(), Map.of(), null, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new MonsterState(new ContentId("rotas:m"), new ContentId("rotas:t"), 1, -1, 1, false,
                List.of(), Map.of(), null, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new MonsterState(new ContentId("rotas:m"), new ContentId("rotas:t"), 1, 0, Double.NaN, false,
                List.of(), Map.of(), null, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new MonsterState(new ContentId("rotas:m"), new ContentId("rotas:t"), 1, 0, 1, false,
                List.of(new ContentId("rotas:a"), new ContentId("rotas:a")), Map.of(), null, "", ""));

        MonsterState state = state();
        CompoundTag oversizedValue = new CompoundTag();
        oversizedValue.putString("var:rpg.note", "x".repeat(16385));
        assertThrows(IllegalArgumentException.class, () -> state.runtime(oversizedValue));

        CompoundTag unsupportedValue = new CompoundTag();
        unsupportedValue.put("nested", new CompoundTag());
        assertThrows(IllegalArgumentException.class, () -> state.runtime(unsupportedValue));

        CompoundTag tooManyKeys = new CompoundTag();
        for (int i = 0; i < 129; i++) { tooManyKeys.putLong("key" + i, i); }
        assertThrows(IllegalArgumentException.class, () -> state.runtime(tooManyKeys));

        CompoundTag accepted = new CompoundTag();
        accepted.putString("var:rpg.note", "x".repeat(16384));
        state.runtime(accepted);
        assertEquals("x".repeat(16384), state.runtime().getString("var:rpg.note"));
        assertThrows(IllegalArgumentException.class, () -> state.runtime(oversizedValue));
        assertEquals(1, state.runtime().size());
    }

    @Test void runtimeIsCopiedSoCallersCannotMutatePersistedState() {
        MonsterState state = state();
        CompoundTag runtime = new CompoundTag();
        runtime.putLong("cooldown:rotas:affix/strong:INTERVAL", 10L);
        state.runtime(runtime);
        runtime.putLong("cooldown:rotas:affix/strong:INTERVAL", 99L);
        assertEquals(10L, state.runtime().getLong("cooldown:rotas:affix/strong:INTERVAL"));
        CompoundTag borrowed = state.runtime();
        borrowed.putLong("cooldown:rotas:affix/strong:INTERVAL", 77L);
        assertEquals(10L, state.runtime().getLong("cooldown:rotas:affix/strong:INTERVAL"));
    }
}
