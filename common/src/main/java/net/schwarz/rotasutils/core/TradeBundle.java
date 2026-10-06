package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TradeBundle(String name, Map<String, List<TradeBook.Recipe>> recipes, List<StarRules.Source> sources) {
    public static TradeBundle parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String name = root.get("bundle").getAsString();
        Set<String> requires = new LinkedHashSet<>();
        if (root.has("requires")) {
            root.getAsJsonArray("requires").forEach(mod -> requires.add(mod.getAsString()));
        }
        Map<String, List<TradeBook.Recipe>> recipes = new LinkedHashMap<>();
        if (root.has("trades")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("trades").entrySet()) {
                List<TradeBook.Recipe> list = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    TradeBook.Recipe recipe = TradeBook.recipe(element.getAsJsonObject());
                    Set<String> all = new LinkedHashSet<>(requires);
                    all.addAll(recipe.requires());
                    list.add(new TradeBook.Recipe(recipe.id(), recipe.level(), recipe.secret(), recipe.open(), recipe.quality(),
                            recipe.seconds(), recipe.ingredients(), recipe.outputs(), List.copyOf(all)));
                }
                recipes.put(entry.getKey(), List.copyOf(list));
            }
        }
        List<StarRules.Source> sources = new ArrayList<>();
        if (root.has("stars")) {
            root.getAsJsonArray("stars").forEach(s -> sources.add(StarRules.source(s.getAsJsonObject())));
        }
        return new TradeBundle(name, recipes, List.copyOf(sources));
    }

    public record Merged(List<TradeBook> books, StarRules stars) {
    }

    public static Merged apply(List<TradeBook> books, StarRules stars, List<TradeBundle> bundles) {
        Map<String, TradeBook> byTrade = new LinkedHashMap<>();
        books.forEach(book -> byTrade.put(book.trade(), book));
        StarRules rules = stars;
        for (TradeBundle bundle : bundles) {
            for (Map.Entry<String, List<TradeBook.Recipe>> entry : bundle.recipes().entrySet()) {
                TradeBook base = byTrade.get(entry.getKey());
                if (base == null) {
                    throw new IllegalArgumentException(bundle.name() + ": no role called " + entry.getKey());
                }
                byTrade.put(entry.getKey(), base.withRecipes(entry.getValue()));
            }
            rules = rules.withItems(bundle.sources());
        }
        return new Merged(List.copyOf(byTrade.values()), rules);
    }
}
