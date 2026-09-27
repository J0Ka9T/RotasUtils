package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import static org.junit.jupiter.api.Assertions.*;

class ItemDefinitionsTest {
    private final ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));

    private ContentRegistry.Source source(String id, String kind, String body) throws Exception {
        return new ContentRegistry.Source(id, ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:" + id + "\",\"kind\":\"" + kind + "\",\"body\":" + body + "}"));
    }

    private List<ContentRegistry.Source> sword() throws Exception {
        return List.of(
                source("rarity/common", "rarity", "{\"label\":\"Common\",\"rank\":0,\"modifier_multiplier\":1}"),
                source("rarity/rare", "rarity", "{\"label\":\"Rare\",\"color\":\"blue\",\"rank\":2,\"modifier_multiplier\":2}"),
                source("item/sword", "item", "{\"item\":\"minecraft:iron_sword\",\"slot\":\"MAINHAND\",\"min_level\":1,\"max_level\":50,"
                        + "\"rarities\":{\"rotas:rarity/common\":9,\"rotas:rarity/rare\":1},"
                        + "\"modifiers\":[{\"attribute\":\"minecraft:generic.attack_damage\",\"base\":2,\"per_level\":0.5}],"
                        + "\"requirement\":{\"min_level\":10,\"stats\":{\"rotas:stat/might\":5}}}"));
    }

    @Test void itemProfilesCompileThroughTheSharedRegistryAndRequireKnownRarities() throws Exception {
        var sources = sword();
        assertFalse(registry.prepare(List.of(sources.get(2))).valid(), "missing rarity must reject the pack");
        var prepared = registry.prepare(sources);
        assertTrue(prepared.valid(), prepared.issues().toString());
        var profile = prepared.snapshot().items().profiles().get(new ContentId("rotas:item/sword"));
        assertEquals("minecraft:iron_sword", profile.item());
        assertEquals(10, profile.requirement().minLevel());
        assertEquals(2, prepared.snapshot().items().rarities().size());
        // Item levels always clamp into the profile band, whatever the source level is.
        assertEquals(1, profile.level(-5));
        assertEquals(50, profile.level(9999));
        assertEquals(24, profile.level(24));
    }

    @Test void modifiersScaleWithLevelAndRarityAndSumPerOperation() {
        var flat = new ItemDefinitions.Modifier("minecraft:generic.attack_damage", ItemDefinitions.Operation.ADDITION, 2, 0.5);
        var extra = new ItemDefinitions.Modifier("minecraft:generic.attack_damage", ItemDefinitions.Operation.ADDITION, 1, 0);
        var percent = new ItemDefinitions.Modifier("minecraft:generic.attack_damage", ItemDefinitions.Operation.MULTIPLY_BASE, 0.1, 0);
        assertEquals(4.5, flat.at(6, 1), 1e-9);
        assertEquals(9.0, flat.at(6, 2), 1e-9);
        var derived = ItemDefinitions.derive(List.of(flat, extra, percent), 6, 1, ItemDefinitions.Operation.ADDITION);
        assertEquals(5.5, derived.get("minecraft:generic.attack_damage"), 1e-9);
        assertEquals(1, ItemDefinitions.derive(List.of(flat, extra, percent), 6, 1, ItemDefinitions.Operation.MULTIPLY_BASE).size());
        assertThrows(IllegalArgumentException.class,
                () -> new ItemDefinitions.Modifier("minecraft:generic.attack_damage", ItemDefinitions.Operation.ADDITION, Double.NaN, 0));
    }

    @Test void requirementsReportTheFirstUnmetGateAndPassWhenSatisfied() {
        var requirement = new ItemDefinitions.Requirement(10, Map.of("rotas:stat/might", 5.0));
        assertEquals("เลเวล 10", requirement.unmet(9, stat -> 99.0), "requirement text is Thai like the rest of the interface");
        assertEquals("rotas:stat/might 5.0", requirement.unmet(10, stat -> 4.0));
        assertEquals("rotas:stat/might 5.0", requirement.unmet(10, stat -> null));
        assertEquals("", requirement.unmet(10, stat -> 5.0));
        assertEquals("", new ItemDefinitions.Requirement(1, Map.of()).unmet(1, stat -> null));
    }

    @Test void rarityRollsAreWeightedDeterministicAndSkipRaritiesThePackDropped() throws Exception {
        var prepared = registry.prepare(sword());
        var catalog = prepared.snapshot().items();
        var profile = catalog.profiles().get(new ContentId("rotas:item/sword"));
        var random = new SplittableRandom(7);
        int rare = 0;
        for (int i = 0; i < 10000; i++) {
            if (ItemDefinitions.rarity(profile, catalog.rarities(), random).value().equals("rotas:rarity/rare")) { rare++; }
        }
        assertTrue(rare > 850 && rare < 1150, Integer.toString(rare));
        assertEquals(ItemDefinitions.rarity(profile, catalog.rarities(), new SplittableRandom(3)),
                ItemDefinitions.rarity(profile, catalog.rarities(), new SplittableRandom(3)));
        // Only the surviving rarity can be rolled once the pack drops the other one.
        var reduced = Map.of(new ContentId("rotas:rarity/rare"), catalog.rarities().get(new ContentId("rotas:rarity/rare")));
        assertEquals("rotas:rarity/rare", ItemDefinitions.rarity(profile, reduced, new SplittableRandom(1)).value());
        assertThrows(IllegalArgumentException.class, () -> ItemDefinitions.rarity(profile, Map.of(), new SplittableRandom(1)));
    }

    @Test void lootTablesBoundRollsAndApplyTheTierMultiplierToTheRollCount() throws Exception {
        var table = ItemDefinitions.lootTable(new ContentId("rotas:loot/test"),
                ContentPacks.parse("{\"min_rolls\":2,\"max_rolls\":2,\"entries\":[{\"weight\":1,\"item\":\"minecraft:diamond\",\"min_count\":1,\"max_count\":3}]}"),
                json -> ConditionEngine.ALWAYS);
        var random = new SplittableRandom(11);
        assertEquals(2, table.rolls(random, 1));
        assertEquals(4, table.rolls(random, 2));
        assertEquals(0, table.rolls(random, 0));
        assertEquals(64, table.rolls(random, 1000), "the hard ceiling caps any multiplier");
        assertThrows(IllegalArgumentException.class, () -> table.rolls(random, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ItemDefinitions.lootTable(new ContentId("rotas:loot/bad"),
                ContentPacks.parse("{\"entries\":[{\"weight\":1,\"item\":\"minecraft:diamond\",\"profile\":\"rotas:item/sword\"}]}"),
                json -> ConditionEngine.ALWAYS), "an entry cannot be both a vanilla item and a profile");
    }

    @Test void setsRejectContradictoryMembershipAndReportEarnedBonuses() throws Exception {
        var sources = new java.util.ArrayList<>(sword());
        sources.add(source("item/shield", "item", "{\"item\":\"minecraft:shield\",\"set\":\"rotas:set/iron\",\"rarities\":{\"rotas:rarity/common\":1}}"));
        sources.add(source("set/iron", "set", "{\"label\":\"Iron\",\"pieces\":[\"rotas:item/shield\"],"
                + "\"bonuses\":[{\"pieces\":2,\"modifiers\":[{\"attribute\":\"minecraft:generic.armor\",\"base\":2}]}]}"));
        // A two-piece bonus cannot be earned by a one-piece set.
        assertFalse(registry.prepare(sources).valid());

        var set = ItemDefinitions.set(new ContentId("rotas:set/iron"),
                ContentPacks.parse("{\"pieces\":[\"rotas:item/a\",\"rotas:item/b\",\"rotas:item/c\"],\"bonuses\":["
                        + "{\"pieces\":3,\"modifiers\":[{\"attribute\":\"minecraft:generic.armor\",\"base\":4}]},"
                        + "{\"pieces\":2,\"modifiers\":[{\"attribute\":\"minecraft:generic.armor\",\"base\":2}]}]}"));
        assertEquals(List.of(2, 3), set.bonuses().stream().map(ItemDefinitions.SetBonus::pieces).toList());
        assertEquals(0, set.active(1).size());
        assertEquals(1, set.active(2).size());
        assertEquals(2, set.active(3).size());
    }
}
