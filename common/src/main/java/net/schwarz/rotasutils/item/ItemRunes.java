package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.RuneType;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ItemRunes {
    public static final String TAG = "RotasRunes";
    public static final String TIERS = "RotasRuneTiers";
    public static final String ITEM_TIER = "RuneTier";
    public static final int MAX_TIER = 3;
    private static final String[] NUMERALS = {"", "I", "II", "III"};

    private ItemRunes() {
    }

    public static int power(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 1;
            case 2 -> 2;
            default -> 4;
        };
    }

    public static int clampTier(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    public static String numeral(int tier) {
        return NUMERALS[clampTier(tier)];
    }

    public static int tierOf(ItemStack stack) {
        CompoundTag tag = stack == null || stack.isEmpty() ? null : stack.getTag();
        return tag != null && tag.contains(ITEM_TIER) ? clampTier(tag.getInt(ITEM_TIER)) : 1;
    }

    public static ItemStack stack(RuneType rune, int tier, int count) {
        ItemStack stack = new ItemStack(RotasRegistry.RUNES.get(rune).get(), count);
        if (clampTier(tier) > 1) {
            stack.getOrCreateTag().putInt(ITEM_TIER, clampTier(tier));
        }
        return stack;
    }

    public static List<RuneType> read(ItemStack stack) {
        List<RuneType> runes = new ArrayList<>();
        CompoundTag tag = stack == null || stack.isEmpty() ? null : stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return runes;
        }
        ListTag list = tag.getList(TAG, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            runes.add(RuneType.byId(list.getString(i)));
        }
        return runes;
    }

    public static RuneType get(ItemStack stack, int slot) {
        List<RuneType> runes = read(stack);
        return slot >= 0 && slot < runes.size() ? runes.get(slot) : null;
    }

    public static int tier(ItemStack stack, int slot) {
        CompoundTag tag = stack == null || stack.isEmpty() ? null : stack.getTag();
        if (tag == null || !tag.contains(TIERS, Tag.TAG_LIST)) {
            return 1;
        }
        ListTag list = tag.getList(TIERS, Tag.TAG_INT);
        return slot >= 0 && slot < list.size() ? clampTier(list.getInt(slot)) : 1;
    }

    public static void set(ItemStack stack, int slot, RuneType rune) {
        set(stack, slot, rune, 1);
    }

    public static void set(ItemStack stack, int slot, RuneType rune, int tier) {
        List<RuneType> runes = read(stack);
        List<Integer> tiers = new ArrayList<>();
        for (int i = 0; i < Math.max(runes.size(), slot + 1); i++) {
            tiers.add(tier(stack, i));
        }
        while (runes.size() <= slot) {
            runes.add(null);
        }
        runes.set(slot, rune);
        tiers.set(slot, rune == null ? 1 : clampTier(tier));
        ListTag names = new ListTag();
        ListTag levels = new ListTag();
        for (int i = 0; i < runes.size(); i++) {
            names.add(StringTag.valueOf(runes.get(i) == null ? "" : runes.get(i).id()));
            levels.add(IntTag.valueOf(tiers.get(i)));
        }
        stack.getOrCreateTag().put(TAG, names);
        stack.getOrCreateTag().put(TIERS, levels);
    }

    public static Map<RuneType, Integer> active(ItemStack stack, int activeSlots) {
        Map<RuneType, Integer> counts = new EnumMap<>(RuneType.class);
        List<RuneType> runes = read(stack);
        for (int i = 0; i < Math.min(activeSlots, runes.size()); i++) {
            RuneType rune = runes.get(i);
            if (rune != null) {
                counts.merge(rune, power(tier(stack, i)), Integer::sum);
            }
        }
        return counts;
    }
}
