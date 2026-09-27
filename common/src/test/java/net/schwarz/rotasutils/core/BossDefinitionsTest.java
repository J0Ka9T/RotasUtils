package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BossDefinitionsTest {
    private final ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));

    private ContentRegistry.Source source(String id, String kind, String body) throws Exception {
        return new ContentRegistry.Source(id, ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:" + id + "\",\"kind\":\"" + kind + "\",\"body\":" + body + "}"));
    }

    private BossDefinitions.Boss parse(String body) throws Exception {
        return BossDefinitions.boss(new ContentId("rotas:boss/test"), ContentPacks.parse(body),
                json -> ConditionEngine.ALWAYS, id -> transaction -> { });
    }

    @Test void phasesMustDescendFromFullHealthAndMapBackToTheCurrentHealth() throws Exception {
        var boss = parse("{\"phases\":[{\"threshold\":1,\"label\":\"Opening\"},{\"threshold\":0.6},{\"threshold\":0.25}]}");
        assertEquals(3, boss.phases().size());
        assertEquals(0, boss.phaseAt(1.0));
        assertEquals(0, boss.phaseAt(0.61));
        assertEquals(1, boss.phaseAt(0.6));
        assertEquals(1, boss.phaseAt(0.26));
        assertEquals(2, boss.phaseAt(0.25));
        assertEquals(2, boss.phaseAt(0.0));
        // Healing back above a threshold returns the boss to the earlier phase rather than skipping.
        assertEquals(0, boss.phaseAt(0.9));
        assertThrows(IllegalArgumentException.class, () -> boss.phaseAt(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"phases\":[{\"threshold\":0.5},{\"threshold\":0.8}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"phases\":[{\"threshold\":0.5}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"phases\":[]}"));
    }

    @Test void arenaEnrageAndShareDefaultsAreBounded() throws Exception {
        var boss = parse("{\"phases\":[{\"threshold\":1}],\"arena_radius\":64,\"enrage_seconds\":30,\"reset_seconds\":45,\"minimum_share\":0.2,"
                + "\"enrage_attributes\":{\"minecraft:generic.attack_damage\":{\"multiplier\":2}}}");
        assertEquals(64, boss.arenaRadius());
        assertEquals(600, boss.enrageTicks());
        assertEquals(900, boss.resetTicks());
        assertEquals(0.2, boss.minimumShare());
        assertTrue(boss.leash());
        assertEquals(1, boss.enrageAttributes().size());
        assertThrows(IllegalArgumentException.class, () -> parse("{\"phases\":[{\"threshold\":1}],\"arena_radius\":4}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"phases\":[{\"threshold\":1}],\"minimum_share\":2}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"phases\":[{\"threshold\":1,\"on_interval\":[\"rotas:action/x\"]}]}"),
                "interval actions without an interval would never run");
    }

    @Test void contributionSharesNormaliseAndRejectCorruptLedgers() {
        var shares = BossDefinitions.shares(Map.of("a", 30.0, "b", 10.0));
        assertEquals(0.75, shares.get("a"), 1e-9);
        assertEquals(0.25, shares.get("b"), 1e-9);
        assertTrue(BossDefinitions.shares(Map.of()).isEmpty());
        assertTrue(BossDefinitions.shares(Map.of("a", 0.0)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BossDefinitions.shares(Map.of("a", -1.0)));
        assertThrows(IllegalArgumentException.class, () -> BossDefinitions.shares(Map.of("a", Double.NaN)));
    }

    @Test void bossesCompileThroughTheRegistryAndBindToMonsterProfiles() throws Exception {
        var boss = source("boss/warden", "boss", "{\"label\":\"Warden\",\"phases\":[{\"threshold\":1},{\"threshold\":0.5,\"label\":\"Enraged\"}]}");
        var tier = source("tier/boss", "tier", "{\"rank\":5,\"boss\":true}");
        var monster = source("monster/warden", "monster", "{\"selector\":{\"entities\":[\"minecraft:zombie\"]},"
                + "\"level\":{\"strategy\":\"FIXED\",\"value\":30,\"min\":1,\"max\":50},\"tiers\":{\"rotas:tier/boss\":1},\"boss\":\"rotas:boss/warden\"}");
        assertFalse(registry.prepare(List.of(tier, monster)).valid(), "a missing boss reference rejects the pack");
        var prepared = registry.prepare(List.of(boss, tier, monster));
        assertTrue(prepared.valid(), prepared.issues().toString());
        var catalog = prepared.snapshot().monsters();
        assertEquals(new ContentId("rotas:boss/warden"), catalog.profiles().get(new ContentId("rotas:monster/warden")).boss());
        assertEquals("Warden", catalog.bosses().get(new ContentId("rotas:boss/warden")).label());
    }
}
