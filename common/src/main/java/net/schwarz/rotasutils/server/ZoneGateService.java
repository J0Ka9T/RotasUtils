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

/**
 * Keeps players out of a level zone until its entry requirements are met.
 *
 * <p>A zone is "gated" when it carries at least one blocking (not recommendation-only) entry
 * requirement. The check runs after each player tick on the server thread. A player inside a gated
 * zone they do not pass is dismounted, pushed back to the last position where they were allowed and
 * clear of every locked edge, and shoved a little further outward. When they logged in or the gate
 * was added while they were already inside, the push goes to the nearest outside spot they fit into.
 * A whole-dimension gate has no local exit, so it sends the player to the overworld spawn. The action
 * bar names the first requirement still missing, with a spark and a shield sound so the border reads
 * as a wall rather than lag, and warns ahead while the player is within {@link #APPROACH_BLOCKS}.</p>
 *
 * <p>Requirements are evaluated through {@link RequirementChecker}, the same gate every quest,
 * board and skill node uses. Results are reused for {@link #CHECK_CACHE_TICKS} ticks per player and
 * zone, and an edited zone (a new {@link ZoneDef} instance) is re-checked immediately.</p>
 *
 * <p>Administrators (permission level 2) are never pushed so a locked build stays reachable, unless
 * they switch on gate testing with {@code /rotas zone gate test} to experience the lock as a player.</p>
 */
public final class ZoneGateService {
    /** One warning every two seconds while a player keeps pushing at a locked border. */
    private static final int MESSAGE_INTERVAL_TICKS = 40;
    /** How long a requirement result is reused; a finished quest opens the zone within a second. */
    static final int CHECK_CACHE_TICKS = 20;
    /** How far the nearest-outside search walks, so a huge zone still resolves quickly. */
    private static final int PUSH_STEP_LIMIT = 64;
    /**
     * A remembered or pushed-to spot keeps this far from a locked edge, so a player still holding
     * forward after a push does not cross again on the very next tick.
     */
    static final double SAFE_MARGIN = 1.5;
    /** Within this many blocks of a locked edge the action bar warns before the player touches it. */
    static final double APPROACH_BLOCKS = 4.0;
    /** One heads-up every three seconds while a player walks along a locked border. */
    private static final int APPROACH_INTERVAL_TICKS = 60;
    /** Outward shove after a push so the border reads as a wall, not a rubber band. */
    private static final double KNOCKBACK = 0.45;
    private static final double KNOCKBACK_LIFT = 0.2;
    /** How far up or down a push target may shift to find floor with headroom. */
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

    /** One pass over the gated zones around a player: the zone they are in, and the nearest one ahead. */
    private record Scan(Blocker inside, Blocker ahead, double aheadDistance) {
    }

    /** Drops the per-position gate state, e.g. after a dimension change. */
    public static void forget(UUID player) {
        lastAllowed.remove(player);
        lastMessage.remove(player);
        lastApproach.remove(player);
        checks.forget(player);
    }

    /** Drops every gate state of a player who left, including an administrator's test switch. */
    public static void logout(UUID player) {
        forget(player);
        adminTesting.remove(player);
    }

    /** Switches whether an administrator is held to entry locks; returns the new state. */
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

    /** Event hook: runs after each player tick. */
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
            // Administrators may inspect or build inside a locked zone without being pushed out.
            lastAllowed.remove(id);
            return;
        }
        String dimension = serverPlayer.level().dimension().location().toString();
        long now = serverPlayer.level().getGameTime();
        Scan scan = scan(data, serverPlayer, dimension, now);
        if (scan.inside() == null) {
            if (scan.ahead() == null || scan.aheadDistance() >= SAFE_MARGIN) {
                // Only spots clear of every locked edge are worth returning to.
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

    /**
     * The highest-priority gated zone the player is inside and does not pass, plus the nearest gated
     * zone within {@link #APPROACH_BLOCKS} they would be refused from. Requirements are only checked
     * for zones that close, so far-away locks cost one distance test per tick.
     */
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
            String missing = zone.hasEntryLock() ? checks.blockedLabel(player.getUUID(), zone, now,
                    () -> RequirementChecker.firstBlockedLabel(player, data, zone.entryRequirements())) : null;
            if (missing == null && dungeon) {
                // A dungeon is also closed while full, cooling down, or to a player without its key or fee.
                missing = DungeonService.refusal(player, data, zone);
            }
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

    /**
     * The first thing still keeping {@code player} out of {@code zone} (a missing requirement, or a full
     * or cooling dungeon), or null when the zone is open to them. Uses the same one-second cache as the
     * border, so showing locks costs no extra requirement checks.
     */
    public static String lockReason(ServerPlayer player, RotasData data, ZoneDef zone, long now) {
        String missing = zone.hasEntryLock() ? checks.blockedLabel(player.getUUID(), zone, now,
                () -> RequirementChecker.firstBlockedLabel(player, data, zone.entryRequirements())) : null;
        if (missing == null && zone.features().dungeonRun()) {
            missing = DungeonService.refusal(player, data, zone);
        }
        return missing;
    }

    /**
     * Blocks from an outside position to the zone, capped at {@code limit}. An area's own distance
     * covers the usual case; standing in an excluded hole (distance 0 but not contained) falls back to
     * probing the sixteen horizontal rays for the nearest point back inside.
     */
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
        // Reused per player: this runs every tick for everyone outside a lock.
        double[] spot = lastAllowed.computeIfAbsent(player.getUUID(), id -> new double[3]);
        spot[0] = player.getX();
        spot[1] = player.getY();
        spot[2] = player.getZ();
    }

    private static void pushOut(ServerPlayer player, ZoneDef blocker) {
        if (player.isPassenger()) {
            // A mount or boat would otherwise carry the player straight back across the border.
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
            // A whole-overworld gate has nowhere to send the player, so warn without teleporting in
            // a loop. Whole-dimension gates elsewhere send the player home instead.
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

    /**
     * Shoves the player a little further along the way they were pushed. The teleport alone leaves a
     * player who holds forward walking straight back into the wall; the shove makes it feel like one.
     */
    private static void knockBack(ServerPlayer player, double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-3) {
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            player.setDeltaMovement(dx / length * KNOCKBACK, KNOCKBACK_LIFT, dz / length * KNOCKBACK);
        }
        // Player motion is client-authoritative; this sends the new velocity after the teleport.
        player.hurtMarked = true;
    }

    /**
     * The nearest outside spot the player fits into, preferring floor under their feet when they were
     * standing. Falls back to the nearest geometric exit when every ray ends in terrain.
     */
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

    /** The candidate shifted up or down by at most {@link #SAFE_Y_SEARCH} blocks to a free, outside spot. */
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

    /** The first entry of {@link #outsideCandidates}, or null for a whole-dimension zone. */
    static double[] nearestOutside(ZoneDef zone, double x, double y, double z) {
        List<double[]> candidates = outsideCandidates(zone, x, y, z);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Points outside the zone along sixteen horizontal rays, nearest first; empty for a
     * whole-dimension zone. Each point sits {@link #SAFE_MARGIN} past the edge when that is still
     * outside, so the knockback does not carry the player straight back in. Horizontal only: every
     * shape that can hold a player can be left on the ground.
     */
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
                // Walk on until the spot is SAFE_MARGIN from every edge, not just along this ray; a
                // slanted ray leaves the surface at a shallow angle. Keep the first exit if blocked.
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

    /** Action-bar heads-up while the player walks toward a zone that would refuse them. */
    private static void warnAhead(ServerPlayer player, Blocker blocker, long now) {
        Long bumped = lastMessage.get(player.getUUID());
        if (bumped != null && now - bumped >= 0 && now - bumped < MESSAGE_INTERVAL_TICKS) {
            // Just pushed out: the lock message is still on screen.
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

    /**
     * Per-player, per-zone memo of the first blocking requirement label (null when the player passes).
     * An entry is reused only for the same zone instance within {@link #CHECK_CACHE_TICKS}; zones are
     * immutable, so saving an edited zone replaces the instance and forces a fresh check.
     */
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
