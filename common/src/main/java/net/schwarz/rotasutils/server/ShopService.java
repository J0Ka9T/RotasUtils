package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.MerchantDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShopService {
    public record Result(boolean ok, String message) {
    }

    private ShopService() {
    }

    public static void open(ServerPlayer player, RotasData data, NpcDef npc) {
        RotasNetwork.openScreen(player, "shop", snapshot(player, data, npc));
    }

    private static double discount(RotasData data, ServerPlayer player) {
        return Math.max(0, Math.min(0.9, SeasonService.perk(data, data.progress(player.getUUID())).shopDiscount));
    }

    public static CompoundTag snapshot(ServerPlayer player, RotasData data, NpcDef npc) {
        CompoundTag tag = new CompoundTag();
        tag.putString("npc", npc.id());
        tag.putString("name", npc.name());
        tag.putString("title", npc.title());
        tag.putString("entity", npc.entityUuid());
        tag.putString("greeting", npc.greeting());
        double discount = discount(data, player);
        tag.putDouble("discount", discount);
        tag.putString("rank", SeasonService.active(data)
                ? SeasonService.rankName(data.progress(player.getUUID()), SeasonService.rules(data)) : "");
        ListTag offers = new ListTag();
        Set<String> currencies = new LinkedHashSet<>();
        for (int index = 0; index < npc.trades().size(); index++) {
            NpcDef.Trade trade = npc.trades().get(index);
            if (!trade.valid()) continue;
            CompoundTag offer = new CompoundTag();
            offer.putString("source", "npc");
            offer.putString("key", Integer.toString(index));
            offer.put("result", trade.result().save(new CompoundTag()));
            ListTag costs = new ListTag();
            costs.add(itemCost(player, trade.costA(), trade.costA().getCount()));
            if (!trade.costB().isEmpty()) costs.add(itemCost(player, trade.costB(), trade.costB().getCount()));
            offer.put("items", costs);
            offer.put("currencies", new ListTag());
            offer.putInt("stock", -1);
            offer.putInt("limit", -1);
            offer.putBoolean("open", true);
            offers.add(offer);
        }
        if (!npc.merchantId().isBlank() && data.kernel() != null) {
            try {
                ContentId merchantId = new ContentId(npc.merchantId());
                var service = data.kernel().merchants();
                var merchant = data.kernel().content().merchants().get(merchantId);
                if (merchant != null) {
                    tag.putString("merchant_label", merchant.label());
                    for (MerchantDefinitions.Trade trade : merchant.trades()) {
                        offers.add(kernelOffer(player, data, service, merchantId, trade, discount, currencies));
                    }
                }
            } catch (RuntimeException unavailable) {
                tag.putString("merchant_error", String.valueOf(unavailable.getMessage()));
            }
        }
        tag.put("offers", offers);
        CompoundTag wallet = new CompoundTag();
        for (String currency : currencies) {
            wallet.putLong(currency, data.progress(player.getUUID()).rpg().currency(currency));
        }
        tag.put("wallet", wallet);
        return tag;
    }

    private static CompoundTag kernelOffer(ServerPlayer player, RotasData data, MerchantService service, ContentId merchantId,
                                           MerchantDefinitions.Trade trade, double discount, Set<String> currencies) {
        CompoundTag offer = new CompoundTag();
        offer.putString("source", "kernel");
        offer.putString("key", trade.key());
        offer.putString("label", trade.label());
        ItemStack result = ItemStack.EMPTY;
        try {
            if (trade.profile() != null) {
                result = ItemFactory.create(data.kernel().content().items(), trade.profile(), trade.itemLevel(),
                        Math.max(1, Math.min(64, trade.count())), new java.util.SplittableRandom(trade.key().hashCode()));
            } else {
                Item item = item(trade.item());
                if (item != null) result = new ItemStack(item, Math.max(1, Math.min(64, trade.count())));
            }
        } catch (RuntimeException unknown) {
            result = ItemStack.EMPTY;
        }
        offer.put("result", result.save(new CompoundTag()));
        ListTag items = new ListTag();
        ListTag money = new ListTag();
        for (MerchantDefinitions.Cost cost : trade.costs()) {
            if (cost.item() != null) {
                Item item = item(cost.item());
                if (item != null) items.add(itemCost(player, new ItemStack(item), cost.amount()));
            } else {
                String currency = cost.currency().value();
                currencies.add(currency);
                CompoundTag entry = new CompoundTag();
                entry.putString("id", currency);
                entry.putLong("base", cost.amount());
                entry.putLong("amount", discount > 0 ? MerchantService.discounted(cost.amount(), discount) : cost.amount());
                entry.putLong("have", data.progress(player.getUUID()).rpg().currency(currency));
                money.add(entry);
            }
        }
        offer.put("items", items);
        offer.put("currencies", money);
        offer.putInt("stock", service.remaining(merchantId, trade));
        offer.putInt("stock_max", trade.stock());
        offer.putInt("limit", trade.perPlayerLimit() > 0 ? trade.perPlayerLimit() : -1);
        offer.putInt("bought", service.purchased(player, merchantId, trade));
        boolean open;
        try {
            open = service.available(player, merchantId, trade);
        } catch (RuntimeException failure) {
            open = false;
        }
        offer.putBoolean("open", open);
        return offer;
    }

    private static CompoundTag itemCost(ServerPlayer player, ItemStack cost, long amount) {
        CompoundTag entry = new CompoundTag();
        ItemStack shown = cost.copyWithCount(1);
        entry.put("item", shown.save(new CompoundTag()));
        entry.putLong("amount", amount);
        entry.putLong("have", count(player, cost));
        return entry;
    }

    private static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ITEM.containsKey(location) ? BuiltInRegistries.ITEM.get(location) : null;
    }

    private static boolean pays(ItemStack stack, ItemStack cost) {
        if (stack.isEmpty() || !stack.is(cost.getItem()) || ItemFactory.isRpgItem(stack)) return false;
        return !cost.hasTag() || ItemStack.isSameItemSameTags(stack, cost);
    }

    private static long count(ServerPlayer player, ItemStack cost) {
        long total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (pays(stack, cost)) total += stack.getCount();
        }
        return total;
    }

    public static Result buy(ServerPlayer player, RotasData data, NpcDef npc, String source, String key, int count) {
        if ("kernel".equals(source)) {
            if (npc.merchantId().isBlank() || data.kernel() == null) {
                return new Result(false, ThaiText.t("rotasutils.msg.shop.unavailable"));
            }
            MerchantService.Result result;
            try {
                result = data.kernel().merchants().buy(player, new ContentId(npc.merchantId()), key, count, discount(data, player));
            } catch (RuntimeException failure) {
                return new Result(false, ThaiText.t("rotasutils.msg.shop.unavailable"));
            }
            if (result == MerchantService.Result.TRADED) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7f, 1.0f);
            }
            return new Result(result == MerchantService.Result.TRADED,
                    ThaiText.t("rotasutils.msg.shop.result." + result.name().toLowerCase(java.util.Locale.ROOT)));
        }
        int index;
        try {
            index = Integer.parseInt(key);
        } catch (NumberFormatException malformed) {
            return new Result(false, ThaiText.t("rotasutils.msg.shop.result.unknown_trade"));
        }
        if (index < 0 || index >= npc.trades().size() || !npc.trades().get(index).valid()) {
            return new Result(false, ThaiText.t("rotasutils.msg.shop.result.unknown_trade"));
        }
        return barter(player, npc.trades().get(index), count);
    }

    private static Result barter(ServerPlayer player, NpcDef.Trade trade, int count) {
        List<ItemStack> costs = new ArrayList<>();
        costs.add(trade.costA());
        if (!trade.costB().isEmpty()) costs.add(trade.costB());
        for (ItemStack cost : costs) {
            long needed = (long) cost.getCount() * count;
            for (ItemStack other : costs) {
                if (other != cost && ItemStack.isSameItemSameTags(other, cost)) needed += (long) other.getCount() * count;
            }
            if (count(player, cost) < needed) {
                return new Result(false, ThaiText.t("rotasutils.msg.shop.result.missing_items"));
            }
        }
        var inventory = player.getInventory();
        for (ItemStack cost : costs) {
            long left = (long) cost.getCount() * count;
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!pays(stack, cost)) continue;
                int take = (int) Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        inventory.setChanged();
        for (int i = 0; i < count; i++) {
            RewardService.give(player, trade.result().copy());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7f, 1.0f);
        return new Result(true, ThaiText.t("rotasutils.msg.shop.result.traded"));
    }
}
