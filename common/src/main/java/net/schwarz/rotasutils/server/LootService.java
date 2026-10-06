package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ItemCatalog;
import net.schwarz.rotasutils.core.ItemDefinitions;
import net.schwarz.rotasutils.core.KernelContext;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.UUID;

public final class LootService {
    public static final int MAX_STACKS = 64;
    private LootService() { }

    public static SplittableRandom random(String seed) {
        UUID id = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        return new SplittableRandom(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }

    public static List<ItemStack> roll(ItemCatalog catalog, ContentId tableId, String seed, int level,
                                       double multiplier, KernelContext context) {
        var table = catalog.loot().get(tableId);
        if (table == null) { throw new IllegalArgumentException("Unknown or disabled loot table: " + tableId); }
        SplittableRandom random = random(seed + "|" + tableId);
        Map<Integer, Integer> pool = new TreeMap<>();
        for (int index = 0; index < table.entries().size(); index++) {
            var entry = table.entries().get(index);
            if (context == null || entry.condition().test(context)) { pool.put(index, entry.weight()); }
        }
        List<ItemStack> stacks = new ArrayList<>();
        if (pool.isEmpty()) { return List.of(); }
        int rolls = table.rolls(random, multiplier);
        for (int i = 0; i < rolls && stacks.size() < MAX_STACKS; i++) {
            var entry = table.entries().get(MonsterDefinitions.weighted(pool, random));
            int count = entry.count(random);
            if (entry.profile() != null) {
                stacks.add(ItemFactory.create(catalog, entry.profile(), level, count, random));
            } else {
                var item = BuiltInRegistries.ITEM.get(new ResourceLocation(entry.item()));
                if (item == net.minecraft.world.item.Items.AIR) { throw new IllegalArgumentException("Unknown loot item: " + entry.item()); }
                stacks.add(new ItemStack(item, Math.min(count, item.getMaxStackSize())));
            }
        }
        return List.copyOf(stacks);
    }

    public static boolean grantOnce(ServerPlayer player, RotasData data, ContentId tableId, String occurrence,
                                    int level, double multiplier) {
        if (data.kernel() == null) { throw new IllegalStateException("RPG kernel is not ready"); }
        var context = new KernelPlayerContext(player, Map.of());
        String receipt = "kernel|loot|" + tableId + "|" + occurrence;
        try (KernelContext.Transaction transaction = context.begin()) {
            transaction.seed(receipt);
            if (transaction.claimed(receipt)) { return false; }
            transaction.loot(tableId.value(), level, multiplier);
            transaction.claim(receipt);
            transaction.commit();
            return true;
        }
    }

    public static boolean canDeliver(ServerPlayer player, List<ItemStack> stacks) {
        var inventory = new net.minecraft.world.entity.player.Inventory(player);
        var original = player.getInventory();
        inventory.selected = original.selected;
        for (int slot = 0; slot < original.getContainerSize(); slot++) {
            inventory.setItem(slot, original.getItem(slot).copy());
        }
        int mailSlots = net.schwarz.rotasutils.progress.PlayerProgress.MAILBOX_LIMIT
                - RotasData.get(player.server).progress(player.getUUID()).mailbox().size();
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            if (inventory.add(copy) && copy.isEmpty()) continue;
            if (--mailSlots < 0) return false;
        }
        return true;
    }

    public static int deliver(ServerPlayer player, List<ItemStack> stacks) {
        var progress = RotasData.get(player.server).progress(player.getUUID());
        int stored = 0;
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            if (player.getInventory().add(copy) && copy.isEmpty()) { continue; }
            progress.addMail(copy.save(new CompoundTag()));
            stored++;
        }
        return stored;
    }

    public static int recover(ServerPlayer player) {
        var progress = RotasData.get(player.server).progress(player.getUUID());
        List<CompoundTag> pending = progress.drainMail();
        int delivered = 0;
        List<ItemStack> leftovers = new ArrayList<>();
        for (CompoundTag tag : pending) {
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty()) { continue; }
            ItemStack copy = stack.copy();
            if (player.getInventory().add(copy) && copy.isEmpty()) { delivered++; } else { leftovers.add(copy); }
        }
        leftovers.forEach(stack -> progress.addMail(stack.save(new CompoundTag())));
        return delivered;
    }

    public static List<String> preview(ItemCatalog catalog, ContentId tableId, String seed, int level, double multiplier) {
        List<String> lines = new ArrayList<>();
        for (ItemStack stack : roll(catalog, tableId, seed, level, multiplier, null)) {
            ContentId profile = ItemFactory.profileId(stack);
            lines.add(stack.getCount() + "x " + stack.getHoverName().getString()
                    + (profile == null ? "" : " (" + profile + " ilvl " + ItemFactory.level(stack)
                    + " " + ItemFactory.rarity(stack) + ")"));
        }
        return List.copyOf(lines);
    }

    public static void checkBuildable(ItemCatalog catalog, ItemDefinitions.LootTable table, List<String> issues) {
        table.entries().forEach(entry -> {
            if (entry.item() != null && !BuiltInRegistries.ITEM.containsKey(new ResourceLocation(entry.item()))) {
                issues.add("Unknown loot item: " + entry.item());
            }
            if (entry.profile() != null && !catalog.profiles().containsKey(entry.profile())) {
                issues.add("Unknown loot profile: " + entry.profile());
            }
        });
    }
}
