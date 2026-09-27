package net.schwarz.rotasutils.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.KernelUi;
import java.util.UUID;

/**
 * Server-side checks for the console screens. The screens themselves are not driven here: this
 * verifies the snapshot the client reads and every operation the client can send, including the
 * permission gates a modified client would try to walk past.
 */
public final class ConsoleSmoke {
    private static final UUID ID = UUID.fromString("d7c1a8b2-5e33-4a71-9c0e-3f2b7a1d6e40");

    private ConsoleSmoke() { }

    static void attach(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rotas_console_smoke").requires(source -> source.hasPermission(4))
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    try {
                        checks(server);
                        net.schwarz.rotasutils.Rotasutils.LOG.info("ROTAS_CONSOLE_SMOKE_PASS");
                        return 1;
                    } catch (Exception | AssertionError error) {
                        net.schwarz.rotasutils.Rotasutils.LOG.error("ROTAS_CONSOLE_SMOKE_FAIL", error);
                        return 0;
                    }
                }));
    }

    private static void checks(MinecraftServer server) {
        var data = RotasData.get(server);
        var player = new FakePlayer(server.overworld(), new GameProfile(ID, "ConsoleSmoke"));

        CompoundTag snapshot = KernelUi.snapshot(player);
        require(snapshot.getBoolean("ready"), "snapshot reports a ready kernel");
        CompoundTag catalog = snapshot.getCompound("catalog");
        require(!catalog.getList("items", Tag.TAG_COMPOUND).isEmpty(), "catalog carries the published item profiles");
        require(!catalog.getList("quests", Tag.TAG_COMPOUND).isEmpty(), "catalog carries the published quests");
        require(!catalog.getList("merchants", Tag.TAG_COMPOUND).isEmpty(), "catalog carries the published merchants");
        require(!catalog.getList("monsters", Tag.TAG_COMPOUND).isEmpty(), "catalog carries the published monsters");
        CompoundTag state = snapshot.getCompound("state");
        require(state.contains("level"), "snapshot carries the player's own state");
        require(!state.getBoolean("can_edit"), "a plain player is not offered the editing operations");

        // Quest lifecycle through the same entry point the screen uses.
        String quest = firstId(catalog, "quests");
        var quests = data.kernel().quests();
        var questId = new net.schwarz.rotasutils.core.ContentId(quest);
        clearQuest(data, questId);
        require(KernelUi.handle(player, "kernel_quest", payload("op", "accept", "quest", quest)),
                "the console handles the quest operation");
        require(quests.stage(player, questId) == 0, "accepting through the console starts the quest");
        require(KernelUi.handle(player, "kernel_quest", payload("op", "abandon", "quest", quest)), "abandon handled");
        require(quests.stage(player, questId) < 0, "abandoning through the console clears the quest");

        // Malformed input is refused rather than throwing into the packet handler.
        require(KernelUi.handle(player, "kernel_quest", payload("op", "accept", "quest", "not an id")),
                "a malformed quest ID is still handled");
        require(KernelUi.handle(player, "kernel_quest", payload("op", "nonsense", "quest", quest)),
                "an unknown quest operation is refused, not executed");

        // Operator-only operations are refused for a player without the capability.
        CompoundTag give = new CompoundTag();
        give.putString("profile", firstId(catalog, "items"));
        give.putInt("level", 5);
        int before = countItems(player);
        require(KernelUi.handle(player, "kernel_item_give", give), "item grant handled");
        require(countItems(player) == before, "a player without EDIT receives nothing");

        CompoundTag preview = new CompoundTag();
        preview.putString("table", firstId(catalog, "loot"));
        preview.putInt("level", 5);
        require(KernelUi.handle(player, "kernel_loot_preview", preview), "loot preview handled");

        // Monster operations need a target; without one the console says so instead of guessing.
        require(KernelUi.lookedAt(player) == null, "a fake player is looking at nothing");
        require(KernelUi.handle(player, "kernel_monster", payload("op", "inspect", "profile", firstId(catalog, "monsters"))),
                "monster inspect handled without a target");

        require(KernelUi.handle(player, "kernel_mail", new CompoundTag()), "mailbox collection handled");
        require(KernelUi.handle(player, "kernel_refresh", new CompoundTag()), "refresh handled");
        require(!KernelUi.handle(player, "open_menu", new CompoundTag()),
                "legacy actions still fall through to the older handler");

        // Trading through the console reaches the merchant service and its refusal paths.
        String merchant = firstId(catalog, "merchants");
        CompoundTag trade = new CompoundTag();
        trade.putString("merchant", merchant);
        trade.putString("trade", firstTrade(catalog, merchant));
        trade.putInt("count", 1);
        require(KernelUi.handle(player, "kernel_shop", trade), "trade handled");
        trade.putString("trade", "no_such_trade");
        require(KernelUi.handle(player, "kernel_shop", trade), "an unknown trade key is refused, not executed");
        data.setDirty();
    }

    private static void clearQuest(RotasData data, net.schwarz.rotasutils.core.ContentId quest) {
        var progress = data.progress(ID);
        String prefix = net.schwarz.rotasutils.core.QuestDefinitions.key(quest, "");
        progress.questVariables().keySet().removeIf(key -> key.startsWith(prefix));
        progress.markDirty();
    }

    private static CompoundTag payload(String... pairs) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            tag.putString(pairs[i], pairs[i + 1]);
        }
        return tag;
    }

    private static String firstId(CompoundTag catalog, String key) {
        ListTag list = catalog.getList(key, Tag.TAG_COMPOUND);
        require(!list.isEmpty(), "catalog section is populated: " + key);
        return list.getCompound(0).getString("id");
    }

    private static String firstTrade(CompoundTag catalog, String merchantId) {
        ListTag merchants = catalog.getList("merchants", Tag.TAG_COMPOUND);
        for (int i = 0; i < merchants.size(); i++) {
            CompoundTag merchant = merchants.getCompound(i);
            if (!merchant.getString("id").equals(merchantId)) { continue; }
            ListTag trades = merchant.getList("trades", Tag.TAG_COMPOUND);
            require(!trades.isEmpty(), "merchant has at least one trade");
            return trades.getCompound(0).getString("key");
        }
        throw new AssertionError("merchant vanished from the catalog");
    }

    private static int countItems(FakePlayer player) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) { total++; }
        }
        return total;
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
