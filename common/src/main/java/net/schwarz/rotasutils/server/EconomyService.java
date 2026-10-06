package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.TradeBook;
import net.schwarz.rotasutils.core.WorthTable;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.LinkedHashSet;
import java.util.Set;

public final class EconomyService {
    private EconomyService() {
    }

public static void openSell(ServerPlayer player) {
        CompoundTag payload = new CompoundTag();
        ListTag rows = new ListTag();
        var items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            long value = WorthService.sellValue(stack);
            if (stack.isEmpty() || value <= 0 || ItemFactory.isRpgItem(stack)) {
                continue;
            }
            CompoundTag row = new CompoundTag();
            row.putInt("slot", slot);
            row.put("item", stack.save(new CompoundTag()));
            row.putLong("gold", value);
            rows.add(row);
        }
        payload.put("rows", rows);
        payload.putInt("sell", WorthService.table().sellPercent());
        payload.putLong("balance", RotasData.get(player.server).progress(player.getUUID()).rpg().currency(GoldCoinService.CURRENCY));
        RotasNetwork.openScreen(player, "sell", payload);
    }

    public static void sell(ServerPlayer player, int slot) {
        var items = player.getInventory().items;
        long total = 0;
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            if (slot >= 0 && i != slot) {
                continue;
            }
            ItemStack stack = items.get(i);
            long value = WorthService.sellValue(stack);
            if (stack.isEmpty() || value <= 0 || ItemFactory.isRpgItem(stack)) {
                continue;
            }
            if (!GoldCoinService.deposit(player, value)) {
                RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.sell.wallet_full"));
                break;
            }
            total += value;
            count += stack.getCount();
            items.set(i, ItemStack.EMPTY);
        }
        if (count == 0) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.sell.nothing"));
        } else {
            RotasData data = RotasData.get(player.server);
            data.audit(player.getGameProfile().getName() + " sold " + count + " items for " + total);
            TitleService.count(player.server, data, player.getUUID(), net.schwarz.rotasutils.title.TitleCounters.TRADE_GOLD, total);
            data.setDirty();
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
            RotasNetwork.feedback(player, true, ThaiText.t("rotasutils.sell.done", count, total));
            RotasNetwork.syncProgress(player);
        }
        openSell(player);
    }

public static void openWorth(ServerPlayer player, String select) {
        WorthTable table = WorthService.table();
        CompoundTag payload = WorthService.toTag();
        payload.putString("select", select == null ? "" : select);
        Set<String> needs = new LinkedHashSet<>();
        for (TradeBook book : TradeConfig.all()) {
            for (TradeBook.Recipe recipe : book.recipes()) {
                recipe.outputs().forEach(o -> needs.add(o.item()));
                recipe.ingredients().forEach(i -> needs.add(i.icon()));
            }
        }
        ListTag list = new ListTag();
        for (String id : needs) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (!id.isEmpty() && table.prices().get(id) == null && location != null && BuiltInRegistries.ITEM.containsKey(location)) {
                list.add(StringTag.valueOf(id));
            }
        }
        payload.put("needs", list);
        RotasNetwork.openScreen(player, "worth", payload);
    }

    public static void setWorth(ServerPlayer player, String item, long gold) {
        ResourceLocation location = ResourceLocation.tryParse(item);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.worth.bad_item"));
            return;
        }
        try {
            WorthService.set(player.server, item, Math.max(0, Math.min(WorthTable.MAX_PRICE, gold)));
        } catch (IllegalArgumentException refused) {
            RotasNetwork.feedback(player, false, refused.getMessage());
            return;
        }
        RotasData.get(player.server).audit(player.getGameProfile().getName() + " priced " + item + " at " + gold);
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            RotasNetwork.syncContent(online);
        }
        openWorth(player, item);
    }
}
