package net.schwarz.rotasutils.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class Nbt {
    private Nbt() {
    }

    public static <T> ListTag saveList(Collection<T> values, Function<T, CompoundTag> writer) {
        ListTag list = new ListTag();
        for (T value : values) {
            list.add(writer.apply(value));
        }
        return list;
    }

    public static <T> List<T> loadList(CompoundTag tag, String key, Function<CompoundTag, T> reader) {
        List<T> out = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            T value = reader.apply(list.getCompound(i));
            if (value != null) {
                out.add(value);
            }
        }
        return out;
    }

    public static ListTag saveStrings(Collection<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    public static List<String> loadStrings(CompoundTag tag, String key) {
        List<String> out = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    public static Set<String> loadStringSet(CompoundTag tag, String key) {
        return new LinkedHashSet<>(loadStrings(tag, key));
    }

    public static CompoundTag saveStringIntMap(Map<String, Integer> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach(tag::putInt);
        return tag;
    }

    public static Map<String, Integer> loadStringIntMap(CompoundTag tag, String key) {
        Map<String, Integer> out = new LinkedHashMap<>();
        CompoundTag sub = tag.getCompound(key);
        for (String k : sub.getAllKeys()) {
            out.put(k, sub.getInt(k));
        }
        return out;
    }

    public static CompoundTag saveStringLongMap(Map<String, Long> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach(tag::putLong);
        return tag;
    }

    public static Map<String, Long> loadStringLongMap(CompoundTag tag, String key) {
        Map<String, Long> out = new LinkedHashMap<>();
        CompoundTag sub = tag.getCompound(key);
        for (String k : sub.getAllKeys()) {
            out.put(k, sub.getLong(k));
        }
        return out;
    }

    public static CompoundTag saveStack(ItemStack stack) {
        return stack.save(new CompoundTag());
    }

    public static ItemStack loadStack(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return new ItemStack(Items.PAPER);
        }
        ItemStack stack = ItemStack.of(tag.getCompound(key));
        return stack.isEmpty() ? new ItemStack(Items.PAPER) : stack;
    }

    public static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> type, E fallback) {
        String name = tag.getString(key);
        if (name.isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
