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

/**
 * What a mob normally drops, worked out by asking the game rather than by reading its loot table.
 *
 * <p>A loot table is a tree of pools, conditions and functions; reading it back into "this mob drops
 * rotten flesh" means re-implementing half of vanilla and still getting modded tables wrong. So this
 * rolls the real table a few hundred times against a throwaway copy of the mob and reports what
 * actually came out, with how often. That is also what an administrator wants to see: the drops as
 * players meet them, not the theory.</p>
 *
 * <p>The throwaway entity is never added to the world and is discarded straight after, and the result
 * is cached per entity type for as long as the server runs.</p>
 */
public final class VanillaDropScan {
    /** Rolls per scan. Enough for a 1-in-100 drop to show up, cheap enough to run while a screen opens. */
    private static final int ROLLS = 300;
    /** A scan lists at most this many different items. */
    private static final int MAX_ITEMS = 64;

    private static final Map<String, List<Drop>> CACHE = new ConcurrentHashMap<>();

    private VanillaDropScan() {
    }

    /** One item a mob was seen to drop. */
    public record Drop(String item, int maxCount, double chance) {
    }

    /** Forgets every scan; called when the server stops or content reloads. */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * What this entity type drops, from cache when it has been asked before. Returns an empty list when
     * the mob has no loot table, cannot be built, or simply never drops anything.
     */
    public static List<Drop> scan(ServerLevel level, ServerPlayer killer, String entityId) {
        List<Drop> cached = CACHE.get(entityId);
        if (cached != null) {
            return cached;
        }
        List<Drop> drops = List.of();
        try {
            drops = roll(level, killer, entityId);
        } catch (RuntimeException failure) {
            // A modded mob that cannot be built off-world must not take the screen down with it.
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

    /** The loot table id that means "this entity drops nothing". */
    private static ResourceLocation emptyTable() {
        return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;
    }
}
