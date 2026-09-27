package net.schwarz.rotasutils.level;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-wide settings for leveling every mob, not just the ones an author gave a profile.
 *
 * <p>A mob with no matching monster profile still gets a level: the zone it spawned in (or the
 * distance-from-spawn ramp outside any zone) decides the band, a stable roll from the mob's UUID
 * picks the level inside it, and health/damage grow per level. Location, never the nearby player,
 * decides danger. The name template is applied to the
 * mob's nameplate. Everything here is editable from the admin UI and saved with the level config,
 * so a server can narrow the feature to hostiles only, exclude NPC mods or raise the spawn ramp.</p>
 */
public final class MobLevelConfig {
    public static final int MAX_LEVEL = 100;
    public static final int MAX_BLOCKS_PER_LEVEL = 100_000;

    private boolean enabled = true;
    private boolean nameVisible = true;
    /** Nameplate template; must keep {level} so the difficulty colour can read it back. */
    private String nameFormat = "{name} [Lv {level}]";
    /** Colour the nameplate by (mob level - viewer level) instead of leaving it white. */
    private boolean colorByDelta = true;
    /** Level outside any zone, at the spawn point. */
    private int spawnLevel = 1;
    /** Extra blocks travelled before the fallback ramp adds one level; 0 disables the ramp. */
    private int levelPerBlocks = 0;
    /** Hard ceiling for naturally generated monsters, clamped into 1..100. */
    private int maxLevel = 100;
    /** Levels above the distance floor a wilderness mob may roll, so the ramp is not one flat level. */
    private int bandSpread = 3;
    public static final int MAX_BAND_SPREAD = 1000;
    private double healthPerLevel = 0.05;
    private double damagePerLevel = 0.03;
    private long baseXp = 20;
    private double xpPerLevel = 2;
    /** Empty means every mob category; otherwise the vanilla category names such as MONSTER. */
    private final Set<String> categories = new LinkedHashSet<>();
    private final Set<String> excludeEntities = new LinkedHashSet<>(List.of(
            "minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem", "minecraft:snow_golem"));
    private final Set<String> excludeTags = new LinkedHashSet<>();
    /** Mod namespaces whose entities never level; NPC mods ship "mobs" that are really shopkeepers. */
    private final Set<String> excludeNamespaces = new LinkedHashSet<>(List.of(
            "easy_npc", "easynpc", "easy_npc_bundle"));
    /**
     * Whether MISC-category entities are skipped when no category allow-list is set. MISC holds
     * summoned weapons, golems and marker entities rather than things a player fights.
     */
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

    /** Adds or removes a category filter entry; an unknown/blank value is ignored. */
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

    /**
     * Whether a mob should receive the default level. Tamed animals and known NPC namespaces are
     * always skipped; the rest is a category allow-list and an entity/tag deny-list.
     */
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
        // With no explicit categories, skip MISC: summons, projectiles-as-mobs, golems and other
        // non-creature entities are not combat monsters and must not get levels or plates.
        if (categories.isEmpty()) {
            return !skipMisc || category == null || !category.equalsIgnoreCase("misc");
        }
        return (category != null && categories.contains(category.toUpperCase(java.util.Locale.ROOT)));
    }

    /** Applies the name template; unknown placeholders are left untouched. */
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
        // Replace, don't merge: these two start with defaults, so merging a saved list back into them
        // silently resurrected every default an admin had removed on the next load.
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
