package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record TradeBook(String trade, String job, String station, int[] slotLevels, List<Recipe> recipes) {
    public static final int MAX_RECIPES = 200;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public record Ingredient(String item, int count, int minStar, boolean graded) {
        public Ingredient(String item, int count, int minStar) {
            this(item, count, minStar, false);
        }

        public List<String> options() {
            return List.of(item.split("\\|"));
        }

        public String icon() {
            for (String option : options()) {
                if (!option.startsWith("#")) return option;
            }
            return "";
        }

        public int need(int target) {
            return graded ? Math.max(minStar, target) : minStar;
        }
    }

    public record Output(String item, int count, String nbt) {
        public Output(String item, int count) {
            this(item, count, "");
        }
    }

    public record Recipe(String id, int level, boolean secret, boolean open, boolean quality, int seconds,
                         List<Ingredient> ingredients, List<Output> outputs, List<String> requires) {
        public Recipe(String id, int level, boolean secret, boolean open, boolean quality, int seconds,
                      List<Ingredient> ingredients, List<Output> outputs) {
            this(id, level, secret, open, quality, seconds, ingredients, outputs, List.of());
        }

        public int outputStar(int target) {
            return quality ? Math.max(0, Math.min(2, target)) : 0;
        }
    }

    public Recipe find(String id) {
        for (Recipe recipe : recipes) {
            if (recipe.id().equals(id)) return recipe;
        }
        return null;
    }

    public int slots(int level) {
        int count = 0;
        for (int needed : slotLevels) {
            if (level >= needed) count++;
        }
        return count;
    }

    public Set<String> stationOutputs() {
        Set<String> out = new HashSet<>();
        for (Recipe recipe : recipes) {
            if (!recipe.open()) out.add(recipe.outputs().get(0).item());
        }
        for (Recipe recipe : recipes) {
            if (recipe.open()) recipe.outputs().forEach(o -> out.remove(o.item()));
        }
        return out;
    }

    public enum State { AVAILABLE, NEED_LEVEL, NEED_SCROLL, SECRET }

    public static State state(Recipe recipe, int level, boolean learned) {
        boolean levelOk = level >= recipe.level();
        if (!recipe.secret()) return levelOk ? State.AVAILABLE : State.NEED_LEVEL;
        if (learned) return levelOk ? State.AVAILABLE : State.NEED_LEVEL;
        return levelOk ? State.NEED_SCROLL : State.SECRET;
    }

public static TradeBook parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String trade = str(root, "trade", "");
        if (!ID.matcher(trade).matches()) throw new IllegalArgumentException("trade must be 1..32 of a-z, 0-9, _");
        String job = str(root, "job", trade);
        String station = item(str(root, "station", ""));
        JsonArray levels = root.has("slotLevels") ? root.getAsJsonArray("slotLevels") : new JsonArray();
        int[] slotLevels = new int[Math.min(levels.size(), 8)];
        for (int i = 0; i < slotLevels.length; i++) {
            slotLevels[i] = levels.get(i).getAsInt();
        }
        if (slotLevels.length == 0) throw new IllegalArgumentException("slotLevels needs at least one level");
        List<Recipe> recipes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("recipes")) {
            Recipe recipe = recipe(element.getAsJsonObject());
            if (!seen.add(recipe.id())) throw new IllegalArgumentException("Duplicate recipe id: " + recipe.id());
            recipes.add(recipe);
        }
        if (recipes.size() > MAX_RECIPES) throw new IllegalArgumentException("More than " + MAX_RECIPES + " recipes");
        return new TradeBook(trade, job, station, slotLevels, List.copyOf(recipes));
    }

    public TradeBook withRecipes(List<Recipe> extra) {
        List<Recipe> all = new ArrayList<>(recipes);
        Set<String> seen = new HashSet<>();
        recipes.forEach(r -> seen.add(r.id()));
        for (Recipe recipe : extra) {
            if (!seen.add(recipe.id())) throw new IllegalArgumentException("Duplicate recipe id: " + recipe.id());
            all.add(recipe);
        }
        if (all.size() > MAX_RECIPES) throw new IllegalArgumentException("More than " + MAX_RECIPES + " recipes");
        return new TradeBook(trade, job, station, slotLevels, List.copyOf(all));
    }

    public static Recipe recipe(JsonObject o) {
        String id = str(o, "id", "");
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Recipe id must be 1..32 of a-z, 0-9, _: " + id);
        int level = (int) num(o, "level", 1);
        int seconds = (int) num(o, "seconds", 30);
        if (level < 1 || level > 100) throw new IllegalArgumentException(id + ": level must be 1..100");
        if (seconds < 5 || seconds > 86_400) throw new IllegalArgumentException(id + ": seconds must be 5..86400");
        List<Ingredient> ingredients = new ArrayList<>();
        boolean anyGraded = false;
        for (JsonElement e : o.getAsJsonArray("ingredients")) {
            JsonObject i = e.getAsJsonObject();
            int count = (int) num(i, "count", 1), min = (int) num(i, "star", 0);
            if (count < 1 || count > 64 || min < 0 || min > 2) throw new IllegalArgumentException(id + ": bad ingredient");
            boolean graded = flag(i, "graded");
            anyGraded |= graded;
            ingredients.add(new Ingredient(choices(str(i, "item", "")), count, min, graded));
        }
        List<Output> outputs = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("outputs")) {
            JsonObject out = e.getAsJsonObject();
            int count = (int) num(out, "count", 1);
            if (count < 1 || count > 64) throw new IllegalArgumentException(id + ": bad output count");
            outputs.add(new Output(item(str(out, "item", "")), count, str(out, "nbt", "")));
        }
        if (ingredients.isEmpty() || ingredients.size() > 6) throw new IllegalArgumentException(id + ": needs 1..6 ingredients");
        if (outputs.isEmpty() || outputs.size() > 4) throw new IllegalArgumentException(id + ": needs 1..4 outputs");
        boolean quality = flag(o, "quality");
        if (quality != anyGraded) {
            throw new IllegalArgumentException(id + ": a quality recipe needs a graded ingredient, and only then");
        }
        List<String> requires = new ArrayList<>();
        if (o.has("requires")) {
            for (JsonElement mod : o.getAsJsonArray("requires")) {
                requires.add(mod.getAsString());
            }
        }
        return new Recipe(id, level, flag(o, "secret"), flag(o, "open"), quality, seconds,
                List.copyOf(ingredients), List.copyOf(outputs), List.copyOf(requires));
    }

    private static boolean flag(JsonObject o, String key) {
        return o.has(key) && o.get(key).getAsBoolean();
    }

    private static String choices(String text) {
        for (String part : text.split("\\|")) {
            item(part.startsWith("#") ? part.substring(1) : part);
        }
        return text;
    }

    private static String item(String id) {
        if (!ITEM.matcher(id).matches()) throw new IllegalArgumentException("Not an item id: " + id);
        return id;
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }
}
