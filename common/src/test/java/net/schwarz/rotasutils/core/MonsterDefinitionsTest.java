package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import static org.junit.jupiter.api.Assertions.*;

class MonsterDefinitionsTest {
    private final ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));
    private ContentRegistry.Source source(String id, String kind, String body) throws Exception {
        return new ContentRegistry.Source(id, ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:" + id + "\",\"kind\":\"" + kind + "\",\"body\":" + body + "}"));
    }
    @Test void formulasAreDeterministicBoundedAndCannotExecuteCode() {
        var formula = NumericExpression.compile("clamp(floor(player.level * 1.25) + max(region.level, 2), 1, 100)");
        assertEquals(28, formula.evaluate(name -> name.equals("player.level") ? 20 : 3));
        assertThrows(IllegalArgumentException.class, () -> NumericExpression.compile("Runtime.exec('bad')"));
        assertThrows(IllegalArgumentException.class, () -> NumericExpression.compile("(".repeat(40) + "1" + ")".repeat(40)));
        assertThrows(IllegalArgumentException.class, () -> NumericExpression.compile("1/0").evaluate(name -> 0));
        assertThrows(IllegalArgumentException.class, () -> NumericExpression.compile("missing.fact").evaluate(name -> Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> NumericExpression.compile("pow(1000000,1000000)").evaluate(name -> 0));
    }
    @Test void profilesCompileThroughSharedRegistryAndRequireValidReferences() throws Exception {
        var profile = source("monster/zombie", "monster", "{\"selector\":{\"entities\":[\"minecraft:zombie\"]},\"level\":{\"min\":1,\"max\":20,\"strategy\":\"RANDOM\"},\"tiers\":{\"rotas:tier/elite\":1},\"affixes\":[\"rotas:affix/strong\"]}");
        var tier = source("tier/elite", "tier", "{\"rank\":2,\"affix_count\":1}");
        var affix = source("affix/strong", "affix", "{\"attributes\":{\"minecraft:generic.attack_damage\":{\"multiplier\":1.5}}}");
        assertFalse(registry.prepare(List.of(profile, tier)).valid());
        var prepared = registry.prepare(List.of(profile, tier, affix));
        assertTrue(prepared.valid(), prepared.issues().toString());
        assertEquals(1, prepared.snapshot().monsters().candidates("minecraft:zombie").size());
        assertEquals(0, prepared.snapshot().monsters().candidates("minecraft:skeleton").size());
    }
    @Test void weightedTiersHaveExpectedDistributionAndStableSeed() {
        var random = new SplittableRandom(200); Map<String, Integer> pool = new java.util.TreeMap<>(Map.of("normal", 9, "elite", 1));
        int elite = 0;
        for (int i = 0; i < 10000; i++) { if (MonsterDefinitions.weighted(pool, random).equals("elite")) { elite++; } }
        assertTrue(elite > 900 && elite < 1100, Integer.toString(elite));
        assertEquals(MonsterDefinitions.weighted(pool, new SplittableRandom(10)), MonsterDefinitions.weighted(pool, new SplittableRandom(10)));
        assertThrows(IllegalArgumentException.class, () -> MonsterDefinitions.weighted(Map.of("bad", -1), random));
    }
    @Test void affixIncompatibilitiesAreSymmetricAndCannotRepeat() throws Exception {
        var a = MonsterDefinitions.affix(new ContentId("rotas:a"), ContentPacks.parse("{\"tags\":[\"fire\"]}"), ignored -> ConditionEngine.ALWAYS, ignored -> tx -> { });
        var b = MonsterDefinitions.affix(new ContentId("rotas:b"), ContentPacks.parse("{\"incompatible\":[\"fire\"]}"), ignored -> ConditionEngine.ALWAYS, ignored -> tx -> { });
        assertFalse(a.compatible(List.of(b))); assertFalse(b.compatible(List.of(a))); assertFalse(a.compatible(List.of(a)));
        assertTrue(a.compatible(List.of()));
    }
    @Test void derivedAttributesComposeAndClampWithoutChangingBaseEntityState() {
        var derived = MonsterDefinitions.derive(5, List.of(Map.of("minecraft:generic.max_health", new MonsterDefinitions.Scale(1, .1, 2)),
                Map.of("minecraft:generic.max_health", new MonsterDefinitions.Scale(2, 0, 3))));
        assertEquals(2.8, derived.get("minecraft:generic.max_health").multiplier(), .0001);
        assertEquals(5, derived.get("minecraft:generic.max_health").add());
        assertThrows(IllegalArgumentException.class, () -> new MonsterDefinitions.Scale(Double.NaN, 0, 0));
    }
    @Test void levelRulesRespectStrategiesOffsetsAndBounds() {
        var fixed = new MonsterDefinitions.LevelRule(MonsterDefinitions.Strategy.NEAREST_PLAYER, 2, 20, 3, -4, null);
        assertEquals(2, fixed.choose(name -> 1, new SplittableRandom()));
        assertEquals(20, fixed.choose(name -> 999, new SplittableRandom()));
        var selector = new MonsterDefinitions.Selector(Set.of("minecraft:zombie"), Set.of("rotas:undead"), Set.of(), Set.of(), Set.of("minecraft:overworld"), Set.of("NATURAL"), Set.of(), Set.of());
        assertTrue(selector.matches("minecraft:zombie", Set.of("rotas:undead"), "minecraft:plains", "minecraft:overworld", "NATURAL", ""));
        assertFalse(selector.matches("minecraft:zombie", Set.of("rotas:undead"), "minecraft:plains", "minecraft:the_nether", "NATURAL", ""));
    }
}
