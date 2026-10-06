package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.event.EventRules;
import net.schwarz.rotasutils.event.EventType;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventService {
    private static final Map<UUID, Map<String, Long>> LAST_FIRED = new ConcurrentHashMap<>();

    private EventService() {
    }

    public static void clear() {
        LAST_FIRED.clear();
    }

    public static void forget(UUID player) {
        LAST_FIRED.remove(player);
    }

    public static SeasonRules.EventRule rule(RotasData data, EventType type, String subject) {
        if (data == null || type == null) {
            return null;
        }
        SeasonRules.EventRules catalogue = SeasonService.rules(data).events;
        if (catalogue == null || !catalogue.enabled || catalogue.rules == null) {
            return null;
        }
        List<SeasonRules.EventRule> rules = new ArrayList<>(List.of(catalogue.rules));
        return EventRules.best(rules, SeasonRules.EventRule::eventType, rule -> rule.filter, type, subject);
    }

    public static double multiplier(RotasData data, EventType type, String subject) {
        SeasonRules.EventRule rule = rule(data, type, subject);
        return rule == null || !rule.enabled ? 1.0 : Math.max(0, rule.xpMultiplier);
    }

    public static double fire(ServerPlayer player, RotasData data, EventType type, String subject) {
        if (player == null || data == null) {
            return 1.0;
        }
        SeasonRules.EventRule rule = rule(data, type, subject);
        if (rule == null || !rule.enabled) {
            return 1.0;
        }
        boolean pays = rule.xpFlat > 0 || rule.gold > 0
                || rule.announcement() != EventRules.Announce.OFF;
        if (!pays) {
            return Math.max(0, rule.xpMultiplier);
        }
        try {
            String key = rule.type + "|" + rule.filter;
            long now = System.currentTimeMillis() / 1000L;
            Map<String, Long> fired = LAST_FIRED.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
            if (!EventRules.offCooldown(now, fired.getOrDefault(key, 0L), rule.cooldownSeconds)) {
                return Math.max(0, rule.xpMultiplier);
            }
            fired.put(key, now);
            pay(player, data, rule, type, subject);
        } catch (RuntimeException failure) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("Event rule {} failed: {}", rule.type, failure.getMessage());
        }
        return Math.max(0, rule.xpMultiplier);
    }

    private static void pay(ServerPlayer player, RotasData data, SeasonRules.EventRule rule,
                            EventType type, String subject) {
        PlayerProgress progress = data.progress(player.getUUID());
        boolean paid = false;
        if (rule.xpFlat > 0) {
            ProgressService.addExperience(player, data, rule.xpFlat, false);
            paid = true;
        }
        if (rule.gold > 0) {
            progress.rpg().currency(SeasonService.rules(data).currency, rule.gold);
            progress.markDirty();
            data.setDirty();
            paid = true;
        }
        EventRules.Announce announce = rule.announcement();
        if (announce != EventRules.Announce.OFF) {
            var message = ThaiText.c("rotasutils.event.announce",
                    player.getGameProfile().getName(), ThaiText.t(type.nameKey()),
                    subject == null || subject.isBlank() ? "-" : subject);
            if (announce == EventRules.Announce.SERVER) {
                player.server.getPlayerList().broadcastSystemMessage(message, false);
            } else {
                player.sendSystemMessage(message);
            }
        }
        if (paid) {
            RotasNetwork.syncProgress(player);
        }
    }

public static List<SeasonRules.EventRule> rules(RotasData data) {
        SeasonRules.EventRules catalogue = SeasonService.rules(data).events;
        return catalogue == null || catalogue.rules == null ? List.of() : List.of(catalogue.rules);
    }

    public static SeasonRules.EventRule exact(RotasData data, EventType type, String filter) {
        String wanted = filter == null ? "" : filter.trim().toLowerCase(java.util.Locale.ROOT);
        for (SeasonRules.EventRule rule : rules(data)) {
            if (rule.eventType() == type && rule.filter.equals(wanted)) {
                return rule;
            }
        }
        return null;
    }

    public static boolean add(net.minecraft.server.MinecraftServer server, RotasData data,
                              EventType type, String filter) {
        SeasonRules.EventRules catalogue = SeasonService.rules(data).events;
        if (catalogue == null || type == null || !EventRules.validFilter(filter)) {
            return false;
        }
        String wanted = filter == null ? "" : filter.trim().toLowerCase(java.util.Locale.ROOT);
        if (wanted.isEmpty() || exact(data, type, wanted) != null
                || catalogue.rules.length >= SeasonRules.EventRules.MAX_RULES) {
            return false;
        }
        SeasonRules.EventRule rule = new SeasonRules.EventRule();
        rule.type = type.name();
        rule.filter = wanted;
        List<SeasonRules.EventRule> updated = new ArrayList<>(List.of(catalogue.rules));
        updated.add(rule);
        catalogue.rules = updated.toArray(new SeasonRules.EventRule[0]);
        return save(server, data, "event rule added " + type + " " + wanted);
    }

    public static boolean remove(net.minecraft.server.MinecraftServer server, RotasData data,
                                 EventType type, String filter) {
        SeasonRules.EventRules catalogue = SeasonService.rules(data).events;
        String wanted = filter == null ? "" : filter.trim().toLowerCase(java.util.Locale.ROOT);
        if (catalogue == null || type == null || wanted.isEmpty()) {
            return false;
        }
        List<SeasonRules.EventRule> updated = new ArrayList<>();
        boolean removed = false;
        for (SeasonRules.EventRule rule : catalogue.rules) {
            if (!removed && rule.eventType() == type && rule.filter.equals(wanted)) {
                removed = true;
                continue;
            }
            updated.add(rule);
        }
        if (!removed) {
            return false;
        }
        catalogue.rules = updated.toArray(new SeasonRules.EventRule[0]);
        return save(server, data, "event rule removed " + type + " " + wanted);
    }

    public static boolean edit(net.minecraft.server.MinecraftServer server, RotasData data, EventType type,
                               String filter, boolean enabled, double xpMultiplier, long xpFlat, long gold,
                               EventRules.Announce announce, int cooldownSeconds) {
        SeasonRules.EventRule rule = exact(data, type, filter);
        if (rule == null) {
            return false;
        }
        rule.enabled = enabled;
        rule.xpMultiplier = xpMultiplier;
        rule.xpFlat = xpFlat;
        rule.gold = gold;
        rule.announce = (announce == null ? EventRules.Announce.OFF : announce).name();
        rule.cooldownSeconds = cooldownSeconds;
        return save(server, data, "event rule edited " + type + " " + rule.filter);
    }

    public static boolean setEnabled(net.minecraft.server.MinecraftServer server, RotasData data, boolean enabled) {
        SeasonRules.EventRules catalogue = SeasonService.rules(data).events;
        if (catalogue == null) {
            return false;
        }
        catalogue.enabled = enabled;
        return save(server, data, "event catalogue enabled=" + enabled);
    }

    public static boolean enabled(RotasData data) {
        SeasonRules.EventRules catalogue = SeasonService.rules(data).events;
        return catalogue != null && catalogue.enabled;
    }

    private static boolean save(net.minecraft.server.MinecraftServer server, RotasData data, String reason) {
        data.levelConfig().season().sanitize();
        data.setDirty();
        data.audit(reason);
        try {
            SeasonConfigFile.write(server, data.levelConfig().season());
        } catch (java.io.IOException failure) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("season.json could not be written after an event change: {}",
                    failure.getMessage());
        }
        return true;
    }

    public static EventType typeOf(net.schwarz.rotasutils.quest.objective.EventKind kind) {
        return EventType.of(kind);
    }
}
