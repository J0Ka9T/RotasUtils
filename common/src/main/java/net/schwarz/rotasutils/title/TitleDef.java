package net.schwarz.rotasutils.title;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.stat.CharacterStat;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TitleDef {
    public static final int MAX_EFFECTS = 4;
    public static final int MAX_TITLES = 256;

    public enum Condition {
        LEVEL,
        KILL_ENTITY,
        KILL_ANY,
        KILL_BOSS,
        QUEST,
        QUEST_COUNT,
        REFINE,
        GOLD,
        NEMESIS,
        BOUNTY,
        BREED,
        BESTIARY,
        WAYSTONE,
        TITLE_COUNT,
        DEATH,
        TRADE,
        SUB_LEVEL,
        STAT,
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

    public enum Category {
        PROGRESS, COMBAT, CRAFTING, WEALTH, SPECIAL;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String id;
    private String name;
    private Rarity rarity;
    private String description = "";
    private int color = 0xFFE0C173;
    private boolean unique;
    private boolean enabled = true;
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

    public List<String> targets() {
        List<String> out = new ArrayList<>();
        for (String part : target.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public boolean matchesEntity(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return false;
        }
        String id = entityId.toLowerCase(Locale.ROOT);
        for (String pattern : targets()) {
            if (pattern.equals(id) || pattern.endsWith(":*") && id.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }
    public void setTarget(String value) { target = value == null ? "" : value.trim(); }
    public long amount() { return amount; }
    public void setAmount(long value) { amount = Math.max(0, Math.min(1_000_000_000L, value)); }
    public int order() { return order; }
    public void setOrder(int value) { order = value; }
    public List<CharacterStat.Effect> effects() { return effects; }

    public Rarity rarity() {
        if (rarity != null) return rarity;
        return unique ? Rarity.LEGENDARY : !effects.isEmpty() ? Rarity.RARE : Rarity.COMMON;
    }

    public void setRarity(Rarity value) { rarity = value; }

    public Category category() {
        return switch (condition) {
            case LEVEL, QUEST, QUEST_COUNT, BESTIARY, WAYSTONE, SUB_LEVEL -> Category.PROGRESS;
            case KILL_ENTITY, KILL_ANY, KILL_BOSS, NEMESIS, BOUNTY -> Category.COMBAT;
            case REFINE, BREED, STAT -> Category.CRAFTING;
            case GOLD, TRADE -> Category.WEALTH;
            case MANUAL, TITLE_COUNT, DEATH -> Category.SPECIAL;
        };
    }

    public void addEffect(CharacterStat.Effect effect) {
        if (effect != null && effects.size() < MAX_EFFECTS) {
            effects.add(effect);
        }
    }

    public boolean met(long progress) {
        return condition != Condition.MANUAL && progress >= Math.max(1, amount);
    }

    public String counterKey() {
        return switch (condition) {
            case KILL_ENTITY -> {
                List<String> parts = targets();
                yield parts.isEmpty() ? "" : parts.size() == 1 && !parts.get(0).endsWith("*")
                        ? TitleCounters.killKey(parts.get(0)) : TitleCounters.KILL_PREFIX + "group." + id;
            }
            case KILL_ANY -> TitleCounters.KILL_ANY;
            case KILL_BOSS -> TitleCounters.KILL_BOSS;
            case REFINE -> TitleCounters.REFINE_BEST;
            case NEMESIS -> TitleCounters.NEMESIS_SLAIN;
            case BOUNTY -> TitleCounters.BOUNTIES;
            case BREED -> TitleCounters.BRED;
            case DEATH -> TitleCounters.DEATHS;
            case TRADE -> TitleCounters.TRADE_GOLD;
            case STAT -> target.isBlank() ? "" : TitleCounters.statKey(target);
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
