package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.stat.CharacterStat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CardIndex {
    public record Entry(String id, String name, int color, String fits, List<CharacterStat.Effect> effects) {
        public Entry {
            id = id == null ? "" : id;
            name = name == null || name.isBlank() ? id : name;
            color = color | 0xFF000000;
            fits = fits == null || fits.isBlank() ? "ANY" : fits;
            effects = effects == null ? List.of() : List.copyOf(effects);
        }

        public boolean fits(String category) {
            return "ANY".equalsIgnoreCase(fits) || fits.equalsIgnoreCase(category);
        }
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private CardIndex() {
    }

    public static void set(Map<String, Entry> entries) {
        ENTRIES.clear();
        if (entries != null) {
            ENTRIES.putAll(entries);
        }
    }

    public static Entry get(String id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static Map<String, Entry> all() {
        return Map.copyOf(ENTRIES);
    }

    public static String name(String id) {
        Entry entry = get(id);
        return entry == null ? (id == null ? "" : id) : entry.name();
    }

    public static int color(String id) {
        Entry entry = get(id);
        return entry == null ? 0xFFB07CE8 : entry.color();
    }

public static CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ENTRIES.forEach((id, entry) -> {
            CompoundTag one = new CompoundTag();
            one.putString("name", entry.name());
            one.putInt("color", entry.color());
            one.putString("fits", entry.fits());
            net.minecraft.nbt.ListTag effects = new net.minecraft.nbt.ListTag();
            entry.effects().forEach(effect -> effects.add(effect.save()));
            one.put("effects", effects);
            tag.put(id, one);
        });
        return tag;
    }

    public static void load(CompoundTag tag) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String id : tag.getAllKeys()) {
            CompoundTag one = tag.getCompound(id);
            List<CharacterStat.Effect> effects = new java.util.ArrayList<>();
            net.minecraft.nbt.ListTag list = one.getList("effects", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size() && index < CardEffects.MAX_LINES; index++) {
                effects.add(CharacterStat.Effect.load(list.getCompound(index)));
            }
            entries.put(id, new Entry(id, one.getString("name"), one.getInt("color"), one.getString("fits"), effects));
        }
        set(entries);
    }
}
