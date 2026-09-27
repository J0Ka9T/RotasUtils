package net.schwarz.rotasutils.title;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One title a player can earn and wear (ฉายา).
 *
 * <p>A title is a name in front of a player's own: "ผู้ล่ามังกร", "คนแรกที่ตีบวก 10". It is earned by
 * doing something, never bought, and a {@link #unique() unique} one belongs to exactly one player on
 * the whole server - the first to meet its condition keeps it forever, and everyone else who gets
 * there later is simply too late.</p>
 *
 * <p>A title may carry a small bonus. It uses the same {@link CharacterStat.Effect} the character
 * stats use, so the bonus rides the modifier pipeline that already exists instead of a second one.</p>
 */
public final class TitleDef {
    /** Titles, effects and the announcement text are all bounded so no configuration can flood a client. */
    public static final int MAX_EFFECTS = 4;
    public static final int MAX_TITLES = 256;

    /** What a player has to do to earn it. */
    public enum Condition {
        /** Reaches a level. {@code target} is ignored. */
        LEVEL,
        /** Kills {@code amount} of one entity type, named by {@code target}. */
        KILL_ENTITY,
        /** Kills {@code amount} monsters of any kind. */
        KILL_ANY,
        /** Kills {@code amount} bosses. */
        KILL_BOSS,
        /** Completes the quest named by {@code target}. */
        QUEST,
        /** Completes {@code amount} quests of any kind. */
        QUEST_COUNT,
        /** Gets any item to refine level {@code amount}. */
        REFINE,
        /** Holds {@code amount} of the season currency at once. */
        GOLD,
        /** Slays {@code amount} nemeses. */
        NEMESIS,
        /** Turns in {@code amount} bounty contracts. */
        BOUNTY,
        /** Breeds {@code amount} foals in the stable. */
        BREED,
        /** Records {@code amount} kinds of monster in the bestiary. */
        BESTIARY,
        /** Discovers {@code amount} waystones. */
        WAYSTONE,
        /** Owns {@code amount} other titles. */
        TITLE_COUNT,
        /** Dies {@code amount} times, and keeps going. */
        DEATH,
        /** Earns {@code amount} gold from the collector and the auction house. */
        TRADE,
        /** Only an administrator can hand it out. */
        MANUAL;

        public static Condition byName(String name) {
            if (name != null) {
                for (Condition value : values()) {
                    if (value.name().equalsIgnoreCase(name.trim())) {
                        return value;
                    }
                }
            }
            return MANUAL;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How rare a title reads as: its frame colour and sort weight in the title list. */
    public enum Rarity {
        COMMON(0xFFB6A17C), UNCOMMON(0xFF86C05C), RARE(0xFF5AA9E6), EPIC(0xFFB07CE8), LEGENDARY(0xFFE0AC4C);

        public final int color;

        Rarity(int color) {
            this.color = color;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Rarity byName(String name) {
            for (Rarity value : values()) {
                if (value.name().equalsIgnoreCase(name == null ? "" : name.trim())) return value;
            }
            return null;
        }
    }

    /** What kind of deed a title records, read from its condition: the tabs of the title list. */
    public enum Category {
        PROGRESS, COMBAT, CRAFTING, WEALTH, SPECIAL;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String id;
    private String name;
    /** Null until an admin picks one; {@link #rarity()} then derives it from the title itself. */
    private Rarity rarity;
    private String description = "";
    private int color = 0xFFE0C173;
    private boolean unique;
    private boolean enabled = true;
    /** Hidden titles are not listed until they are earned, so finding one is the reward. */
    private boolean hidden;
    private Condition condition = Condition.MANUAL;
    private String target = "";
    private long amount = 1;
    private int order;
    private final List<CharacterStat.Effect> effects = new ArrayList<>();

    public TitleDef(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A title needs an id");
        }
        this.id = id.trim();
        this.name = name == null || name.isBlank() ? this.id : name;
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String value) { name = value == null || value.isBlank() ? id : value; }
    public String description() { return description; }
    public void setDescription(String value) { description = value == null ? "" : value; }
    public int color() { return color; }
    public void setColor(int value) { color = value | 0xFF000000; }
    public boolean unique() { return unique; }
    public void setUnique(boolean value) { unique = value; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean hidden() { return hidden; }
    public void setHidden(boolean value) { hidden = value; }
    public Condition condition() { return condition; }
    public void setCondition(Condition value) { condition = value == null ? Condition.MANUAL : value; }
    public String target() { return target; }
    public void setTarget(String value) { target = value == null ? "" : value.trim(); }
    public long amount() { return amount; }
    public void setAmount(long value) { amount = Math.max(0, Math.min(1_000_000_000L, value)); }
    public int order() { return order; }
    public void setOrder(int value) { order = value; }
    public List<CharacterStat.Effect> effects() { return effects; }

    /**
     * The rarity an admin set, or one read from the title: a unique title is legendary, one that
     * grants a bonus is rare, the rest are common. Old worlds get sensible rarities with no editing.
     */
    public Rarity rarity() {
        if (rarity != null) return rarity;
        return unique ? Rarity.LEGENDARY : !effects.isEmpty() ? Rarity.RARE : Rarity.COMMON;
    }

    public void setRarity(Rarity value) { rarity = value; }

    public Category category() {
        return switch (condition) {
            case LEVEL, QUEST, QUEST_COUNT, BESTIARY, WAYSTONE -> Category.PROGRESS;
            case KILL_ENTITY, KILL_ANY, KILL_BOSS, NEMESIS, BOUNTY -> Category.COMBAT;
            case REFINE, BREED -> Category.CRAFTING;
            case GOLD, TRADE -> Category.WEALTH;
            case MANUAL, TITLE_COUNT, DEATH -> Category.SPECIAL;
        };
    }

    public void addEffect(CharacterStat.Effect effect) {
        if (effect != null && effects.size() < MAX_EFFECTS) {
            effects.add(effect);
        }
    }

    /** True when {@code progress} of the right kind is enough to earn this title. */
    public boolean met(long progress) {
        return condition != Condition.MANUAL && progress >= Math.max(1, amount);
    }

    /** The counter key this title watches, or an empty string when it watches nothing countable. */
    public String counterKey() {
        return switch (condition) {
            case KILL_ENTITY -> target.isBlank() ? "" : TitleCounters.KILL_PREFIX + target;
            case KILL_ANY -> TitleCounters.KILL_ANY;
            case KILL_BOSS -> TitleCounters.KILL_BOSS;
            case REFINE -> TitleCounters.REFINE_BEST;
            case NEMESIS -> TitleCounters.NEMESIS_SLAIN;
            case BOUNTY -> TitleCounters.BOUNTIES;
            case BREED -> TitleCounters.BRED;
            case DEATH -> TitleCounters.DEATHS;
            case TRADE -> TitleCounters.TRADE_GOLD;
            default -> "";
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("desc", description);
        tag.putInt("color", color);
        tag.putBoolean("unique", unique);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("hidden", hidden);
        tag.putString("condition", condition.name());
        tag.putString("target", target);
        tag.putLong("amount", amount);
        tag.putInt("order", order);
        if (rarity != null) tag.putString("rarity", rarity.name());
        tag.put("effects", Nbt.saveList(effects, CharacterStat.Effect::save));
        return tag;
    }

    public static TitleDef load(CompoundTag tag) {
        TitleDef title = new TitleDef(tag.getString("id"), tag.getString("name"));
        title.description = tag.getString("desc");
        title.color = tag.contains("color") ? tag.getInt("color") | 0xFF000000 : 0xFFE0C173;
        title.unique = tag.getBoolean("unique");
        title.enabled = !tag.contains("enabled", Tag.TAG_BYTE) || tag.getBoolean("enabled");
        title.hidden = tag.getBoolean("hidden");
        title.condition = Condition.byName(tag.getString("condition"));
        title.target = tag.getString("target");
        title.setAmount(tag.getLong("amount"));
        title.order = tag.getInt("order");
        title.rarity = Rarity.byName(tag.getString("rarity"));
        for (CharacterStat.Effect effect : Nbt.loadList(tag, "effects", CharacterStat.Effect::load)) {
            title.addEffect(effect);
        }
        return title;
    }
}
