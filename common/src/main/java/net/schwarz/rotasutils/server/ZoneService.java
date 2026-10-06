package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.MobLevelConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ZoneService {
    private ZoneService() {
    }

    public record Region(String id, String name, int level, int min, int max, double distance,
                         ZoneDef.Danger danger, int recommendedMin, int recommendedMax,
                         double xpMultiplier, boolean safe, String nearZone, double blend) {
        public boolean wilderness() {
            return id.isEmpty();
        }

        public String bandLabel() {
            return min == max ? "Lv " + min : "Lv " + min + "-" + max;
        }
    }

    public static MonsterService.Environment environment(Mob mob) {
        MinecraftServer server = mob.getServer();
        if (server == null || mob.level().isClientSide() || !(mob.level() instanceof ServerLevel level)) {
            return neutral();
        }
        try {
            Region region = region(RotasData.get(server), level, mob.getX(), mob.getY(), mob.getZ());
            Map<String, Double> numbers = new HashMap<>();
            numbers.put("world.tier", 1.0);
            numbers.put("dimension.level", 1.0);
            numbers.put("region.level", (double) region.level());
            numbers.put("region.min", (double) region.min());
            numbers.put("region.max", (double) region.max());
            numbers.put("region.distance", region.distance());
            numbers.put("region.safe", region.safe() ? 1.0 : 0.0);
            numbers.put("region.blend", region.blend());
            numbers.put("region.xp_multiplier", region.xpMultiplier());
            return new MonsterService.Environment(region.id(), numbers);
        } catch (RuntimeException failure) {
            return neutral(RotasData.get(server).levelConfig().mobLevel().spawnLevel());
        }
    }

    private static MonsterService.Environment neutral() {
        return neutral(1);
    }

    private static MonsterService.Environment neutral(int level) {
        double band = Math.max(1, level);
        return new MonsterService.Environment("", Map.of(
                "world.tier", 1.0, "dimension.level", 1.0,
                "region.level", band, "region.min", band, "region.max", band, "region.distance", 0.0));
    }

    public static Region region(RotasData data, ServerLevel level, double x, double y, double z) {
        return resolve(data.zones().values(), level.dimension().location().toString(), x, y, z,
                spawnDistance(level, x, z), data.levelConfig().mobLevel());
    }

    public static Region resolve(Collection<ZoneDef> zones, String dimension, double x, double y, double z,
                                 double spawnDistance, MobLevelConfig config) {
        ZoneDef zone = select(zones, dimension, x, y, z);
        if (zone != null) {
            int mid = (int) Math.round((zone.levelMin() + (double) zone.levelMax()) / 2.0);
            return new Region(zone.id(), zone.name(), mid, zone.levelMin(), zone.levelMax(), spawnDistance,
                    zone.danger(), zone.recommendedMin(), zone.recommendedMax(), zone.xpMultiplier(),
                    zone.safe(), "", 1.0);
        }
        int floor = wildernessFloor(config, spawnDistance);
        int ceiling = (int) Math.max(floor, Math.min(config.maxLevel(), (long) floor + config.bandSpread()));
        ZoneDef near = nearestRing(zones, dimension, x, y, z);
        if (near != null) {
            double gap = near.distance(x, y, z);
            double weight = 1.0 - gap / (near.transitionBlocks() + 1.0);
            int min = lerp(floor, near.levelMin(), weight);
            int max = Math.max(min, lerp(ceiling, near.levelMax(), weight));
            return new Region("", "", min, min, max, spawnDistance, ZoneDef.Danger.NORMAL,
                    min, max, 1.0, false, near.id(), weight);
        }
        return new Region("", "", floor, floor, ceiling, spawnDistance, ZoneDef.Danger.NORMAL,
                floor, ceiling, 1.0, false, "", 0.0);
    }

    public static int wildernessFloor(MobLevelConfig config, double spawnDistance) {
        long floor = config.spawnLevel();
        if (config.levelPerBlocks() > 0 && Double.isFinite(spawnDistance) && spawnDistance > 0) {
            floor += (long) Math.min(MobLevelConfig.MAX_LEVEL, spawnDistance / config.levelPerBlocks());
        }
        return (int) Math.max(1, Math.min(config.maxLevel(), floor));
    }

    public static int levelFor(UUID mob, Region region) {
        return MonsterThreat.levelInBand(mob, region.min(), region.max());
    }

    public static ZoneDef select(Collection<ZoneDef> zones, String dimension, double x, double y, double z) {
        return ZoneDef.select(zones, dimension, x, y, z);
    }

    static ZoneDef nearestRing(Collection<ZoneDef> zones, String dimension, double x, double y, double z) {
        ZoneDef best = null;
        double bestGap = Double.MAX_VALUE;
        for (ZoneDef zone : zones) {
            if (!zone.appliesTo(dimension) || zone.areas().isEmpty() || zone.transitionBlocks() <= 0) {
                continue;
            }
            double gap = zone.distance(x, y, z);
            if (gap <= 0 || gap > zone.transitionBlocks()) {
                continue;
            }
            boolean better = best == null || zone.priority() > best.priority()
                    || (zone.priority() == best.priority() && (gap < bestGap
                    || (gap == bestGap && zone.id().compareTo(best.id()) < 0)));
            if (better) {
                best = zone;
                bestGap = gap;
            }
        }
        return best;
    }

    private static int lerp(int from, int to, double weight) {
        double clamped = Math.max(0.0, Math.min(1.0, weight));
        return (int) Math.round(from + (to - from) * clamped);
    }

    public static double spawnDistance(ServerLevel level, double x, double z) {
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = x - spawn.getX();
        double dz = z - spawn.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
