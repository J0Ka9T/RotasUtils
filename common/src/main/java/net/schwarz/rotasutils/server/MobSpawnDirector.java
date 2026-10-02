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
    /** Setups that name a kind of mob (by id, tag or mod), best first, for the spawn gate. */
    private static List<MonsterDefinitions.Profile> cachedGate = List.of();
    /** True when at least one gated setup limits normal spawning; otherwise the gate has nothing to do. */
    private static boolean cachedRestricts;
    private static List<MonsterDefinitions.Profile> cachedExtra = List.of();
    private static Map<String, Set<String>> cachedZoneEntities = Map.of();

    /**
     * Drops the cached catalogue when the server stops. These statics hold a whole MonsterCatalog and
     * its profile graph; on an integrated server the JVM outlives the world, so without this the
     * previous world's content stays reachable after the player returns to the title screen.
     */
    public static synchronized void clearCaches() {
        cachedCatalog = null;
        cachedGate = List.of();
        cachedRestricts = false;
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

    /**
     * Cancels a spawn. Architectury 9.2.14 reads the result differently per loader: Fabric treats false as
     * "spawn denied", but Forge hands the value to setSpawnCancelled, so only true cancels there.
     */
    private static EventResult deny() {
        return dev.architectury.platform.Platform.isForge() ? EventResult.interruptTrue() : EventResult.interruptFalse();
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
                if (top != null && !own.contains(id) && zoneNamesKind(catalog, top.id(), entity.getType())) {
                    own = Set.of(id);
                }
                if (!ZoneMobPolicy.naturalSpawnAllowed(id, true, top, hostileOff, own)) {
                    return deny();
                }
            }
            List<MonsterDefinitions.Profile> profiles = gateRules(catalog);
            if (profiles.isEmpty()) {
                return EventResult.pass();
            }
            String dimension = level.dimension().location().toString();
            ZoneDef top = ZoneService.select(data.zones().values(), dimension, x, y, z);
            String scope = top == null ? "" : top.id();
            String biome = accessor.getBiome(BlockPos.containing(x, y, z)).unwrapKey()
                    .map(key -> key.location().toString()).orElse("");
            Set<String> tags = tagsOf(entity.getType());
            for (MonsterDefinitions.Profile profile : profiles) {
                // The best setup that matches this mob decides: a zone version inside its zones, else the global one.
                if (!ZoneMobPolicy.profileAllowed(profile.selector().regions(), top)
                        || !profile.selector().matches(id, tags, biome, dimension, type.name(), scope)) {
                    continue;
                }
                MobSpawnRules rules = profile.spawning();
                if (!rules.naturalAllowed(place(data, level, x, y, z))) {
                    return deny();
                }
                // World generation can run off the server thread; the crowd count only runs where it is safe.
                if (rules.maxNearby() > 0 && type == MobSpawnType.NATURAL && level.getServer().isSameThread()
                        && nearby(level, profile.selector(), x, y, z) >= rules.maxNearby()) {
                    return deny();
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
        List<String> ids = profile.selector().entities().stream()
                .filter(id -> !profile.selector().excluded().contains(id)).toList();
        if (ids.isEmpty()) {
            return;
        }
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
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
        // The mob joins as EVENT, so that is the reason its setup is matched on. Tags, biome, dimension and
        // exclusions all have to agree, exactly as they do when the setup is assigned.
        if (!profile.selector().matches(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), tagsOf(type), biome,
                        dimension, MobSpawnType.EVENT.name(), top == null ? "" : top.id())
                || !ZoneMobPolicy.profileAllowed(profile.selector().regions(), top)
                || (type.getCategory() == MobCategory.MONSTER
                    && !ZoneRuleResolver.hostileSpawningEnabled(data.zones().values(), dimension, px, pos.getY(), pz))
                || !rules.extraAllowed(place(data, level, px, pos.getY(), pz))) {
            return;
        }
        int room = rules.extraCap() - nearby(level, profile.selector(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
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
        return new MobSpawnRules.Place(dimension, zones, level.isDay(), (int) Math.floor(y),
                !level.dimensionType().hasFixedTime());
    }

    private static Set<String> tagsOf(EntityType<?> type) {
        return type.builtInRegistryHolder().tags().map(key -> key.location().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** Whether a mob is one of the kinds a selector names: by id, tag or mod, minus exclusions. */
    private static boolean sameKind(MonsterDefinitions.Selector selector, EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        String id = key.toString();
        return !selector.excluded().contains(id)
                && (selector.entities().isEmpty() || selector.entities().contains(id))
                && (selector.namespaces().isEmpty() || selector.namespaces().contains(key.getNamespace()))
                && (selector.entityTags().isEmpty() || tagsOf(type).stream().anyMatch(selector.entityTags()::contains));
    }

    private static int nearby(ServerLevel level, MonsterDefinitions.Selector selector, double x, double y, double z) {
        AABB box = new AABB(x - CROWD_RADIUS, y - CROWD_RADIUS, z - CROWD_RADIUS,
                x + CROWD_RADIUS, y + CROWD_RADIUS, z + CROWD_RADIUS);
        return level.getEntitiesOfClass(Mob.class, box, mob -> sameKind(selector, mob.getType())).size();
    }

    /** Whether a Mob Setup scoped to this zone covers the mob by tag or mod (named ids are in zoneEntities). */
    private static synchronized boolean zoneNamesKind(MonsterCatalog catalog, String zoneId, EntityType<?> type) {
        refresh(catalog);
        return cachedGate.stream().anyMatch(profile -> profile.selector().regions().contains(zoneId)
                && sameKind(profile.selector(), type));
    }

    /** Entity ids named by the Mob Setups scoped to each zone: the mobs an isolated zone lets spawn. */
    private static synchronized Map<String, Set<String>> zoneEntities(MonsterCatalog catalog) {
        refresh(catalog);
        return cachedZoneEntities;
    }

    /** The setups the gate consults, or an empty list when none of them limits spawning. */
    private static synchronized List<MonsterDefinitions.Profile> gateRules(MonsterCatalog catalog) {
        refresh(catalog);
        return cachedRestricts ? cachedGate : List.of();
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
        List<MonsterDefinitions.Profile> gate = new ArrayList<>();
        boolean restricts = false;
        List<MonsterDefinitions.Profile> extra = new ArrayList<>();
        Map<String, Set<String>> perZone = new HashMap<>();
        catalog.profiles().values().forEach(profile -> profile.selector().regions().forEach(zone ->
                perZone.computeIfAbsent(zone, ignored -> new HashSet<>()).addAll(profile.selector().entities())));
        Map<String, Set<String>> zoneEntities = new HashMap<>();
        perZone.forEach((zone, entities) -> zoneEntities.put(zone, Set.copyOf(entities)));
        cachedZoneEntities = Map.copyOf(zoneEntities);
        for (MonsterDefinitions.Profile profile : catalog.profiles().values().stream().sorted(ORDER).toList()) {
            MonsterDefinitions.Selector selector = profile.selector();
            // Rules only bind mobs a setup names (by id, tag or mod), so a broad profile can never stop all spawning.
            if (selector.entities().isEmpty() && selector.entityTags().isEmpty() && selector.namespaces().isEmpty()) {
                continue;
            }
            gate.add(profile);
            restricts |= profile.spawning().restrictsNatural();
            // Extra spawns need a concrete list of mobs to pick from.
            if (profile.spawning().extra().enabled() && !selector.entities().isEmpty()) {
                extra.add(profile);
            }
        }
        // Every setup is kept, so a zone version without limits still overrides a restrictive global setup inside its zone.
        cachedGate = List.copyOf(gate);
        cachedRestricts = restricts;
        cachedExtra = List.copyOf(extra);
        cachedCatalog = catalog;
    }
}
