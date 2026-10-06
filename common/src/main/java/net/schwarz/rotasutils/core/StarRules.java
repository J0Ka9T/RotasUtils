package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record StarRules(List<Source> sources, List<Buff> silverMeal, List<Buff> goldMeal) {
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public record Source(String job, Set<String> items, double silverBase, double silverPerLevel, int goldFromLevel,
                         double goldBase, double goldPerLevel, double maxChance) {
        public int roll(int level, double roll) {
            double gold = level >= goldFromLevel ? goldBase + goldPerLevel * (level - goldFromLevel) : 0;
            double silver = silverBase + silverPerLevel * Math.max(0, level - 1);
            double sum = gold + silver;
            double total = Math.min(maxChance, sum);
            if (total <= 0) return 0;
            double scale = sum > total ? total / sum : 1;
            if (roll < gold * scale) return 2;
            return roll < total ? 1 : 0;
        }
    }

    public record Buff(String effect, int seconds, int amplifier) {
    }

    public Source sourceFor(String job, String item) {
        return sourceFor(job, item, tag -> false);
    }

    public Source sourceFor(String job, String item, java.util.function.Predicate<String> inTag) {
        for (Source source : sources) {
            if (!source.job().equals(job)) continue;
            if (source.items().contains(item)) return source;
            for (String entry : source.items()) {
                if (entry.startsWith("#") && inTag.test(entry.substring(1))) return source;
            }
        }
        return null;
    }

    public List<String> jobsFor(String item) {
        List<String> jobs = new ArrayList<>();
        for (Source source : sources) {
            if (source.items().contains(item) && !jobs.contains(source.job())) jobs.add(source.job());
        }
        return jobs;
    }

    public List<String> jobsFor(List<String> options) {
        List<String> jobs = new ArrayList<>();
        for (String option : options) {
            for (String job : jobsFor(option)) {
                if (!jobs.contains(job)) jobs.add(job);
            }
        }
        return jobs;
    }

    public List<Buff> meal(int star) {
        return star >= 2 ? goldMeal : star == 1 ? silverMeal : List.of();
    }

    public static StarRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Source> sources = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("sources")) {
            sources.add(source(element.getAsJsonObject()));
        }
        JsonObject meal = root.has("meal") ? root.getAsJsonObject("meal") : new JsonObject();
        return new StarRules(List.copyOf(sources), buffs(meal, "silver"), buffs(meal, "gold"));
    }

    public static Source source(JsonObject s) {
        Set<String> items = new LinkedHashSet<>();
        for (JsonElement item : s.getAsJsonArray("items")) {
            String id = item.getAsString();
            if (!ITEM.matcher(id.startsWith("#") ? id.substring(1) : id).matches()) throw new IllegalArgumentException("Not an item id: " + id);
            items.add(id);
        }
        return new Source(s.get("job").getAsString(), Set.copyOf(items), num(s, "silverBase", 0.05),
                num(s, "silverPerLevel", 0.01), (int) num(s, "goldFromLevel", 8), num(s, "goldBase", 0.01),
                num(s, "goldPerLevel", 0.004), Math.min(0.9, num(s, "maxChance", 0.5)));
    }

    public StarRules withItems(List<Source> extra) {
        List<Source> merged = new ArrayList<>(sources);
        for (Source add : extra) {
            boolean found = false;
            for (int i = 0; i < merged.size(); i++) {
                Source have = merged.get(i);
                if (have.job().equals(add.job())) {
                    Set<String> items = new LinkedHashSet<>(have.items());
                    items.addAll(add.items());
                    merged.set(i, new Source(have.job(), Set.copyOf(items), have.silverBase(), have.silverPerLevel(),
                            have.goldFromLevel(), have.goldBase(), have.goldPerLevel(), have.maxChance()));
                    found = true;
                }
            }
            if (!found) merged.add(add);
        }
        return new StarRules(List.copyOf(merged), silverMeal, goldMeal);
    }

    private static List<Buff> buffs(JsonObject meal, String key) {
        List<Buff> out = new ArrayList<>();
        JsonArray array = meal.has(key) ? meal.getAsJsonArray(key) : new JsonArray();
        for (JsonElement element : array) {
            JsonObject b = element.getAsJsonObject();
            int seconds = (int) num(b, "seconds", 30), amplifier = (int) num(b, "amplifier", 0);
            if (seconds < 1 || seconds > 3600 || amplifier < 0 || amplifier > 4) {
                throw new IllegalArgumentException("meal buff outside 1..3600 s or amplifier 0..4");
            }
            String effect = b.get("effect").getAsString();
            if (!ITEM.matcher(effect).matches()) throw new IllegalArgumentException("Not an effect id: " + effect);
            out.add(new Buff(effect, seconds, amplifier));
        }
        return List.copyOf(out);
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }
}
