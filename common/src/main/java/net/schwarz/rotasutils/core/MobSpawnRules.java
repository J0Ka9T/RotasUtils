package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;

public record MobSpawnRules(boolean natural, Where where, Set<String> zones, Set<String> dimensions, Time time,
                            int minY, int maxY, int maxNearby, Extra extra) {
    public enum Where { ANYWHERE, ONLY_IN_ZONES, NOT_IN_ZONES }

    public enum Time { ANY, DAY, NIGHT }

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

    public record Place(String dimension, Set<String> zones, boolean day, int y, boolean timed) {
        public Place {
            zones = Set.copyOf(zones);
        }

        public Place(String dimension, Set<String> zones, boolean day, int y) {
            this(dimension, zones, day, y, true);
        }
    }

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

    public boolean restrictsNatural() {
        return !natural || where != Where.ANYWHERE || !dimensions.isEmpty() || time != Time.ANY
                || minY > LOWEST_Y || maxY < HIGHEST_Y || maxNearby > 0;
    }

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
