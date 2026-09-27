package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneDungeon;
import net.schwarz.rotasutils.core.ZoneSpawnPoint;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs dungeon zones ({@link ZoneDungeon}): the party, the waves, the final boss, the clock and the pay.
 *
 * <p>A run starts when the first player walks in. Everyone who walks in while there is room joins the
 * party and pays the key and fee on the way in; who may walk in at all is decided by
 * {@link #refusal}, which the zone gate calls, so a full or cooling-down dungeon pushes players back
 * like any locked zone. Each stage is one wave (or the boss); the next starts a few seconds after the
 * last monster of the current one dies. Clearing pays every party member still online and starts
 * their cooldown; running out of time or leaving the dungeon empty for 30 seconds ends the run with no
 * pay. A boss bar shows the stage, monsters left and time left to everyone inside.</p>
 *
 * <p>Run state lives in memory. A restart ends every run, and dungeon monsters left in the world are
 * removed as they load (they carry {@link #MOB_TAG}).</p>
 */
public final class DungeonService {
    public static final String MOB_TAG = "rotas_dungeon";
    private static final String COOLDOWN_PREFIX = "rpg.dungeon_cd.";
    private static final int START_DELAY_SECONDS = 5;
    private static final int STAGE_GAP_SECONDS = 4;
    private static final int EMPTY_ABORT_SECONDS = 30;
    private static final int EXIT_WINDOW_SECONDS = 20;
    /** A failed run keeps the party out this long, so it cannot restart the instant it ends. */
    private static final int FAIL_COOLDOWN_SECONDS = 60;

    private enum Phase { STARTING, FIGHTING, BETWEEN, CLEARED, FAILED }

    private static final class Run {
        final String zoneId;
        final Set<UUID> party = new LinkedHashSet<>();
        final Set<UUID> mobs = new HashSet<>();
        final ServerBossEvent bar;
        Phase phase = Phase.STARTING;
        int stage = -1;
        long phaseEndsAt;
        long deadline;
        int emptySeconds;

        Run(String zoneId, Component title) {
            this.zoneId = zoneId;
            this.bar = new ServerBossEvent(title, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private static final Map<String, Run> runs = new ConcurrentHashMap<>();
    private static final Set<UUID> tracked = ConcurrentHashMap.newKeySet();

    private DungeonService() {
    }

    // Gate --------------------------------------------------------------------------------------

    /**
     * Why this player may not step into the dungeon right now, or null when they may. Party members
     * always pass. Called by the zone gate every tick for players inside, so it only reads.
     */
    public static String refusal(ServerPlayer player, RotasData data, ZoneDef zone) {
        ZoneDungeon dungeon = zone.features().dungeon();
        if (!dungeon.enabled()) {
            return null;
        }
        Run run = runs.get(zone.id());
        if (run != null && run.party.contains(player.getUUID())) {
            return null;
        }
        long left = cooldownLeft(data.progress(player.getUUID()), zone.id());
        if (left > 0) {
            return ThaiText.t("rotasutils.dungeon.refuse.cooldown", QuestService.formatDuration(left / 1000).trim());
        }
        if (run != null && (run.phase == Phase.CLEARED || run.phase == Phase.FAILED)) {
            return ThaiText.t("rotasutils.dungeon.refuse.closing");
        }
        if (run != null && run.party.size() >= dungeon.maxPlayers()) {
            return ThaiText.t("rotasutils.dungeon.refuse.full", run.party.size(), dungeon.maxPlayers());
        }
        Item key = item(dungeon.keyItem());
        if (key != Items.AIR && count(player, key) < dungeon.keyCount()) {
            return ThaiText.t("rotasutils.dungeon.refuse.key", dungeon.keyCount(), key.getDescription().getString());
        }
        if (dungeon.entryGold() > 0
                && data.progress(player.getUUID()).rpg().currency(SeasonService.rules(data).currency) < dungeon.entryGold()) {
            return ThaiText.t("rotasutils.dungeon.refuse.gold", dungeon.entryGold());
        }
        return null;
    }

    // Tick --------------------------------------------------------------------------------------

    /** Once a second on the server thread. */
    public static void tick(MinecraftServer server) {
        RotasData data = RotasData.get(server);
        long now = System.currentTimeMillis();
        Set<String> seen = new HashSet<>();
        for (ZoneDef zone : data.zones().values()) {
            if (!zone.enabled() || !zone.features().dungeonRun()) {
                continue;
            }
            seen.add(zone.id());
            ServerLevel level = level(server, zone);
            if (level == null) {
                continue;
            }
            try {
                tickZone(server, data, level, zone, now);
            } catch (RuntimeException failure) {
                Rotasutils.LOG.error("Dungeon {} failed a tick: {}", zone.id(), failure.getMessage(), failure);
            }
        }
        for (String id : List.copyOf(runs.keySet())) {
            if (!seen.contains(id)) {
                end(server, runs.get(id));
            }
        }
    }

    private static void tickZone(MinecraftServer server, RotasData data, ServerLevel level, ZoneDef zone, long now) {
        ZoneDungeon dungeon = zone.features().dungeon();
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && zone.contains(player.getX(), player.getY(), player.getZ())) {
                inside.add(player);
            }
        }
        Run run = runs.get(zone.id());
        // Join everyone inside who is not yet in the party and passes the gate.
        for (ServerPlayer player : inside) {
            if (run != null && run.party.contains(player.getUUID())) {
                continue;
            }
            if (exempt(player) || refusal(player, data, zone) != null) {
                continue;
            }
            if (run == null) {
                run = new Run(zone.id(), Component.literal(name(zone)));
                run.phaseEndsAt = now + START_DELAY_SECONDS * 1000L;
                run.deadline = now + (START_DELAY_SECONDS + dungeon.timeLimitSeconds()) * 1000L;
                runs.put(zone.id(), run);
            }
            join(player, data, zone, run);
        }
        if (run == null) {
            return;
        }

        // Boss bar follows whoever is inside.
        Set<UUID> insideIds = new HashSet<>();
        for (ServerPlayer player : inside) {
            insideIds.add(player.getUUID());
            run.bar.addPlayer(player);
        }
        for (ServerPlayer shown : List.copyOf(run.bar.getPlayers())) {
            if (!insideIds.contains(shown.getUUID())) {
                run.bar.removePlayer(shown);
            }
        }

        boolean partyInside = run.party.stream().anyMatch(insideIds::contains);
        run.emptySeconds = partyInside ? 0 : run.emptySeconds + 1;
        run.mobs.removeIf(id -> !(level.getEntity(id) instanceof Mob mob) || !mob.isAlive());

        switch (run.phase) {
            case STARTING, BETWEEN -> {
                if (run.emptySeconds >= EMPTY_ABORT_SECONDS) {
                    fail(server, data, zone, run, "rotasutils.dungeon.abandoned");
                } else if (now >= run.phaseEndsAt) {
                    startStage(server, data, level, zone, run, inside);
                }
            }
            case FIGHTING -> {
                if (now >= run.deadline) {
                    fail(server, data, zone, run, "rotasutils.dungeon.timeout");
                } else if (run.emptySeconds >= EMPTY_ABORT_SECONDS) {
                    fail(server, data, zone, run, "rotasutils.dungeon.abandoned");
                } else if (run.mobs.isEmpty()) {
                    if (run.stage + 1 >= dungeon.stages()) {
                        clear(server, data, zone, run);
                    } else {
                        run.phase = Phase.BETWEEN;
                        run.phaseEndsAt = now + STAGE_GAP_SECONDS * 1000L;
                        titles(server, run, Component.literal(""), ThaiText.c("rotasutils.dungeon.stage_done",
                                run.stage + 1, dungeon.stages()).withStyle(ChatFormatting.GREEN));
                    }
                }
            }
            case CLEARED, FAILED -> {
                if (now >= run.phaseEndsAt) {
                    end(server, run);
                    return;
                }
            }
        }
        updateBar(dungeon, run, now);
    }

    private static void join(ServerPlayer player, RotasData data, ZoneDef zone, Run run) {
        ZoneDungeon dungeon = zone.features().dungeon();
        Item key = item(dungeon.keyItem());
        if (key != Items.AIR) {
            take(player, key, dungeon.keyCount());
        }
        if (dungeon.entryGold() > 0) {
            PlayerProgress progress = data.progress(player.getUUID());
            progress.rpg().currency(SeasonService.rules(data).currency, -dungeon.entryGold());
            progress.markDirty();
        }
        run.party.add(player.getUUID());
        player.sendSystemMessage(ThaiText.c("rotasutils.dungeon.joined", name(zone), run.party.size(),
                dungeon.maxPlayers()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (run.phase == Phase.STARTING) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            player.connection.send(new ClientboundSetSubtitleTextPacket(ThaiText.c("rotasutils.dungeon.starting",
                    START_DELAY_SECONDS).withStyle(ChatFormatting.GRAY)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name(zone))
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
        }
    }

    private static void startStage(MinecraftServer server, RotasData data, ServerLevel level, ZoneDef zone, Run run,
                                   List<ServerPlayer> inside) {
        ZoneDungeon dungeon = zone.features().dungeon();
        run.stage++;
        run.phase = Phase.FIGHTING;
        boolean boss = run.stage >= dungeon.waves().size();
        String profile = boss ? dungeon.bossProfile() : dungeon.waves().get(run.stage).profile();
        int count = boss ? 1 : dungeon.waves().get(run.stage).count();
        List<BlockPos> anchors = anchors(zone, boss);
        for (int i = 0; i < count; i++) {
            Mob mob = spawn(data, level, zone, profile, anchors, inside, i);
            if (mob != null) {
                run.mobs.add(mob.getUUID());
                tracked.add(mob.getUUID());
            }
        }
        if (run.mobs.isEmpty()) {
            // Nothing could be spawned (unknown Mob Setup, no room): skip the stage rather than stall.
            Rotasutils.LOG.warn("Dungeon {} stage {} spawned nothing from {}", zone.id(), run.stage + 1, profile);
        }
        Component title = boss ? ThaiText.c("rotasutils.dungeon.boss").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : ThaiText.c("rotasutils.dungeon.wave", run.stage + 1, dungeon.waves().size())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        titles(server, run, title, Component.literal(""));
        for (ServerPlayer player : inside) {
            player.playNotifySound(boss ? SoundEvents.WITHER_SPAWN : SoundEvents.RAID_HORN.value(),
                    SoundSource.HOSTILE, boss ? 0.6f : 1.0f, 1.0f);
        }
    }

    private static void clear(MinecraftServer server, RotasData data, ZoneDef zone, Run run) {
        ZoneDungeon dungeon = zone.features().dungeon();
        run.phase = Phase.CLEARED;
        run.phaseEndsAt = System.currentTimeMillis() + EXIT_WINDOW_SECONDS * 1000L;
        String currency = SeasonService.rules(data).currency;
        for (UUID id : run.party) {
            PlayerProgress progress = data.progress(id);
            setCooldown(progress, zone.id(), dungeon.cooldownSeconds());
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            if (dungeon.rewardGold() > 0) {
                progress.rpg().currency(currency, dungeon.rewardGold());
                progress.markDirty();
            }
            if (dungeon.rewardXp() > 0) {
                ProgressService.awardFromSource(player, data, XpSource.DUNGEON_COMPLETE, "dungeon:" + zone.id(),
                        dungeon.rewardXp());
            }
            for (String line : dungeon.rewardItems()) {
                ItemStack stack = stack(line);
                if (!stack.isEmpty()) {
                    RewardService.give(player, stack);
                }
            }
            player.sendSystemMessage(ThaiText.c("rotasutils.dungeon.cleared_chat", name(zone), dungeon.rewardGold(),
                    dungeon.rewardXp(), EXIT_WINDOW_SECONDS).withStyle(ChatFormatting.GREEN));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
        }
        titles(server, run, ThaiText.c("rotasutils.dungeon.cleared").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(name(zone)));
        data.setDirty();
        data.audit("dungeon_clear " + zone.id() + " party=" + run.party.size());
    }

    private static void fail(MinecraftServer server, RotasData data, ZoneDef zone, Run run, String reasonKey) {
        run.phase = Phase.FAILED;
        run.phaseEndsAt = System.currentTimeMillis() + 3000L;
        despawn(server, zone, run);
        for (UUID id : run.party) {
            setCooldown(data.progress(id), zone.id(),
                    Math.min(FAIL_COOLDOWN_SECONDS, zone.features().dungeon().cooldownSeconds()));
        }
        titles(server, run, ThaiText.c("rotasutils.dungeon.failed").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                ThaiText.c(reasonKey));
    }

    private static void end(MinecraftServer server, Run run) {
        if (run == null) {
            return;
        }
        runs.remove(run.zoneId);
        run.bar.removeAllPlayers();
        RotasData data = RotasData.get(server);
        ZoneDef zone = data.zone(run.zoneId);
        if (zone != null) {
            despawn(server, zone, run);
        }
    }

    private static void despawn(MinecraftServer server, ZoneDef zone, Run run) {
        ServerLevel level = level(server, zone);
        for (UUID id : run.mobs) {
            tracked.remove(id);
            if (level != null && level.getEntity(id) instanceof Mob mob) {
                mob.discard();
            }
        }
        run.mobs.clear();
    }

    /** Dungeon monsters that load without a run (after a restart) are removed. */
    public static void onEntityAdd(Entity entity) {
        if (entity instanceof Mob mob && !mob.level().isClientSide && mob.getTags().contains(MOB_TAG)
                && !tracked.contains(mob.getUUID())) {
            mob.discard();
        }
    }

    /** Drops every run, e.g. when the server stops. */
    public static void clearAll() {
        runs.values().forEach(run -> run.bar.removeAllPlayers());
        runs.clear();
        tracked.clear();
    }

    // Spawning ----------------------------------------------------------------------------------

    /** Where monsters appear: the zone's spawn points (boss points first for the boss). */
    private static List<BlockPos> anchors(ZoneDef zone, boolean boss) {
        List<BlockPos> anchors = new ArrayList<>();
        for (ZoneSpawnPoint point : zone.features().spawnPoints()) {
            if (!boss || point.kind() == ZoneSpawnPoint.Kind.BOSS) {
                anchors.add(new BlockPos(point.x(), point.y(), point.z()));
            }
        }
        if (anchors.isEmpty() && boss) {
            return anchors(zone, false);
        }
        return anchors;
    }

    private static Mob spawn(RotasData data, ServerLevel level, ZoneDef zone, String profileId, List<BlockPos> anchors,
                             List<ServerPlayer> inside, int index) {
        var kernel = data.kernel();
        if (kernel == null) {
            return null;
        }
        var profile = kernel.content().monsters().profiles().get(new ContentId(profileId));
        String entityId = profile == null ? null : profile.selector().entities().stream().sorted().findFirst().orElse(null);
        EntityType<?> type = entityId == null ? null
                : BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(entityId)).orElse(null);
        if (type == null) {
            return null;
        }
        BlockPos at = place(level, zone, type, anchors, inside, index);
        if (at == null) {
            return null;
        }
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) {
            if (created != null) {
                created.discard();
            }
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        mob.addTag(MOB_TAG);
        if (!level.addFreshEntity(mob)) {
            return null;
        }
        try {
            if (kernel.monsters().peek(mob) == null) {
                kernel.monsters().assign(mob, profile.id(), null, "DUNGEON");
            }
        } catch (RuntimeException failure) {
            mob.discard();
            Rotasutils.LOG.warn("Dungeon {} could not set up {}: {}", zone.id(), profileId, failure.getMessage());
            return null;
        }
        if (!inside.isEmpty()) {
            mob.setTarget(inside.get(index % inside.size()));
        }
        return mob;
    }

    /** A free spot with floor near an anchor (or a party member), inside the zone. */
    private static BlockPos place(ServerLevel level, ZoneDef zone, EntityType<?> type, List<BlockPos> anchors,
                                  List<ServerPlayer> inside, int index) {
        BlockPos base;
        if (!anchors.isEmpty()) {
            base = anchors.get(index % anchors.size());
        } else if (!inside.isEmpty()) {
            base = inside.get(index % inside.size()).blockPosition();
        } else {
            return null;
        }
        var random = level.random;
        int spread = anchors.isEmpty() ? 9 : 3;
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = random.nextInt(spread * 2 + 1) - spread;
            int dz = random.nextInt(spread * 2 + 1) - spread;
            if (anchors.isEmpty() && Math.abs(dx) < 4 && Math.abs(dz) < 4) {
                continue;
            }
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos at = base.offset(dx, dy, dz);
                if (!zone.contains(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)) {
                    continue;
                }
                if (level.getBlockState(at.below()).isSolid()
                        && level.noCollision(type.getAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) {
                    return at;
                }
            }
        }
        return anchors.isEmpty() ? null : base;
    }

    // Helpers -----------------------------------------------------------------------------------

    private static void updateBar(ZoneDungeon dungeon, Run run, long now) {
        long left = Math.max(0, run.deadline - now);
        String time = QuestService.formatDuration(left / 1000).trim();
        Component text = switch (run.phase) {
            case STARTING -> ThaiText.c("rotasutils.dungeon.bar.starting",
                    Math.max(0, (run.phaseEndsAt - now + 999) / 1000), run.party.size(), dungeon.maxPlayers());
            case CLEARED -> ThaiText.c("rotasutils.dungeon.bar.cleared", Math.max(0, (run.phaseEndsAt - now + 999) / 1000));
            case FAILED -> ThaiText.c("rotasutils.dungeon.failed");
            default -> ThaiText.c("rotasutils.dungeon.bar.fighting", Math.min(dungeon.stages(), run.stage + (run.phase == Phase.BETWEEN ? 2 : 1)),
                    dungeon.stages(), run.mobs.size(), time);
        };
        run.bar.setName(text);
        run.bar.setColor(run.phase == Phase.CLEARED ? BossEvent.BossBarColor.GREEN
                : left < 60_000 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        long total = Math.max(1, dungeon.timeLimitSeconds() * 1000L);
        run.bar.setProgress(run.phase == Phase.STARTING || run.phase == Phase.CLEARED ? 1f
                : Math.max(0f, Math.min(1f, left / (float) total)));
    }

    private static void titles(MinecraftServer server, Run run, Component title, Component subtitle) {
        for (UUID id : run.party) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    static long cooldownLeft(PlayerProgress progress, String zoneId) {
        String until = progress.questVariables().get(COOLDOWN_PREFIX + zoneId);
        if (until == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(until) - System.currentTimeMillis());
        } catch (NumberFormatException corrupt) {
            return 0;
        }
    }

    private static void setCooldown(PlayerProgress progress, String zoneId, int seconds) {
        if (seconds <= 0) {
            progress.removeQuestVariables(List.of(COOLDOWN_PREFIX + zoneId));
            return;
        }
        progress.questVariables().put(COOLDOWN_PREFIX + zoneId, String.valueOf(System.currentTimeMillis() + seconds * 1000L));
        progress.markDirty();
    }

    /** Administrators outside gate testing walk through without joining, so they can inspect a run. */
    private static boolean exempt(ServerPlayer player) {
        return player.hasPermissions(2) && !ZoneGateService.adminTesting(player.getUUID());
    }

    private static ServerLevel level(MinecraftServer server, ZoneDef zone) {
        String dimension = zone.dimension().isEmpty() ? "minecraft:overworld" : zone.dimension();
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static String name(ZoneDef zone) {
        return zone.name().isEmpty() ? zone.id() : zone.name();
    }

    private static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id == null ? "" : id);
        return location == null || id.isBlank() ? Items.AIR : BuiltInRegistries.ITEM.get(location);
    }

    /** {@code "minecraft:diamond 2"} as a stack; empty when the item is unknown. */
    static ItemStack stack(String line) {
        String[] parts = line == null ? new String[0] : line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = item(parts[0]);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        int count = 1;
        if (parts.length > 1) {
            try {
                count = Math.max(1, Math.min(item.getMaxStackSize() * 4, Integer.parseInt(parts[1])));
            } catch (NumberFormatException ignored) {
                count = 1;
            }
        }
        return new ItemStack(item, count);
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(ServerPlayer player, Item item, int amount) {
        int left = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                return;
            }
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** For the zone editor: whether a dungeon has a run going and how many are in it. */
    public static Map<String, Integer> activeParties() {
        Map<String, Integer> parties = new HashMap<>();
        runs.forEach((id, run) -> parties.put(id, run.party.size()));
        return parties;
    }
}
