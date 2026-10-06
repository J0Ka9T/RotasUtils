package net.schwarz.rotasutils.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.schwarz.rotasutils.core.PerkRules;
import net.schwarz.rotasutils.core.StarRules;
import net.schwarz.rotasutils.core.TradeBook;
import net.schwarz.rotasutils.core.TradeOverrides;
import net.schwarz.rotasutils.core.WorthTable;
import net.schwarz.rotasutils.data.RotasData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingsPanels {
    private SettingsPanels() {
    }

    public static final int INT = 0, DECIMAL = 1, PERCENT = 2, FLAG = 3;

    public record Field(String key, String group, String field, double value, double def, double step, double min, double max, int format) {
    }

    public static List<String> scopes() {
        return scopes(null);
    }

    public static List<String> scopes(RotasData data) {
        List<String> out = new ArrayList<>(List.of("worth", "slots", "perks", "stars"));
        for (TradeBook book : TradeConfig.all()) {
            out.add("recipes:" + book.trade());
        }
        if (data != null) {
            out.addAll(RolePanels.scopes(data));
            out.addAll(MinePanels.scopes(data));
        }
        return out;
    }

public static List<Field> fields(String scope) {
        return fields(null, scope);
    }

    public static List<Field> fields(RotasData data, String scope) {
        if (RolePanels.owns(scope)) {
            return data == null ? List.of() : RolePanels.fields(data, scope);
        }
        if (MinePanels.owns(scope)) {
            return data == null ? List.of() : MinePanels.fields(data, scope);
        }
        List<Field> out = new ArrayList<>();
        if (scope.equals("worth")) {
            WorthTable t = WorthService.table();
            WorthTable d = WorthTable.empty();
            out.add(new Field("worth.sell", "worth", "sell", t.sellPercent(), d.sellPercent(), 5, 0, 500, INT));
            out.add(new Field("worth.silver", "worth", "silver", t.silverPercent(), d.silverPercent(), 10, 100, 2000, INT));
            out.add(new Field("worth.gold", "worth", "gold", t.goldPercent(), d.goldPercent(), 10, 100, 5000, INT));
            return out;
        }
        TradeOverrides overrides = TradeConfig.overrides();
        TradeOverrides.World base = TradeConfig.defaults();
        for (Map.Entry<String, Field> entry : spec(base).entrySet()) {
            Field f = entry.getValue();
            if (!inScope(scope, f.key())) {
                continue;
            }
            Double set = overrides.get(storedKey(f.key()));
            double value = set == null ? f.def() : f.key().endsWith(".enabled") ? 1 - set : set;
            out.add(new Field(f.key(), f.group(), f.field(), value, f.def(), f.step(), f.min(), f.max(), f.format()));
        }
        return out;
    }

    private static boolean inScope(String scope, String key) {
        return switch (scope.contains(":") ? scope.substring(0, scope.indexOf(':')) : scope) {
            case "slots" -> key.startsWith("slot.");
            case "perks" -> key.startsWith("perk.");
            case "stars" -> key.startsWith("star.");
            case "recipes" -> key.startsWith("recipe." + scope.substring(scope.indexOf(':') + 1) + ".");
            default -> false;
        };
    }

    private static String storedKey(String key) {
        return key.endsWith(".enabled") ? key.substring(0, key.length() - "enabled".length()) + "disabled" : key;
    }

    private static Map<String, Field> spec(TradeOverrides.World world) {
        Map<String, Field> out = new LinkedHashMap<>();
        for (TradeBook book : world.books()) {
            for (int i = 0; i < book.slotLevels().length; i++) {
                put(out, "slot." + book.trade() + "." + i, "trade:" + book.trade(), "slot", book.slotLevels()[i], 1, 1, 100, INT);
            }
        }
        for (PerkRules.Perk p : world.perks().perks()) {
            String k = "perk." + p.role() + "." + p.type() + ".", g = "perk:" + p.role() + ":" + p.type();
            put(out, k + "base", g, "base", p.base(), 0.5, 0, 10000, DECIMAL);
            put(out, k + "perLevel", g, "perLevel", p.perLevel(), 0.05, 0, 1000, DECIMAL);
            put(out, k + "max", g, "max", p.max(), 1, 0, 10000, DECIMAL);
            if (p.radius() > 0) {
                put(out, k + "radius", g, "radius", p.radius(), 1, 0, 48, INT);
            }
        }
        for (StarRules.Source s : world.stars().sources()) {
            String k = "star." + s.job() + ".", g = "star:" + s.job();
            put(out, k + "silverBase", g, "silverBase", s.silverBase(), 0.005, 0, 1, PERCENT);
            put(out, k + "silverPerLevel", g, "silverPerLevel", s.silverPerLevel(), 0.001, 0, 1, PERCENT);
            put(out, k + "goldFromLevel", g, "goldFromLevel", s.goldFromLevel(), 1, 1, 100, INT);
            put(out, k + "goldBase", g, "goldBase", s.goldBase(), 0.001, 0, 1, PERCENT);
            put(out, k + "goldPerLevel", g, "goldPerLevel", s.goldPerLevel(), 0.0005, 0, 1, PERCENT);
            put(out, k + "maxChance", g, "maxChance", s.maxChance(), 0.01, 0, 0.9, PERCENT);
        }
        for (TradeBook book : world.books()) {
            for (TradeBook.Recipe r : book.recipes()) {
                String k = "recipe." + book.trade() + "." + r.id() + ".", g = "recipe:" + book.trade() + ":" + r.id();
                put(out, k + "enabled", g, "enabled", 1, 1, 0, 1, FLAG);
                put(out, k + "level", g, "level", r.level(), 1, 1, 100, INT);
                put(out, k + "seconds", g, "seconds", r.seconds(), 5, 5, 86_400, INT);
                put(out, k + "secret", g, "secret", r.secret() ? 1 : 0, 1, 0, 1, FLAG);
                put(out, k + "open", g, "open", r.open() ? 1 : 0, 1, 0, 1, FLAG);
            }
        }
        return out;
    }

    private static void put(Map<String, Field> out, String key, String group, String field, double def, double step,
                            double min, double max, int format) {
        out.put(key, new Field(key, group, field, def, def, step, min, max, format));
    }

public static String set(MinecraftServer server, String scope, String key, double value) {
        if (!Double.isFinite(value)) {
            return "not a number";
        }
        if (RolePanels.owns(scope)) {
            return RolePanels.set(RotasData.get(server), key, value);
        }
        if (MinePanels.owns(scope)) {
            return MinePanels.set(RotasData.get(server), key, value);
        }
        Field field = fields(scope).stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
        if (field == null) {
            return "unknown setting";
        }
        double clamped = Math.max(field.min(), Math.min(field.max(), Math.round(value * 10_000) / 10_000.0));
        if (key.startsWith("worth.")) {
            WorthTable t = WorthService.table();
            int v = (int) Math.round(clamped);
            int sell = key.equals("worth.sell") ? v : t.sellPercent();
            int silver = key.equals("worth.silver") ? v : t.silverPercent();
            int gold = key.equals("worth.gold") ? v : t.goldPercent();
            WorthService.setSettings(server, sell, silver, Math.max(gold, silver));
            return null;
        }
        String stored = storedKey(key);
        double storedValue = key.endsWith(".enabled") ? 1 - clamped : clamped;
        double shipped = key.endsWith(".enabled") ? 0 : field.def();
        TradeOverrides next = Math.abs(storedValue - shipped) < 1e-9
                ? TradeConfig.overrides().without(stored) : TradeConfig.overrides().with(stored, storedValue);
        TradeConfig.setOverrides(server, next);
        return null;
    }

    public static void reset(MinecraftServer server, String scope) {
        if (RolePanels.owns(scope)) {
            RolePanels.reset(RotasData.get(server), scope);
            return;
        }
        if (MinePanels.owns(scope)) {
            MinePanels.reset(RotasData.get(server), scope);
            return;
        }
        if (scope.equals("worth")) {
            WorthTable d = WorthTable.empty();
            WorthService.setSettings(server, d.sellPercent(), d.silverPercent(), d.goldPercent());
            return;
        }
        String prefix = switch (scope.contains(":") ? scope.substring(0, scope.indexOf(':')) : scope) {
            case "slots" -> "slot.";
            case "perks" -> "perk.";
            case "stars" -> "star.";
            case "recipes" -> "recipe." + scope.substring(scope.indexOf(':') + 1) + ".";
            default -> null;
        };
        if (prefix != null) {
            TradeConfig.setOverrides(server, TradeConfig.overrides().withoutPrefix(prefix));
        }
    }

public static CompoundTag payload(String scope, int page) {
        return payload(null, scope, page);
    }

    public static CompoundTag payload(RotasData data, String scope, int page) {
        CompoundTag tag = new CompoundTag();
        String use = scopes(data).contains(scope) ? scope : "worth";
        tag.putString("scope", use);
        tag.putInt("page", Math.max(0, page));
        ListTag tabs = new ListTag();
        scopes(data).forEach(s -> tabs.add(StringTag.valueOf(s)));
        tag.put("scopes", tabs);
        ListTag rows = new ListTag();
        for (Field f : fields(data, use)) {
            CompoundTag row = new CompoundTag();
            row.putString("key", f.key());
            row.putString("group", f.group());
            row.putString("field", f.field());
            row.putDouble("value", f.value());
            row.putDouble("def", f.def());
            row.putDouble("step", f.step());
            row.putDouble("min", f.min());
            row.putDouble("max", f.max());
            row.putInt("format", f.format());
            rows.add(row);
        }
        tag.put("fields", rows);
        return tag;
    }
}
