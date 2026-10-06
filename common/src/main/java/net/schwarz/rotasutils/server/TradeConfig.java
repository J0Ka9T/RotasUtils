package net.schwarz.rotasutils.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.PerkRules;
import net.schwarz.rotasutils.core.StarRules;
import net.schwarz.rotasutils.core.TradeBook;
import net.schwarz.rotasutils.core.TradeBundle;
import net.schwarz.rotasutils.core.TradeOverrides;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobDef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TradeConfig {
    private static final String RESOURCES = "/data/rotasutils/trades/";
    private static volatile Map<String, TradeBook> books;
    private static volatile StarRules stars;
    private static volatile Set<String> sealed = Set.of();
    private static volatile PerkRules perks;
    private static volatile TradeOverrides overrides = TradeOverrides.empty();
    private static volatile TradeOverrides.World defaults;

    private TradeConfig() {
    }

    private static void ensureLoaded() {
        if (books == null) {
            synchronized (TradeConfig.class) {
                if (books == null) {
                    perks = PerkRules.parse(builtInText("perks"));
                    set(builtInBooks(), builtInStars());
                    List<String> ignored = new ArrayList<>();
                    layer(ignored, id -> builtInText("compat/" + id));
                }
            }
        }
    }

    public static List<TradeBook> all() {
        ensureLoaded();
        return List.copyOf(books.values());
    }

    public static TradeBook book(String trade) {
        ensureLoaded();
        return books.get(trade);
    }

    public static TradeBook byStation(String stationId) {
        for (TradeBook book : all()) {
            if (book.station().equals(stationId)) return book;
        }
        return null;
    }

    public static TradeBook byJob(String jobId) {
        for (TradeBook book : all()) {
            if (book.job().equals(jobId)) return book;
        }
        return null;
    }

    public static TradeOverrides overrides() {
        ensureLoaded();
        return overrides;
    }

    public static TradeOverrides.World defaults() {
        ensureLoaded();
        return defaults;
    }

    public static void setOverrides(MinecraftServer server, TradeOverrides next) {
        ensureLoaded();
        overrides = next;
        applyOverrides();
        try {
            Path dir = directory(server);
            Files.createDirectories(dir);
            write(dir.resolve("overrides.json"), next.toJson());
        } catch (IOException failure) {
            Rotasutils.LOG.error("overrides.json was not saved: {}", failure.getMessage());
        }
    }

    private static void applyOverrides() {
        TradeOverrides.World world = overrides.apply(defaults.books(), defaults.stars(), defaults.perks());
        Map<String, TradeBook> next = new LinkedHashMap<>();
        world.books().forEach(book -> next.put(book.trade(), book));
        perks = world.perks();
        set(next, world.stars());
    }

    public static PerkRules perks() {
        ensureLoaded();
        return perks;
    }

    public static StarRules stars() {
        ensureLoaded();
        return stars;
    }

    public static boolean sealed(String itemId) {
        ensureLoaded();
        return sealed.contains(itemId);
    }

    public static String sealedBy(String itemId) {
        for (TradeBook book : all()) {
            if (book.stationOutputs().contains(itemId)) return book.trade();
        }
        return null;
    }

    public static Path directory(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve("config").resolve(Rotasutils.MOD_ID).resolve("trades");
    }

    public static void load(MinecraftServer server) {
        String error = reload(server);
        if (error != null) {
            Rotasutils.LOG.error("Some trade files were not applied, the previous books stay active: {}", error);
        }
    }

    public static String reload(MinecraftServer server) {
        ensureLoaded();
        List<String> errors = new ArrayList<>();
        Map<String, TradeBook> next = new LinkedHashMap<>();
        StarRules nextStars = stars;
        Path dir = directory(server);
        try {
            Files.createDirectories(dir);
            for (String id : builtInIds()) {
                Path file = dir.resolve(id + ".json");
                if (!Files.exists(file)) {
                    write(file, builtInText(id));
                }
                try {
                    TradeBook book = TradeBook.parse(Files.readString(file, StandardCharsets.UTF_8));
                    if (!book.trade().equals(id)) {
                        throw new IllegalArgumentException("\"trade\" is " + book.trade() + " but the file is " + id + ".json");
                    }
                    next.put(id, book);
                } catch (RuntimeException failure) {
                    errors.add(id + ".json: " + message(failure));
                    next.put(id, books.getOrDefault(id, parseBuiltIn(id)));
                }
            }
            Path starFile = dir.resolve("stars.json");
            if (!Files.exists(starFile)) {
                write(starFile, builtInText("stars"));
            }
            try {
                nextStars = StarRules.parse(Files.readString(starFile, StandardCharsets.UTF_8));
            } catch (RuntimeException failure) {
                errors.add("stars.json: " + message(failure));
            }
        } catch (IOException failure) {
            errors.add(message(failure));
            return String.join("; ", errors);
        }
        set(next, nextStars);
        try {
            Path perkFile = dir.resolve("perks.json");
            if (!Files.exists(perkFile)) {
                write(perkFile, builtInText("perks"));
            }
            perks = PerkRules.parse(Files.readString(perkFile, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException failure) {
            errors.add("perks.json: " + message(failure));
        }
        try {
            Path overrideFile = dir.resolve("overrides.json");
            overrides = Files.exists(overrideFile)
                    ? TradeOverrides.parse(Files.readString(overrideFile, StandardCharsets.UTF_8)) : TradeOverrides.empty();
        } catch (IOException | RuntimeException failure) {
            errors.add("overrides.json: " + message(failure));
        }
        String worthError = WorthService.load(server);
        if (worthError != null) {
            errors.add("worth.json: " + worthError);
        }
        Path compat = dir.resolve("compat");
        try {
            Files.createDirectories(compat);
            for (String id : builtInBundleIds()) {
                Path file = compat.resolve(id + ".json");
                if (!Files.exists(file)) {
                    write(file, builtInText("compat/" + id));
                }
            }
        } catch (IOException failure) {
            errors.add(message(failure));
        }
        layer(errors, id -> {
            try {
                Path file = compat.resolve(id + ".json");
                return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : builtInText("compat/" + id);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        });
        dropLegacyRows(RotasData.get(server));
        return errors.isEmpty() ? null : String.join("; ", errors);
    }

    private static void layer(List<String> errors, java.util.function.Function<String, String> text) {
        List<TradeBook> merged = new ArrayList<>(books.values());
        StarRules rules = stars;
        for (String id : builtInBundleIds()) {
            try {
                TradeBundle.Merged result = TradeBundle.apply(merged, rules, List.of(TradeBundle.parse(text.apply(id))));
                merged = new ArrayList<>(result.books());
                rules = result.stars();
            } catch (RuntimeException failure) {
                errors.add("compat/" + id + ".json: " + message(failure));
            }
        }
        defaults = new TradeOverrides.World(List.copyOf(merged), rules, perks);
        applyOverrides();
    }

    private static List<String> builtInBundleIds() {
        List<String> ids = new ArrayList<>();
        for (JsonElement id : JsonParser.parseString(builtInText("compat/index")).getAsJsonObject().getAsJsonArray("bundles")) {
            ids.add(id.getAsString());
        }
        return ids;
    }

    private static void set(Map<String, TradeBook> next, StarRules nextStars) {
        Set<String> out = new HashSet<>();
        for (TradeBook book : next.values()) {
            out.addAll(book.stationOutputs());
        }
        Map<String, TradeBook> ordered = new LinkedHashMap<>();
        for (String id : builtInIds()) {
            if (next.containsKey(id)) ordered.put(id, next.get(id));
        }
        books = java.util.Collections.unmodifiableMap(ordered);
        stars = nextStars;
        sealed = Set.copyOf(out);
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null ? failure.toString() : failure.getMessage();
    }

    private static void write(Path file, String text) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

private static List<String> builtInIds() {
        List<String> ids = new ArrayList<>();
        for (JsonElement id : JsonParser.parseString(builtInText("index")).getAsJsonObject().getAsJsonArray("trades")) {
            ids.add(id.getAsString());
        }
        return ids;
    }

    private static Map<String, TradeBook> builtInBooks() {
        Map<String, TradeBook> out = new LinkedHashMap<>();
        for (String id : builtInIds()) {
            out.put(id, parseBuiltIn(id));
        }
        return out;
    }

    private static TradeBook parseBuiltIn(String id) {
        return TradeBook.parse(builtInText(id));
    }

    private static StarRules builtInStars() {
        return StarRules.parse(builtInText("stars"));
    }

    private static String builtInText(String name) {
        try (InputStream in = TradeConfig.class.getResourceAsStream(RESOURCES + name + ".json")) {
            if (in == null) throw new IllegalStateException(name + ".json is missing from the mod jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

private static final Set<String> LEGACY = Set.of("minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_chicken",
            "minecraft:cooked_porkchop", "minecraft:baked_potato", "minecraft:cookie", "minecraft:mushroom_stew",
            "minecraft:pumpkin_pie", "minecraft:beetroot_soup", "minecraft:cooked_salmon", "minecraft:cake",
            "minecraft:golden_carrot", "minecraft:rabbit_stew", "minecraft:golden_apple");

    public static int dropLegacyRows(RotasData data) {
        TradeBook chefBook = book("chef");
        JobDef chef = chefBook == null ? null : data.job(chefBook.job());
        if (chef == null) {
            return 0;
        }
        int before = chef.production().size();
        chef.production().removeIf(row -> (row.activity() == JobDef.ProductionEntry.Activity.CRAFT
                || row.activity() == JobDef.ProductionEntry.Activity.SMELT) && LEGACY.contains(row.selector()));
        int removed = before - chef.production().size();
        if (removed > 0) {
            data.putJob(chef);
            Rotasutils.LOG.info("Chef job: removed {} old sealed rows; dishes are made at the Cooking Station now", removed);
        }
        return removed;
    }
}
