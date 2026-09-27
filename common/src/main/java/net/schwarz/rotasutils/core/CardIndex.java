package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.stat.CharacterStat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every card is called and what it does, in a form both sides can read.
 *
 * <p>The cards themselves are configuration, which lives on the server. An item stack only carries a
 * card id, so a client needs this to draw the card's name, colour and bonus; the server fills it when
 * the configuration loads and sends the same table with the content snapshot. A card a client has not
 * been told about shows its id rather than nothing at all.</p>
 */
public final class CardIndex {
    /** One card as far as display is concerned. */
    public record Entry(String id, String name, int color, String fits, List<CharacterStat.Effect> effects) {
        public Entry {
            id = id == null ? "" : id;
            name = name == null || name.isBlank() ? id : name;
            color = color | 0xFF000000;
            fits = fits == null || fits.isBlank() ? "ANY" : fits;
            effects = effects == null ? List.of() : List.copyOf(effects);
        }

        /** True when this card may go into that kind of item. */
        public boolean fits(String category) {
            return "ANY".equalsIgnoreCase(fits) || fits.equalsIgnoreCase(category);
        }
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private CardIndex() {
    }

    /** Replaces everything known about cards. */
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

    /** The card's name for display, falling back to its id. */
    public static String name(String id) {
        Entry entry = get(id);
        return entry == null ? (id == null ? "" : id) : entry.name();
    }

    public static int color(String id) {
        Entry entry = get(id);
        return entry == null ? 0xFFB07CE8 : entry.color();
    }

    // Transport ---------------------------------------------------------------------------------

    /** The whole table, small enough to ride along with the content snapshot. */
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
