package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Where and how the mobs of one monster profile (a Mob Setup) spawn.
 *
 * <p>Two things read these rules. The spawn gate checks every natural and world-generation spawn of
 * the profile's mobs and cancels the ones the rules forbid; spawners, eggs, commands and the extra
 * spawns below are never blocked. The spawn director adds extra spawns near players when
 * {@link Extra#enabled()} is on, which is how a mob that "never spawns naturally" can still live in
 * one zone only. This class is pure so the rules are unit-tested without a world.</p>
 */
public record MobSpawnRules(boolean natural, Where where, Set<String> zones, Set<String> dimensions, Time time,
                            int minY, int maxY, int maxNearby, Extra extra) {
    public enum Where { ANYWHERE, ONLY_IN_ZONES, NOT_IN_ZONES }

    public enum Time { ANY, DAY, NIGHT }

    /** Extra spawns added near players, on top of (or instead of) normal spawning. */
    public record Extra(boolean enabled, int perMinute, int groupMax, boolean vanillaRules) {
        public Extra {
            perMinute = clamp(perMinute, 1, MAX_PER_MINUTE);
            groupMax = clamp(groupMax, 1, MAX_GROUP);
        }
    }

    public static final int LOWEST_Y = -4096;
    public static final int HIGHEST_Y = 4096;
    public static final int MAX_PER_MINUTE = 60;
    public static final int MAX_GROUP = 8;
    public static final int MAX_NEARBY = 256;
    /** Crowd cap for extra spawns when the profile sets no limit, so they can never flood an area. */
    public static final int DEFAULT_EXTRA_CAP = 8;
    public static final Extra NO_EXTRA = new Extra(false, 6, 1, true);
    public static final MobSpawnRules DEFAULT = new MobSpawnRules(true, Where.ANYWHERE, Set.of(), Set.of(), Time.ANY,
            LOWEST_Y, HIGHEST_Y, 0, NO_EXTRA);

    public MobSpawnRules {
        zones = Set.copyOf(zones);
        dimensions = Set.copyOf(dimensions);
        minY = clamp(minY, LOWEST_Y, HIGHEST_Y);
        maxY = clamp(maxY, LOWEST_Y, HIGHEST_Y);
        maxNearby = clamp(maxNearby, 0, MAX_NEARBY);
        if (minY > maxY) {
            throw new IllegalArgumentException("Spawn min_y is above max_y");
        }
        if (where != Where.ANYWHERE && zones.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one zone for this spawn rule");
        }
    }

    /**
     * One candidate spawn position: its dimension, every zone that contains it, day or night, and height.
     * {@code timed} is false in dimensions with a fixed clock (Nether, End), where day and night mean nothing.
     */
    public record Place(String dimension, Set<String> zones, boolean day, int y, boolean timed) {
        public Place {
            zones = Set.copyOf(zones);
        }

        public Place(String dimension, Set<String> zones, boolean day, int y) {
            this(dimension, zones, day, y, true);
        }
    }

    /** Where, when and at what height the mobs may appear, regardless of how they spawn. */
    public boolean placeAllowed(Place place) {
        if (!dimensions.isEmpty() && !dimensions.contains(place.dimension())) {
            return false;
        }
        if (place.y() < minY || place.y() > maxY) {
            return false;
        }
        if (place.timed() && ((time == Time.DAY && !place.day()) || (time == Time.NIGHT && place.day()))) {
            return false;
        }
        boolean inListed = place.zones().stream().anyMatch(zones::contains);
        return switch (where) {
            case ANYWHERE -> true;
            case ONLY_IN_ZONES -> inListed;
            case NOT_IN_ZONES -> !inListed;
        };
    }

    public boolean naturalAllowed(Place place) {
        return natural && placeAllowed(place);
    }

    public boolean extraAllowed(Place place) {
        return extra.enabled() && placeAllowed(place);
    }

    /** True when normal spawning of these mobs differs from vanilla at all, so the gate must look. */
    public boolean restrictsNatural() {
        return !natural || where != Where.ANYWHERE || !dimensions.isEmpty() || time != Time.ANY
                || minY > LOWEST_Y || maxY < HIGHEST_Y || maxNearby > 0;
    }

    /** How many of these mobs may be around before the director stops adding more. */
    public int extraCap() {
        return maxNearby > 0 ? maxNearby : DEFAULT_EXTRA_CAP;
    }

    public static MobSpawnRules parse(JsonObject json) {
        KernelJson.fields(json, "natural", "where", "zones", "dimensions", "time", "min_y", "max_y", "max_nearby", "extra");
        Extra extra = NO_EXTRA;
        if (json.has("extra")) {
            JsonObject values = KernelJson.object(json, "extra");
            KernelJson.fields(values, "enabled", "per_minute", "group_max", "vanilla_rules");
            extra = new Extra(bool(values, "enabled", false),
                    values.has("per_minute") ? KernelJson.integer(values, "per_minute", 1, MAX_PER_MINUTE) : NO_EXTRA.perMinute(),
                    values.has("group_max") ? KernelJson.integer(values, "group_max", 1, MAX_GROUP) : NO_EXTRA.groupMax(),
                    bool(values, "vanilla_rules", true));
        }
        return new MobSpawnRules(bool(json, "natural", true),
                Where.valueOf(json.has("where") ? KernelJson.string(json, "where") : Where.ANYWHERE.name()),
                strings(json, "zones", false), strings(json, "dimensions", true),
                Time.valueOf(json.has("time") ? KernelJson.string(json, "time") : Time.ANY.name()),
                json.has("min_y") ? KernelJson.integer(json, "min_y", LOWEST_Y, HIGHEST_Y) : LOWEST_Y,
                json.has("max_y") ? KernelJson.integer(json, "max_y", LOWEST_Y, HIGHEST_Y) : HIGHEST_Y,
                json.has("max_nearby") ? KernelJson.integer(json, "max_nearby", 0, MAX_NEARBY) : 0,
                extra);
    }

    private static Set<String> strings(JsonObject json, String key, boolean ids) {
        if (!json.has(key)) {
            return Set.of();
        }
        if (!json.get(key).isJsonArray() || json.getAsJsonArray(key).size() > 64) {
            throw new IllegalArgumentException("Expected at most 64 strings: " + key);
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement entry : json.getAsJsonArray(key)) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                    || entry.getAsString().isBlank() || entry.getAsString().length() > 128) {
                throw new IllegalArgumentException("Invalid string in " + key);
            }
            if (ids) {
                new ContentId(entry.getAsString());
            }
            result.add(entry.getAsString());
        }
        return result;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) {
            throw new IllegalArgumentException("Expected boolean: " + key);
        }
        return json.get(key).getAsBoolean();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
