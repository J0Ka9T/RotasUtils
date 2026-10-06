package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record PerkRules(List<Perk> perks) {
    public static final Set<String> TYPES = Set.of("prospect", "green_thumb", "lucky_waters", "animal_whisperer",
            "banquet", "master_brew", "arcane_mind", "rich_veins", "swift_pick");
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    public record Perk(String role, String type, double base, double perLevel, double max, int radius) {
        public double value(int level) {
            return Math.min(max, base + perLevel * Math.max(0, level - 1));
        }
    }

    public List<Perk> forRole(String role) {
        List<Perk> out = new ArrayList<>();
        for (Perk perk : perks) {
            if (perk.role().equals(role)) out.add(perk);
        }
        return out;
    }

    public static PerkRules parse(String json) {
        List<Perk> perks = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("perks")) {
            JsonObject o = element.getAsJsonObject();
            String role = o.get("role").getAsString(), type = o.get("type").getAsString();
            if (!ID.matcher(role).matches()) throw new IllegalArgumentException("Bad role id: " + role);
            if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown perk type: " + type);
            double base = num(o, "base", 1), per = num(o, "perLevel", 0), max = num(o, "max", base);
            int radius = (int) num(o, "radius", 0);
            if (base < 0 || per < 0 || max < base || radius < 0 || radius > 48) {
                throw new IllegalArgumentException(role + "/" + type + ": numbers out of range");
            }
            perks.add(new Perk(role, type, base, per, max, radius));
        }
        return new PerkRules(List.copyOf(perks));
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }
}
