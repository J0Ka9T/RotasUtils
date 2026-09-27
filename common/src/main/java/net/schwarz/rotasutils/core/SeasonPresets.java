package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Map;

/**
 * One-click difficulty for a section. "Normal" is the mod's defaults; "easy" and "hard" scale a few
 * key values of those defaults, so a preset always lands on a known, sane point instead of drifting
 * from whatever was there before. Only the listed values change; everything else is left alone.
 */
public final class SeasonPresets {
    public enum Level { EASY, NORMAL, HARD }

    /** One scaled value: its path and the factor for easy and for hard. {@code cap} bounds chances. */
    public record Knob(List<String> path, double easy, double hard, double cap) {
        static Knob of(String path, double easy, double hard) {
            return new Knob(List.of(path.split("\\.")), easy, hard, Double.MAX_VALUE);
        }

        static Knob chance(String path, double easy, double hard) {
            return new Knob(List.of(path.split("\\.")), easy, hard, 1.0);
        }
    }

    private static final Map<String, List<Knob>> KNOBS = Map.ofEntries(
            Map.entry("leveling", List.of(Knob.of("mainBaseXp", 0.6, 1.5), Knob.of("subBaseXp", 0.6, 1.5))),
            Map.entry("monster", List.of(Knob.of("monsterLevelBonus", 1.4, 0.7),
                    Knob.chance("overLevelPenaltyPerLevel", 0.5, 1.5), Knob.of("overLevelGrace", 1.6, 0.6))),
            Map.entry("repeatable", List.of(Knob.of("repeatableFullRuns", 2.0, 0.5), Knob.chance("repeatableLowRate", 2.0, 0.5))),
            Map.entry("stats", List.of(Knob.of("stats.startPoints", 1.5, 0.7), Knob.of("stats.pointsPerLevel", 1.5, 0.5))),
            Map.entry("refine", List.of(Knob.chance("refine.chances", 1.25, 0.75), Knob.of("refine.goldPerAttempt", 0.6, 1.5))),
            Map.entry("farming", List.of(Knob.of("farming.comboXpPerKill", 1.5, 0.7), Knob.of("farming.comboLootPerKill", 1.5, 0.7),
                    Knob.of("farming.eliteHealth", 0.8, 1.3), Knob.of("farming.championHealth", 0.8, 1.3))),
            Map.entry("horse", List.of(Knob.of("horse.pullCost", 0.6, 1.5), Knob.of("horse.tenPullCost", 0.6, 1.5),
                    Knob.of("horse.pityLegendary", 0.7, 1.3), Knob.of("horse.pityEpic", 0.7, 1.3),
                    Knob.of("horse.breedBaseCost", 0.6, 1.5), Knob.of("horse.gestationMinutes", 0.5, 2.0),
                    Knob.of("horse.breedCooldownMinutes", 0.5, 2.0), Knob.chance("horse.breedMutationChance", 1.5, 0.6),
                    Knob.chance("horse.breedTraitInheritChance", 1.3, 0.8))),
            Map.entry("npcServices", List.of(Knob.of("npcServices.repairPerDurability", 0.6, 1.5),
                    Knob.of("npcServices.restCost", 0.6, 1.5), Knob.of("npcServices.blessingCost", 0.6, 1.5),
                    Knob.of("npcServices.fortuneCost", 0.6, 1.5), Knob.chance("npcServices.auctionFee", 0.5, 2.0),
                    Knob.of("npcServices.collectorBonus", 1.2, 0.9))),
            Map.entry("milestones", List.of(Knob.of("milestones.goldPerLevel", 1.5, 0.6), Knob.of("milestones.bigMultiplier", 1.4, 0.7))),
            Map.entry("exploration", List.of(Knob.of("exploration.zoneBase", 1.5, 0.6), Knob.of("exploration.waystoneXp", 1.5, 0.6),
                    Knob.of("exploration.newMonsterXp", 1.5, 0.6), Knob.of("exploration.travelXp", 1.5, 0.6),
                    Knob.of("exploration.varietyMax", 1.4, 0.6))),
            Map.entry("cards", List.of(Knob.chance("cards.dropChance", 2.0, 0.5))),
            Map.entry("nemesis", List.of(Knob.of("nemesis.healthPerRank", 0.7, 1.4), Knob.of("nemesis.damagePerRank", 0.7, 1.4),
                    Knob.chance("nemesis.riseChance", 0.6, 1.3))));

    private SeasonPresets() {
    }

    public static boolean has(String section) {
        return KNOBS.containsKey(section);
    }

    /**
     * Sets the section's knobs in {@code draft} to {@code defaults} scaled for {@code level}.
     *
     * @return how many values were set
     */
    public static int apply(JsonObject draft, JsonObject defaults, String section, Level level) {
        int changed = 0;
        for (Knob knob : KNOBS.getOrDefault(section, List.of())) {
            JsonElement base = SettingsTree.get(defaults, knob.path());
            if (base == null) continue;
            double factor = switch (level) {
                case EASY -> knob.easy();
                case HARD -> knob.hard();
                case NORMAL -> 1.0;
            };
            JsonElement scaled = scale(base, factor, knob.cap());
            if (scaled != null && SettingsTree.set(draft, knob.path(), scaled)) changed++;
        }
        return changed;
    }

    private static JsonElement scale(JsonElement value, double factor, double cap) {
        if (value.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) {
                JsonElement scaled = scale(item, factor, cap);
                out.add(scaled == null ? item.deepCopy() : scaled);
            }
            return out;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
        String text = value.getAsString();
        boolean whole = !text.contains(".") && !text.contains("e") && !text.contains("E");
        double result = Math.min(cap, value.getAsDouble() * factor);
        if (whole) return new JsonPrimitive(Math.max(1, Math.round(result)));
        return new JsonPrimitive(Math.round(result * 10_000) / 10_000.0);
    }
}
