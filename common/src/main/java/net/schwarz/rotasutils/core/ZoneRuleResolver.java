package net.schwarz.rotasutils.core;

import java.util.*;

public final class ZoneRuleResolver {
    private ZoneRuleResolver() {}
    public static ResolvedZoneRules resolve(Collection<ZoneDef> zones, String dimension, double x, double y, double z) {
        List<ZoneDef> stack = zones.stream().filter(zone -> zone.appliesTo(dimension) && zone.contains(x,y,z)).sorted(ZoneDef::compare).toList();
        Optional<Boolean> pvp=Optional.empty(), spawn=Optional.empty(), keep=Optional.empty();
        OptionalDouble taken=OptionalDouble.empty(), dealt=OptionalDouble.empty(), healing=OptionalDouble.empty(), xp=OptionalDouble.empty();
        Optional<RespawnTarget> respawn=Optional.empty(); Map<String,String> sources=new LinkedHashMap<>();
        for (ZoneDef zone : stack) {
            ZoneCombatRules r=zone.combatRules();
            if (pvp.isEmpty() && r.pvpEnabled().overridden()) { pvp=Optional.of(r.pvpEnabled().value()); sources.put(ResolvedZoneRules.PVP,zone.id()); }
            if (spawn.isEmpty() && r.hostileSpawningEnabled().overridden()) { spawn=Optional.of(r.hostileSpawningEnabled().value()); sources.put(ResolvedZoneRules.HOSTILE_SPAWNING,zone.id()); }
            if (taken.isEmpty() && r.playerDamageTakenMultiplier().overridden()) { taken=OptionalDouble.of(r.playerDamageTakenMultiplier().value()); sources.put(ResolvedZoneRules.DAMAGE_TAKEN,zone.id()); }
            if (dealt.isEmpty() && r.playerDamageDealtMultiplier().overridden()) { dealt=OptionalDouble.of(r.playerDamageDealtMultiplier().value()); sources.put(ResolvedZoneRules.DAMAGE_DEALT,zone.id()); }
            if (healing.isEmpty() && r.healingMultiplier().overridden()) { healing=OptionalDouble.of(r.healingMultiplier().value()); sources.put(ResolvedZoneRules.HEALING,zone.id()); }
            if (keep.isEmpty() && r.keepInventory().overridden()) { keep=Optional.of(r.keepInventory().value()); sources.put(ResolvedZoneRules.KEEP_INVENTORY,zone.id()); }
            if (xp.isEmpty() && r.rotasXpLossPercentage().overridden()) { xp=OptionalDouble.of(r.rotasXpLossPercentage().value()); sources.put(ResolvedZoneRules.XP_LOSS,zone.id()); }
            if (respawn.isEmpty() && r.respawnTarget()!=null) { respawn=Optional.of(r.respawnTarget()); sources.put(ResolvedZoneRules.RESPAWN,zone.id()); }
        }
        return new ResolvedZoneRules(stack.isEmpty()?"":stack.get(0).id(),pvp,spawn,taken,dealt,healing,keep,xp,respawn,sources);
    }

    public static boolean hostileSpawningEnabled(Collection<ZoneDef> zones, String dimension, double x, double y, double z) {
        ZoneDef decider = null;
        for (ZoneDef zone : zones) {
            if (!zone.combatRules().hostileSpawningEnabled().overridden() || !zone.appliesTo(dimension) || !zone.contains(x, y, z)) continue;
            if (decider == null || ZoneDef.compare(zone, decider) < 0) decider = zone;
        }
        return decider == null || decider.combatRules().hostileSpawningEnabled().value();
    }
}
