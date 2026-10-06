package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record TradeOverrides(Map<String, Double> values) {
    public TradeOverrides {
        values = Map.copyOf(values);
    }

    public static TradeOverrides empty() {
        return new TradeOverrides(Map.of());
    }

    public Double get(String key) {
        return values.get(key);
    }

    public TradeOverrides with(String key, double value) {
        Map<String, Double> next = new TreeMap<>(values);
        next.put(key, value);
        return new TradeOverrides(next);
    }

    public TradeOverrides without(String key) {
        Map<String, Double> next = new TreeMap<>(values);
        next.remove(key);
        return new TradeOverrides(next);
    }

    public TradeOverrides withoutPrefix(String prefix) {
        Map<String, Double> next = new TreeMap<>(values);
        next.keySet().removeIf(key -> key.startsWith(prefix));
        return new TradeOverrides(next);
    }

    public record World(List<TradeBook> books, StarRules stars, PerkRules perks) {
    }

    public World apply(List<TradeBook> books, StarRules stars, PerkRules perks) {
        List<TradeBook> outBooks = new ArrayList<>();
        for (TradeBook book : books) {
            int[] slots = book.slotLevels().clone();
            for (int i = 0; i < slots.length; i++) {
                Double v = values.get("slot." + book.trade() + "." + i);
                if (v != null) slots[i] = (int) Math.max(1, Math.min(100, Math.round(v)));
            }
            List<TradeBook.Recipe> recipes = new ArrayList<>();
            for (TradeBook.Recipe r : book.recipes()) {
                String p = "recipe." + book.trade() + "." + r.id() + ".";
                if (flag(p + "disabled", false)) continue;
                recipes.add(new TradeBook.Recipe(r.id(), (int) clamp(num(p + "level", r.level()), 1, 100), flag(p + "secret", r.secret()),
                        flag(p + "open", r.open()), r.quality(), (int) clamp(num(p + "seconds", r.seconds()), 5, 86_400),
                        r.ingredients(), r.outputs(), r.requires()));
            }
            outBooks.add(new TradeBook(book.trade(), book.job(), book.station(), slots, List.copyOf(recipes)));
        }
        List<StarRules.Source> sources = new ArrayList<>();
        for (StarRules.Source s : stars.sources()) {
            String p = "star." + s.job() + ".";
            sources.add(new StarRules.Source(s.job(), s.items(), clamp(num(p + "silverBase", s.silverBase()), 0, 1),
                    clamp(num(p + "silverPerLevel", s.silverPerLevel()), 0, 1), (int) clamp(num(p + "goldFromLevel", s.goldFromLevel()), 1, 100),
                    clamp(num(p + "goldBase", s.goldBase()), 0, 1), clamp(num(p + "goldPerLevel", s.goldPerLevel()), 0, 1),
                    clamp(num(p + "maxChance", s.maxChance()), 0, 0.9)));
        }
        List<PerkRules.Perk> outPerks = new ArrayList<>();
        for (PerkRules.Perk perk : perks.perks()) {
            String p = "perk." + perk.role() + "." + perk.type() + ".";
            double base = Math.max(0, num(p + "base", perk.base()));
            outPerks.add(new PerkRules.Perk(perk.role(), perk.type(), base, Math.max(0, num(p + "perLevel", perk.perLevel())),
                    Math.max(base, num(p + "max", perk.max())), (int) clamp(num(p + "radius", perk.radius()), 0, 48)));
        }
        return new World(List.copyOf(outBooks), new StarRules(List.copyOf(sources), stars.silverMeal(), stars.goldMeal()),
                new PerkRules(List.copyOf(outPerks)));
    }

    private double num(String key, double fallback) {
        Double v = values.get(key);
        return v == null ? fallback : v;
    }

    private boolean flag(String key, boolean fallback) {
        Double v = values.get(key);
        return v == null ? fallback : v >= 0.5;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        new TreeMap<>(values).forEach(root::addProperty);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    public static TradeOverrides parse(String json) {
        Map<String, Double> values = new TreeMap<>();
        JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(e -> values.put(e.getKey(), e.getValue().getAsDouble()));
        return new TradeOverrides(values);
    }
}
