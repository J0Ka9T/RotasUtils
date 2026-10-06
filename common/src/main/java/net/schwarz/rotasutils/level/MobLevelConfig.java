package net.schwarz.rotasutils.level;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MobLevelConfig {
    public static final int MAX_LEVEL = 100;
    public static final int MAX_BLOCKS_PER_LEVEL = 100_000;

    private boolean enabled = true;
    private boolean nameVisible = true;
    private String nameFormat = "{name} [Lv {level}]";
    private boolean colorByDelta = true;
    private int spawnLevel = 1;
    private int levelPerBlocks = 0;
    private int maxLevel = 100;
    private int bandSpread = 3;
    public static final int MAX_BAND_SPREAD = 1000;
    private double healthPerLevel = 0.05;
    private double damagePerLevel = 0.03;
    private long baseXp = 20;
    private double xpPerLevel = 2;
    private final Set<String> categories = new LinkedHashSet<>();
    private final Set<String> excludeEntities = new LinkedHashSet<>(List.of(
            "minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem", "minecraft:snow_golem"));
    private final Set<String> excludeTags = new LinkedHashSet<>();
    private final Set<String> excludeNamespaces = new LinkedHashSet<>(List.of(
            "easy_npc", "easynpc", "easy_npc_bundle"));
    private boolean skipMisc = true;

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }

    public boolean nameVisible() { return nameVisible; }
    public void setNameVisible(boolean value) { nameVisible = value; }

    public String nameFormat() { return nameFormat; }
    public void setNameFormat(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || !trimmed.contains("{level}") || trimmed.length() > 128) {
            throw new IllegalArgumentException("Mob level name format must contain {level} and be 1..128 characters");
        }
        nameFormat = trimmed;
    }

    public boolean colorByDelta() { return colorByDelta; }
    public void setColorByDelta(boolean value) { colorByDelta = value; }

    public int spawnLevel() { return spawnLevel; }
    public void setSpawnLevel(int value) {
        spawnLevel = clamp(value, 1, MAX_LEVEL);
        if (maxLevel < spawnLevel) { maxLevel = spawnLevel; }
    }

    public int levelPerBlocks() { return levelPerBlocks; }
    public void setLevelPerBlocks(int value) { levelPerBlocks = clamp(value, 0, MAX_BLOCKS_PER_LEVEL); }

    public int maxLevel() { return maxLevel; }
    public void setMaxLevel(int value) { maxLevel = clamp(value, spawnLevel, MAX_LEVEL); }

    public int bandSpread() { return bandSpread; }
    public void setBandSpread(int value) { bandSpread = clamp(value, 0, MAX_BAND_SPREAD); }

    public double healthPerLevel() { return healthPerLevel; }
    public void setHealthPerLevel(double value) { healthPerLevel = clamp(value, 0, 10); }

    public double damagePerLevel() { return damagePerLevel; }
    public void setDamagePerLevel(double value) { damagePerLevel = clamp(value, 0, 10); }

    public long baseXp() { return baseXp; }
    public void setBaseXp(long value) { baseXp = Math.max(0, Math.min(1_000_000_000L, value)); }

    public double xpPerLevel() { return xpPerLevel; }
    public void setXpPerLevel(double value) { xpPerLevel = clamp(value, 0, 1_000_000); }

    public Set<String> categories() { return categories; }
    public Set<String> excludeEntities() { return excludeEntities; }
    public Set<String> excludeTags() { return excludeTags; }
    public Set<String> excludeNamespaces() { return excludeNamespaces; }

    public boolean skipMisc() { return skipMisc; }
    public void setSkipMisc(boolean value) { skipMisc = value; }

    public void toggleExcludedNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) { return; }
        String key = namespace.trim().toLowerCase(java.util.Locale.ROOT);
        if (!excludeNamespaces.remove(key)) { excludeNamespaces.add(key); }
    }

    public void toggleCategory(String category) {
        if (category == null || category.isBlank()) { return; }
        String key = category.trim().toUpperCase(java.util.Locale.ROOT);
        if (!categories.remove(key)) { categories.add(key); }
    }

    public void toggleExcludedEntity(String entityId) {
        if (entityId == null || entityId.isBlank()) { return; }
        String key = entityId.trim();
        if (!excludeEntities.remove(key)) { excludeEntities.add(key); }
    }

    public void toggleExcludedTag(String tag) {
        if (tag == null || tag.isBlank()) { return; }
        String key = tag.trim();
        if (!excludeTags.remove(key)) { excludeTags.add(key); }
    }

    public boolean shouldLevel(String entityId, Set<String> tags, String category, boolean tamed) {
        if (!enabled || tamed || entityId == null || !entityId.contains(":")) {
            return false;
        }
        String namespace = entityId.substring(0, entityId.indexOf(':'));
        if (excludeNamespaces.contains(namespace.toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        if (excludeEntities.contains(entityId)) {
            return false;
        }
        for (String tag : excludeTags) {
            if (tags.contains(tag)) {
                return false;
            }
        }
        if (categories.isEmpty()) {
            return !skipMisc || category == null || !category.equalsIgnoreCase("misc");
        }
        return (category != null && categories.contains(category.toUpperCase(java.util.Locale.ROOT)));
    }

    public String formatName(String baseName, int level) {
        return nameFormat.replace("{name}", baseName)
                .replace("{level}", Integer.toString(level))
                .replace("{tier}", "");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("name_visible", nameVisible);
        tag.putString("name_format", nameFormat);
        tag.putBoolean("color_by_delta", colorByDelta);
        tag.putInt("spawn_level", spawnLevel);
        tag.putInt("level_per_blocks", levelPerBlocks);
        tag.putInt("max_level", maxLevel);
        tag.putInt("band_spread", bandSpread);
        tag.putDouble("health_per_level", healthPerLevel);
        tag.putDouble("damage_per_level", damagePerLevel);
        tag.putLong("base_xp", baseXp);
        tag.putDouble("xp_per_level", xpPerLevel);
        tag.put("categories", Nbt.saveStrings(categories));
        tag.put("exclude_entities", Nbt.saveStrings(excludeEntities));
        tag.put("exclude_tags", Nbt.saveStrings(excludeTags));
        tag.put("exclude_namespaces", Nbt.saveStrings(excludeNamespaces));
        tag.putBoolean("skip_misc", skipMisc);
        return tag;
    }

    public static MobLevelConfig load(CompoundTag tag) {
        MobLevelConfig config = new MobLevelConfig();
        config.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        config.nameVisible = !tag.contains("name_visible") || tag.getBoolean("name_visible");
        config.colorByDelta = !tag.contains("color_by_delta") || tag.getBoolean("color_by_delta");
        config.spawnLevel = clamp(tag.contains("spawn_level") ? tag.getInt("spawn_level") : 1, 1, MAX_LEVEL);
        config.levelPerBlocks = clamp(tag.contains("level_per_blocks") ? tag.getInt("level_per_blocks") : 0,
                0, MAX_BLOCKS_PER_LEVEL);
        config.maxLevel = clamp(tag.contains("max_level") ? tag.getInt("max_level") : 100, config.spawnLevel, MAX_LEVEL);
        config.bandSpread = clamp(tag.contains("band_spread") ? tag.getInt("band_spread") : 3, 0, MAX_BAND_SPREAD);
        config.healthPerLevel = clamp(tag.contains("health_per_level") ? tag.getDouble("health_per_level") : 0.05, 0, 10);
        config.damagePerLevel = clamp(tag.contains("damage_per_level") ? tag.getDouble("damage_per_level") : 0.03, 0, 10);
        config.baseXp = Math.max(0, Math.min(1_000_000_000L, tag.contains("base_xp") ? tag.getLong("base_xp") : 20));
        config.xpPerLevel = clamp(tag.contains("xp_per_level") ? tag.getDouble("xp_per_level") : 2, 0, 1_000_000);
        String format = tag.getString("name_format");
        if (!format.isEmpty() && format.contains("{level}") && format.length() <= 128) {
            config.nameFormat = format;
        }
        config.categories.addAll(Nbt.loadStrings(tag, "categories"));
        config.excludeTags.addAll(Nbt.loadStrings(tag, "exclude_tags"));
        replace(config.excludeEntities, Nbt.loadStrings(tag, "exclude_entities"), tag.contains("exclude_entities"));
        replace(config.excludeNamespaces, Nbt.loadStrings(tag, "exclude_namespaces"), tag.contains("exclude_namespaces"));
        config.skipMisc = !tag.contains("skip_misc") || tag.getBoolean("skip_misc");
        return config;
    }

    private static void replace(Set<String> target, java.util.Collection<String> saved, boolean present) {
        if (!present) { return; }
        target.clear();
        target.addAll(saved);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) { return min; }
        return Math.max(min, Math.min(max, value));
    }
}
