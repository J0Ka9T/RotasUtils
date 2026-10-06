package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;

public final class ZoneSummary {
    private ZoneSummary() {
    }

    public static List<String> lines(ZoneDef zone) {
        ZoneFeatures features = zone.features();
        List<String> out = new ArrayList<>();
        out.add((zone.name().isEmpty() ? zone.id() : zone.name()) + " [" + zone.id() + "] - " + features.type().label()
                + (zone.enabled() ? "" : " (disabled)"));
        out.add("Mobs " + zone.levelLabel() + ", recommended " + zone.recommendedMin() + "-" + zone.recommendedMax()
                + ", " + zone.danger().label() + (zone.safe() ? ", safe" : "") + ", priority " + zone.priority()
                + ", XP x" + trim(zone.xpMultiplier()));
        out.add(zone.areas().isEmpty() ? "Shape: whole dimension" + dimension(zone)
                : "Shape: " + zone.areas().size() + " area(s)" + (zone.excludedAreas().isEmpty() ? ""
                : ", " + zone.excludedAreas().size() + " excluded") + ", fade-in " + zone.transitionBlocks() + " blocks"
                + dimension(zone));
        int locks = (int) zone.entryRequirements().stream().filter(r -> !r.recommendationOnly()).count();
        out.add(locks == 0 ? "Entry: open" : "Entry: locked by " + locks + " requirement(s)");
        if (features.dungeonRun()) {
            ZoneDungeon dungeon = features.dungeon();
            out.add("Dungeon: " + dungeon.waves().size() + " wave(s)" + (dungeon.bossProfile().isEmpty() ? "" : " + boss")
                    + ", up to " + dungeon.maxPlayers() + " players, " + dungeon.timeLimitSeconds() + "s limit"
                    + (dungeon.entryGold() > 0 ? ", fee " + dungeon.entryGold() : "")
                    + (dungeon.keyItem().isEmpty() ? "" : ", key " + dungeon.keyCount() + "x " + dungeon.keyItem()));
        }
        long bosses = features.spawnPoints().stream().filter(p -> p.kind() == ZoneSpawnPoint.Kind.BOSS).count();
        out.add((features.isolateMobs() ? "Mobs: only this zone's own setups" : "Mobs: normal rules")
                + ", " + features.spawnPoints().size() + " spawn point(s)" + (bosses > 0 ? " (" + bosses + " boss)" : ""));
        out.add("Rules: " + rules(zone.combatRules()));
        List<String> moves = new ArrayList<>();
        if (features.movement().noElytra()) moves.add("no elytra");
        if (features.movement().noFlight()) moves.add("no flight");
        if (features.movement().noEnderPearlIn()) moves.add("no pearls in");
        if (!moves.isEmpty() || !features.effects().isEmpty()) {
            out.add("Players: " + (moves.isEmpty() ? "" : String.join(", ", moves))
                    + (moves.isEmpty() || features.effects().isEmpty() ? "" : "; ")
                    + (features.effects().isEmpty() ? "" : features.effects().size() + " effect(s)"));
        }
        return List.copyOf(out);
    }

    private static String dimension(ZoneDef zone) {
        return zone.dimension().isEmpty() ? ", every dimension" : ", " + zone.dimension();
    }

    private static String rules(ZoneCombatRules rules) {
        List<String> set = new ArrayList<>();
        bool(set, "PvP", rules.pvpEnabled());
        bool(set, "hostile spawns", rules.hostileSpawningEnabled());
        bool(set, "keep inventory", rules.keepInventory());
        number(set, "damage taken", rules.playerDamageTakenMultiplier(), "x");
        number(set, "damage dealt", rules.playerDamageDealtMultiplier(), "x");
        number(set, "healing", rules.healingMultiplier(), "x");
        number(set, "XP loss", rules.rotasXpLossPercentage(), "%");
        return set.isEmpty() ? "inherited from the world" : String.join(", ", set);
    }

    private static void bool(List<String> out, String name, RuleBool rule) {
        if (rule.overridden()) out.add(name + (rule.value() ? " on" : " off"));
    }

    private static void number(List<String> out, String name, RuleDouble rule, String unit) {
        if (rule.overridden()) out.add(name + " " + ("x".equals(unit) ? "x" + trim(rule.value()) : trim(rule.value()) + unit));
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
