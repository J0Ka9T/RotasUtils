package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ZoneLint {
    private ZoneLint() {
    }

    public enum Level { ERROR, WARNING, INFO }

    public record Finding(Level level, String message) {
    }

    public static List<Finding> check(ZoneDef zone, Collection<ZoneDef> others) {
        List<Finding> found = new ArrayList<>();
        boolean whole = zone.areas().isEmpty();
        boolean lock = zone.hasEntryLock();
        ZoneFeatures features = zone.features();

        if (whole && lock) {
            found.add(new Finding(Level.ERROR, "Entry lock on a whole-dimension zone: players who fail it have no edge "
                    + "to be pushed back to and are sent to the overworld spawn instead. Add an area."));
        }
        if (whole && features.dungeonRun()) {
            found.add(new Finding(Level.ERROR, "A dungeon needs an area; as a whole-dimension zone it has no entrance."));
        }
        if (whole && !lock && zone.enabled()) {
            found.add(new Finding(Level.WARNING, "No area: this zone covers the whole dimension."));
        }
        if (whole && !zone.excludedAreas().isEmpty()) {
            found.add(new Finding(Level.INFO, "Excluded areas cut holes in the whole dimension."));
        }

        for (ZoneSpawnPoint point : features.spawnPoints()) {
            if (!zone.contains(point.x() + 0.5, point.y(), point.z() + 0.5)) {
                found.add(new Finding(Level.WARNING, "Spawn point '" + point.id() + "' at " + point.x() + " " + point.y()
                        + " " + point.z() + " is outside this zone."));
            }
            if (zone.safe() && point.kind() == ZoneSpawnPoint.Kind.BOSS) {
                found.add(new Finding(Level.WARNING, "Zone is marked safe but boss point '" + point.id() + "' lives in it."));
            }
        }
        if (features.type() == ZoneType.BOSS_ARENA && features.spawnPoints().isEmpty()) {
            found.add(new Finding(Level.WARNING, "A boss arena with no spawn point has no boss."));
        }
        if (features.type() == ZoneType.TOWN && !zone.safe()) {
            found.add(new Finding(Level.INFO, "A town that is not marked safe still lets mobs hurt players."));
        }

        RuleBool pvp = zone.combatRules().pvpEnabled();
        if (zone.safe() && pvp.overridden() && pvp.value()) {
            found.add(new Finding(Level.WARNING, "Zone is marked safe but PvP is switched on."));
        }
        if (zone.recommendedMax() < zone.levelMin() || zone.recommendedMin() > zone.levelMax()) {
            found.add(new Finding(Level.WARNING, "Recommended levels " + zone.recommendedMin() + "-" + zone.recommendedMax()
                    + " do not meet the mob band " + zone.levelMin() + "-" + zone.levelMax() + "."));
        }
        if (zone.xpMultiplier() == 0.0) {
            found.add(new Finding(Level.INFO, "XP multiplier is 0: nothing killed here gives XP."));
        }
        if (!zone.enabled()) {
            found.add(new Finding(Level.INFO, "Zone is disabled; it has no effect until enabled."));
        }

        if (!whole && zone.enabled()) {
            for (ZoneDef other : others) {
                if (other.id().equals(zone.id()) || !other.enabled() || other.areas().isEmpty()
                        || other.priority() != zone.priority() || !sameDimension(zone, other)
                        || !envelopesTouch(zone, other)) {
                    continue;
                }
                found.add(new Finding(Level.WARNING, "Same priority (" + zone.priority() + ") as '" + other.id()
                        + "' and their areas overlap; the winner is chosen by id. Give one a higher priority."));
            }
        }
        found.sort((a, b) -> a.level().compareTo(b.level()));
        return List.copyOf(found);
    }

    public static boolean hasErrors(List<Finding> findings) {
        return findings.stream().anyMatch(finding -> finding.level() == Level.ERROR);
    }

    private static boolean sameDimension(ZoneDef a, ZoneDef b) {
        return a.dimension().isEmpty() || b.dimension().isEmpty() || a.dimension().equals(b.dimension());
    }

    private static boolean envelopesTouch(ZoneDef a, ZoneDef b) {
        for (ZoneArea first : a.areas()) {
            ZoneArea.Bounds p = first.bounds();
            for (ZoneArea second : b.areas()) {
                ZoneArea.Bounds q = second.bounds();
                if (p.minX() <= q.maxX() && p.maxX() >= q.minX() && p.minY() <= q.maxY() && p.maxY() >= q.minY()
                        && p.minZ() <= q.maxZ() && p.maxZ() >= q.minZ()) {
                    return true;
                }
            }
        }
        return false;
    }
}
