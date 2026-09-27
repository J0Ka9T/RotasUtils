package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemCatalogTest {
    private static ItemDefinitions.Profile profile(String id, String set) {
        return new ItemDefinitions.Profile(new ContentId(id), "minecraft:iron_sword",
                Map.of(new ContentId("rotas:rarity/common"), 1), 1, 10, List.of(),
                new ItemDefinitions.Requirement(1, Map.of()), set == null ? null : new ContentId(set), "", List.of(), "", "");
    }
    private static ItemDefinitions.ItemSet set(String id, List<String> pieces) {
        return new ItemDefinitions.ItemSet(new ContentId(id), id, pieces.stream().map(ContentId::new).toList(),
                List.of(new ItemDefinitions.SetBonus(2, List.of(
                        new ItemDefinitions.Modifier("minecraft:generic.armor", ItemDefinitions.Operation.ADDITION, 2, 0)))));
    }

    @Test void setMembershipMustAgreeInBothDirections() {
        var piece = profile("rotas:item/a", "rotas:set/iron");
        var iron = set("rotas:set/iron", List.of("rotas:item/a"));
        var catalog = new ItemCatalog(Map.of(piece.id(), piece), Map.of(), Map.of(iron.id(), iron), Map.of());
        assertEquals(iron.id(), catalog.setOf(piece.id()));

        var orphan = profile("rotas:item/b", "rotas:set/iron");
        assertThrows(IllegalArgumentException.class,
                () -> new ItemCatalog(Map.of(orphan.id(), orphan), Map.of(), Map.of(iron.id(), iron), Map.of()),
                "a profile cannot claim a set that does not list it");

        var other = set("rotas:set/steel", List.of("rotas:item/a"));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemCatalog(Map.of(piece.id(), piece), Map.of(), Map.of(iron.id(), iron, other.id(), other), Map.of()),
                "an item cannot belong to two sets");
    }

    @Test void bonusesReportOnlyEarnedTiersForKnownSets() {
        var piece = profile("rotas:item/a", "rotas:set/iron");
        var iron = set("rotas:set/iron", List.of("rotas:item/a"));
        var catalog = new ItemCatalog(Map.of(piece.id(), piece), Map.of(), Map.of(iron.id(), iron), Map.of());
        assertTrue(catalog.bonuses(Map.of(iron.id(), 1)).isEmpty());
        assertEquals(1, catalog.bonuses(Map.of(iron.id(), 2)).get(iron.id()).size());
        assertTrue(catalog.bonuses(Map.of(new ContentId("rotas:set/unknown"), 9)).isEmpty());
    }

    @Test void catalogBudgetsAndImmutabilityAreEnforced() {
        Map<ContentId, ItemDefinitions.Rarity> rarities = new HashMap<>();
        for (int i = 0; i < 65; i++) {
            rarities.put(new ContentId("rotas:rarity/r" + i), new ItemDefinitions.Rarity(new ContentId("rotas:rarity/r" + i), "r", "white", i, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new ItemCatalog(Map.of(), rarities, Map.of(), Map.of()));
        var catalog = ItemCatalog.empty();
        assertThrows(UnsupportedOperationException.class, () -> catalog.profiles().put(new ContentId("rotas:item/x"), profile("rotas:item/x", null)));
        assertNull(catalog.setOf(new ContentId("rotas:item/x")));
    }
}
