package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DropFilterService {
    private DropFilterService() {
    }

    private static SeasonRules.DropFilter filter(RotasData data) {
        SeasonRules.DropRules rules = SeasonService.rules(data).drops;
        if (rules == null) {
            return null;
        }
        if (rules.filter == null) {
            rules.filter = new SeasonRules.DropFilter();
        }
        return rules.filter;
    }

    public static boolean blocked(RotasData data, String entityId, String itemId) {
        SeasonRules.DropFilter filter = filter(data);
        if (filter == null || !filter.enabled || itemId == null || itemId.isBlank()) {
            return false;
        }
        for (String blocked : filter.blocked) {
            if (itemId.equalsIgnoreCase(blocked)) {
                return true;
            }
        }
        String[] perEntity = entityId == null ? null : filter.byEntity.get(entityId);
        if (perEntity != null) {
            for (String blocked : perEntity) {
                if (itemId.equalsIgnoreCase(blocked)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean blocked(RotasData data, Entity entity, ItemStack stack) {
        if (data == null || entity == null || stack == null || stack.isEmpty()) {
            return false;
        }
        return blocked(data, id(entity), id(stack));
    }

    public static String id(Entity entity) {
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public static String id(ItemStack stack) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static List<String> blockedGlobally(RotasData data) {
        SeasonRules.DropFilter filter = filter(data);
        return filter == null ? List.of() : List.of(filter.blocked);
    }

    public static List<String> blockedFor(RotasData data, String entityId) {
        SeasonRules.DropFilter filter = filter(data);
        if (filter == null || entityId == null) {
            return List.of();
        }
        String[] items = filter.byEntity.get(entityId);
        return items == null ? List.of() : List.of(items);
    }

    public static boolean setGlobal(MinecraftServer server, RotasData data, String itemId, boolean dropsNormally) {
        SeasonRules.DropFilter filter = filter(data);
        if (filter == null || !isItem(itemId)) {
            return false;
        }
        Set<String> blocked = new LinkedHashSet<>(List.of(filter.blocked));
        if (dropsNormally) {
            if (!blocked.remove(normalise(itemId))) {
                return true;
            }
        } else {
            if (blocked.size() >= SeasonRules.DropFilter.MAX_BLOCKED) {
                return false;
            }
            blocked.add(normalise(itemId));
        }
        filter.blocked = blocked.toArray(new String[0]);
        return save(server, data, "drop filter global " + itemId + " drops=" + dropsNormally);
    }

    public static boolean setForEntity(MinecraftServer server, RotasData data, String entityId,
                                       String itemId, boolean dropsNormally) {
        SeasonRules.DropFilter filter = filter(data);
        if (filter == null || !isItem(itemId) || !isEntity(entityId)) {
            return false;
        }
        String entity = normalise(entityId);
        Set<String> blocked = new LinkedHashSet<>(blockedFor(data, entity));
        if (dropsNormally) {
            if (!blocked.remove(normalise(itemId))) {
                return true;
            }
        } else {
            if (blocked.size() >= SeasonRules.DropFilter.MAX_PER_ENTITY) {
                return false;
            }
            if (!filter.byEntity.containsKey(entity)
                    && filter.byEntity.size() >= SeasonRules.DropFilter.MAX_ENTITIES) {
                return false;
            }
            blocked.add(normalise(itemId));
        }
        if (blocked.isEmpty()) {
            filter.byEntity.remove(entity);
        } else {
            filter.byEntity.put(entity, blocked.toArray(new String[0]));
        }
        return save(server, data, "drop filter " + entity + " " + itemId + " drops=" + dropsNormally);
    }

    public static boolean clearEntity(MinecraftServer server, RotasData data, String entityId) {
        SeasonRules.DropFilter filter = filter(data);
        if (filter == null || filter.byEntity.remove(normalise(entityId)) == null) {
            return false;
        }
        return save(server, data, "drop filter cleared " + entityId);
    }

    public static boolean setEnabled(MinecraftServer server, RotasData data, boolean enabled) {
        SeasonRules.DropFilter filter = filter(data);
        if (filter == null) {
            return false;
        }
        filter.enabled = enabled;
        return save(server, data, "drop filter enabled=" + enabled);
    }

    public static boolean enabled(RotasData data) {
        SeasonRules.DropFilter filter = filter(data);
        return filter != null && filter.enabled;
    }

    public static List<String> allItems() {
        List<String> ids = new ArrayList<>();
        BuiltInRegistries.ITEM.keySet().forEach(key -> ids.add(key.toString()));
        ids.sort(String::compareTo);
        return ids;
    }

    public static String saveTable(MinecraftServer server, RotasData data, String target, String json) {
        SeasonRules.DropRules drops = SeasonService.rules(data).drops;
        int colon = target == null ? -1 : target.indexOf(':');
        if (colon <= 0) {
            return "Unknown drop table";
        }
        String kind = target.substring(0, colon);
        String key = target.substring(colon + 1).trim();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        try {
            switch (kind) {
                case "grade" -> {
                    if (!key.matches("common|medium|rare|epic")) return "Unknown grade " + key;
                    SeasonRules.GradeLoot loot = gson.fromJson(json, SeasonRules.GradeLoot.class);
                    loot.minGold = Math.max(0, loot.minGold);
                    loot.maxGold = Math.max(loot.minGold, loot.maxGold);
                    loot.items = lines(loot.items);
                    drops.grades.put(key, loot);
                }
                case "rank", "entity" -> {
                    if (kind.equals("rank") && !key.matches("[A-Z_]{1,32}")) return "Unknown rank " + key;
                    if (kind.equals("entity") && net.minecraft.resources.ResourceLocation.tryParse(normalise(key)) == null) {
                        return "Unknown mob " + key;
                    }
                    if (kind.equals("entity") && (json == null || json.isBlank())) {
                        drops.plain.byEntity.remove(normalise(key));
                        break;
                    }
                    SeasonRules.RankDrop rule = gson.fromJson(json, SeasonRules.RankDrop.class);
                    rule.coinChance = unit(rule.coinChance);
                    rule.lootChance = unit(rule.lootChance);
                    rule.coinMin = Math.max(0, Math.min(1_000_000, rule.coinMin));
                    rule.coinMax = Math.max(rule.coinMin, Math.min(1_000_000, rule.coinMax));
                    rule.coinPerLevel = Double.isFinite(rule.coinPerLevel) ? Math.max(0, Math.min(10_000, rule.coinPerLevel)) : 0;
                    rule.coinMultiplier = Double.isFinite(rule.coinMultiplier) ? Math.max(0, Math.min(1000, rule.coinMultiplier)) : 1;
                    rule.grades = rule.grades == null ? new String[0] : java.util.Arrays.stream(rule.grades)
                            .filter(g -> g != null && g.matches("common|medium|rare|epic")).distinct().toArray(String[]::new);
                    rule.items = lines(rule.items);
                    if (kind.equals("rank")) {
                        drops.ranks.put(key, rule);
                    } else {
                        if (!drops.plain.byEntity.containsKey(normalise(key)) && drops.plain.byEntity.size() >= MAX_OWN_DROPS) {
                            return "Too many mobs with their own drop";
                        }
                        drops.plain.byEntity.put(normalise(key), rule);
                    }
                }
                default -> {
                    return "Unknown drop table";
                }
            }
        } catch (RuntimeException malformed) {
            return "Could not read the table: " + malformed.getMessage();
        }
        save(server, data, "drop table " + target + " saved");
        return null;
    }

    private static final int MAX_OWN_DROPS = 1024;

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    private static String[] lines(String[] items) {
        if (items == null) {
            return new String[0];
        }
        return java.util.Arrays.stream(items).filter(line -> line != null && !line.isBlank())
                .map(String::trim).limit(64).toArray(String[]::new);
    }

    private static boolean save(MinecraftServer server, RotasData data, String reason) {
        data.levelConfig().season().sanitize();
        data.setDirty();
        data.audit(reason);
        try {
            SeasonConfigFile.write(server, data.levelConfig().season());
        } catch (java.io.IOException failure) {
            Rotasutils.LOG.error("season.json could not be written after a drop filter change: {}",
                    failure.getMessage());
        }
        return true;
    }

    private static String normalise(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isItem(String id) {
        ResourceLocation location = ResourceLocation.tryParse(normalise(id));
        return location != null && BuiltInRegistries.ITEM.containsKey(location);
    }

    private static boolean isEntity(String id) {
        ResourceLocation location = ResourceLocation.tryParse(normalise(id));
        return location != null && BuiltInRegistries.ENTITY_TYPE.containsKey(location);
    }
}
