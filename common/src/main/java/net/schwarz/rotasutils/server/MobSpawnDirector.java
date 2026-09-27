package net.schwarz.rotasutils.server;

import dev.architectury.event.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.MobSpawnRules;
import net.schwarz.rotasutils.core.MonsterCatalog;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneMobPolicy;
import net.schwarz.rotasutils.core.ZoneRuleResolver;
import net.schwarz.rotasutils.data.RotasData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Applies Mob Setup spawn rules to the world.
 *
 * <p>{@link #checkSpawn} is the gate: registered on Architectury's spawn check, it cancels natural
 * and world-generation spawns that a setup's rules forbid (wrong zone, dimension, time or height,
 * too many nearby, or natural spawning turned off). Spawners, eggs, commands and this director's own
 * spawns use other spawn reasons and always pass. {@link #tick} runs once a second from
 * {@link MonsterService} and adds the optional extra spawns near players, inside a server-wide
 * attempt budget and a crowd cap, so a busy server never pays for more than a few checks.</p>
 */
public final class MobSpawnDirector {
    private static final int SPAWN_DISTANCE_MIN = 24;
    private static final int SPAWN_DISTANCE_MAX = 44;
    private static final int CROWD_RADIUS = 48;
    /** Extra spawn attempts per second across the whole server. */
    private static final int ATTEMPTS_PER_SECOND = 16;
    private static final Comparator<MonsterDefinitions.Profile> ORDER =
            Comparator.comparingInt(MonsterDefinitions.Profile::priority).reversed()
                    .thenComparing(MonsterDefinitions.Profile::id);

    private static MonsterCatalog cachedCatalog;
    /** Every setup naming an entity, best first, for entities where at least one setup restricts spawning. */
    private static Map<String, List<MonsterDefinitions.Profile>> cachedNatural = Map.of();
    private static List<MonsterDefinitions.Profile> cachedExtra = List.of();
    private static Map<String, Set<String>> cachedZoneEntities = Map.of();

    /**
     * Drops the cached catalogue when the server stops. These statics hold a whole MonsterCatalog and
     * its profile graph; on an integrated server the JVM outlives the world, so without this the
     * previous world's content stays reachable after the player returns to the title screen.
     */
    public static synchronized void clearCaches() {
        cachedCatalog = null;
        cachedNatural = Map.of();
        cachedExtra = List.of();
        cachedZoneEntities = Map.of();
    }

    private final MinecraftServer server;
    private final RpgKernel kernel;
    private final Random random = new Random();
    private boolean loggedFailure;

    MobSpawnDirector(MinecraftServer server, RpgKernel kernel) {
        this.server = server;
        this.kernel = kernel;
    }

    /** Spawn gate for normal world spawning; see the class javadoc. Never throws. */
    public static EventResult checkSpawn(LivingEntity entity, LevelAccessor accessor, double x, double y, double z,
                                         MobSpawnType type) {
        if (!(entity instanceof Mob) || (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION)
                || !(accessor instanceof ServerLevelAccessor serverAccessor)) {
            return EventResult.pass();
        }
        RotasData data = RotasData.instance();
        if (data == null || data.kernel() == null) {
            return EventResult.pass();
        }
        try {
            ServerLevel level = serverAccessor.getLevel();
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            MonsterCatalog catalog = data.kernel().content().monsters();
            if (!data.zones().isEmpty() && entity.getType().getCategory() == MobCategory.MONSTER) {
                // Zone first: a zone with hostile spawning off, or one that keeps outside mobs out,
                // decides before any single setup's rules.
                String dimension = level.dimension().location().toString();
                ZoneDef top = ZoneService.select(data.zones().values(), dimension, x, y, z);
                boolean hostileOff = !ZoneRuleResolver.hostileSpawningEnabled(data.zones().values(), dimension, x, y, z);
                Set<String> own = top == null ? Set.of() : zoneEntities(catalog).getOrDefault(top.id(), Set.of());
                if (!ZoneMobPolicy.naturalSpawnAllowed(id, true, top, hostileOff, own)) {
                    return EventResult.interruptFalse();
                }
            }
            List<MonsterDefinitions.Profile> profiles = naturalRules(catalog).get(id);
            if (profiles == null) {
                return EventResult.pass();
            }
            String scope = scopeZone(data, level, x, y, z);
            for (MonsterDefinitions.Profile profile : profiles) {
                // The best setup in scope decides: a zone version inside its zones, else the global one.
                if (!inScope(profile, scope)) {
                    continue;
                }
                MobSpawnRules rules = profile.spawning();
                if (!rules.naturalAllowed(place(data, level, x, y, z))) {
                    return EventResult.interruptFalse();
                }
                // World generation can run off the server thread; the crowd count only runs where it is safe.
                if (rules.maxNearby() > 0 && type == MobSpawnType.NATURAL && level.getServer().isSameThread()
                        && nearby(level, profile.selector().entities(), x, y, z) >= rules.maxNearby()) {
                    return EventResult.interruptFalse();
                }
                return EventResult.pass();
            }
        } catch (RuntimeException ignored) {
            // A rule lookup must never break world spawning; fall back to vanilla behaviour.
        }
        return EventResult.pass();
    }

    /** Adds extra spawns near players for setups that ask for them. Called once a second. */
    void tick() {
        List<MonsterDefinitions.Profile> extras = extraRules(kernel.content().monsters());
        if (extras.isEmpty()) {
            return;
        }
        RotasData data = RotasData.get(server);
        int budget = ATTEMPTS_PER_SECOND;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            for (MonsterDefinitions.Profile profile : extras) {
                if (budget <= 0) {
                    return;
                }
                // perMinute tries per minute = perMinute/60 chance each second.
                if (random.nextInt(60) >= profile.spawning().extra().perMinute()) {
                    continue;
                }
                budget--;
                try {
                    attempt(data, player, profile);
                } catch (RuntimeException failure) {
                    if (!loggedFailure) {
                        loggedFailure = true;
                        Rotasutils.LOG.warn("Extra mob spawn failed for {}: {}", profile.id(), failure.getMessage());
                    }
                }
            }
        }
    }

    private void attempt(RotasData data, ServerPlayer player, MonsterDefinitions.Profile profile) {
        ServerLevel level = player.serverLevel();
        MobSpawnRules rules = profile.spawning();
        List<String> ids = new ArrayList<>(profile.selector().entities());
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.tryParse(ids.get(random.nextInt(ids.size())))).orElse(null);
        if (type == null || !type.canSummon()) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = SPAWN_DISTANCE_MIN + random.nextDouble() * (SPAWN_DISTANCE_MAX - SPAWN_DISTANCE_MIN);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        SpawnPlacements.Type placement = SpawnPlacements.getPlacementType(type);
        BlockPos pos = findSpot(level, placement, type, x, (int) Math.floor(player.getY()), z);
        if (pos == null || level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPAWN_DISTANCE_MIN, false) != null) {
            return;
        }
        double px = pos.getX() + 0.5;
        double pz = pos.getZ() + 0.5;
        String dimension = level.dimension().location().toString();
        ZoneDef top = ZoneService.select(data.zones().values(), dimension, px, pos.getY(), pz);
        if (!inScope(profile, top == null ? "" : top.id())
                || !ZoneMobPolicy.profileAllowed(profile.selector().regions(), top)
                || (type.getCategory() == MobCategory.MONSTER
                    && !ZoneRuleResolver.hostileSpawningEnabled(data.zones().values(), dimension, px, pos.getY(), pz))
                || !rules.extraAllowed(place(data, level, px, pos.getY(), pz))) {
            return;
        }
        int room = rules.extraCap() - nearby(level, profile.selector().entities(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (room <= 0) {
            return;
        }
        int group = Math.min(room, 1 + random.nextInt(rules.extra().groupMax()));
        for (int i = 0; i < group; i++) {
            BlockPos at = i == 0 ? pos : findSpot(level, placement, type,
                    pos.getX() + random.nextInt(5) - 2, pos.getY(), pos.getZ() + random.nextInt(5) - 2);
            if (at == null) {
                continue;
            }
            if (rules.extra().vanillaRules()
                    && !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, at, level.getRandom())) {
                continue;
            }
            if (!level.noCollision(type.getAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) {
                continue;
            }
            // EVENT, not NATURAL: the gate never cancels the director's own spawns.
            type.spawn(level, at, MobSpawnType.EVENT);
        }
    }

    /** A block near {@code startY} in a loaded chunk where this mob type can stand (or swim). */
    private static BlockPos findSpot(ServerLevel level, SpawnPlacements.Type placement, EntityType<?> type,
                                     int x, int startY, int z) {
        BlockPos column = new BlockPos(x, startY, z);
        if (!level.hasChunkAt(column)) {
            return null;
        }
        int top = Math.min(level.getMaxBuildHeight() - 2, startY + 8);
        int bottom = Math.max(level.getMinBuildHeight() + 1, startY - 24);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= bottom; y--) {
            cursor.setY(y);
            if (NaturalSpawner.isSpawnPositionOk(placement, level, cursor, type)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    static MobSpawnRules.Place place(RotasData data, ServerLevel level, double x, double y, double z) {
        String dimension = level.dimension().location().toString();
        Set<String> zones = new HashSet<>();
        for (ZoneDef zone : data.zones().values()) {
            if (zone.appliesTo(dimension) && zone.contains(x, y, z)) {
                zones.add(zone.id());
            }
        }
        return new MobSpawnRules.Place(dimension, zones, level.isDay(), (int) Math.floor(y));
    }

    /** The zone a mob at this position belongs to, exactly as monster assignment resolves it. */
    private static String scopeZone(RotasData data, ServerLevel level, double x, double y, double z) {
        ZoneDef zone = ZoneService.select(data.zones().values(), level.dimension().location().toString(), x, y, z);
        return zone == null ? "" : zone.id();
    }

    /** Global setups apply everywhere; zone versions only inside their zones. */
    private static boolean inScope(MonsterDefinitions.Profile profile, String zoneId) {
        Set<String> regions = profile.selector().regions();
        return regions.isEmpty() || regions.contains(zoneId);
    }

    private static int nearby(ServerLevel level, Set<String> entities, double x, double y, double z) {
        AABB box = new AABB(x - CROWD_RADIUS, y - CROWD_RADIUS, z - CROWD_RADIUS,
                x + CROWD_RADIUS, y + CROWD_RADIUS, z + CROWD_RADIUS);
        return level.getEntitiesOfClass(Mob.class, box,
                mob -> entities.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString())).size();
    }

    /** Entity ids named by the Mob Setups scoped to each zone: the mobs an isolated zone lets spawn. */
    private static synchronized Map<String, Set<String>> zoneEntities(MonsterCatalog catalog) {
        refresh(catalog);
        return cachedZoneEntities;
    }

    private static synchronized Map<String, List<MonsterDefinitions.Profile>> naturalRules(MonsterCatalog catalog) {
        refresh(catalog);
        return cachedNatural;
    }

    private static synchronized List<MonsterDefinitions.Profile> extraRules(MonsterCatalog catalog) {
        refresh(catalog);
        return cachedExtra;
    }

    /** Rebuilds the per-entity rule lookup whenever content is reloaded (a new catalog instance). */
    private static void refresh(MonsterCatalog catalog) {
        if (catalog == cachedCatalog) {
            return;
        }
        Map<String, List<MonsterDefinitions.Profile>> byEntity = new HashMap<>();
        Set<String> restricted = new HashSet<>();
        List<MonsterDefinitions.Profile> extra = new ArrayList<>();
        Map<String, Set<String>> perZone = new HashMap<>();
        catalog.profiles().values().forEach(profile -> profile.selector().regions().forEach(zone ->
                perZone.computeIfAbsent(zone, ignored -> new HashSet<>()).addAll(profile.selector().entities())));
        Map<String, Set<String>> zoneEntities = new HashMap<>();
        perZone.forEach((zone, entities) -> zoneEntities.put(zone, Set.copyOf(entities)));
        cachedZoneEntities = Map.copyOf(zoneEntities);
        catalog.profiles().values().stream().sorted(ORDER).forEach(profile -> {
            // Rules only bind mobs a setup names explicitly, so a broad profile can never stop all spawning.
            if (profile.selector().entities().isEmpty()) {
                return;
            }
            MobSpawnRules rules = profile.spawning();
            for (String id : profile.selector().entities()) {
                byEntity.computeIfAbsent(id, ignored -> new ArrayList<>()).add(profile);
                if (rules.restrictsNatural()) {
                    restricted.add(id);
                }
            }
            if (rules.extra().enabled()) {
                extra.add(profile);
            }
        });
        // Keep every setup for a restricted mob, so a zone version without limits still overrides a
        // restrictive global setup inside its zone.
        Map<String, List<MonsterDefinitions.Profile>> natural = new HashMap<>();
        restricted.forEach(id -> natural.put(id, List.copyOf(byEntity.get(id))));
        cachedNatural = Map.copyOf(natural);
        cachedExtra = List.copyOf(extra);
        cachedCatalog = catalog;
    }
}
