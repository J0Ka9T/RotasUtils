package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class MonsterState {
    private final ContentId profile, tier, reward;
    private final int level;
    private final long xp;
    private final double lootMultiplier;
    private final boolean boss;
    private final List<ContentId> affixes;
    private final Map<String, MonsterDefinitions.DerivedScale> attributes;
    private final String name, originalName;
    private final MonsterAssignmentSource source;
    private final MonsterRank rank;
    private final MonsterType monsterType;
    private final ContentId customId;
    private CompoundTag runtime = new CompoundTag();
    private boolean rewarded;

    public MonsterState(ContentId profile, ContentId tier, int level, long xp, double lootMultiplier, boolean boss,
                        List<ContentId> affixes, Map<String, MonsterDefinitions.DerivedScale> attributes,
                        ContentId reward, String name, String originalName) {
        this(profile, tier, level, xp, lootMultiplier, boss, affixes, attributes, reward, name, originalName,
                profile != null && profile.value().equals("rotas:default_mob_level") ? MonsterAssignmentSource.NATURAL : MonsterAssignmentSource.CONFIGURED,
                MonsterRank.infer(tier, boss), MonsterType.UNKNOWN,
                profile != null && !profile.value().equals("rotas:default_mob_level") ? profile : null);
    }

    public MonsterState(ContentId profile, ContentId tier, int level, long xp, double lootMultiplier, boolean boss,
                        List<ContentId> affixes, Map<String, MonsterDefinitions.DerivedScale> attributes,
                        ContentId reward, String name, String originalName, MonsterAssignmentSource source,
                        MonsterRank rank, MonsterType monsterType, ContentId customId) {
        if (profile == null || tier == null || level < 1 || level > MonsterLevels.ABSOLUTE_MAX || xp < 0 || xp > 1000000000
                || !Double.isFinite(lootMultiplier) || lootMultiplier < 0 || lootMultiplier > 1000 || affixes.size() > 8
                || new java.util.HashSet<>(affixes).size() != affixes.size() || attributes.size() > 32
                || name == null || name.length() > 512 || originalName == null || originalName.length() > 4096) {
            throw new IllegalArgumentException("Invalid persisted monster state");
        }
        this.profile = profile; this.tier = tier; this.level = level; this.xp = xp; this.lootMultiplier = lootMultiplier;
        this.boss = boss; this.affixes = List.copyOf(affixes); this.attributes = Map.copyOf(attributes); this.reward = reward;
        this.name = name; this.originalName = originalName;
        this.source = java.util.Objects.requireNonNull(source); this.rank = java.util.Objects.requireNonNull(rank);
        this.monsterType = java.util.Objects.requireNonNull(monsterType); this.customId = customId;
        if (source == MonsterAssignmentSource.NATURAL) MonsterLevels.requireNatural(level, MonsterLevels.DEFAULT_NATURAL_MAX);
    }
    public ContentId profile() { return profile; }
    public ContentId tier() { return tier; }
    public int level() { return level; }
    public long xp() { return xp; }
    public boolean boss() { return boss; }
    public double lootMultiplier() { return lootMultiplier; }
    public List<ContentId> affixes() { return affixes; }
    public Map<String, MonsterDefinitions.DerivedScale> attributes() { return attributes; }
    public ContentId reward() { return reward; }
    public String name() { return name; }
    public String originalName() { return originalName; }
    public MonsterAssignmentSource source() { return source; }
    public MonsterRank rank() { return rank; }
    public MonsterType monsterType() { return monsterType; }
    public ContentId customId() { return customId; }
    public boolean rewarded() { return rewarded; }
    public void rewarded(boolean value) { rewarded = value; }
    public CompoundTag runtime() { return runtime.copy(); }
    public void runtime(CompoundTag tag) {
        if (tag.size() > 128) { throw new IllegalArgumentException("Monster runtime key budget exceeded"); }
        for (String key : tag.getAllKeys()) {
            if (key.length() > 256 || !(tag.get(key) instanceof net.minecraft.nbt.NumericTag || tag.get(key) instanceof StringTag)
                    || tag.get(key).getAsString().length() > 16384) { throw new IllegalArgumentException("Monster runtime data exceeds bounds"); }
        }
        runtime = tag.copy();
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("schema", 2); tag.putString("profile", profile.value()); tag.putString("tier", tier.value());
        tag.putInt("level", level); tag.putLong("xp", xp); tag.putDouble("loot_multiplier", lootMultiplier); tag.putBoolean("boss", boss);
        tag.putString("name", name); tag.putString("original_name", originalName); tag.putBoolean("rewarded", rewarded);
        if (reward != null) { tag.putString("reward", reward.value()); }
        tag.putString("source", source.name()); tag.putString("rank", rank.name()); tag.putString("monster_type", monsterType.name());
        if (customId != null) tag.putString("custom_id", customId.value());
        ListTag list = new ListTag(); affixes.forEach(id -> list.add(StringTag.valueOf(id.value()))); tag.put("affixes", list);
        CompoundTag scales = new CompoundTag(); attributes.forEach((id, scale) -> {
            CompoundTag entry = new CompoundTag(); entry.putDouble("multiplier", scale.multiplier()); entry.putDouble("add", scale.add()); scales.put(id, entry);
        });
        tag.put("attributes", scales); tag.put("runtime", runtime.copy()); return tag;
    }
    public static MonsterState load(CompoundTag tag) {
        int schema = tag.getInt("schema");
        if ((schema != 1 && schema != 2) || !tag.contains("level", Tag.TAG_INT) || !tag.contains("xp", Tag.TAG_LONG)
                || !(tag.get("affixes") instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING)
                || list.size() > 8 || !tag.contains("attributes", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Unsupported/corrupt monster state");
        }
        Map<String, MonsterDefinitions.DerivedScale> attributes = new TreeMap<>();
        CompoundTag scales = tag.getCompound("attributes");
        if (scales.size() > 32) { throw new IllegalArgumentException("Monster attribute budget exceeded"); }
        for (String id : scales.getAllKeys()) {
            new ContentId(id); CompoundTag entry = scales.getCompound(id);
            if (!entry.contains("multiplier", Tag.TAG_ANY_NUMERIC) || !entry.contains("add", Tag.TAG_ANY_NUMERIC)) { throw new IllegalArgumentException("Malformed monster attribute"); }
            attributes.put(id, new MonsterDefinitions.DerivedScale(entry.getDouble("multiplier"), entry.getDouble("add")));
        }
        List<ContentId> affixes = java.util.stream.IntStream.range(0, list.size()).mapToObj(i -> new ContentId(list.getString(i))).toList();
        ContentId profile = new ContentId(tag.getString("profile")); ContentId tier = new ContentId(tag.getString("tier"));
        boolean boss = tag.getBoolean("boss");
        MonsterAssignmentSource source = schema == 2 ? readEnum(tag, "source", MonsterAssignmentSource.class, MonsterAssignmentSource.CONFIGURED)
                : profile.value().equals("rotas:default_mob_level") ? MonsterAssignmentSource.NATURAL : MonsterAssignmentSource.CONFIGURED;
        MonsterRank rank = schema == 2 ? readEnum(tag, "rank", MonsterRank.class, MonsterRank.NORMAL) : MonsterRank.infer(tier, boss);
        MonsterType type = schema == 2 ? readEnum(tag, "monster_type", MonsterType.class, MonsterType.UNKNOWN) : MonsterType.UNKNOWN;
        ContentId customId = schema == 2 && tag.contains("custom_id") ? new ContentId(tag.getString("custom_id"))
                : source == MonsterAssignmentSource.CONFIGURED ? profile : null;
        int level = source == MonsterAssignmentSource.NATURAL ? MonsterLevels.natural(tag.getInt("level"), 100) : MonsterLevels.configured(tag.getInt("level"));
        MonsterState state = new MonsterState(profile, tier, level, tag.getLong("xp"),
                tag.getDouble("loot_multiplier"), tag.getBoolean("boss"), affixes, attributes,
                tag.contains("reward") ? new ContentId(tag.getString("reward")) : null, tag.getString("name"), tag.getString("original_name"),
                source, rank, type, customId);
        state.rewarded = tag.getBoolean("rewarded"); state.runtime(tag.getCompound("runtime")); return state;
    }
    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> type, E fallback) {
        try { return Enum.valueOf(type, tag.getString(key)); } catch (IllegalArgumentException ignored) { return fallback; }
    }
}
