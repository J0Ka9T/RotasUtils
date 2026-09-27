package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MonsterCatalogTest {
    private static MonsterDefinitions.Profile profile(String id, int priority, boolean manualOnly, Set<String> entities) {
        return new MonsterDefinitions.Profile(new ContentId(id), priority,
                new MonsterDefinitions.Selector(entities, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()),
                manualOnly, false, new MonsterDefinitions.LevelRule(MonsterDefinitions.Strategy.FIXED, 1, 10, 1, 0, null),
                10, 1, Map.of(new ContentId("rotas:tier/normal"), 1), List.of(), Map.of(), "", null, null, null,
                MobSpawnRules.DEFAULT);
    }
    private static MonsterDefinitions.Tier tier(String id) {
        return new MonsterDefinitions.Tier(new ContentId(id), id, 1, 1, 1, 1, false, Map.of());
    }

    @Test void candidatesAreOrderedByPriorityThenIdAndIncludeBroadProfiles() {
        var low = profile("rotas:monster/any_low", 0, false, Set.of());
        var high = profile("rotas:monster/zombie_high", 10, false, Set.of("minecraft:zombie"));
        var equalA = profile("rotas:monster/a_equal", 5, false, Set.of("minecraft:zombie"));
        var equalB = profile("rotas:monster/b_equal", 5, false, Set.of());
        var catalog = new MonsterCatalog(Map.of(low.id(), low, high.id(), high, equalA.id(), equalA, equalB.id(), equalB), Map.of(), Map.of(), Map.of());

        assertEquals(List.of(high.id(), equalA.id(), equalB.id(), low.id()),
                catalog.candidates("minecraft:zombie").stream().map(MonsterDefinitions.Profile::id).toList());
        // A profile with an entity selector never reaches unrelated entity types, broad ones always do.
        assertEquals(List.of(equalB.id(), low.id()),
                catalog.candidates("minecraft:skeleton").stream().map(MonsterDefinitions.Profile::id).toList());
    }

    @Test void manualOnlyProfilesStayOutOfAutomaticAssignmentButRemainAddressable() {
        var manual = profile("rotas:monster/summoned", 100, true, Set.of("minecraft:zombie"));
        var catalog = new MonsterCatalog(Map.of(manual.id(), manual), Map.of(), Map.of(), Map.of());
        assertTrue(catalog.candidates("minecraft:zombie").isEmpty());
        assertEquals(manual, catalog.profiles().get(manual.id()));
    }

    @Test void catalogBudgetsAndImmutabilityAreEnforced() {
        Map<ContentId, MonsterDefinitions.Tier> tiers = new HashMap<>();
        for (int i = 0; i < 65; i++) { tiers.put(new ContentId("rotas:tier/t" + i), tier("rotas:tier/t" + i)); }
        assertThrows(IllegalArgumentException.class, () -> new MonsterCatalog(Map.of(), tiers, Map.of(), Map.of()));

        var profile = profile("rotas:monster/zombie", 0, false, Set.of("minecraft:zombie"));
        var catalog = new MonsterCatalog(Map.of(profile.id(), profile), Map.of(), Map.of(), Map.of());
        assertThrows(UnsupportedOperationException.class, () -> catalog.profiles().put(new ContentId("rotas:monster/extra"), profile));
        assertThrows(UnsupportedOperationException.class, () -> catalog.candidates("minecraft:zombie").clear());
        assertTrue(MonsterCatalog.empty().candidates("minecraft:zombie").isEmpty());
    }
}
