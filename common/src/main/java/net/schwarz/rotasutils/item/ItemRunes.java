package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.core.RuneType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The runes inscribed on a weapon, kept in a {@code RotasRunes} list next to its refine level and
 * sockets. Position matters: slot 0 opens at the first refine threshold, slot 1 at the second, and an
 * empty slot is stored as an empty string so a later slot can be filled before an earlier one.
 *
 * <p>A weapon that drops below a threshold keeps its runes, but only the slots it still has count in
 * combat, so a failed refine costs power without deleting what the player paid for.</p>
 */
public final class ItemRunes {
    public static final String TAG = "RotasRunes";

    private ItemRunes() {
    }

    /** The rune in each stored slot, null where the slot is empty or holds an unknown id. */
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

    /** The rune in one slot, or null. */
    public static RuneType get(ItemStack stack, int slot) {
        List<RuneType> runes = read(stack);
        return slot >= 0 && slot < runes.size() ? runes.get(slot) : null;
    }

    /** Writes {@code rune} into {@code slot}, replacing whatever was there. */
    public static void set(ItemStack stack, int slot, RuneType rune) {
        List<RuneType> runes = read(stack);
        while (runes.size() <= slot) {
            runes.add(null);
        }
        runes.set(slot, rune);
        ListTag list = new ListTag();
        for (RuneType type : runes) {
            list.add(StringTag.valueOf(type == null ? "" : type.id()));
        }
        stack.getOrCreateTag().put(TAG, list);
    }

    /** How many of each rune count, looking only at the first {@code activeSlots} slots. */
    public static Map<RuneType, Integer> active(ItemStack stack, int activeSlots) {
        Map<RuneType, Integer> counts = new EnumMap<>(RuneType.class);
        List<RuneType> runes = read(stack);
        for (int i = 0; i < Math.min(activeSlots, runes.size()); i++) {
            RuneType rune = runes.get(i);
            if (rune != null) {
                counts.merge(rune, 1, Integer::sum);
            }
        }
        return counts;
    }
}
