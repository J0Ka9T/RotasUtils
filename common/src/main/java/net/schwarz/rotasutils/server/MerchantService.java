package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.KernelContext;
import net.schwarz.rotasutils.core.MerchantDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MerchantService {
    public enum Result { TRADED, UNKNOWN_TRADE, UNAVAILABLE, INSUFFICIENT_FUNDS, MISSING_ITEMS, OUT_OF_STOCK, LIMIT_REACHED }

    private final MinecraftServer server;
    private final RpgKernel kernel;
    private long trades, refused, compensations;

    public MerchantService(MinecraftServer server, RpgKernel kernel) { this.server = server; this.kernel = kernel; }

    public MerchantDefinitions.Merchant merchant(ContentId id) {
        var merchant = kernel.content().merchants().get(id);
        if (merchant == null) { throw new IllegalArgumentException("Unknown or disabled merchant: " + id); }
        return merchant;
    }

    public int remaining(ContentId merchant, MerchantDefinitions.Trade trade) {
        if (!trade.limited()) { return -1; }
        long window = trade.window(now());
        long used = RotasData.get(server).counter(MerchantDefinitions.stockKey(merchant, trade.key()), window);
        return (int) Math.max(0, trade.stock() - used);
    }

    public int purchased(ServerPlayer player, ContentId merchant, MerchantDefinitions.Trade trade) {
        String stored = RotasData.get(server).progress(player.getUUID()).questVariables()
                .get(MerchantDefinitions.limitKey(merchant, trade.key()));
        return MerchantDefinitions.purchases(stored, trade.window(now()));
    }

    public List<String> list(ServerPlayer player, ContentId id) {
        var merchant = merchant(id);
        var context = new KernelPlayerContext(player, Map.of());
        List<String> lines = new ArrayList<>();
        if (!merchant.requirement().test(context)) { return List.of(); }
        for (var trade : merchant.trades()) {
            boolean open = trade.condition().test(context);
            lines.add(trade.key() + (trade.label().isEmpty() ? "" : " (" + trade.label() + ")")
                    + " stock=" + (trade.limited() ? remaining(id, trade) + "/" + trade.stock() : "unlimited")
                    + (trade.perPlayerLimit() > 0 ? " yours=" + purchased(player, id, trade) + "/" + trade.perPlayerLimit() : "")
                    + (open ? "" : " [locked]"));
        }
        return List.copyOf(lines);
    }

    public boolean available(ServerPlayer player, ContentId id, MerchantDefinitions.Trade trade) {
        var context = new KernelPlayerContext(player, Map.of());
        return merchant(id).requirement().test(context) && trade.condition().test(context);
    }

    public Result buy(ServerPlayer player, ContentId id, String tradeKey, int count) {
        return buy(player, id, tradeKey, count, 0);
    }

    public static long discounted(long amount, double discount) {
        double safe = Double.isFinite(discount) ? Math.max(0, Math.min(0.9, discount)) : 0;
        return Math.max(1, (long) Math.ceil(amount * (1 - safe)));
    }

    public Result buy(ServerPlayer player, ContentId id, String tradeKey, int count, double discount) {
        if (!server.isSameThread()) { throw new IllegalStateException("Trading requires the server thread"); }
        if (count < 1 || count > 64) { throw new IllegalArgumentException("Trade count must be 1..64"); }
        var merchant = merchant(id);
        MerchantDefinitions.Trade trade;
        try { trade = merchant.trade(tradeKey); }
        catch (IllegalArgumentException unknown) { refused++; return Result.UNKNOWN_TRADE; }

        var data = RotasData.get(server);
        var context = new KernelPlayerContext(player, Map.of());
        if (!merchant.requirement().test(context) || !trade.condition().test(context)) { refused++; return Result.UNAVAILABLE; }

        long window = trade.window(now());
        int alreadyBought = purchased(player, id, trade);
        if (trade.perPlayerLimit() > 0 && (long) alreadyBought + count > trade.perPlayerLimit()) { refused++; return Result.LIMIT_REACHED; }

        for (var cost : trade.costs()) {
            if (cost.item() != null && costItem(cost.item()) == null) {
                refused++;
                Rotasutils.LOG.warn("RPG merchant {} trade {} references unknown item {}",
                        id, trade.key(), cost.item());
                return Result.UNAVAILABLE;
            }
        }

        var progress = data.progress(player.getUUID());
        var payment = net.schwarz.rotasutils.core.MerchantPurchaseCosts.calculate(trade.costs(), count);
        var currencyCosts = new java.util.LinkedHashMap<String, Long>();
        payment.currencies().forEach((currency, amount) -> currencyCosts.put(currency, discount > 0 ? discounted(amount, discount) : amount));
        var itemCosts = payment.items();
        for (var cost : currencyCosts.entrySet()) {
            if (progress.rpg().currency(cost.getKey()) < cost.getValue()) {
                refused++;
                return Result.INSUFFICIENT_FUNDS;
            }
        }
        for (var cost : itemCosts.entrySet()) {
            if (countItems(player, costItem(cost.getKey())) < cost.getValue()) {
                refused++;
                return Result.MISSING_ITEMS;
            }
        }
        if (trade.limited() && !data.addCounter(MerchantDefinitions.stockKey(id, trade.key()), window, trade.stock(), count)) {
            refused++;
            return Result.OUT_OF_STOCK;
        }

        List<ItemStack> inventoryBefore = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            inventoryBefore.add(player.getInventory().getItem(slot).copy());
        }
        try {
            for (var cost : itemCosts.entrySet()) {
                takeItems(player, costItem(cost.getKey()), cost.getValue());
            }
            try (KernelContext.Transaction transaction = context.begin()) {
                transaction.seed("merchant|" + id + "|" + trade.key() + "|" + window + "|" + player.getUUID());
                for (var cost : currencyCosts.entrySet()) {
                    transaction.currency(cost.getKey(), -cost.getValue());
                }
                if (trade.perPlayerLimit() > 0) {
                    transaction.variable(MerchantDefinitions.limitKey(id, trade.key()),
                            MerchantDefinitions.purchaseValue(window, alreadyBought + count));
                }
                for (int i = 0; i < count; i++) {
                    if (trade.profile() != null) { transaction.profileItem(trade.profile().value(), trade.itemLevel(), trade.count()); }
                    else { transaction.item(trade.item(), trade.count()); }
                }
                transaction.commit();
            }
        } catch (RuntimeException failure) {
            compensate(player, inventoryBefore, data, id, trade, window, count);
            throw failure;
        }
        trades++;
        return Result.TRADED;
    }

    private void compensate(ServerPlayer player, List<ItemStack> inventoryBefore, RotasData data, ContentId id,
                            MerchantDefinitions.Trade trade, long window, int count) {
        compensations++;
        for (int slot = 0; slot < inventoryBefore.size(); slot++) {
            player.getInventory().setItem(slot, inventoryBefore.get(slot));
        }
        player.getInventory().setChanged();
        if (trade.limited()) {
            String key = MerchantDefinitions.stockKey(id, trade.key());
            long used = data.counter(key, window);
            data.addCounter(key, window, trade.stock(), 0);
            data.releaseCounter(key, window, Math.max(0, used - count));
        }
        Rotasutils.LOG.warn("RPG merchant trade rolled back for {}", player.getGameProfile().getName());
    }

    private static net.minecraft.world.item.Item costItem(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(location);
    }

    private int countItems(ServerPlayer player, net.minecraft.world.item.Item target) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(target) && !ItemFactory.isRpgItem(stack)) { total += stack.getCount(); }
        }
        return total;
    }

    private List<ItemStack> takeItems(ServerPlayer player, net.minecraft.world.item.Item target, long amount) {
        List<ItemStack> removed = new ArrayList<>();
        long left = amount;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(target) || ItemFactory.isRpgItem(stack)) { continue; }
            int take = (int) Math.min(left, stack.getCount());
            removed.add(stack.copyWithCount(take));
            stack.shrink(take);
            left -= take;
        }
        if (left > 0) {
            throw new IllegalStateException("Inventory changed while the trade was being paid");
        }
        return removed;
    }

    private long now() { return System.currentTimeMillis() / 1000L; }

    public String diagnostics() {
        return "Merchants trades=" + trades + " refused=" + refused + " rollbacks=" + compensations;
    }

    public CompoundTag describe(ContentId id, MerchantDefinitions.Trade trade) {
        CompoundTag tag = new CompoundTag();
        tag.putString("merchant", id.value());
        tag.putString("trade", trade.key());
        tag.putInt("remaining", remaining(id, trade));
        return tag;
    }
}
