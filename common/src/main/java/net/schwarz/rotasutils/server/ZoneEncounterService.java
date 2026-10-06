package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneEncounterSchedule;
import net.schwarz.rotasutils.core.ZoneEncounterState;
import net.schwarz.rotasutils.core.ZoneSpawnPoint;
import net.schwarz.rotasutils.data.RotasData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZoneEncounterService {
    private final MinecraftServer server;
    private final RpgKernel kernel;
    private final Map<String, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, String> pointByMob = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private long spawned, resets, deaths, rejected;

    ZoneEncounterService(MinecraftServer server, RpgKernel kernel) {
        this.server = server;
        this.kernel = kernel;
    }

    public static String key(String zoneId, String pointId) {
        return zoneId + "#" + pointId;
    }

    void tick() {
        RotasData data = RotasData.get(server);
        long now = server.overworld().getGameTime();
        Set<String> live = new HashSet<>();
        for (ZoneDef zone : data.zones().values()) {
            if (zone.features().spawnPoints().isEmpty() || zone.features().dungeonRun()) {
                continue;
            }
            ServerLevel level = level(zone);
            for (ZoneSpawnPoint point : zone.features().spawnPoints()) {
                String key = key(zone.id(), point.id());
                live.add(key);
                if (!zone.enabled() || level == null) {
                    continue;
                }
                try {
                    tickPoint(data, level, zone, point, key, now);
                } catch (RuntimeException failure) {
                    error(key, failure);
                }
            }
        }
        for (String key : List.copyOf(data.zoneEncounters().keySet())) {
            if (!live.contains(key)) {
                retire(data, key);
            }
        }
    }

    private void tickPoint(RotasData data, ServerLevel level, ZoneDef zone, ZoneSpawnPoint point, String key, long now) {
        BlockPos home = new BlockPos(point.x(), point.y(), point.z());
        if (!level.isPositionEntityTicking(home)) {
            return;
        }
        ZoneEncounterState state = data.zoneEncounter(key);
        if (state == null) {
            state = ZoneEncounterState.fresh(now);
        }
        Mob mob = state.mob() != null && level.getEntity(state.mob()) instanceof Mob found && found.isAlive() ? found : null;
        if (state.mob() != null && mob == null) {
            pointByMob.remove(state.mob());
            state = state.died(now, point.respawnSeconds());
            removeBar(key);
        }
        List<ServerPlayer> inside = new ArrayList<>();
        boolean near = false;
        double wake = (double) point.activationRadius() * point.activationRadius();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            if (zone.contains(player.getX(), player.getY(), player.getZ())) {
                inside.add(player);
            }
            if (player.distanceToSqr(point.x() + 0.5, point.y(), point.z() + 0.5) <= wake) {
                near = true;
            }
        }
        if (!inside.isEmpty()) {
            state = state.seen(now);
        }
        if (mob != null) {
            pointByMob.put(mob.getUUID(), key);
            if (!zone.contains(mob.getX(), mob.getY(), mob.getZ())) {
                sendHome(mob, point);
            } else if (ZoneEncounterSchedule.shouldReset(now, state.lastPlayerSeen())
                    && (mob.getHealth() < mob.getMaxHealth() || mob.distanceToSqr(point.x() + 0.5, point.y(), point.z() + 0.5) > 9)) {
                mob.setHealth(mob.getMaxHealth());
                sendHome(mob, point);
                state = state.seen(now);
                resets++;
            }
            updateBar(key, point, mob, inside);
        } else if (ZoneEncounterSchedule.shouldSpawn(now, state.nextSpawnAt(), false, near)) {
            Mob created = spawn(level, point, key);
            if (created != null) {
                state = state.spawned(created.getUUID(), now);
                pointByMob.put(created.getUUID(), key);
            }
        }
        data.putZoneEncounter(key, state);
    }

    private Mob spawn(ServerLevel level, ZoneSpawnPoint point, String key) {
        var profile = kernel.content().monsters().profiles().get(new ContentId(point.profile()));
        String entityId = profile == null ? null : profile.selector().entities().stream().sorted().findFirst().orElse(null);
        EntityType<?> type = entityId == null ? null
                : BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(entityId)).orElse(null);
        if (type == null) {
            if (warned.add(key)) {
                Rotasutils.LOG.warn("Zone spawn point {} needs a Mob Setup that names a mob: {}", key, point.profile());
            }
            return null;
        }
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) {
            if (created != null) {
                created.discard();
            }
            return null;
        }
        BlockPos home = new BlockPos(point.x(), point.y(), point.z());
        mob.moveTo(point.x() + 0.5, point.y(), point.z() + 0.5, level.random.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(home), MobSpawnType.EVENT, null, null);
        mob.setPersistenceRequired();
        if (!level.addFreshEntity(mob)) {
            return null;
        }
        try {
            if (kernel.monsters().peek(mob) == null) {
                kernel.monsters().assign(mob, profile.id(), null, "ZONE_POINT");
            }
        } catch (RuntimeException failure) {
            mob.discard();
            throw failure;
        }
        warned.remove(key);
        spawned++;
        return mob;
    }

    private static void sendHome(Mob mob, ZoneSpawnPoint point) {
        mob.getNavigation().stop();
        mob.teleportTo(point.x() + 0.5, point.y(), point.z() + 0.5);
    }

    private void updateBar(String key, ZoneSpawnPoint point, Mob mob, List<ServerPlayer> inside) {
        if (point.kind() != ZoneSpawnPoint.Kind.BOSS) {
            removeBar(key);
            return;
        }
        ServerBossEvent bar = bars.computeIfAbsent(key, ignored ->
                new ServerBossEvent(mob.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(mob.getDisplayName());
        bar.setProgress(mob.getMaxHealth() <= 0 ? 0 : Math.max(0, Math.min(1, mob.getHealth() / mob.getMaxHealth())));
        for (ServerPlayer watcher : List.copyOf(bar.getPlayers())) {
            if (!inside.contains(watcher)) {
                bar.removePlayer(watcher);
            }
        }
        inside.forEach(bar::addPlayer);
    }

    private void removeBar(String key) {
        ServerBossEvent bar = bars.remove(key);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    public void onDeath(LivingEntity entity) {
        String key = pointByMob.remove(entity.getUUID());
        if (key == null) {
            return;
        }
        RotasData data = RotasData.get(server);
        ZoneEncounterState state = data.zoneEncounter(key);
        ZoneSpawnPoint point = point(data, key);
        if (state != null && point != null && entity.getUUID().equals(state.mob())) {
            data.putZoneEncounter(key, state.died(server.overworld().getGameTime(), point.respawnSeconds()));
            deaths++;
        }
        removeBar(key);
    }

    public boolean respawnNow(String zoneId, String pointId) {
        RotasData data = RotasData.get(server);
        String key = key(zoneId, pointId);
        ZoneEncounterState state = data.zoneEncounter(key);
        if (point(data, key) == null || (state != null && state.mob() != null)) {
            return false;
        }
        long now = server.overworld().getGameTime();
        data.putZoneEncounter(key, new ZoneEncounterState(null, now, state == null ? now : state.lastPlayerSeen()));
        return true;
    }

    private void retire(RotasData data, String key) {
        ZoneEncounterState state = data.zoneEncounter(key);
        if (state != null && state.mob() != null) {
            pointByMob.remove(state.mob());
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(state.mob()) instanceof Mob mob) {
                    mob.discard();
                    break;
                }
            }
        }
        removeBar(key);
        data.removeZoneEncounter(key);
    }

    private static ZoneSpawnPoint point(RotasData data, String key) {
        int split = key.lastIndexOf('#');
        ZoneDef zone = split < 0 ? null : data.zone(key.substring(0, split));
        return zone == null ? null : zone.features().spawnPoint(key.substring(split + 1));
    }

    private ServerLevel level(ZoneDef zone) {
        if (zone.dimension().isEmpty()) {
            return server.overworld();
        }
        ResourceLocation id = ResourceLocation.tryParse(zone.dimension());
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public void close() {
        bars.values().forEach(ServerBossEvent::removeAllPlayers);
        bars.clear();
        pointByMob.clear();
    }

    public String diagnostics() {
        return "Zone encounters spawned=" + spawned + " deaths=" + deaths + " resets=" + resets
                + " bars=" + bars.size() + " rejected=" + rejected;
    }

    private void error(String key, RuntimeException failure) {
        rejected++;
        if (warned.add(key + ":error")) {
            Rotasutils.LOG.error("Zone spawn point {} failed: {}", key, failure.getMessage(), failure);
        }
    }
}
