package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.schwarz.rotasutils.core.NemesisMath;
import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.util.ThaiText;
import net.schwarz.rotasutils.worldevent.WorldEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * World events (เหตุการณ์โลก): a zone or a patch of wilderness changes for a while, and players have a
 * reason to go there.
 *
 * <p>Everything an event does rides systems that already exist: mobs levelled inside it get its level
 * bonus and elite chances in {@link MonsterService}, kills inside it pay its experience and loot
 * multipliers through the normal kill path, and its reward is a {@link SeasonRules.TrackReward} paid
 * like a track rung. This class only decides when and where events run, keeps their boss bars, brings
 * their waves and counts their goals. It runs once a second and does nothing while no event runs.</p>
 */
public final class WorldEventService {
    /** Marks a mob brought by an event wave. It is promoted like a wild mob, and does not burn. */
    public static final String WAVE_TAG = "rotas_event_wave";
    private static final String EVENT_TAG_PREFIX = "rotas_event:";
    private static final TagKey<net.minecraft.world.level.block.Block> ORES_C =
            TagKey.create(Registries.BLOCK, new ResourceLocation("c", "ores"));
    private static final TagKey<net.minecraft.world.level.block.Block> ORES_FORGE =
            TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "ores"));

    private record Mined(UUID player, ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
    }

    private static final Map<Integer, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<Integer, Long> LAST_WAVE = new HashMap<>();
    private static final List<Mined> MINED = new ArrayList<>();
    private static long nextRollAt;

    private WorldEventService() {
    }

    public static void clear() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
        LAST_WAVE.clear();
        MINED.clear();
        nextRollAt = 0;
    }

    private static SeasonRules.WorldEventRules rules(RotasData data) {
        return SeasonService.rules(data).worldEvents;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    // Lookups used by the kill, drop, spawn and heal paths ---------------------------------------

    /** The kind of the event running where this entity stands, or null. Cheap when nothing runs. */
    public static SeasonRules.WorldEventDef at(LivingEntity entity) {
        RotasData data = RotasData.instance();
        if (data == null || data.worldEvents().isEmpty() || entity.level().isClientSide()) {
            return null;
        }
        WorldEvent event = eventAt(data, entity.level().dimension().location().toString(),
                entity.getX(), entity.getY(), entity.getZ());
        return event == null ? null : def(data, event);
    }

    private static WorldEvent eventAt(RotasData data, String dimension, double x, double y, double z) {
        for (WorldEvent event : data.worldEvents().values()) {
            if (event.contains(data.zone(event.zoneId()), dimension, x, y, z)) {
                return event;
            }
        }
        return null;
    }

    private static SeasonRules.WorldEventDef def(RotasData data, WorldEvent event) {
        SeasonRules.WorldEventRules rules = rules(data);
        return rules == null || !rules.enabled ? null : rules.types.get(event.type());
    }

    public static double xpMultiplier(LivingEntity victim) {
        SeasonRules.WorldEventDef def = at(victim);
        return def == null ? 1.0 : def.xpMultiplier;
    }

    public static double healingMultiplier(LivingEntity entity) {
        SeasonRules.WorldEventDef def = at(entity);
        return def == null ? 1.0 : def.healingMultiplier;
    }

    // Goals -----------------------------------------------------------------------------------

    /** A hostile kill inside an event with a kill goal counts toward it. */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        RotasData data = RotasData.get(killer.server);
        if (data.worldEvents().isEmpty() || !BestiaryService.recordable(victim)) {
            return;
        }
        WorldEvent event = eventAt(data, victim.level().dimension().location().toString(),
                victim.getX(), victim.getY(), victim.getZ());
        SeasonRules.WorldEventDef def = event == null ? null : def(data, event);
        if (def == null || !"KILL".equals(def.goal)) {
            return;
        }
        advance(killer.server, data, event, def, killer.getUUID());
    }

    /**
     * An ore broken inside an event. The drops are worked out now, with the player's tool, and paid a
     * second later only if the block is really gone - another mod's protection may still cancel it.
     */
    public static void onBlockBreak(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
        RotasData data = RotasData.get(player.server);
        if (data.worldEvents().isEmpty() || player.isCreative() || !(level instanceof ServerLevel server)
                || !ore(state) || ProductionService.recentlyPlaced(level, pos) || MINED.size() >= 512) {
            return;
        }
        WorldEvent event = eventAt(data, level.dimension().location().toString(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        SeasonRules.WorldEventDef def = event == null ? null : def(data, event);
        if (def == null || (def.oreBonusChance <= 0 && !"MINE".equals(def.goal))) {
            return;
        }
        List<ItemStack> drops = def.oreBonusChance > 0 && player.getRandom().nextDouble() < def.oreBonusChance
                ? Block.getDrops(state, server, pos, level.getBlockEntity(pos), player, player.getMainHandItem())
                : List.of();
        MINED.add(new Mined(player.getUUID(), server, pos.immutable(), state, List.copyOf(drops)));
    }

    private static boolean ore(BlockState state) {
        if (state.is(ORES_C) || state.is(ORES_FORGE)) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    private static void settleMined(MinecraftServer server, RotasData data) {
        if (MINED.isEmpty()) {
            return;
        }
        List<Mined> batch = new ArrayList<>(MINED);
        MINED.clear();
        for (Mined mined : batch) {
            if (mined.level().getBlockState(mined.pos()).is(mined.state().getBlock())) {
                continue;
            }
            for (ItemStack stack : mined.drops()) {
                Block.popResource(mined.level(), mined.pos(), stack.copy());
            }
            WorldEvent event = eventAt(data, mined.level().dimension().location().toString(),
                    mined.pos().getX() + 0.5, mined.pos().getY(), mined.pos().getZ() + 0.5);
            SeasonRules.WorldEventDef def = event == null ? null : def(data, event);
            if (def != null && "MINE".equals(def.goal)) {
                advance(server, data, event, def, mined.player());
            }
        }
    }

    private static void advance(MinecraftServer server, RotasData data, WorldEvent event,
                                SeasonRules.WorldEventDef def, UUID player) {
        int total = event.contribute(player, 1);
        data.setDirty();
        if (def.goalCount > 0 && total >= def.goalCount) {
            complete(server, data, event, def);
        }
    }

    /** The goal is met: everyone who did enough is paid once, and the event ends. */
    private static void complete(MinecraftServer server, RotasData data, WorldEvent event, SeasonRules.WorldEventDef def) {
        int paid = 0;
        for (var entry : event.contributors().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (entry.getValue() < def.minContribution) {
                player.sendSystemMessage(ThaiText.c("rotasutils.msg.worldevent.too_little",
                        entry.getValue(), def.minContribution).withStyle(ChatFormatting.GRAY));
                continue;
            }
            TrackRewards.Paid reward = TrackRewards.pay(player, data, def.reward, "worldevent:" + event.id());
            String described = TrackRewards.describe(reward);
            if (!described.isBlank()) {
                player.sendSystemMessage(ThaiText.c("rotasutils.msg.worldevent.reward", described)
                        .withStyle(ChatFormatting.GOLD));
            }
            net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
            paid++;
        }
        server.getPlayerList().broadcastSystemMessage(ThaiText.c("rotasutils.msg.worldevent.victory", def.name, paid)
                .withStyle(ChatFormatting.GOLD), false);
        end(data, event);
    }

    // Starting and ending ----------------------------------------------------------------------

    /**
     * Starts an event. {@code typeId} null picks a kind by weight; {@code anchor} with {@code here}
     * puts it around that player, otherwise it goes to a random open zone or, lacking one, into the
     * wilderness near a random player. Returns null with nowhere or nothing to start.
     */
    public static WorldEvent start(MinecraftServer server, RotasData data, String typeId, ServerPlayer anchor, boolean here) {
        SeasonRules.WorldEventRules rules = rules(data);
        if (rules == null || rules.types.isEmpty()) {
            return null;
        }
        java.util.Random random = new java.util.Random();
        long now = now();
        Placement place = here && anchor != null ? wildAt(anchor.serverLevel(), anchor.blockPosition(), rules)
                : place(server, data, rules, random);
        if (place == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(place.dimension())));
        boolean night = level != null && level.isNight();
        String type = typeId != null ? typeId : pickType(rules, night, random);
        SeasonRules.WorldEventDef def = type == null ? null : rules.types.get(type);
        if (def == null || (def.nightOnly && !night)) {
            return null;
        }
        WorldEvent event = new WorldEvent(data.nextWorldEventId(), type, place.dimension(), place.zoneId(),
                place.x(), place.z(), place.radius(), now, now + rules.durationMinutes * 60L);
        data.putWorldEvent(event);
        announceStart(server, data, event, def, rules);
        return event;
    }

    private record Placement(String dimension, String zoneId, int x, int z, int radius, String label) {
    }

    private static Placement place(MinecraftServer server, RotasData data, SeasonRules.WorldEventRules rules,
                                   java.util.Random random) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator()) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        List<Placement> zones = new ArrayList<>();
        for (ZoneDef zone : data.zones().values()) {
            if (!zone.enabled() || zone.safe() || zone.areas().isEmpty() || zone.danger() == ZoneDef.Danger.SAFE
                    || (zone.combatRules().hostileSpawningEnabled().overridden()
                    && !zone.combatRules().hostileSpawningEnabled().value())) {
                continue;
            }
            String dimension = zone.dimension().isEmpty() ? Level.OVERWORLD.location().toString() : zone.dimension();
            boolean busy = data.worldEvents().values().stream().anyMatch(event -> event.zoneId().equals(zone.id()));
            boolean visited = players.stream().anyMatch(player -> player.level().dimension().location().toString().equals(dimension));
            if (busy || !visited) {
                continue;
            }
            ZoneArea.Bounds bounds = zone.areas().get(0).bounds();
            int x = (bounds.minX() + bounds.maxX()) / 2;
            int z = (bounds.minZ() + bounds.maxZ()) / 2;
            int radius = Math.max(8, Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) / 2);
            zones.add(new Placement(dimension, zone.id(), x, z, radius, zone.name().isBlank() ? zone.id() : zone.name()));
        }
        if (!zones.isEmpty()) {
            return zones.get(random.nextInt(zones.size()));
        }
        ServerPlayer anchor = players.get(random.nextInt(players.size()));
        ServerLevel level = anchor.serverLevel();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = rules.wildMinDistance + random.nextInt(Math.max(1, rules.wildMaxDistance - rules.wildMinDistance + 1));
            int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            ZoneService.Region region = ZoneService.region(data, level, x, anchor.getY(), z);
            if (region.safe() || eventAt(data, level.dimension().location().toString(), x, anchor.getY(), z) != null) {
                continue;
            }
            return wildAt(level, new BlockPos(x, anchor.getBlockY(), z), rules);
        }
        return null;
    }

    private static Placement wildAt(ServerLevel level, BlockPos center, SeasonRules.WorldEventRules rules) {
        return new Placement(level.dimension().location().toString(), "", center.getX(), center.getZ(), rules.wildRadius,
                ThaiText.t("rotasutils.worldevent.place_wild", center.getX(), center.getZ()));
    }

    private static String pickType(SeasonRules.WorldEventRules rules, boolean night, java.util.Random random) {
        int total = 0;
        for (SeasonRules.WorldEventDef def : rules.types.values()) {
            if (def.weight > 0 && (!def.nightOnly || night)) {
                total += def.weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (var entry : rules.types.entrySet()) {
            SeasonRules.WorldEventDef def = entry.getValue();
            if (def.weight <= 0 || (def.nightOnly && !night)) {
                continue;
            }
            roll -= def.weight;
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** The place name players read: the zone's name, or the coordinates of a wilderness event. */
    public static String placeName(RotasData data, WorldEvent event) {
        if (event.inZone()) {
            ZoneDef zone = data.zone(event.zoneId());
            return zone == null || zone.name().isBlank() ? event.zoneId() : zone.name();
        }
        return ThaiText.t("rotasutils.worldevent.place_wild", event.centerX(), event.centerZ());
    }

    /** "about 250 blocks to the north-east", or "in another dimension", for one player. */
    public static String directionFor(ServerPlayer player, WorldEvent event) {
        if (!player.level().dimension().location().toString().equals(event.dimension())) {
            return ThaiText.t("rotasutils.worldevent.elsewhere");
        }
        double dx = event.centerX() + 0.5 - player.getX();
        double dz = event.centerZ() + 0.5 - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= event.radius()) {
            return ThaiText.t("rotasutils.worldevent.here");
        }
        return ThaiText.t("rotasutils.worldevent.direction", NemesisMath.roughDistance(distance),
                ThaiText.t("rotasutils.compass." + NemesisMath.compass(dx, dz)));
    }

    private static void announceStart(MinecraftServer server, RotasData data, WorldEvent event,
                                      SeasonRules.WorldEventDef def, SeasonRules.WorldEventRules rules) {
        String place = placeName(data, event);
        String goal = switch (def.goal) {
            case "KILL" -> ThaiText.t("rotasutils.worldevent.goal.kill", def.goalCount, rules.durationMinutes);
            case "MINE" -> ThaiText.t("rotasutils.worldevent.goal.mine", def.goalCount, rules.durationMinutes);
            default -> ThaiText.t("rotasutils.worldevent.goal.none", rules.durationMinutes);
        };
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.worldevent.start", def.name, place)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            if (!def.description.isBlank()) {
                player.sendSystemMessage(Component.literal(def.description).withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(Component.literal(goal + " - " + directionFor(player, event))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Ends an event without paying anything: its time ran out, dawn came, or an admin stopped it. */
    public static void expire(MinecraftServer server, RotasData data, WorldEvent event, String reasonKey) {
        SeasonRules.WorldEventDef def = def(data, event);
        String name = def == null ? event.type() : def.name;
        server.getPlayerList().broadcastSystemMessage(ThaiText.c(reasonKey, name).withStyle(ChatFormatting.GRAY), false);
        end(data, event);
    }

    private static void end(RotasData data, WorldEvent event) {
        data.removeWorldEvent(event.id());
        ServerBossEvent bar = BARS.remove(event.id());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        LAST_WAVE.remove(event.id());
    }

    // The once-a-second pass ------------------------------------------------------------------

    public static void tick(MinecraftServer server, RotasData data) {
        SeasonRules.WorldEventRules rules = rules(data);
        long now = now();
        settleMined(server, data);
        if (rules == null || !rules.enabled) {
            if (!data.worldEvents().isEmpty()) {
                for (WorldEvent event : List.copyOf(data.worldEvents().values())) {
                    end(data, event);
                }
            }
            return;
        }
        if (nextRollAt == 0) {
            nextRollAt = now + rules.intervalMinutes * 60L;
        } else if (now >= nextRollAt) {
            nextRollAt = now + rules.intervalMinutes * 60L;
            if (data.worldEvents().size() < rules.maxActive && Math.random() < rules.startChance) {
                start(server, data, null, null, false);
            }
        }
        if (data.worldEvents().isEmpty()) {
            return;
        }
        for (WorldEvent event : List.copyOf(data.worldEvents().values())) {
            SeasonRules.WorldEventDef def = def(data, event);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(event.dimension())));
            if (def == null || level == null || (event.inZone() && data.zone(event.zoneId()) == null)) {
                end(data, event);
                continue;
            }
            if (now >= event.endsAt()) {
                expire(server, data, event, "rotasutils.msg.worldevent.expired");
                continue;
            }
            if (def.nightOnly && !level.isNight()) {
                expire(server, data, event, "rotasutils.msg.worldevent.dawn");
                continue;
            }
            List<ServerPlayer> inside = inside(server, data, event);
            updateBar(event, def, rules, inside, now);
            for (ServerPlayer player : inside) {
                applyEffects(player, def);
            }
            waves(level, event, def, inside, now);
        }
    }

    private static List<ServerPlayer> inside(MinecraftServer server, RotasData data, WorldEvent event) {
        List<ServerPlayer> inside = new ArrayList<>();
        ZoneDef zone = data.zone(event.zoneId());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (event.contains(zone, player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) {
                inside.add(player);
            }
        }
        return inside;
    }

    private static void updateBar(WorldEvent event, SeasonRules.WorldEventDef def, SeasonRules.WorldEventRules rules,
                                  List<ServerPlayer> inside, long now) {
        ServerBossEvent bar = BARS.computeIfAbsent(event.id(), id -> new ServerBossEvent(Component.literal(def.name),
                switch (def.goal) {
                    case "KILL" -> BossEvent.BossBarColor.RED;
                    case "MINE" -> BossEvent.BossBarColor.YELLOW;
                    default -> BossEvent.BossBarColor.PURPLE;
                }, BossEvent.BossBarOverlay.PROGRESS));
        long left = event.secondsLeft(now);
        String time = String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60);
        boolean goal = def.goalCount > 0;
        bar.setName(Component.literal(goal
                ? ThaiText.t("rotasutils.worldevent.bar_goal", def.name, event.progress(), def.goalCount, time)
                : ThaiText.t("rotasutils.worldevent.bar", def.name, time)));
        double span = Math.max(1, rules.durationMinutes * 60L);
        bar.setProgress((float) Math.max(0, Math.min(1, goal ? event.progress() / (double) def.goalCount : left / span)));
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!inside.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : inside) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
                player.displayClientMessage(ThaiText.c("rotasutils.msg.worldevent.enter", def.name)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    private record ParsedEffect(MobEffect effect, int amplifier) {
    }

    private static final Map<String, ParsedEffect> EFFECTS = new HashMap<>();

    private static void applyEffects(ServerPlayer player, SeasonRules.WorldEventDef def) {
        for (String line : def.playerEffects) {
            ParsedEffect parsed = EFFECTS.computeIfAbsent(line == null ? "" : line, WorldEventService::parseEffect);
            if (parsed != null) {
                player.addEffect(new MobEffectInstance(parsed.effect(), 60, parsed.amplifier(), true, false, true));
            }
        }
    }

    private static ParsedEffect parseEffect(String line) {
        String[] parts = line.trim().split("\\s+");
        ResourceLocation id = parts.length == 0 ? null : ResourceLocation.tryParse(parts[0]);
        MobEffect effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect == null) {
            return null;
        }
        int level = 1;
        if (parts.length > 1) {
            try {
                level = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                level = 1;
            }
        }
        return new ParsedEffect(effect, Math.max(1, Math.min(5, level)) - 1);
    }

    /** Brings a wave around up to four players inside, keeping at most {@code maxAlive} wave mobs near each. */
    private static void waves(ServerLevel level, WorldEvent event, SeasonRules.WorldEventDef def,
                              List<ServerPlayer> inside, long now) {
        if (def.spawns.length == 0 || def.waveSize <= 0 || inside.isEmpty()
                || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        long last = LAST_WAVE.getOrDefault(event.id(), 0L);
        if (now - last < def.waveSeconds) {
            return;
        }
        LAST_WAVE.put(event.id(), now);
        RotasData data = RotasData.get(level.getServer());
        ZoneDef zone = data.zone(event.zoneId());
        String tag = EVENT_TAG_PREFIX + event.id();
        List<ServerPlayer> targets = new ArrayList<>(inside);
        Collections.shuffle(targets);
        int budget = Math.max(1, (int) Math.min(event.secondsLeft(now), 1800)) * 20;
        for (ServerPlayer player : targets.subList(0, Math.min(4, targets.size()))) {
            if (player.level() != level) {
                continue;
            }
            int alive = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(32),
                    mob -> mob.isAlive() && mob.getTags().contains(tag)).size();
            int count = Math.min(def.waveSize, def.maxAlive - alive);
            for (int i = 0; i < count; i++) {
                EntityType<?> type = SpawnPlacer.mobType(def.spawns[level.getRandom().nextInt(def.spawns.length)]);
                if (type == null) {
                    continue;
                }
                BlockPos pos = SpawnPlacer.find(level, player.blockPosition(), 10, 20, type, level.getRandom(),
                        spot -> event.contains(zone, event.dimension(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
                Mob mob = pos == null ? null : SpawnPlacer.create(level, type, pos, level.getRandom());
                if (mob == null) {
                    continue;
                }
                mob.addTag(WAVE_TAG);
                mob.addTag(tag);
                mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, budget, 0, false, false));
                level.addFreshEntity(mob);
            }
        }
    }

    /** One line per running event for {@code /rotas worldevent}. */
    public static List<Component> describe(ServerPlayer viewer, RotasData data) {
        List<Component> lines = new ArrayList<>();
        long now = now();
        for (WorldEvent event : data.worldEvents().values()) {
            SeasonRules.WorldEventDef def = def(data, event);
            if (def == null) {
                continue;
            }
            long left = event.secondsLeft(now);
            String progress = def.goalCount > 0 ? event.progress() + "/" + def.goalCount : "-";
            lines.add(ThaiText.c("rotasutils.cmd.worldevent.line", event.id(), def.name, placeName(data, event),
                    progress, left / 60, viewer == null ? "" : directionFor(viewer, event)));
        }
        return lines;
    }

    /** Everything the admin world-event screen shows: the switch, each type, and each running event. */
    public static net.minecraft.nbt.CompoundTag adminState(ServerPlayer viewer, RotasData data) {
        SeasonRules.WorldEventRules rules = rules(data);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("enabled", rules != null && rules.enabled);
        net.minecraft.nbt.ListTag types = new net.minecraft.nbt.ListTag();
        if (rules != null) {
            rules.types.forEach((id, def) -> {
                net.minecraft.nbt.CompoundTag row = new net.minecraft.nbt.CompoundTag();
                row.putString("id", id);
                row.putString("name", def.name.isBlank() ? id : def.name);
                row.putString("description", def.description);
                row.putString("goal", def.goal);
                row.putInt("goal_count", def.goalCount);
                row.putBoolean("night", def.nightOnly);
                types.add(row);
            });
        }
        tag.put("types", types);
        // The whole block (schedule + every kind in full) for the admin form editors.
        var season = com.google.gson.JsonParser.parseString(SeasonService.rules(data).toJson()).getAsJsonObject();
        if (season.has("worldEvents")) {
            String rulesJson = season.get("worldEvents").toString();
            // NBT strings are length-prefixed UTF; a block over the limit falls back to the JSON editor.
            if (rulesJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 60_000) {
                tag.putString("rules_json", rulesJson);
            }
        }
        net.minecraft.nbt.ListTag running = new net.minecraft.nbt.ListTag();
        long now = now();
        for (WorldEvent event : data.worldEvents().values()) {
            SeasonRules.WorldEventDef def = def(data, event);
            net.minecraft.nbt.CompoundTag row = new net.minecraft.nbt.CompoundTag();
            row.putInt("id", event.id());
            row.putString("name", def == null || def.name.isBlank() ? event.type() : def.name);
            row.putString("place", placeName(data, event));
            row.putString("progress", def != null && def.goalCount > 0 ? event.progress() + "/" + def.goalCount : "-");
            row.putLong("left", event.secondsLeft(now));
            row.putString("direction", viewer == null ? "" : directionFor(viewer, event));
            running.add(row);
        }
        tag.put("running", running);
        return tag;
    }

    /** Turns automatic world events on or off and writes the season file so it survives a restart. */
    public static void setEnabled(MinecraftServer server, RotasData data, boolean on) {
        SeasonRules.WorldEventRules rules = rules(data);
        if (rules == null) {
            return;
        }
        rules.enabled = on;
        data.setDirty();
        try {
            SeasonConfigFile.write(server, SeasonService.rules(data));
        } catch (java.io.IOException ignored) {
            // The in-memory switch still applies until the next restart.
        }
    }

    /** Removes an event by id, as an administrator's stop. */
    public static boolean stop(MinecraftServer server, RotasData data, int id) {
        WorldEvent event = data.worldEvents().get(id);
        if (event == null) {
            return false;
        }
        expire(server, data, event, "rotasutils.msg.worldevent.stopped");
        return true;
    }

    /** Drops a leaving player from every bar; the next pass would, but a bar should not hold a stale player. */
    public static void forget(ServerPlayer player) {
        for (Iterator<ServerBossEvent> it = BARS.values().iterator(); it.hasNext(); ) {
            it.next().removePlayer(player);
        }
    }
}
