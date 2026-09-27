package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.network.KernelUi;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The console draws whatever the server sends, so the two sides have to agree on the shape of the
 * snapshot. This walks live content through the server encoder into the client read model.
 */
class KernelUiCatalogTest {
    private final ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));

    private ContentRegistry.Source source(String id, String kind, String body) throws Exception {
        return new ContentRegistry.Source(id, ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:" + id + "\",\"kind\":\"" + kind + "\",\"body\":" + body + "}"));
    }

    private ContentRegistry.Snapshot content() throws Exception {
        var prepared = registry.prepare(List.of(
                source("rarity/common", "rarity", "{\"label\":\"Common\"}"),
                source("item/blade", "item", "{\"item\":\"minecraft:iron_sword\",\"slot\":\"MAINHAND\",\"min_level\":2,\"max_level\":40,"
                        + "\"rarities\":{\"rotas:rarity/common\":1},\"requirement\":{\"min_level\":7},"
                        + "\"modifiers\":[{\"attribute\":\"minecraft:generic.attack_damage\",\"base\":2}]}"),
                source("loot/hoard", "loot", "{\"min_rolls\":1,\"max_rolls\":3,\"entries\":["
                        + "{\"weight\":1,\"item\":\"minecraft:bone\"},{\"weight\":1,\"profile\":\"rotas:item/blade\"}]}"),
                source("tier/elite", "tier", "{\"label\":\"Elite\",\"rank\":2,\"affix_count\":1}"),
                source("affix/swift", "affix", "{\"weight\":1}"),
                source("boss/warden", "boss", "{\"label\":\"Warden\",\"arena_radius\":40,\"enrage_seconds\":120,\"minimum_share\":0.25,"
                        + "\"phases\":[{\"threshold\":1},{\"threshold\":0.4}]}"),
                source("monster/warden", "monster", "{\"manual_only\":true,\"selector\":{\"entities\":[\"minecraft:zombie\"]},"
                        + "\"level\":{\"strategy\":\"RANDOM\",\"min\":5,\"max\":30},\"tiers\":{\"rotas:tier/elite\":1},"
                        + "\"affixes\":[\"rotas:affix/swift\"],\"boss\":\"rotas:boss/warden\",\"loot\":\"rotas:loot/hoard\"}"),
                source("action/coin", "action", "{\"type\":\"currency\",\"id\":\"rotas:gold\",\"amount\":5}"),
                source("reward/coin", "reward", "{\"actions\":[\"rotas:action/coin\"]}"),
                source("quest/hunt", "quest", "{\"label\":\"Hunt\",\"reset\":\"DAILY\",\"bounty_limit\":3,\"reward\":\"rotas:reward/coin\","
                        + "\"stages\":[{\"label\":\"Track it\",\"objectives\":[{\"event\":\"rotas:monster_defeated\",\"count\":4,\"label\":\"Defeat 4\"}]},"
                        + "{\"objectives\":[{\"event\":\"rotas:item_obtained\",\"count\":1}]}]}"),
                source("merchant/smith", "merchant", "{\"label\":\"Smith\",\"trades\":["
                        + "{\"key\":\"blade\",\"label\":\"A blade\",\"profile\":\"rotas:item/blade\",\"item_level\":12,\"stock\":4,"
                        + "\"restock_seconds\":3600,\"per_player_limit\":1,\"costs\":[{\"currency\":\"rotas:gold\",\"amount\":25}]},"
                        + "{\"key\":\"scrap\",\"item\":\"minecraft:iron_ingot\",\"count\":2,"
                        + "\"costs\":[{\"item\":\"minecraft:bone\",\"amount\":8},{\"currency\":\"rotas:gold\",\"amount\":1}]}]}")));
        assertTrue(prepared.valid(), prepared.issues().toString());
        return prepared.snapshot();
    }

    @Test void everySectionOfTheCatalogSurvivesTheRoundTrip() throws Exception {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("ready", true);
        tag.putString("hash", "abc");
        tag.putLong("revision", 7);
        tag.put("catalog", KernelUi.catalog(content()));
        tag.put("state", new net.minecraft.nbt.CompoundTag());
        ClientKernelState.apply(tag);

        assertTrue(ClientKernelState.ready());
        assertEquals(7, ClientKernelState.revision());
        assertFalse(ClientKernelState.truncated());

        var item = ClientKernelState.items().get(0);
        assertEquals("rotas:item/blade", item.id());
        assertEquals("minecraft:iron_sword", item.item());
        assertEquals(2, item.levelMin());
        assertEquals(40, item.levelMax());
        assertEquals(7, item.minPlayerLevel());
        assertEquals(1, item.modifiers());
        assertEquals("MAINHAND", item.slot());

        var table = ClientKernelState.loot().get(0);
        assertEquals(1, table.minRolls());
        assertEquals(3, table.maxRolls());
        assertEquals(2, table.entries());

        var monster = ClientKernelState.monsters().get(0);
        assertEquals(5, monster.levelMin());
        assertEquals(30, monster.levelMax());
        assertTrue(monster.manualOnly());
        assertEquals("rotas:boss/warden", monster.boss());
        assertEquals("rotas:loot/hoard", monster.loot());

        var boss = ClientKernelState.bosses().get(0);
        assertEquals("Warden", boss.label());
        assertEquals(2, boss.phases());
        assertEquals(40, boss.arenaRadius());
        assertEquals(120, boss.enrageSeconds());
        assertEquals(0.25, boss.minimumShare(), 1e-9);

        var quest = ClientKernelState.quests().get(0);
        assertEquals("Hunt", quest.label());
        assertEquals("DAILY", quest.reset());
        assertEquals(3, quest.bountyLimit());
        assertEquals(2, quest.stages().size());
        assertEquals("Track it", quest.stages().get(0).label());
        assertEquals("Defeat 4", quest.stages().get(0).objectives().get(0).label());
        assertEquals(4, quest.stages().get(0).objectives().get(0).count());
        // An objective without a label falls back to its event, so a row is never blank.
        assertEquals("rotas:item_obtained", quest.stages().get(1).objectives().get(0).label());

        var merchant = ClientKernelState.merchants().get(0);
        assertEquals("Smith", merchant.label());
        assertEquals(2, merchant.trades().size());
        var blade = merchant.trades().get(0);
        assertEquals("A blade", blade.label());
        assertEquals("rotas:item/blade", blade.result());
        assertEquals(4, blade.stock());
        assertEquals(1, blade.perPlayerLimit());
        assertEquals("25 gold", blade.cost());
        assertEquals("8 bone + 1 gold", merchant.trades().get(1).cost(), "mixed costs read as one line");
    }

    @Test void playerStateDrivesTheQuestAndTradeRows() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("ready", true);
        tag.put("catalog", new net.minecraft.nbt.CompoundTag());
        var state = new net.minecraft.nbt.CompoundTag();
        state.putInt("level", 12);
        state.putInt("mailbox", 3);
        state.putBoolean("can_edit", true);
        var wallet = new net.minecraft.nbt.CompoundTag();
        wallet.putLong("rotas:gold", 250);
        state.put("wallet", wallet);

        var quests = new net.minecraft.nbt.ListTag();
        var quest = new net.minecraft.nbt.CompoundTag();
        quest.putString("id", "rotas:quest/hunt");
        quest.putInt("stage", 1);
        quest.putBoolean("available", false);
        quest.putBoolean("claimable", false);
        var objectives = new net.minecraft.nbt.CompoundTag();
        objectives.putInt("o0", 2);
        quest.put("objectives", objectives);
        quests.add(quest);
        state.put("quests", quests);

        var merchants = new net.minecraft.nbt.ListTag();
        var merchant = new net.minecraft.nbt.CompoundTag();
        merchant.putString("id", "rotas:merchant/smith");
        var remaining = new net.minecraft.nbt.CompoundTag();
        remaining.putInt("blade", 2);
        var purchased = new net.minecraft.nbt.CompoundTag();
        purchased.putInt("blade", 1);
        merchant.put("remaining", remaining);
        merchant.put("purchased", purchased);
        merchants.add(merchant);
        state.put("merchants", merchants);
        tag.put("state", state);

        ClientKernelState.apply(tag);
        assertEquals(12, ClientKernelState.level());
        assertEquals(3, ClientKernelState.mailbox());
        assertTrue(ClientKernelState.canEdit());
        assertEquals(250L, ClientKernelState.wallet().get("rotas:gold"));
        var questState = ClientKernelState.questState("rotas:quest/hunt");
        assertEquals(1, questState.stage());
        assertEquals(2, questState.objectives().get("o0"));
        var tradeState = ClientKernelState.tradeState("rotas:merchant/smith", "blade");
        assertEquals(2, tradeState.remaining());
        assertEquals(1, tradeState.purchased());
        assertNull(ClientKernelState.tradeState("rotas:merchant/smith", "missing"));
    }

    @Test void anUnreadyKernelClearsTheModelInsteadOfShowingStaleContent() throws Exception {
        var ready = new net.minecraft.nbt.CompoundTag();
        ready.putBoolean("ready", true);
        ready.put("catalog", KernelUi.catalog(content()));
        ready.put("state", new net.minecraft.nbt.CompoundTag());
        ClientKernelState.apply(ready);
        assertFalse(ClientKernelState.items().isEmpty());

        var loading = new net.minecraft.nbt.CompoundTag();
        loading.putBoolean("ready", false);
        ClientKernelState.apply(loading);
        assertFalse(ClientKernelState.ready());
        assertTrue(ClientKernelState.items().isEmpty());
        assertTrue(ClientKernelState.quests().isEmpty());
        assertTrue(ClientKernelState.merchants().isEmpty());
    }

    @Test void shortNameKeepsThePartThatDistinguishesAnId() {
        assertEquals("warden_blade", KernelUi.shortName("rotas:item/warden_blade"));
        assertEquals("gold", KernelUi.shortName("rotas:gold"));
        assertEquals("plain", KernelUi.shortName("plain"));
    }
}
