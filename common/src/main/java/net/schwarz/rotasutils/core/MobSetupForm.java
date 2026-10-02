package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.schwarz.rotasutils.util.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Plain-language view of a monster profile body, used by the Mob Setup screens.
 *
 * <p>It edits only the fields the easy editor shows and leaves everything else in the body alone
 * (affixes, rewards, bosses, other selectors), so an admin can switch between the easy editor and
 * the advanced JSON editor without losing work.</p>
 */
public final class MobSetupForm {
    public static final String HEALTH = "minecraft:generic.max_health";
    public static final String DAMAGE = "minecraft:generic.attack_damage";
    public static final String ARMOR = "minecraft:generic.armor";
    public static final String SPEED = "minecraft:generic.movement_speed";
    public static final String NORMAL_TIER = "rotas:tier/normal";
    public static final String ELITE_TIER = "rotas:tier/elite";

    private final JsonObject body;

    public MobSetupForm(JsonObject body) {
        this.body = body.deepCopy();
        object(this.body, "selector");
        object(this.body, "level");
        if (!this.body.has("tiers") || !this.body.get("tiers").isJsonObject() || this.body.getAsJsonObject("tiers").size() == 0) {
            JsonObject tiers = new JsonObject();
            tiers.addProperty(NORMAL_TIER, 1);
            this.body.add("tiers", tiers);
        }
    }

    public static MobSetupForm parse(String json) {
        return new MobSetupForm(JsonParser.parseString(json).getAsJsonObject());
    }

    /** A sensible starting point for one mob: scales with the nearest player, gently tougher per level. */
    public static MobSetupForm create(String entityId) {
        JsonObject body = new JsonObject();
        body.addProperty("priority", 10);
        MobSetupForm form = new MobSetupForm(body);
        form.setEntities(List.of(entityId));
        form.setStrategy("NEAREST_PLAYER");
        form.setMin(1);
        form.setMax(100);
        form.setOffset(0);
        form.setBaseXp(20);
        form.setXpPerLevel(2);
        form.setName("{name} [Lv {level}]");
        form.setScale(HEALTH, 1, 0.03, 0);
        return form;
    }

    public JsonObject body() {
        return body.deepCopy();
    }

    public String json() {
        return body.toString();
    }

    // Mobs -----------------------------------------------------------------

    public List<String> entities() {
        return strings(selector(), "entities");
    }

    public void setEntities(List<String> ids) {
        JsonArray array = new JsonArray();
        ids.stream().distinct().limit(64).forEach(array::add);
        if (array.isEmpty()) {
            selector().remove("entities");
        } else {
            selector().add("entities", array);
        }
    }

    /** True when the profile also matches by tags, mods, biomes or other rules the easy editor does not show. */
    public boolean usesOtherSelectors() {
        return selector().keySet().stream().anyMatch(key -> !key.equals("entities") && !key.equals("regions")
                && selector().get(key).isJsonArray() && !selector().getAsJsonArray(key).isEmpty());
    }

    // Scope ----------------------------------------------------------------
    // A setup is global (no zones) or applies only inside chosen level zones. A zone version always
    // outranks the global setup for the same mob, so inside its zones its level, strength, rewards and
    // spawn rules are the ones used, and everywhere else the global setup still applies.

    public static final int GLOBAL_PRIORITY = 10;
    public static final int ZONE_PRIORITY = 50;

    public List<String> scopeZones() {
        return strings(selector(), "regions");
    }

    public boolean zoneScoped() {
        return !scopeZones().isEmpty();
    }

    public void setScopeZones(List<String> zoneIds) {
        JsonArray array = new JsonArray();
        zoneIds.stream().distinct().limit(64).forEach(array::add);
        if (array.isEmpty()) {
            selector().remove("regions");
            if (priority() == ZONE_PRIORITY) {
                setPriority(GLOBAL_PRIORITY);
            }
        } else {
            selector().add("regions", array);
            setPriority(Math.max(priority(), ZONE_PRIORITY));
        }
    }

    public void toggleScopeZone(String zoneId) {
        List<String> zones = new ArrayList<>(scopeZones());
        if (!zones.remove(zoneId)) {
            zones.add(zoneId);
        }
        setScopeZones(zones);
    }

    public int priority() {
        return integer(body, "priority", 0);
    }

    public void setPriority(int value) {
        body.addProperty("priority", clamp(value, -10_000, 10_000));
    }

    /** A copy of this setup ready to become a zone version: same settings, no zones chosen yet. */
    public MobSetupForm copyForZone() {
        MobSetupForm copy = new MobSetupForm(body);
        copy.setScopeZones(List.of());
        copy.setPriority(ZONE_PRIORITY);
        return copy;
    }

    public String name() {
        return text(body, "name", "");
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            body.remove("name");
        } else {
            body.addProperty("name", name.length() > 128 ? name.substring(0, 128) : name);
        }
    }

    /** The category's name in the Mob Setup screen; empty for a setup made before categories. */
    public String category() {
        return text(body, "category", "");
    }

    public void setCategory(String category) {
        if (category == null || category.isBlank()) {
            body.remove("category");
        } else {
            String trimmed = category.trim();
            body.addProperty("category", trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed);
        }
    }

    /** What the Mob Setup screen calls this setup: its category name, or its first mob's id. */
    public String label() {
        if (!category().isEmpty()) {
            return category();
        }
        return entities().isEmpty() ? "(no mobs)" : entities().get(0);
    }

    // Level ----------------------------------------------------------------

    public String strategy() {
        return text(level(), "strategy", "FIXED");
    }

    public void setStrategy(String strategy) {
        level().addProperty("strategy", strategy);
        if (!strategy.equals("EXPRESSION")) {
            level().remove("expression");
        }
    }

    public int min() {
        return integer(level(), "min", 1);
    }

    public int max() {
        return Math.max(min(), integer(level(), "max", min()));
    }

    public int offset() {
        return integer(level(), "offset", 0);
    }

    public int fixedLevel() {
        return Math.max(min(), Math.min(max(), integer(level(), "value", min())));
    }

    public void setMin(int value) {
        int clamped = clamp(value, 1, 10_000);
        level().addProperty("min", clamped);
        // Compare the stored max: max() already floors at min(), so it can never be below it.
        if (integer(level(), "max", clamped) < clamped) {
            level().addProperty("max", clamped);
        }
        if (level().has("value") && integer(level(), "value", clamped) < clamped) {
            level().addProperty("value", clamped);
        }
    }

    public void setMax(int value) {
        level().addProperty("max", Math.max(min(), clamp(value, 1, 10_000)));
    }

    public void setOffset(int value) {
        level().addProperty("offset", clamp(value, -10_000, 10_000));
    }

    /** Every mob gets exactly this level. */
    public void setFixed(int value) {
        int clamped = clamp(value, 1, 10_000);
        setStrategy("FIXED");
        level().addProperty("min", clamped);
        level().addProperty("max", clamped);
        level().addProperty("value", clamped);
    }

    // Strength ---------------------------------------------------------------

    public double multiplier(String attribute) {
        return scale(attribute, "multiplier", 1);
    }

    public double perLevel(String attribute) {
        return scale(attribute, "per_level", 0);
    }

    public double add(String attribute) {
        return scale(attribute, "add", 0);
    }

    public void setScale(String attribute, double multiplier, double perLevel, double add) {
        double m = round(Math.max(0, Math.min(100, multiplier)));
        double p = round(Math.max(-1, Math.min(10, perLevel)));
        double a = round(Math.max(-10_000, Math.min(10_000, add)));
        JsonObject attributes = object(body, "attributes");
        if (m == 1 && p == 0 && a == 0) {
            attributes.remove(attribute);
        } else {
            JsonObject scale = new JsonObject();
            scale.addProperty("multiplier", m);
            if (p != 0) scale.addProperty("per_level", p);
            if (a != 0) scale.addProperty("add", a);
            attributes.add(attribute, scale);
        }
        if (attributes.size() == 0) {
            body.remove("attributes");
        }
    }

    /** Value of an attribute at {@code level} for a mob whose unmodified value is {@code base}. */
    public double valueAt(String attribute, double base, int level) {
        double factor = Math.max(0, Math.min(10_000, multiplier(attribute) + perLevel(attribute) * (level - 1)));
        return base * factor + add(attribute);
    }

    /** True when the profile uses tiers other than the standard Common and Elite ones. */
    public boolean customTiers() {
        return tiers().keySet().stream().anyMatch(key -> !key.equals(NORMAL_TIER) && !key.equals(ELITE_TIER));
    }

    public int eliteChance() {
        JsonObject tiers = tiers();
        if (!tiers.has(ELITE_TIER)) {
            return 0;
        }
        double elite = tiers.get(ELITE_TIER).getAsDouble();
        double total = 0;
        for (String key : tiers.keySet()) {
            total += tiers.get(key).getAsDouble();
        }
        return total <= 0 ? 0 : (int) Math.round(elite * 100 / total);
    }

    /** Percent of mobs that roll the Elite tier; ignored for profiles with custom tiers. */
    public void setEliteChance(int percent) {
        if (customTiers()) {
            return;
        }
        int chance = clamp(percent, 0, 90);
        JsonObject tiers = new JsonObject();
        tiers.addProperty(NORMAL_TIER, 100 - chance);
        if (chance > 0) {
            tiers.addProperty(ELITE_TIER, chance);
        }
        body.add("tiers", tiers);
    }

    public List<String> tierIds() {
        return new ArrayList<>(tiers().keySet());
    }

    // Rewards --------------------------------------------------------------

    public long baseXp() {
        return body.has("base_xp") ? body.get("base_xp").getAsLong() : 0;
    }

    public void setBaseXp(long value) {
        body.addProperty("base_xp", Math.max(0, Math.min(1_000_000_000L, value)));
    }

    public double xpPerLevel() {
        return body.has("xp_per_level") ? body.get("xp_per_level").getAsDouble() : 0;
    }

    public void setXpPerLevel(double value) {
        body.addProperty("xp_per_level", round(Math.max(0, Math.min(1_000_000, value))));
    }

    public String loot() {
        return text(body, "loot", "");
    }

    public void setLoot(String loot) {
        if (loot == null || loot.isBlank()) {
            body.remove("loot");
        } else {
            body.addProperty("loot", loot);
        }
    }

    public boolean applyExisting() {
        return body.has("apply_existing") && body.get("apply_existing").getAsBoolean();
    }

    public void setApplyExisting(boolean value) {
        body.addProperty("apply_existing", value);
    }

    public boolean manualOnly() {
        return body.has("manual_only") && body.get("manual_only").getAsBoolean();
    }

    public void setManualOnly(boolean value) {
        body.addProperty("manual_only", value);
    }

    // Spawning -------------------------------------------------------------
    // Reads never create the "spawning" object (that would mark an untouched setup as changed), and
    // every write prunes values back to their defaults, so a setup with vanilla spawning has no block.

    public boolean naturalSpawning() {
        JsonObject spawning = spawningRead();
        return !spawning.has("natural") || spawning.get("natural").getAsBoolean();
    }

    public void setNaturalSpawning(boolean value) {
        editSpawning(spawning -> spawning.addProperty("natural", value));
    }

    public String spawnWhere() {
        return text(spawningRead(), "where", MobSpawnRules.Where.ANYWHERE.name());
    }

    public void setSpawnWhere(String where) {
        editSpawning(spawning -> spawning.addProperty("where", MobSpawnRules.Where.valueOf(where).name()));
    }

    public List<String> spawnZones() {
        return strings(spawningRead(), "zones");
    }

    public void toggleSpawnZone(String zoneId) {
        List<String> zones = new ArrayList<>(spawnZones());
        if (!zones.remove(zoneId)) {
            zones.add(zoneId);
        }
        editSpawning(spawning -> {
            JsonArray array = new JsonArray();
            zones.stream().limit(64).forEach(array::add);
            spawning.add("zones", array);
        });
    }

    public String spawnTime() {
        return text(spawningRead(), "time", MobSpawnRules.Time.ANY.name());
    }

    public void setSpawnTime(String time) {
        editSpawning(spawning -> spawning.addProperty("time", MobSpawnRules.Time.valueOf(time).name()));
    }

    /** The one dimension these mobs are limited to, or empty for any dimension. */
    public String spawnDimension() {
        List<String> dimensions = strings(spawningRead(), "dimensions");
        return dimensions.isEmpty() ? "" : dimensions.get(0);
    }

    public void setSpawnDimension(String dimension) {
        editSpawning(spawning -> {
            JsonArray array = new JsonArray();
            if (dimension != null && !dimension.isBlank()) {
                array.add(dimension);
            }
            spawning.add("dimensions", array);
        });
    }

    public int spawnMinY() {
        return integer(spawningRead(), "min_y", MobSpawnRules.LOWEST_Y);
    }

    public int spawnMaxY() {
        return integer(spawningRead(), "max_y", MobSpawnRules.HIGHEST_Y);
    }

    public void setSpawnHeight(int minY, int maxY) {
        int low = clamp(minY, MobSpawnRules.LOWEST_Y, MobSpawnRules.HIGHEST_Y);
        int high = Math.max(low, clamp(maxY, MobSpawnRules.LOWEST_Y, MobSpawnRules.HIGHEST_Y));
        editSpawning(spawning -> {
            spawning.addProperty("min_y", low);
            spawning.addProperty("max_y", high);
        });
    }

    public int maxNearby() {
        return integer(spawningRead(), "max_nearby", 0);
    }

    public void setMaxNearby(int value) {
        editSpawning(spawning -> spawning.addProperty("max_nearby", clamp(value, 0, MobSpawnRules.MAX_NEARBY)));
    }

    public boolean extraSpawns() {
        JsonObject extra = extraRead();
        return extra.has("enabled") && extra.get("enabled").getAsBoolean();
    }

    public void setExtraSpawns(boolean value) {
        editExtra(extra -> extra.addProperty("enabled", value));
    }

    public int extraPerMinute() {
        return integer(extraRead(), "per_minute", MobSpawnRules.NO_EXTRA.perMinute());
    }

    public void setExtraPerMinute(int value) {
        editExtra(extra -> extra.addProperty("per_minute", clamp(value, 1, MobSpawnRules.MAX_PER_MINUTE)));
    }

    public int extraGroup() {
        return integer(extraRead(), "group_max", MobSpawnRules.NO_EXTRA.groupMax());
    }

    public void setExtraGroup(int value) {
        editExtra(extra -> extra.addProperty("group_max", clamp(value, 1, MobSpawnRules.MAX_GROUP)));
    }

    public boolean extraVanillaRules() {
        JsonObject extra = extraRead();
        return !extra.has("vanilla_rules") || extra.get("vanilla_rules").getAsBoolean();
    }

    public void setExtraVanillaRules(boolean value) {
        editExtra(extra -> extra.addProperty("vanilla_rules", value));
    }

    private JsonObject spawningRead() {
        return body.has("spawning") && body.get("spawning").isJsonObject() ? body.getAsJsonObject("spawning") : new JsonObject();
    }

    private JsonObject extraRead() {
        JsonObject spawning = spawningRead();
        return spawning.has("extra") && spawning.get("extra").isJsonObject() ? spawning.getAsJsonObject("extra") : new JsonObject();
    }

    private void editSpawning(java.util.function.Consumer<JsonObject> change) {
        JsonObject spawning = object(body, "spawning");
        change.accept(spawning);
        pruneSpawning();
    }

    private void editExtra(java.util.function.Consumer<JsonObject> change) {
        JsonObject extra = object(object(body, "spawning"), "extra");
        change.accept(extra);
        pruneSpawning();
    }

    /** Drops every spawning value that equals its default, and the whole block when nothing is left. */
    private void pruneSpawning() {
        JsonObject spawning = object(body, "spawning");
        removeIf(spawning, "natural", true);
        removeIf(spawning, "where", MobSpawnRules.Where.ANYWHERE.name());
        removeIf(spawning, "time", MobSpawnRules.Time.ANY.name());
        removeIf(spawning, "min_y", MobSpawnRules.LOWEST_Y);
        removeIf(spawning, "max_y", MobSpawnRules.HIGHEST_Y);
        removeIf(spawning, "max_nearby", 0);
        for (String list : List.of("zones", "dimensions")) {
            if (spawning.has(list) && spawning.get(list).isJsonArray() && spawning.getAsJsonArray(list).isEmpty()) {
                spawning.remove(list);
            }
        }
        if (spawning.has("extra") && spawning.get("extra").isJsonObject()) {
            JsonObject extra = spawning.getAsJsonObject("extra");
            if (!extra.has("enabled") || !extra.get("enabled").getAsBoolean()) {
                spawning.remove("extra");
            } else {
                removeIf(extra, "per_minute", MobSpawnRules.NO_EXTRA.perMinute());
                removeIf(extra, "group_max", MobSpawnRules.NO_EXTRA.groupMax());
                removeIf(extra, "vanilla_rules", true);
            }
        }
        if (spawning.size() == 0) {
            body.remove("spawning");
        }
    }

    private static void removeIf(JsonObject json, String key, Object defaultValue) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return;
        }
        var value = json.getAsJsonPrimitive(key);
        boolean matches = defaultValue instanceof Boolean flag ? value.isBoolean() && value.getAsBoolean() == flag
                : defaultValue instanceof Integer number ? value.isNumber() && value.getAsInt() == number
                : value.isString() && value.getAsString().equals(defaultValue);
        if (matches) {
            json.remove(key);
        }
    }

    // Documents ------------------------------------------------------------

    public static JsonObject document(String id, JsonObject body) {
        JsonObject document = new JsonObject();
        document.addProperty("schema", 1);
        document.addProperty("id", id);
        document.addProperty("kind", "monster");
        document.add("body", body.deepCopy());
        return document;
    }

    /** Definitions for the standard tiers, so a first mob setup works without any other content. */
    public static JsonObject tierDefinition(String id) {
        JsonObject body = new JsonObject();
        switch (id) {
            case NORMAL_TIER -> {
                body.addProperty("label", "Common");
                body.addProperty("rank", 0);
            }
            case ELITE_TIER -> {
                body.addProperty("label", "Elite");
                body.addProperty("rank", 2);
                body.addProperty("xp_multiplier", 2.5);
                body.addProperty("loot_multiplier", 2);
                JsonObject attributes = new JsonObject();
                JsonObject health = new JsonObject();
                health.addProperty("multiplier", 2);
                attributes.add(HEALTH, health);
                JsonObject damage = new JsonObject();
                damage.addProperty("multiplier", 1.4);
                attributes.add(DAMAGE, damage);
                body.add("attributes", attributes);
            }
            default -> {
                return null;
            }
        }
        JsonObject document = new JsonObject();
        document.addProperty("schema", 1);
        document.addProperty("id", id);
        document.addProperty("kind", "tier");
        document.add("body", body);
        return document;
    }

    /** A readable id such as {@code rotas:monster/zombie}, unique among {@code taken}. */
    public static String newId(String entityId, Set<String> taken) {
        String path = entityId.contains(":") ? entityId.substring(entityId.indexOf(':') + 1) : entityId;
        String base = "rotas:monster/" + Ids.slug(path);
        String id = base;
        for (int n = 2; taken.contains(id); n++) {
            id = base + "_" + n;
        }
        return id;
    }

    // Helpers --------------------------------------------------------------

    private JsonObject selector() {
        return object(body, "selector");
    }

    private JsonObject level() {
        return object(body, "level");
    }

    private JsonObject tiers() {
        return object(body, "tiers");
    }

    private double scale(String attribute, String field, double fallback) {
        if (!body.has("attributes") || !body.getAsJsonObject("attributes").has(attribute)) {
            return fallback;
        }
        JsonObject scale = body.getAsJsonObject("attributes").getAsJsonObject(attribute);
        return scale.has(field) ? scale.get(field).getAsDouble() : fallback;
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            parent.add(key, new JsonObject());
        }
        return parent.getAsJsonObject(key);
    }

    private static List<String> strings(JsonObject parent, String key) {
        List<String> values = new ArrayList<>();
        if (parent.has(key) && parent.get(key).isJsonArray()) {
            for (JsonElement element : parent.getAsJsonArray(key)) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private static String text(JsonObject parent, String key, String fallback) {
        return parent.has(key) ? parent.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject parent, String key, int fallback) {
        return parent.has(key) ? parent.get(key).getAsInt() : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
