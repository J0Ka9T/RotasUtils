package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.DropGrade;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/** Rolls what one grade of drop is worth. Pure content: nothing here touches a player. */
public final class DropLoot {
    /** Upper bound on one kill's drop, so no configuration can flood a bag in a single roll. */
    private static final int MAX_STACKS = 16;

    private DropLoot() {
    }

    /**
     * The loot one grade pays. A content-pack loot table named after the grade wins if one exists;
     * otherwise the grade's entry in {@code season.json} under {@code drops.grades} is rolled.
     */
    public static List<ItemStack> roll(RotasData data, DropGrade grade, String seed) {
        ContentId tableId = new ContentId(grade.tableId());
        if (data.kernel() != null && data.kernel().content().items().loot().containsKey(tableId)) {
            return LootService.roll(data.kernel().content().items(), tableId, seed, 1, 1.0, null);
        }
        return fromRules(SeasonService.rules(data).drops, grade, LootService.random(seed));
    }

    /**
     * Rolls the configured contents of a grade: its gold range, then each item line. A grade the
     * configuration does not describe falls back to the common grade, and then to nothing.
     */
    private static List<ItemStack> fromRules(SeasonRules.DropRules rules, DropGrade grade, RandomGenerator random) {
        SeasonRules.GradeLoot loot = rules == null || rules.grades == null ? null : rules.grades.get(grade.key());
        if (loot == null && rules != null && rules.grades != null) {
            loot = rules.grades.get(DropGrade.COMMON.key());
        }
        if (loot == null) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        long gold = loot.minGold + (loot.maxGold <= loot.minGold ? 0 : random.nextLong(loot.maxGold - loot.minGold + 1));
        if (gold > 0) {
            stacks.add(net.schwarz.rotasutils.item.GoldCoins.stack(gold));
        }
        for (String line : loot.items) {
            if (stacks.size() >= MAX_STACKS) {
                break;
            }
            ItemStack rolled = parse(line, random);
            if (!rolled.isEmpty()) {
                stacks.add(rolled);
            }
        }
        return List.copyOf(stacks);
    }

    /**
     * One configured item line: {@code "minecraft:diamond 1-3"}, {@code "minecraft:diamond 2"} or
     * {@code "minecraft:diamond 1-3 @0.5"} for a chance. A line that cannot be read, or names an
     * item another mod has removed, is skipped rather than failing the whole drop.
     */
    static ItemStack parse(String line, RandomGenerator random) {
        if (line == null || line.isBlank()) {
            return ItemStack.EMPTY;
        }
        String[] parts = line.trim().split("\\s+");
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation(parts[0].toLowerCase(Locale.ROOT)));
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        int min = 1;
        int max = 1;
        double chance = 1.0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            try {
                if (part.startsWith("@")) {
                    chance = Double.parseDouble(part.substring(1));
                } else if (part.contains("-")) {
                    int dash = part.indexOf('-');
                    min = Integer.parseInt(part.substring(0, dash));
                    max = Integer.parseInt(part.substring(dash + 1));
                } else {
                    min = max = Integer.parseInt(part);
                }
            } catch (NumberFormatException malformed) {
                return ItemStack.EMPTY;
            }
        }
        if (min < 1 || max < min || chance <= 0 || random.nextDouble() >= chance) {
            return ItemStack.EMPTY;
        }
        int count = min + (max == min ? 0 : random.nextInt(max - min + 1));
        return new ItemStack(item, Math.min(count, item.getMaxStackSize()));
    }
}
