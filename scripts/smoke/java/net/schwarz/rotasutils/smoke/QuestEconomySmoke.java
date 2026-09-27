package net.schwarz.rotasutils.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ItemFactory;
import net.schwarz.rotasutils.server.LootService;
import net.schwarz.rotasutils.server.MerchantService;
import net.schwarz.rotasutils.server.QuestKernelService;
import net.schwarz.rotasutils.server.RpgKernel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dedicated-server checks for kernel quests, bounty limits, merchant stock and trade atomicity. */
public final class QuestEconomySmoke {
    private static final UUID ONE = UUID.fromString("b41a3f6c-3c9f-4c1e-8a1a-0d5a4c1f7b31");
    private static final UUID TWO = UUID.fromString("c52b4a7d-4d0a-4d2f-9b2b-1e6b5d2a8c42");
    private static final String ACTOR = "smoke-quest";
    private static final ContentId QUEST = new ContentId("rotas:quest/smoke_hunt");
    private static final ContentId MERCHANT = new ContentId("rotas:merchant/smoke");
    private static final ContentId EVENT = new ContentId("rotas:smoke_event");
    private static final List<String> DEFINITIONS = List.of(
            "{\"schema\":1,\"id\":\"rotas:action/smoke_quest_coin\",\"kind\":\"action\",\"body\":{\"type\":\"currency\",\"id\":\"rotas:quest_coin\",\"amount\":50}}",
            "{\"schema\":1,\"id\":\"rotas:reward/smoke_quest\",\"kind\":\"reward\",\"body\":{\"actions\":[\"rotas:action/smoke_quest_coin\"]}}",
            "{\"schema\":1,\"id\":\"rotas:quest/smoke_hunt\",\"kind\":\"quest\",\"body\":{\"label\":\"Smoke Hunt\",\"reset\":\"DAILY\",\"bounty_limit\":1,"
                    + "\"reward\":\"rotas:reward/smoke_quest\",\"stages\":["
                    + "{\"label\":\"Hunt\",\"objectives\":[{\"event\":\"rotas:smoke_event\",\"count\":2,\"match\":{\"event.kind\":\"hunt\"}}]},"
                    + "{\"label\":\"Return\",\"objectives\":[{\"event\":\"rotas:smoke_event\",\"count\":1,\"match\":{\"event.kind\":\"return\"}}]}]}}",
            "{\"schema\":1,\"id\":\"rotas:merchant/smoke\",\"kind\":\"merchant\",\"body\":{\"label\":\"Smoke Trader\",\"trades\":["
                    + "{\"key\":\"bread\",\"item\":\"minecraft:bread\",\"count\":2,\"stock\":2,\"restock_seconds\":3600,\"per_player_limit\":1,"
                    + "\"costs\":[{\"currency\":\"rotas:quest_coin\",\"amount\":10}]},"
                    + "{\"key\":\"trade_up\",\"item\":\"minecraft:diamond\",\"count\":1,"
                    + "\"costs\":[{\"item\":\"minecraft:iron_ingot\",\"amount\":4},{\"currency\":\"rotas:quest_coin\",\"amount\":5}]}]}}");

    private QuestEconomySmoke() { }

    static void attach(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rotas_quest_economy_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    var kernel = RotasData.get(server).kernel();
                    if (kernel.content().quests().containsKey(QUEST)) { return report(server); }
                    if (kernel.history().draft(ACTOR) != null) { kernel.history().discard(ACTOR); }
                    kernel.history().begin(ACTOR);
                    try {
                        for (String definition : DEFINITIONS) { kernel.history().put(ACTOR, ContentPacks.parse(definition)); }
                    } catch (java.io.IOException error) { throw new IllegalArgumentException(error); }
                    require(kernel.validateDraft(ACTOR, true, () -> true, result -> {
                        if (!result.valid()) {
                            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_QUEST_ECONOMY_SMOKE_FAIL {}", result.issues());
                            return;
                        }
                        report(server);
                    }), "quest and merchant publication scheduled");
                    return 1;
                }));
    }

    private static int report(MinecraftServer server) {
        try {
            quests(server);
            merchants(server);
            net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_QUEST_ECONOMY_SMOKE_PASS");
            return 1;
        } catch (Exception | AssertionError error) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_QUEST_ECONOMY_SMOKE_FAIL", error);
            return 0;
        }
    }

    private static void quests(MinecraftServer server) {
        var data = RotasData.get(server);
        var service = data.kernel().quests();
        var player = new FakePlayer(server.overworld(), new GameProfile(ONE, "QuestSmokeOne"));
        // Quest state and receipts persist, so every run starts from a clean slate for this player.
        reset(data, ONE);
        reset(data, TWO);

        require(service.available(player).contains(QUEST), "a fresh quest is offered");
        require(service.accept(player, QUEST) == QuestKernelService.Result.ACCEPTED, "accepting a quest");
        require(service.stage(player, QUEST) == 0, "accepted quest starts at stage 0");
        require(service.accept(player, QUEST) == QuestKernelService.Result.ALREADY_ACTIVE, "a quest cannot be accepted twice");
        require(!service.available(player).contains(QUEST), "an active quest is not offered again");

        RpgKernel.emit(player, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "other"));
        require(service.progress(player, QUEST, "o0") == 0, "a non-matching event does not advance an objective");
        RpgKernel.emit(player, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "hunt"));
        require(service.progress(player, QUEST, "o0") == 1, "a matching event advances the objective");
        require(service.stage(player, QUEST) == 0, "the stage holds until every objective is complete");
        RpgKernel.emit(player, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "hunt"));
        require(service.stage(player, QUEST) == 1, "completing the objectives advances the stage");
        require(service.progress(player, QUEST, "o0") == 0, "the new stage starts with cleared counters");

        RpgKernel.emit(player, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "hunt"));
        require(service.stage(player, QUEST) == 1, "stage two ignores the previous stage's event");
        RpgKernel.emit(player, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "return"));
        require(service.stage(player, QUEST) == -1, "the final stage completes the quest");
        require(service.completedAt(player, QUEST) > 0, "completion is stamped");
        require(!service.available(player).contains(QUEST), "a daily quest is not available again the same day");

        long before = data.progress(ONE).rpg().currency("rotas:quest_coin");
        require(service.claim(player, QUEST) == QuestKernelService.Result.CLAIMED, "claiming the completion reward");
        require(data.progress(ONE).rpg().currency("rotas:quest_coin") == before + 50, "claim paid the reward once");
        require(service.claim(player, QUEST) == QuestKernelService.Result.UNAVAILABLE, "the receipt blocks a second claim");
        require(data.progress(ONE).rpg().currency("rotas:quest_coin") == before + 50, "a blocked claim pays nothing");

        // The bounty limit is world-wide for the window, so the second player is refused.
        var second = new FakePlayer(server.overworld(), new GameProfile(TWO, "QuestSmokeTwo"));
        require(service.accept(second, QUEST) == QuestKernelService.Result.ACCEPTED, "second player accepts");
        RpgKernel.emit(second, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "hunt"));
        RpgKernel.emit(second, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "hunt"));
        RpgKernel.emit(second, EVENT.value(), UUID.randomUUID().toString(), Map.of("event.kind", "return"));
        require(service.stage(second, QUEST) == -1, "second player completed the quest");
        long secondBefore = data.progress(TWO).rpg().currency("rotas:quest_coin");
        require(service.claim(second, QUEST) == QuestKernelService.Result.LIMIT_REACHED, "the bounty limit refuses the claim");
        require(data.progress(TWO).rpg().currency("rotas:quest_coin") == secondBefore, "a refused bounty pays nothing");

        require(service.accept(player, QUEST) == QuestKernelService.Result.UNAVAILABLE, "the daily reset gate holds");
        data.setDirty();
    }

    private static void merchants(MinecraftServer server) {
        var data = RotasData.get(server);
        var service = data.kernel().merchants();
        var one = new FakePlayer(server.overworld(), new GameProfile(ONE, "QuestSmokeOne"));
        var two = new FakePlayer(server.overworld(), new GameProfile(TWO, "QuestSmokeTwo"));
        one.getInventory().clearContent();
        two.getInventory().clearContent();
        var trade = service.merchant(MERCHANT).trade("bread");
        // Stock lives in a world counter, so a fresh window is needed for a repeatable check.
        data.releaseCounter(net.schwarz.rotasutils.core.MerchantDefinitions.stockKey(MERCHANT, "bread"),
                trade.window(System.currentTimeMillis() / 1000L), 0);
        clearLimit(data, ONE);
        clearLimit(data, TWO);
        // The quest claim already paid this player, so the wallet is set rather than topped up.
        setBalance(data, ONE, 100);
        setBalance(data, TWO, 100);

        require(service.remaining(MERCHANT, trade) == 2, "stock starts full: " + service.remaining(MERCHANT, trade));
        require(service.buy(one, MERCHANT, "bread", 1) == MerchantService.Result.TRADED, "first purchase");
        require(count(one, Items.BREAD) == 2, "the trade delivered its result count");
        require(data.progress(ONE).rpg().currency("rotas:quest_coin") == 90, "currency debited exactly once");
        require(service.remaining(MERCHANT, trade) == 1, "stock decreased");
        require(service.buy(one, MERCHANT, "bread", 1) == MerchantService.Result.LIMIT_REACHED, "per-player limit refuses a second buy");
        require(data.progress(ONE).rpg().currency("rotas:quest_coin") == 90, "a refused trade costs nothing");
        require(service.buy(two, MERCHANT, "bread", 1) == MerchantService.Result.TRADED, "another player may still buy");
        require(service.remaining(MERCHANT, trade) == 0, "stock is exhausted");
        clearLimit(data, ONE);
        require(service.buy(one, MERCHANT, "bread", 1) == MerchantService.Result.OUT_OF_STOCK, "an empty stock refuses the trade");
        require(data.progress(ONE).rpg().currency("rotas:quest_coin") == 90, "an out-of-stock trade costs nothing");

        // Item costs: the ingots leave the inventory, the result arrives, and nothing partially applies.
        one.getInventory().clearContent();
        one.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        require(service.buy(one, MERCHANT, "trade_up", 1) == MerchantService.Result.TRADED, "item cost trade");
        require(count(one, Items.IRON_INGOT) == 0, "item cost consumed");
        require(count(one, Items.DIAMOND) == 1, "item cost trade delivered");
        require(data.progress(ONE).rpg().currency("rotas:quest_coin") == 85, "the mixed cost also debited currency");
        require(service.buy(one, MERCHANT, "trade_up", 1) == MerchantService.Result.MISSING_ITEMS, "missing items refuse the trade");
        require(count(one, Items.DIAMOND) == 1, "a refused item trade delivers nothing");

        // An RPG stack is not a plain ingot, so it cannot pay a vanilla item cost.
        one.getInventory().clearContent();
        var catalog = data.kernel().content().items();
        if (catalog.profiles().containsKey(new ContentId("rotas:item/smoke_sword"))) {
            one.getInventory().add(ItemFactory.create(catalog, new ContentId("rotas:item/smoke_sword"), 1, 1, LootService.random("merchant|exploit")));
        }
        one.getInventory().add(new ItemStack(Items.IRON_INGOT, 3));
        require(service.buy(one, MERCHANT, "trade_up", 1) == MerchantService.Result.MISSING_ITEMS, "partial payment is refused");
        require(count(one, Items.IRON_INGOT) == 3, "a refused trade leaves the payment untouched");

        setBalance(data, ONE, 0);
        one.getInventory().clearContent();
        one.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        require(service.buy(one, MERCHANT, "trade_up", 1) == MerchantService.Result.INSUFFICIENT_FUNDS, "empty wallet refuses the trade");
        require(count(one, Items.IRON_INGOT) == 4, "an unaffordable trade keeps the item cost");
        require(service.buy(one, MERCHANT, "missing", 1) == MerchantService.Result.UNKNOWN_TRADE, "an unknown trade key is refused");
        one.getInventory().clearContent();
        two.getInventory().clearContent();
        data.setDirty();
    }

    private static void reset(RotasData data, UUID id) {
        var progress = data.progress(id);
        progress.questVariables().keySet().removeIf(key -> key.startsWith("rpg.q.rotas.quest-smoke_hunt"));
        progress.claimedRewards().removeIf(receipt -> receipt.contains("rotas:quest/smoke_hunt"));
        progress.markDirty();
        data.releaseCounter("bounty|" + QUEST, data.kernel().content().quests().get(QUEST)
                .window(System.currentTimeMillis() / 1000L), 0);
    }

    private static void clearLimit(RotasData data, UUID id) {
        var progress = data.progress(id);
        progress.questVariables().remove(net.schwarz.rotasutils.core.MerchantDefinitions.limitKey(MERCHANT, "bread"));
        progress.markDirty();
    }

    private static void setBalance(RotasData data, UUID id, long balance) {
        var progress = data.progress(id);
        long delta = balance - progress.rpg().currency("rotas:quest_coin");
        if (delta != 0) { progress.rpg().currency("rotas:quest_coin", delta); progress.markDirty(); }
    }

    private static int count(FakePlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) { total += stack.getCount(); }
        }
        return total;
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
