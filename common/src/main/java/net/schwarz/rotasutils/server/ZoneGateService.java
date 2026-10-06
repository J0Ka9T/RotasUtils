package net.schwarz.rotasutils.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ZoneGateService {
    private static final int MESSAGE_INTERVAL_TICKS = 40;
    static final int CHECK_CACHE_TICKS = 20;
    private static final int PUSH_STEP_LIMIT = 64;
    static final double SAFE_MARGIN = 1.5;
    static final double APPROACH_BLOCKS = 4.0;
    private static final int APPROACH_INTERVAL_TICKS = 60;
    private static final double KNOCKBACK = 0.45;
    private static final double KNOCKBACK_LIFT = 0.2;
    private static final int SAFE_Y_SEARCH = 3;
    private static final String OVERWORLD = "minecraft:overworld";

    private static final double[] DIRECTIONS = directions();
    private static final Map<UUID, double[]> lastAllowed = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastApproach = new ConcurrentHashMap<>();
    private static final Set<UUID> adminTesting = ConcurrentHashMap.newKeySet();
    private static final GateCache checks = new GateCache();

    private ZoneGateService() {
    }

    private record Blocker(ZoneDef zone, String missing) {
    }

    private record Scan(Blocker inside, Blocker ahead, double aheadDistance) {
    }

    public static void forget(UUID player) {
        lastAllowed.remove(player);
        lastMessage.remove(player);
        lastApproach.remove(player);
        checks.forget(player);
    }

    public static void logout(UUID player) {
        forget(player);
        adminTesting.remove(player);
    }

    public static boolean toggleAdminTest(UUID player) {
        if (adminTesting.remove(player)) {
            return false;
        }
        adminTesting.add(player);
        return true;
    }

    public static boolean adminTesting(UUID player) {
        return adminTesting.contains(player);
    }

    public static void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return;
        }
        if (serverPlayer.connection == null || !serverPlayer.isAlive() || serverPlayer.isSpectator()) {
            return;
        }
        RotasData data = RotasData.instance();
        if (data == null || data.zones().isEmpty()) {
            return;
        }
        UUID id = serverPlayer.getUUID();
        if (serverPlayer.hasPermissions(2) && !adminTesting.contains(id)) {
            lastAllowed.remove(id);
            return;
        }
        String dimension = serverPlayer.level().dimension().location().toString();
        long now = serverPlayer.level().getGameTime();
        Scan scan = scan(data, serverPlayer, dimension, now);
        if (scan.inside() == null) {
            if (scan.ahead() == null || scan.aheadDistance() >= SAFE_MARGIN) {
                remember(serverPlayer);
            }
            if (scan.ahead() != null) {
                warnAhead(serverPlayer, scan.ahead(), now);
            }
            return;
        }
        pushOut(serverPlayer, scan.inside().zone());
        warn(serverPlayer, scan.inside(), now);
    }

    private static Scan scan(RotasData data, ServerPlayer player, String dimension, long now) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        ZoneDef inside = null;
        String insideMissing = null;
        ZoneDef ahead = null;
        String aheadMissing = null;
        double aheadDistance = Double.MAX_VALUE;
        for (ZoneDef zone : data.zones().values()) {
            boolean dungeon = zone.features().dungeonRun();
            if ((!zone.hasEntryLock() && !dungeon) || !zone.appliesTo(dimension)) {
                continue;
            }
            boolean within = zone.contains(x, y, z);
            if (within && inside != null && ZoneDef.compare(zone, inside) >= 0) {
                continue;
            }
            double gap = within ? 0.0 : edgeDistance(zone, x, y, z, APPROACH_BLOCKS);
            if (!within && gap >= Math.min(APPROACH_BLOCKS, aheadDistance)) {
                continue;
            }
            String missing = lockReason(player, data, zone, now);
            if (missing == null) {
                continue;
            }
            if (within) {
                inside = zone;
                insideMissing = missing;
            } else {
                ahead = zone;
                aheadMissing = missing;
                aheadDistance = gap;
            }
        }
        return new Scan(inside == null ? null : new Blocker(inside, insideMissing),
                ahead == null ? null : new Blocker(ahead, aheadMissing), aheadDistance);
    }

    public static String lockReason(ServerPlayer player, RotasData data, ZoneDef zone, long now) {
        if (!zone.hasEntryLock() && !zone.features().dungeonRun()) {
            return null;
        }
        return checks.blockedLabel(player.getUUID(), zone, now, () -> {
            String missing = zone.hasEntryLock()
                    ? RequirementChecker.firstBlockedLabel(player, data, zone.entryRequirements()) : null;
            return missing == null && zone.features().dungeonRun() ? DungeonService.refusal(player, data, zone) : missing;
        });
    }

    static double edgeDistance(ZoneDef zone, double x, double y, double z, double limit) {
        double distance = zone.distance(x, y, z);
        if (distance > 0.0) {
            return Math.min(distance, limit);
        }
        double best = limit;
        for (int i = 0; i < DIRECTIONS.length; i += 2) {
            for (double step = 0.25; step < best; step += 0.25) {
                if (zone.contains(x + DIRECTIONS[i] * step, y, z + DIRECTIONS[i + 1] * step)) {
                    best = step;
                    break;
                }
            }
        }
        return best;
    }

    private static void remember(ServerPlayer player) {
        double[] spot = lastAllowed.computeIfAbsent(player.getUUID(), id -> new double[3]);
        spot[0] = player.getX();
        spot[1] = player.getY();
        spot[2] = player.getZ();
    }

    private static void pushOut(ServerPlayer player, ZoneDef blocker) {
        if (player.isPassenger()) {
            player.stopRiding();
        }
        double[] target = lastAllowed.get(player.getUUID());
        if (target != null && blocker.contains(target[0], target[1], target[2])) {
            target = null;
        }
        if (target == null) {
            target = safeOutside(player, blocker);
        }
        if (target == null) {
            double[] spawn = overworldSpawn(player);
            if (spawn == null) {
                return;
            }
            if (blocker.appliesTo(OVERWORLD) && blocker.contains(spawn[0], spawn[1], spawn[2])) {
                return;
            }
            player.teleportTo(player.server.overworld(), spawn[0], spawn[1], spawn[2],
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            lastAllowed.put(player.getUUID(), spawn);
            return;
        }
        double fromX = player.getX();
        double fromZ = player.getZ();
        ServerLevel level = player.serverLevel();
        player.teleportTo(level, target[0], target[1], target[2], player.getYRot(), player.getXRot());
        player.fallDistance = 0.0f;
        knockBack(player, target[0] - fromX, target[2] - fromZ);
        lastAllowed.put(player.getUUID(), target);
    }

    private static void knockBack(ServerPlayer player, double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-3) {
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            player.setDeltaMovement(dx / length * KNOCKBACK, KNOCKBACK_LIFT, dz / length * KNOCKBACK);
        }
        player.hurtMarked = true;
    }

    private static double[] safeOutside(ServerPlayer player, ZoneDef zone) {
        List<double[]> candidates = outsideCandidates(zone, player.getX(), player.getY(), player.getZ());
        if (candidates.isEmpty()) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        boolean needsFloor = player.onGround();
        for (double[] candidate : candidates) {
            double[] spot = fit(level, player, zone, candidate, needsFloor);
            if (spot != null) {
                return spot;
            }
        }
        return candidates.get(0);
    }

    private static double[] fit(ServerLevel level, ServerPlayer player, ZoneDef zone, double[] at, boolean needsFloor) {
        for (int i = 0; i <= SAFE_Y_SEARCH * 2; i++) {
            int dy = (i + 1) / 2 * (i % 2 == 0 ? -1 : 1);
            double y = Math.floor(at[1]) + dy;
            if (zone.contains(at[0], y, at[2])) {
                continue;
            }
            AABB box = player.getDimensions(player.getPose()).makeBoundingBox(at[0], y, at[2]);
            if (!level.noCollision(player, box)) {
                continue;
            }
            if (needsFloor && level.noCollision(player, box.move(0.0, -0.6, 0.0)) && !level.containsAnyLiquid(box)) {
                continue;
            }
            return new double[]{at[0], y, at[2]};
        }
        return null;
    }

    static double[] nearestOutside(ZoneDef zone, double x, double y, double z) {
        List<double[]> candidates = outsideCandidates(zone, x, y, z);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    static List<double[]> outsideCandidates(ZoneDef zone, double x, double y, double z) {
        if (zone.areas().isEmpty()) {
            return List.of();
        }
        List<double[]> found = new ArrayList<>();
        for (int i = 0; i < DIRECTIONS.length; i += 2) {
            double dx = DIRECTIONS[i];
            double dz = DIRECTIONS[i + 1];
            for (int step = 1; step <= PUSH_STEP_LIMIT; step++) {
                if (zone.contains(x + dx * step, y, z + dz * step)) {
                    continue;
                }
                double reach = step;
                for (double more = step; more <= step + SAFE_MARGIN * 3; more += 0.25) {
                    double px = x + dx * more;
                    double pz = z + dz * more;
                    if (zone.contains(px, y, pz)) {
                        break;
                    }
                    if (edgeDistance(zone, px, y, pz, SAFE_MARGIN) >= SAFE_MARGIN) {
                        reach = more;
                        break;
                    }
                }
                found.add(new double[]{x + dx * reach, y, z + dz * reach, reach});
                break;
            }
        }
        found.sort(Comparator.comparingDouble(point -> point[3]));
        List<double[]> result = new ArrayList<>(found.size());
        for (double[] point : found) {
            result.add(new double[]{point[0], point[1], point[2]});
        }
        return result;
    }

    private static double[] overworldSpawn(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return null;
        }
        var spawn = overworld.getSharedSpawnPos();
        return new double[]{spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5};
    }

    private static void warn(ServerPlayer player, Blocker blocker, long now) {
        Long last = lastMessage.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < MESSAGE_INTERVAL_TICKS) {
            return;
        }
        lastMessage.put(player.getUUID(), now);
        ZoneDef zone = blocker.zone();
        String name = zone.name().isEmpty() ? zone.id() : zone.name();
        player.displayClientMessage(ThaiText.c("rotasutils.msg.zone.locked", name, blocker.missing()), true);
        player.serverLevel().sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true,
                player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.35, 0.6, 0.35, 0.02);
        player.playNotifySound(SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.3f);
    }

    private static void warnAhead(ServerPlayer player, Blocker blocker, long now) {
        Long bumped = lastMessage.get(player.getUUID());
        if (bumped != null && now - bumped >= 0 && now - bumped < MESSAGE_INTERVAL_TICKS) {
            return;
        }
        Long last = lastApproach.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < APPROACH_INTERVAL_TICKS) {
            return;
        }
        lastApproach.put(player.getUUID(), now);
        ZoneDef zone = blocker.zone();
        String name = zone.name().isEmpty() ? zone.id() : zone.name();
        player.displayClientMessage(ThaiText.c("rotasutils.msg.zone.locked_ahead", name, blocker.missing()), true);
    }

    private static double[] directions() {
        double[] dirs = new double[32];
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16.0;
            dirs[i * 2] = Math.cos(angle);
            dirs[i * 2 + 1] = Math.sin(angle);
        }
        return dirs;
    }

    static final class GateCache {
        private record Entry(ZoneDef zone, String missing, long checkedAt) {
        }

        private final Map<UUID, Map<String, Entry>> entries = new ConcurrentHashMap<>();

        String blockedLabel(UUID player, ZoneDef zone, long now, Supplier<String> evaluate) {
            Map<String, Entry> perPlayer = entries.computeIfAbsent(player, id -> new HashMap<>());
            Entry entry = perPlayer.get(zone.id());
            if (entry != null && entry.zone() == zone
                    && now >= entry.checkedAt() && now - entry.checkedAt() < CHECK_CACHE_TICKS) {
                return entry.missing();
            }
            String missing = evaluate.get();
            perPlayer.put(zone.id(), new Entry(zone, missing, now));
            return missing;
        }

        void forget(UUID player) {
            entries.remove(player);
        }
    }
}
