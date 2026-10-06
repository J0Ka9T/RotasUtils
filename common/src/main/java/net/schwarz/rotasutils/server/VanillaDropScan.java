package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.schwarz.rotasutils.Rotasutils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaDropScan {
    private static final int ROLLS = 300;
    private static final int MAX_ITEMS = 64;

    private static final Map<String, List<Drop>> CACHE = new ConcurrentHashMap<>();

    private VanillaDropScan() {
    }

    public record Drop(String item, int maxCount, double chance) {
    }

    public static void clear() {
        CACHE.clear();
    }

    public static List<Drop> scan(ServerLevel level, ServerPlayer killer, String entityId) {
        List<Drop> cached = CACHE.get(entityId);
        if (cached != null) {
            return cached;
        }
        List<Drop> drops = List.of();
        try {
            drops = roll(level, killer, entityId);
        } catch (RuntimeException failure) {
            Rotasutils.LOG.warn("Could not scan the drops of {}: {}", entityId, failure.toString());
        }
        CACHE.put(entityId, drops);
        return drops;
    }

    private static List<Drop> roll(ServerLevel level, ServerPlayer killer, String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null) {
            return List.of();
        }
        ResourceLocation table = type.getDefaultLootTable();
        if (table == null || table.equals(emptyTable())) {
            return List.of();
        }
        LootTable loot = level.getServer().getLootData().getLootTable(table);
        if (loot == LootTable.EMPTY) {
            return List.of();
        }
        Entity entity = type.create(level);
        if (entity == null) {
            return List.of();
        }
        try {
            entity.moveTo(killer.getX(), killer.getY(), killer.getZ());
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, entity)
                    .withParameter(LootContextParams.ORIGIN, entity.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .create(LootContextParamSets.ENTITY);

            Map<String, int[]> seen = new LinkedHashMap<>();
            for (int roll = 0; roll < ROLLS; roll++) {
                for (ItemStack stack : loot.getRandomItems(params)) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    String item = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    int[] counts = seen.computeIfAbsent(item, key -> new int[2]);
                    counts[0]++;
                    counts[1] = Math.max(counts[1], stack.getCount());
                    if (seen.size() >= MAX_ITEMS) {
                        break;
                    }
                }
            }
            List<Drop> drops = new ArrayList<>();
            seen.forEach((item, counts) -> drops.add(new Drop(item, counts[1],
                    Math.min(1.0, counts[0] / (double) ROLLS))));
            drops.sort((left, right) -> Double.compare(right.chance(), left.chance()));
            return List.copyOf(drops);
        } finally {
            entity.discard();
        }
    }

    private static ResourceLocation emptyTable() {
        return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;
    }
}
