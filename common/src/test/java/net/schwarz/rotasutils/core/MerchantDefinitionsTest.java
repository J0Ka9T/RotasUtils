package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MerchantDefinitionsTest {
    private final ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));

    private ContentRegistry.Source source(String id, String kind, String body) throws Exception {
        return new ContentRegistry.Source(id, ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:" + id + "\",\"kind\":\"" + kind + "\",\"body\":" + body + "}"));
    }

    private MerchantDefinitions.Merchant parse(String body) throws Exception {
        return MerchantDefinitions.merchant(new ContentId("rotas:merchant/test"), ContentPacks.parse(body), json -> ConditionEngine.ALWAYS);
    }

    @Test void tradesRequireExactlyOneResultAndAtLeastOneCost() throws Exception {
        var merchant = parse("{\"label\":\"Smith\",\"trades\":[{\"key\":\"blade\",\"item\":\"minecraft:iron_sword\",\"count\":1,"
                + "\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":25}]}]}");
        assertEquals(1, merchant.trades().size());
        assertEquals("minecraft:iron_sword", merchant.trade("blade").item());
        assertThrows(IllegalArgumentException.class, () -> merchant.trade("missing"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"trades\":[{\"key\":\"a\",\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":1}]}]}"),
                "a trade must produce something");
        assertThrows(IllegalArgumentException.class, () -> parse("{\"trades\":[{\"key\":\"a\",\"item\":\"minecraft:stone\",\"profile\":\"rotas:item/x\","
                + "\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":1}]}]}"), "a trade cannot produce both");
        assertThrows(IllegalArgumentException.class, () -> parse("{\"trades\":[{\"key\":\"a\",\"item\":\"minecraft:stone\",\"costs\":[]}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"trades\":["
                + "{\"key\":\"a\",\"item\":\"minecraft:stone\",\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":1}]},"
                + "{\"key\":\"a\",\"item\":\"minecraft:dirt\",\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":1}]}]}"),
                "duplicate trade keys would make a purchase ambiguous");
        assertThrows(IllegalArgumentException.class,
                () -> new MerchantDefinitions.Cost(new ContentId("rotas:gold"), "minecraft:stone", 1));
        assertThrows(IllegalArgumentException.class, () -> new MerchantDefinitions.Cost(new ContentId("rotas:gold"), null, 0));
    }

    @Test void stockWindowsRollOverOnlyForRestockingTrades() throws Exception {
        var merchant = parse("{\"trades\":["
                + "{\"key\":\"daily\",\"item\":\"minecraft:bread\",\"stock\":4,\"restock_seconds\":3600,\"per_player_limit\":2,"
                + "\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":1}]},"
                + "{\"key\":\"unique\",\"item\":\"minecraft:cake\",\"stock\":1,"
                + "\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":5}]},"
                + "{\"key\":\"endless\",\"item\":\"minecraft:stone\",\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":1}]}]}");
        var daily = merchant.trade("daily");
        assertTrue(daily.limited());
        assertEquals(daily.window(3600), daily.window(7199));
        assertNotEquals(daily.window(3600), daily.window(7200));
        assertEquals(0, merchant.trade("unique").window(999999), "a trade without restock stays in one window forever");
        assertFalse(merchant.trade("endless").limited());
        assertThrows(IllegalArgumentException.class, () -> parse("{\"trades\":[{\"key\":\"a\",\"item\":\"minecraft:stone\",\"restock_seconds\":60,"
                + "\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":1}]}]}"), "restocking nothing is a content error");
    }

    @Test void perPlayerPurchaseCountersResetWithTheirWindow() {
        assertEquals(0, MerchantDefinitions.purchases(null, 5));
        assertEquals(0, MerchantDefinitions.purchases("", 5));
        assertEquals(3, MerchantDefinitions.purchases(MerchantDefinitions.purchaseValue(5, 3), 5));
        assertEquals(0, MerchantDefinitions.purchases(MerchantDefinitions.purchaseValue(4, 3), 5), "an old window counts as zero");
        assertEquals(0, MerchantDefinitions.purchases("garbage", 5));
        assertEquals(0, MerchantDefinitions.purchases("5:-2", 5));
        String key = MerchantDefinitions.limitKey(new ContentId("rotas:merchant/town_smith"), "blade");
        assertTrue(key.matches("rpg\\.[a-z0-9_.-]{1,120}"), key);
        assertTrue(MerchantDefinitions.limitKey(new ContentId("rotas:merchant/" + "a".repeat(140)), "blade")
                .matches("rpg\\.[a-z0-9_.-]{1,120}"));
    }

    @Test void merchantsCompileThroughTheRegistryAndRequireTheirItemProfiles() throws Exception {
        var merchant = source("merchant/smith", "merchant", "{\"label\":\"Smith\",\"trades\":[{\"key\":\"blade\",\"profile\":\"rotas:item/sword\","
                + "\"item_level\":10,\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":25}]}]}");
        assertFalse(registry.prepare(List.of(merchant)).valid(), "a missing item profile rejects the pack");
        var rarity = source("rarity/common", "rarity", "{\"label\":\"Common\"}");
        var item = source("item/sword", "item", "{\"item\":\"minecraft:iron_sword\",\"rarities\":{\"rotas:rarity/common\":1}}");
        var prepared = registry.prepare(List.of(merchant, rarity, item));
        assertTrue(prepared.valid(), prepared.issues().toString());
        var compiled = prepared.snapshot().merchants().get(new ContentId("rotas:merchant/smith"));
        assertEquals(10, compiled.trade("blade").itemLevel());
        assertEquals(new ContentId("rotas:item/sword"), compiled.trade("blade").profile());
    }
}
