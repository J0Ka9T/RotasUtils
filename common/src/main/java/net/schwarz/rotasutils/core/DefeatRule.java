package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public record DefeatRule(Set<Attack> attacks, Set<String> items, Set<String> damageTypes, int minLevel,
                         double resisted, String hint, Set<String> immune) {
    public enum Attack { MELEE, RANGED, MAGIC }

    public static final DefeatRule NONE = new DefeatRule(Set.of(), Set.of(), Set.of(), 0, 1.0, "", Set.of());

    public DefeatRule {
        attacks = attacks.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(attacks));
        items = Set.copyOf(items); damageTypes = Set.copyOf(damageTypes); immune = Set.copyOf(immune);
        if (minLevel < 0 || minLevel > MonsterLevels.ABSOLUTE_MAX) { throw new IllegalArgumentException("Defeat min_level out of range"); }
        if (!Double.isFinite(resisted) || resisted < 0 || resisted > 1) { throw new IllegalArgumentException("Defeat resisted must be 0..1"); }
        if (hint == null || hint.length() > 128) { throw new IllegalArgumentException("Defeat hint exceeds 128 characters"); }
    }

    public boolean active() {
        return requirements() || !immune.isEmpty();
    }

    public boolean requirements() {
        return !attacks.isEmpty() || !items.isEmpty() || !damageTypes.isEmpty() || minLevel > 0;
    }

    public boolean immuneTo(Predicate<String> damageIs) {
        return immune.stream().anyMatch(damageIs);
    }

    public double multiplier(Attack attack, Predicate<String> holds, Predicate<String> damageIs, int playerLevel) {
        if (immuneTo(damageIs)) { return 0.0; }
        return met(attack, holds, damageIs, playerLevel) ? 1.0 : resisted;
    }

    public boolean met(Attack attack, Predicate<String> holds, Predicate<String> damageIs, int playerLevel) {
        return (attacks.isEmpty() || (attack != null && attacks.contains(attack)))
                && (items.isEmpty() || items.stream().anyMatch(holds))
                && (damageTypes.isEmpty() || damageTypes.stream().anyMatch(damageIs))
                && (minLevel <= 0 || playerLevel >= minLevel);
    }

    public String describe() {
        if (!hint.isBlank()) { return hint; }
        StringBuilder text = new StringBuilder("Resists that attack. Needs");
        if (!attacks.isEmpty()) { text.append(' ').append(String.join("/", attacks.stream().map(a -> a.name().toLowerCase(Locale.ROOT)).toList())).append(" attack"); }
        if (!items.isEmpty()) { text.append(" with ").append(String.join(" or ", items)); }
        if (!damageTypes.isEmpty()) { text.append(", damage ").append(String.join(" or ", damageTypes)); }
        if (minLevel > 0) { text.append(", level ").append(minLevel).append('+'); }
        return text.toString();
    }

    public static DefeatRule parse(JsonObject json) {
        KernelJson.fields(json, "attacks", "items", "damage_types", "min_level", "resisted", "hint", "immune");
        Set<Attack> attacks = EnumSet.noneOf(Attack.class);
        if (json.has("attacks")) {
            if (!json.get("attacks").isJsonArray()) { throw new IllegalArgumentException("Expected a list: defeat.attacks"); }
            for (JsonElement entry : json.getAsJsonArray("attacks")) { attacks.add(Attack.valueOf(entry.getAsString().trim().toUpperCase(Locale.ROOT))); }
        }
        int minLevel = json.has("min_level") ? json.get("min_level").getAsInt() : 0;
        double resisted = json.has("resisted") ? json.get("resisted").getAsDouble() : 0.0;
        String hint = json.has("hint") ? json.get("hint").getAsString() : "";
        return new DefeatRule(attacks, strings(json, "items"), strings(json, "damage_types"), minLevel, resisted, hint, strings(json, "immune"));
    }

    private static Set<String> strings(JsonObject json, String key) {
        if (!json.has(key)) { return Set.of(); }
        if (!json.get(key).isJsonArray()) { throw new IllegalArgumentException("Expected a list: defeat." + key); }
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() > 32) { throw new IllegalArgumentException("At most 32 entries in defeat." + key); }
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement entry : array) {
            String value = entry.getAsString().trim();
            String id = value.startsWith("#") ? value.substring(1) : value;
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) { throw new IllegalArgumentException("Invalid id in defeat." + key + ": " + value); }
            values.add(value);
        }
        return values;
    }
}
