package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.*;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.MobLevelConfig;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public final class MonsterService {
    /** Synthetic profile/tier ids for mobs leveled by {@link MobLevelConfig} alone. */
    public static final ContentId DEFAULT_PROFILE = new ContentId("rotas:default_mob_level");
    public static final ContentId DEFAULT_TIER = new ContentId("rotas:tier/default");
    public record Environment(String region, Map<String, Double> numbers) {
        public Environment { Objects.requireNonNull(region); numbers = Map.copyOf(numbers); }
    }
    private static Function<Mob, Environment> environment = mob -> new Environment("", Map.of("world.tier", 1.0, "dimension.level", 1.0));
    private static boolean environmentFrozen;
    public static synchronized void environment(Function<Mob, Environment> provider) {
        if (environmentFrozen) { throw new IllegalStateException("Register monster environment providers during initialization"); }
        environment = Objects.requireNonNull(provider);
    }
    private record Runtime(Mob mob, MonsterState state) { }
    private record Pending(Mob mob, String reason, boolean disk) { }
    private record Death(Mob mob, MonsterState state, LivingEntity killer) { }
    private final MinecraftServer server;
    private final RpgKernel kernel;
    private final Map<UUID, Runtime> loaded = new HashMap<>();
    private final LinkedHashMap<UUID, Pending> pending = new LinkedHashMap<>();
    private final NavigableMap<Long, LinkedHashSet<UUID>> timers = new TreeMap<>();
    private final Map<UUID, Long> scheduled = new HashMap<>();
    private final java.util.ArrayDeque<Death> deaths = new java.util.ArrayDeque<>();
    /** Recently logged failures, oldest first, so {@link #error} can evict instead of going deaf. */
    private final Set<String> errors = new LinkedHashSet<>();
    /** Attribute names owned per mob and owner key, so a later apply can remove exactly its own work. */
    private final Map<UUID, Map<String, Set<String>>> ownedScales = new HashMap<>();
    /** Loaded mobs that carry a boss definition; the boss service ticks only these. */
    private final LinkedHashSet<UUID> bosses = new LinkedHashSet<>();
    private boolean applying;
    private long assignments, restored, triggers, rejected;
    /** Extra spawns from Mob Setup spawn rules; ticked once a second. */
    private final MobSpawnDirector spawns;
    private int spawnTicks;

    public MonsterService(MinecraftServer server, RpgKernel kernel) {
        this.server = server; this.kernel = kernel;
        this.spawns = new MobSpawnDirector(server, kernel);
        synchronized (MonsterService.class) { environmentFrozen = true; }
    }
    public static MonsterService get(LivingEntity entity) {
        if (entity.level().isClientSide || entity.getServer() == null) { return null; }
        var kernel = RotasData.get(entity.getServer()).kernel(); return kernel == null ? null : kernel.monsters();
    }
    public static MonsterState state(LivingEntity entity) {
        var service = get(entity); return service == null ? null : service.peek(entity);
    }
    public MonsterState peek(LivingEntity entity) {
        var entry = loaded.get(entity.getUUID());
        if (entry != null && entry.mob() == entity) { return entry.state(); }
        return null;
    }
    private void thread() { if (!server.isSameThread()) { throw new IllegalStateException("Monster operations require server thread"); } }
    public Environment environment(Mob mob) { return environment.apply(mob); }
    public RpgKernel kernel() { return kernel; }

    public void enqueue(Mob mob, String reason, boolean disk) {
        thread();
        if (pending.size() >= 8192) { error("spawn-budget", new IllegalStateException("Monster pending spawn budget exceeded")); return; }
        pending.put(mob.getUUID(), new Pending(mob, reason, disk));
    }
    public void join(Mob mob, String reason, boolean disk) {
        thread(); if (mob.isRemoved() || !mob.isAlive() || peek(mob) != null) { return; }
        // Bosses that carry their own level and name are not re-leveled.
        if (mob instanceof net.schwarz.rotasutils.entity.OwnsItsLevel) { return; }
        try {
            var tag = MonsterStorage.read(mob);
            if (!tag.isEmpty()) {
                var state = MonsterState.load(tag); loaded.put(mob.getUUID(), new Runtime(mob, state));
                apply(mob, state); schedule(mob, state); track(mob, state); restored++; return;
            }
            String entity = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            Set<String> tags = mob.getType().builtInRegistryHolder().tags().map(key -> key.location().toString()).collect(java.util.stream.Collectors.toSet());
            String biome = mob.level().getBiome(mob.blockPosition()).unwrapKey().map(key -> key.location().toString()).orElse("");
            Environment env = environment(mob);
            boolean matched = false;
            net.schwarz.rotasutils.core.ZoneDef top = env.region().isEmpty() ? null : RotasData.get(server).zone(env.region());
            for (var profile : kernel.content().monsters().candidates(entity)) {
                if (disk && !profile.applyExisting()) { continue; }
                // A zone that keeps outside mobs out only takes the Mob Setups scoped to it.
                if (!net.schwarz.rotasutils.core.ZoneMobPolicy.profileAllowed(profile.selector().regions(), top)) { continue; }
                if (profile.selector().matches(entity, tags, biome, mob.level().dimension().location().toString(), reason, env.region())) {
                    assign(mob, profile.id(), null, reason); matched = true; break;
                }
            }
            // No authored profile matched: every mob still levels from its zone or the spawn ramp.
            if (!matched) { assignDefault(mob, entity, tags, reason); }
        } catch (RuntimeException failure) { error("join:" + mob.getType(), failure); }
    }

    /**
     * Levels a mob that has no matching monster profile from the universal {@link MobLevelConfig}.
     *
     * <p>Kept beside {@link #assign} on purpose: the profile path owns tiers, affixes, loot and
     * rewards, while this path only owns a level band, per-level health/damage and a nameplate. The
     * two share persistence, attribute application and the spawn event, so a default mob behaves
     * like any other Rotas monster to the rest of the system (storage, clear, kill XP, receipts).</p>
     */
    public MonsterState assignDefault(Mob mob, String entity, Set<String> tags, String reason) {
        thread();
        if (!mob.isAlive() || mob.isRemoved()) { throw new IllegalArgumentException("Monster must be alive and loaded"); }
        MonsterState existing = peek(mob);
        if (existing != null) { return existing; }
        MobLevelConfig config = RotasData.get(server).levelConfig().mobLevel();
        boolean tamed = (mob instanceof net.minecraft.world.entity.TamableAnimal animal && animal.isTame())
                || (mob instanceof net.minecraft.world.entity.OwnableEntity ownable && ownable.getOwnerUUID() != null)
                || (mob instanceof net.minecraft.world.entity.TraceableEntity traceable && traceable.getOwner() != null);
        if (!config.shouldLevel(entity, tags, mob.getType().getCategory().getName(), tamed)) { return null; }
        Environment env = environment(mob);
        // Safe zones (towns, hubs) do not level new mobs; a mob that wanders in keeps what it has.
        if (env.numbers().getOrDefault("region.safe", 0.0) >= 0.5) { return null; }
        ServerPlayer nearest = nearest(mob);
        int regionMin = (int) Math.round(env.numbers().getOrDefault("region.min", (double) config.spawnLevel()));
        int regionMax = (int) Math.round(env.numbers().getOrDefault("region.max", (double) config.maxLevel()));
        // A world event running here makes what spawns in it tougher; the band itself is the zone's.
        var event = WorldEventService.at(mob);
        int bonus = event == null ? 0 : event.mobLevelBonus;
        int level = MonsterLevels.natural(Math.max(1, MonsterThreat.levelInBand(mob.getUUID(), regionMin, regionMax) + bonus),
                config.maxLevel());
        // A naturally spawned mob may be promoted. Spawners, eggs and commands never are, so an elite
        // can be met in the wild but never farmed from a cage.
        MonsterRank rank = FarmingService.rollElite(RotasData.get(server), mob, reason);
        var farming = SeasonService.rules(RotasData.get(server)).farming;
        List<MobAffix> affixes = MobAffixService.roll(mob, rank, level, farming);
        List<Map<String, MonsterDefinitions.Scale>> layers = naturalLayers(config, farming, rank, affixes,
                event == null ? 1.0 : event.mobHealth, event == null ? 1.0 : event.mobDamage);
        // Weak mobs keep the configured floor, but tough and modded monsters still pay their
        // threat-rated XP, so leveling a boss never shrinks its reward to a pittance. The level
        // adds a mild bonus on top so a higher level is worth more.
        long threat = MonsterXpService.calculate(mob, RotasData.get(server).levelConfig(), false);
        double levelBonus = 1.0 + 0.03 * Math.max(0, level - 1);
        double xp = Math.max(config.baseXp() + config.xpPerLevel() * (level - 1), threat * levelBonus);
        String original = mob.hasCustomName() ? Component.Serializer.toJson(mob.getCustomName()) : "";
        String name = config.nameVisible() ? config.formatName(mob.getName().getString(), level) : "";
        name = rankedName(name, mob.getName().getString(), rank);
        xp *= rankXp(farming, rank);
        for (MobAffix affix : affixes) {
            xp *= affix.xpMultiplier();
        }
        MonsterState state = new MonsterState(DEFAULT_PROFILE, DEFAULT_TIER, level,
                Math.max(0, Math.min(1000000000, Math.round(xp))), 1.0, false,
                affixes.stream().map(MobAffix::id).toList(),
                MonsterDefinitions.derive(level, layers), null, name, original,
                MonsterAssignmentSource.NATURAL, rank, MonsterTypes.infer(entity), null);
        MonsterStorage.write(mob, state.save()); loaded.put(mob.getUUID(), new Runtime(mob, state)); pending.remove(mob.getUUID());
        apply(mob, state); schedule(mob, state); track(mob, state); assignments++;
        if (rank != MonsterRank.NORMAL) {
            FarmingService.marked(mob, rank, RotasData.get(server));
        }
        trigger(mob, nearest, MonsterDefinitions.Trigger.SPAWN, Map.of("event.spawn_reason", reason));
        kernel.events().emit(new KernelEventBus.Event(new ContentId("rotas:entity_spawned"), mob.getUUID().toString(),
                new MonsterContext(this, mob, state, nearest, Map.of())));
        return state;
    }

    public MonsterState assign(Mob mob, ContentId id, Integer levelOverride, String reason) {
        thread();
        if (!mob.isAlive() || mob.isRemoved()) { throw new IllegalArgumentException("Monster must be alive and loaded"); }
        var catalog = kernel.content().monsters(); var profile = catalog.profiles().get(id);
        if (profile == null) { throw new IllegalArgumentException("Unknown or disabled monster profile: " + id); }
        MonsterState previous = peek(mob);
        if (previous != null) { throw new IllegalStateException("Monster is already assigned; clear explicitly before reassignment"); }
        var random = new SplittableRandom(mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits());
        var tier = catalog.tiers().get(MonsterDefinitions.weighted(new TreeMap<>(profile.tiers()), random));
        ServerPlayer nearest = nearest(mob);
        Environment env = environment(mob);
        Map<String, Double> facts = new HashMap<>(env.numbers());
        facts.put("player.level", nearest == null ? (double) profile.level().min() : (double) RotasData.get(server).progress(nearest.getUUID()).level());
        facts.put("player.party_level", nearest == null ? facts.get("player.level") : partyLevel(nearest));
        facts.put("player.party_size", nearest == null ? 1 : (double) partySize(nearest));
        facts.put("monster.tier", (double) tier.rank()); facts.put("monster.base_xp", (double) profile.baseXp());
        facts.put("server.xp_multiplier", 1.0); facts.put("event.xp_multiplier", 1.0);
        int level = levelOverride == null ? profile.level().choose(name -> facts.getOrDefault(name, Double.NaN), random) : levelOverride;
        if (level < profile.level().min() || level > profile.level().max()) { throw new IllegalArgumentException("Override is outside profile level bounds"); }
        var provisional = new MonsterState(id, tier.id(), level, 0, tier.lootMultiplier(), tier.boss(), List.of(), Map.of(), profile.reward(), "", "",
                MonsterAssignmentSource.CONFIGURED, MonsterRank.infer(tier.id(), tier.boss()), MonsterType.UNKNOWN, id);
        var context = new MonsterContext(this, mob, provisional, nearest, Map.of("event.spawn_reason", reason));
        List<MonsterDefinitions.Affix> chosen = new ArrayList<>();
        Map<ContentId, Integer> candidates = new TreeMap<>();
        String entity = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        Set<String> entityTags = mob.getType().builtInRegistryHolder().tags().map(key -> key.location().toString()).collect(java.util.stream.Collectors.toSet());
        String biome = mob.level().getBiome(mob.blockPosition()).unwrapKey().map(key -> key.location().toString()).orElse("");
        for (ContentId affixId : profile.affixes()) {
            var affix = catalog.affixes().get(affixId);
            if (level >= affix.minLevel() && tier.rank() >= affix.minTier() && affix.condition().test(context)
                    && affix.selector().matches(entity, entityTags, biome, mob.level().dimension().location().toString(), reason, env.region())) {
                candidates.put(affixId, affix.weight());
            }
        }
        while (chosen.size() < tier.affixCount() && !candidates.isEmpty()) {
            ContentId selected = MonsterDefinitions.weighted(candidates, random); candidates.remove(selected);
            var affix = catalog.affixes().get(selected); if (affix.compatible(chosen)) { chosen.add(affix); }
        }
        List<Map<String, MonsterDefinitions.Scale>> layers = new ArrayList<>(); layers.add(profile.attributes()); layers.add(tier.attributes());
        double xp = (profile.baseXp() + profile.xpPerLevel() * (level - 1)) * tier.xpMultiplier();
        for (var affix : chosen) { layers.add(affix.attributes()); xp = Math.min(1000000000, xp * affix.xpMultiplier()); }
        String original = mob.hasCustomName() ? Component.Serializer.toJson(mob.getCustomName()) : "";
        String name = profile.name().isEmpty() ? "" : profile.name().replace("{name}", mob.getName().getString()).replace("{level}", Integer.toString(level)).replace("{tier}", tier.label());
        MonsterState state = new MonsterState(id, tier.id(), level, Math.max(0, Math.min(1000000000, Math.round(xp))), tier.lootMultiplier(), tier.boss(),
                chosen.stream().map(MonsterDefinitions.Affix::id).toList(), MonsterDefinitions.derive(level, layers), profile.reward(), name, original,
                MonsterAssignmentSource.CONFIGURED, MonsterRank.infer(tier.id(), tier.boss()), MonsterTypes.infer(entity), id);
        MonsterStorage.write(mob, state.save()); loaded.put(mob.getUUID(), new Runtime(mob, state)); pending.remove(mob.getUUID());
        apply(mob, state); schedule(mob, state); track(mob, state); assignments++;
        trigger(mob, nearest, MonsterDefinitions.Trigger.SPAWN, Map.of("event.spawn_reason", reason));
        kernel.events().emit(new KernelEventBus.Event(new ContentId("rotas:entity_spawned"), mob.getUUID().toString(), new MonsterContext(this, mob, state, nearest, Map.of())));
        return state;
    }

    /**
     * Re-derives an already-assigned mob at a new level, keeping its profile, tier, affixes, reward
     * and boss runtime intact. Until this existed a level could only be changed with clear + assign,
     * which dropped the mob's stored identity. {@code clampToBand} keeps the new level inside the
     * profile's band; the zone path passes false because the caller already resolved the band.
     */
    public MonsterState relevel(Mob mob, int requested, boolean clampToBand) {
        thread();
        MonsterState state = peek(mob);
        if (state == null) { return null; }
        var catalog = kernel.content().monsters();
        var profile = catalog.profiles().get(state.profile());
        MobLevelConfig config = RotasData.get(server).levelConfig().mobLevel();
        int min = 1, max = MonsterLevels.ABSOLUTE_MAX;
        List<Map<String, MonsterDefinitions.Scale>> layers = new ArrayList<>();
        double xp;
        String nameTemplate;
        if (profile != null) {
            min = profile.level().min();
            max = profile.level().max();
            layers.add(profile.attributes());
            var tier = catalog.tiers().get(state.tier());
            if (tier != null) { layers.add(tier.attributes()); }
            xp = (profile.baseXp() + profile.xpPerLevel() * (requested - 1)) * (tier == null ? 1.0 : tier.xpMultiplier());
            for (ContentId affixId : state.affixes()) {
                var affix = catalog.affixes().get(affixId);
                if (affix == null) { continue; }
                layers.add(affix.attributes());
                xp = Math.min(1000000000, xp * affix.xpMultiplier());
            }
            nameTemplate = profile.name();
        } else {
            max = config.maxLevel();
            layers.addAll(naturalLayers(config, SeasonService.rules(RotasData.get(server)).farming, state.rank(),
                    MobAffix.of(state.affixes()), 1.0, 1.0));
            // Keep the threat-rated part of the stored reward and scale it with the new level, so
            // re-leveling a boss does not discard the XP it earned from its own stats.
            long oldFloor = config.baseXp() + Math.round(config.xpPerLevel() * Math.max(0, state.level() - 1));
            long newFloor = config.baseXp() + Math.round(config.xpPerLevel() * Math.max(0, requested - 1));
            xp = oldFloor <= 0 ? newFloor : Math.max(newFloor, state.xp() * (double) newFloor / oldFloor);
            nameTemplate = config.nameVisible() ? config.nameFormat() : "";
        }
        int level = state.source() == MonsterAssignmentSource.NATURAL
                ? MonsterLevels.natural(requested, config.maxLevel())
                : clampToBand ? Math.max(min, Math.min(max, requested)) : MonsterLevels.configured(requested);
        var tier = catalog.tiers().get(state.tier());
        String tierLabel = tier == null ? "" : tier.label();
        String name = nameTemplate.isEmpty() ? "" : nameTemplate.replace("{name}", originalName(mob, state))
                .replace("{level}", Integer.toString(level)).replace("{tier}", tierLabel);
        if (profile == null) {
            name = rankedName(name, originalName(mob, state), state.rank());
        }
        MonsterState next = new MonsterState(state.profile(), state.tier(), level,
                Math.max(0, Math.min(1000000000, Math.round(xp))), state.lootMultiplier(), state.boss(),
                state.affixes(), MonsterDefinitions.derive(level, layers), state.reward(), name, state.originalName(),
                state.source(), state.rank(), state.monsterType(), state.customId());
        next.runtime(state.runtime().copy());
        // Carry the payout latch across a relevel. Without it a mob that already paid its kill or
        // boss reward and is then re-leveled (zone wand, /monster level) pays a second time.
        next.rewarded(state.rewarded());
        loaded.put(mob.getUUID(), new Runtime(mob, next));
        persist(mob, next); apply(mob, next); schedule(mob, next); track(mob, next);
        return next;
    }

    /**
     * Gives an assigned mob a new plate name and keeps it in the stored state, so a later relevel, a
     * clear or a reload recognises the name as Rotas' own instead of treating it as another mod's.
     */
    public MonsterState rename(Mob mob, String name) {
        thread();
        MonsterState state = peek(mob);
        if (state == null || name == null || name.length() > 512) { return null; }
        if (!name.equals(state.name())) {
            MonsterState next = new MonsterState(state.profile(), state.tier(), state.level(), state.xp(),
                    state.lootMultiplier(), state.boss(), state.affixes(), state.attributes(), state.reward(), name,
                    state.originalName(), state.source(), state.rank(), state.monsterType(), state.customId());
            next.runtime(state.runtime());
            next.rewarded(state.rewarded());
            loaded.put(mob.getUUID(), new Runtime(mob, next));
            persist(mob, next);
            state = next;
        }
        if (!mob.hasCustomName() || !name.equals(mob.getCustomName().getString())) {
            mob.setCustomName(styledName(state));
        }
        mob.setCustomNameVisible(true);
        return state;
    }

    /** The mob's un-leveled name, preferring the name captured before Rotas renamed it. */
    private static String originalName(Mob mob, MonsterState state) {
        if (!state.originalName().isEmpty()) {
            try {
                var component = Component.Serializer.fromJson(state.originalName());
                if (component != null) { return component.getString(); }
            } catch (RuntimeException ignored) {
                // A corrupt stored name falls through to the live entity name.
            }
        }
        if (mob.hasCustomName() && mob.getCustomName().getString().equals(state.name())) {
            return mob.getType().getDescription().getString();
        }
        return mob.getName().getString();
    }

    /**
     * Attribute layers for a naturally leveled mob: the per-level curve, the rank's multipliers (and a
     * world event's, at spawn), then each affix's own stats. Relevel rebuilds the same stack, so a
     * re-leveled elite stays an elite instead of shrinking back to a plain mob.
     */
    private static List<Map<String, MonsterDefinitions.Scale>> naturalLayers(MobLevelConfig config,
            net.schwarz.rotasutils.level.SeasonRules.FarmingRules farming, MonsterRank rank, List<MobAffix> affixes,
            double eventHealth, double eventDamage) {
        double healthScale = Math.max(0.1, Math.min(100, rankHealth(farming, rank) * eventHealth));
        double damageScale = Math.max(0.1, Math.min(100, rankDamage(farming, rank) * eventDamage));
        Map<String, MonsterDefinitions.Scale> scales = new TreeMap<>();
        if (config.healthPerLevel() > 0 || healthScale != 1.0) {
            scales.put("minecraft:generic.max_health",
                    new MonsterDefinitions.Scale(healthScale, Math.max(0, config.healthPerLevel()), 0));
        }
        if (config.damagePerLevel() > 0 || damageScale != 1.0) {
            scales.put("minecraft:generic.attack_damage",
                    new MonsterDefinitions.Scale(damageScale, Math.max(0, config.damagePerLevel()), 0));
        }
        List<Map<String, MonsterDefinitions.Scale>> layers = new ArrayList<>();
        layers.add(scales);
        for (MobAffix affix : affixes) {
            layers.add(affix.attributes());
        }
        return layers;
    }

    private static double rankHealth(net.schwarz.rotasutils.level.SeasonRules.FarmingRules f, MonsterRank rank) {
        if (f == null || rank == null) return 1.0;
        return switch (rank) {
            case VETERAN -> f.veteranHealth;
            case ELITE -> f.eliteHealth;
            case CHAMPION -> f.championHealth;
            default -> 1.0;
        };
    }

    private static double rankDamage(net.schwarz.rotasutils.level.SeasonRules.FarmingRules f, MonsterRank rank) {
        if (f == null || rank == null) return 1.0;
        return switch (rank) {
            case VETERAN -> f.veteranDamage;
            case ELITE -> f.eliteDamage;
            case CHAMPION -> f.championDamage;
            default -> 1.0;
        };
    }

    private static double rankXp(net.schwarz.rotasutils.level.SeasonRules.FarmingRules f, MonsterRank rank) {
        if (f == null || rank == null) return 1.0;
        return switch (rank) {
            case VETERAN -> f.veteranXp;
            case ELITE -> f.eliteXp;
            case CHAMPION -> f.championXp;
            default -> 1.0;
        };
    }

    /** {@code "[Elite] Zombie Lv 12"}: the rank word leads, so a plain nameplate still warns. */
    private static String rankedName(String name, String fallback, MonsterRank rank) {
        if (rank != MonsterRank.VETERAN && rank != MonsterRank.ELITE && rank != MonsterRank.CHAMPION) {
            return name;
        }
        return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.monster.rank_prefix." + rank.key()) + " "
                + (name.isBlank() ? fallback : name);
    }

    /**
     * The plate name as a component: rank-coloured, carrying the rank and affixes as a hidden mark the
     * client reads for the plate and target frame (see {@link MobAffix#encode}).
     */
    static Component styledName(MonsterState state) {
        MonsterRank rank = state.rank() == null ? MonsterRank.NORMAL : state.rank();
        List<MobAffix> affixes = MobAffix.of(state.affixes());
        var component = Component.literal(state.name());
        if (rank == MonsterRank.NORMAL && affixes.isEmpty()) {
            return component;
        }
        return component.withStyle(style -> style
                .withColor(rank == MonsterRank.NORMAL ? null : net.minecraft.network.chat.TextColor.fromRgb(rank.rgb()))
                .withInsertion(MobAffix.encode(rank, affixes)));
    }

    /** True when the mob's current custom name is one Rotas gave it, so it may be replaced. */
    private static boolean ownsName(Mob mob, MonsterState state) {
        if (!mob.hasCustomName()) return true;
        Component current = mob.getCustomName();
        if (Component.Serializer.toJson(current).equals(state.originalName())) return true;
        String insertion = current.getStyle().getInsertion();
        return (insertion != null && insertion.startsWith(MobAffix.MARK)) || current.getString().equals(state.name());
    }

    private ServerPlayer nearest(Mob mob) {
        ServerPlayer result = null; double distance = 64 * 64;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != mob.level() || player.isSpectator() || !player.isAlive()) { continue; }
            double squared = player.distanceToSqr(mob); if (squared < distance) { result = player; distance = squared; }
        }
        return result;
    }
    /**
     * How far a party member counts as "here" for monster scaling.
     *
     * <p>Reads the same server setting the party, quest and XP-share code uses. This was hardcoded
     * to 64 blocks, so raising or lowering the party radius changed who shared the XP but not who
     * the monster scaled against.</p>
     */
    private double partyRadiusSquared() {
        double radius = RotasData.get(server).serverSettings().partyNearbyRadius();
        return radius * radius;
    }
    private double partyLevel(ServerPlayer player) {
        var data = RotasData.get(server); UUID party = data.progress(player.getUUID()).partyId();
        if (party == null) { return data.progress(player.getUUID()).level(); }
        double sum = 0; int count = 0; double radiusSquared = partyRadiusSquared();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.level() == player.level() && other.distanceToSqr(player) <= radiusSquared && party.equals(data.progress(other.getUUID()).partyId())) { sum += data.progress(other.getUUID()).level(); count++; }
        }
        return count == 0 ? data.progress(player.getUUID()).level() : sum / count;
    }
    private int partySize(ServerPlayer player) {
        var data = RotasData.get(server); UUID party = data.progress(player.getUUID()).partyId();
        if (party == null) { return 1; }
        int count = 0; double radiusSquared = partyRadiusSquared();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.level() == player.level() && other.distanceToSqr(player) <= radiusSquared && party.equals(data.progress(other.getUUID()).partyId())) { count++; }
        }
        return Math.max(1, count);
    }
    private static UUID modifier(String attribute, String operation) {
        return UUID.nameUUIDFromBytes(("rotasutils.monster/" + attribute + "/" + operation).getBytes(StandardCharsets.UTF_8));
    }
    private void apply(Mob mob, MonsterState state) {
        float beforeMax = mob.getMaxHealth(), health = mob.getHealth();
        state.attributes().forEach((id, scale) -> {
            var attribute = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(id));
            if (attribute == null) { throw new IllegalArgumentException("Stored monster attribute missing: " + id); }
            var instance = mob.getAttribute(attribute); if (instance == null) { return; }
            UUID multiply = modifier(id, "scale"), add = modifier(id, "add"); instance.removeModifier(multiply); instance.removeModifier(add);
            instance.addTransientModifier(new AttributeModifier(multiply, "rotasutils.monster.scale", scale.multiplier() - 1, AttributeModifier.Operation.MULTIPLY_BASE));
            instance.addTransientModifier(new AttributeModifier(add, "rotasutils.monster.add", scale.add(), AttributeModifier.Operation.ADDITION));
        });
        if (mob.isAlive()) { mob.setHealth(Math.min(mob.getMaxHealth(), beforeMax <= 0 ? health : health / beforeMax * mob.getMaxHealth())); }
        if (!state.name().isEmpty() && ownsName(mob, state)) {
            mob.setCustomName(styledName(state));
            // Without this the generated "Name [Lv N]" only shows when the mob is the crosshair
            // target, which is exactly when the player can already read a nameplate elsewhere.
            mob.setCustomNameVisible(true);
        }
    }
    public void clear(Mob mob) {
        thread(); var state = peek(mob); if (state == null) { return; }
        state.attributes().forEach((id, scale) -> {
            var attribute = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(id)); if (attribute == null) { return; }
            var instance = mob.getAttribute(attribute); if (instance != null) { instance.removeModifier(modifier(id, "scale")); instance.removeModifier(modifier(id, "add")); }
        });
        if (mob.getHealth() > mob.getMaxHealth()) { mob.setHealth(mob.getMaxHealth()); }
        if (!state.name().isEmpty() && mob.hasCustomName() && mob.getCustomName().getString().equals(state.name())) {
            mob.setCustomName(state.originalName().isEmpty() ? null : Component.Serializer.fromJson(state.originalName()));
            mob.setCustomNameVisible(false);
        }
        MonsterStorage.write(mob, new net.minecraft.nbt.CompoundTag()); forget(mob);
    }
    public void forget(Mob mob) {
        thread();
        var entry = loaded.get(mob.getUUID());
        // Only forget the bookkeeping if this is still the live instance. A dimension change spawns
        // the new copy before the old one leaves, and wiping by UUID alone tore the boss tracking and
        // owned-scale records out from under the surviving mob - after which applyOwnedScales could
        // no longer remove its own modifiers and the boss simply stopped ticking.
        if (entry == null || entry.mob() == mob) {
            bosses.remove(mob.getUUID()); ownedScales.remove(mob.getUUID());
            loaded.remove(mob.getUUID()); unschedule(mob.getUUID());
        }
        var queued = pending.get(mob.getUUID()); if (queued != null && queued.mob() == mob) { pending.remove(mob.getUUID()); }
    }
    private void unschedule(UUID id) {
        Long time = scheduled.remove(id); if (time == null) { return; }
        Set<UUID> group = timers.get(time); if (group != null) { group.remove(id); if (group.isEmpty()) { timers.remove(time); } }
    }
    private void schedule(Mob mob, MonsterState state) {
        unschedule(mob.getUUID()); long now = server.overworld().getGameTime(), due = Long.MAX_VALUE;
        var runtime = state.runtime(); boolean changed = false;
        for (ContentId id : state.affixes()) {
            var affix = kernel.content().monsters().affixes().get(id); if (affix == null) { continue; }
            for (var hook : affix.hooks()) {
                if (hook.trigger() != MonsterDefinitions.Trigger.INTERVAL) { continue; }
                String key = "cooldown:" + id + ":INTERVAL";
                if (!runtime.contains(key)) { runtime.putLong(key, now + hook.interval()); changed = true; }
                due = Math.min(due, Math.max(now + 1, runtime.getLong(key)));
            }
        }
        if (changed) { state.runtime(runtime); persist(mob, state); }
        if (due == Long.MAX_VALUE) { return; }
        timers.computeIfAbsent(due, ignored -> new LinkedHashSet<>()).add(mob.getUUID()); scheduled.put(mob.getUUID(), due);
    }
    /**
     * Applies one owner's scaled attribute modifiers, replacing whatever that owner applied before.
     * Boss phases and enrage use this so their modifiers never stack with each other or with the
     * profile/tier/affix modifiers applied at assignment.
     */
    public void applyOwnedScales(Mob mob, String owner, Map<String, MonsterDefinitions.Scale> scales) {
        thread();
        Map<String, Set<String>> perMob = ownedScales.computeIfAbsent(mob.getUUID(), key -> new HashMap<>());
        for (String attribute : perMob.getOrDefault(owner, Set.of())) { removeOwned(mob, owner, attribute); }
        perMob.remove(owner);
        var state = peek(mob);
        int level = state == null ? 1 : state.level();
        Set<String> applied = new LinkedHashSet<>();
        scales.forEach((id, scale) -> {
            var attribute = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(id));
            if (attribute == null) { throw new IllegalArgumentException("Unknown owned attribute: " + id); }
            var instance = mob.getAttribute(attribute);
            if (instance == null) { return; }
            UUID multiply = owned(owner, id, "scale"), add = owned(owner, id, "add");
            instance.removeModifier(multiply); instance.removeModifier(add);
            instance.addTransientModifier(new AttributeModifier(multiply, "rotasutils." + owner + ".scale",
                    scale.at(level) - 1, AttributeModifier.Operation.MULTIPLY_BASE));
            instance.addTransientModifier(new AttributeModifier(add, "rotasutils." + owner + ".add",
                    scale.add(), AttributeModifier.Operation.ADDITION));
            applied.add(id);
        });
        if (!applied.isEmpty()) { perMob.put(owner, Set.copyOf(applied)); }
        if (mob.isAlive() && mob.getHealth() > mob.getMaxHealth()) { mob.setHealth(mob.getMaxHealth()); }
    }

    /**
     * True while the live entity still carries an owner's modifier on an attribute. Read from the entity
     * rather than the bookkeeping: transient modifiers do not survive an unload or a dimension change,
     * and the owner has to know when to put them back.
     */
    public static boolean carriesOwned(Mob mob, String owner, String attribute) {
        var type = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(attribute));
        var instance = type == null ? null : mob.getAttribute(type);
        return instance != null && instance.getModifier(owned(owner, attribute, "scale")) != null;
    }

    private void removeOwned(Mob mob, String owner, String id) {
        var attribute = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(id));
        if (attribute == null) { return; }
        var instance = mob.getAttribute(attribute);
        if (instance != null) { instance.removeModifier(owned(owner, id, "scale")); instance.removeModifier(owned(owner, id, "add")); }
    }

    private static UUID owned(String owner, String attribute, String kind) {
        return UUID.nameUUIDFromBytes(("rotasutils." + owner + "/" + attribute + "/" + kind).getBytes(StandardCharsets.UTF_8));
    }

    private void track(Mob mob, MonsterState state) {
        var profile = kernel.content().monsters().profiles().get(state.profile());
        if (profile != null && profile.boss() != null && bosses.size() < 256) { bosses.add(mob.getUUID()); }
    }

    /** Aura, regeneration and fuse warnings for every loaded mob with a rank or a built-in affix. */
    private void tickAffixes() {
        var farming = SeasonService.rules(RotasData.get(server)).farming;
        for (Runtime entry : List.copyOf(loaded.values())) {
            MonsterState state = entry.state();
            if ((state.rank() == null || state.rank() == MonsterRank.NORMAL) && state.affixes().isEmpty()) continue;
            if (entry.mob().isRemoved()) continue;
            MobAffixService.tick(entry.mob(), state, farming);
        }
    }

    public void tick() {
        thread();
        if (spawnTicks % 10 == 0) {
            try { tickAffixes(); } catch (RuntimeException failure) { error("affix-tick", failure); }
        }
        if (++spawnTicks >= 20) {
            spawnTicks = 0;
            try { spawns.tick(); } catch (RuntimeException failure) { error("spawn-director", failure); }
            try { kernel.encounters().tick(); } catch (RuntimeException failure) { error("zone-encounters", failure); }
        }
        for (UUID id : List.copyOf(bosses)) {
            var entry = loaded.get(id);
            if (entry == null || entry.mob().isRemoved() || !entry.mob().isAlive()) { bosses.remove(id); continue; }
            kernel.bosses().tick(entry.mob(), entry.state());
        }
        for (int i = 0; i < 128 && !deaths.isEmpty(); i++) {
            var death = deaths.removeFirst();
            trigger(death.mob(), death.state(), death.killer(), MonsterDefinitions.Trigger.DEATH, Map.of());
        }
        for (int i = 0; i < 256 && !pending.isEmpty(); i++) {
            var iterator = pending.entrySet().iterator(); var entry = iterator.next().getValue(); iterator.remove(); join(entry.mob(), entry.reason(), entry.disk());
        }
        long now = server.overworld().getGameTime();
        for (int i = 0; i < 128 && !timers.isEmpty() && timers.firstKey() <= now; i++) {
            var group = timers.firstEntry(); UUID id = group.getValue().iterator().next(); unschedule(id);
            var entry = loaded.get(id); if (entry == null) { continue; }
            if (entry.mob().isRemoved() || !entry.mob().isAlive()) { forget(entry.mob()); continue; }
            trigger(entry.mob(), entry.mob().getTarget(), MonsterDefinitions.Trigger.INTERVAL, Map.of()); schedule(entry.mob(), entry.state());
        }
    }
    public void contentReloaded() {
        thread(); for (var entry : loaded.values()) { schedule(entry.mob(), entry.state()); }
    }
    public void trigger(Mob mob, LivingEntity target, MonsterDefinitions.Trigger event, Map<String, String> facts) {
        thread(); if (applying) { return; } var state = peek(mob); if (state == null) { return; }
        trigger(mob, state, target, event, facts);
    }
    private void trigger(Mob mob, MonsterState state, LivingEntity target, MonsterDefinitions.Trigger event, Map<String, String> facts) {
        long now = server.overworld().getGameTime();
        kernel.bosses().handle(mob, state, target, event, facts);
        for (ContentId id : state.affixes()) {
            var affix = kernel.content().monsters().affixes().get(id); if (affix == null) { continue; }
            for (var hook : affix.hooks()) {
                if (hook.trigger() != event) { continue; }
                String key = "cooldown:" + id + ":" + event;
                if (state.runtime().getLong(key) > now) { continue; }
                var context = new MonsterContext(this, mob, state, target, facts);
                try {
                    if (!hook.condition().test(context)) {
                        if (event == MonsterDefinitions.Trigger.INTERVAL) {
                            var runtime = state.runtime(); runtime.putLong(key, now + hook.interval()); state.runtime(runtime); persist(mob, state);
                        }
                        continue;
                    }
                    try (var transaction = context.begin()) {
                        transaction.cooldown(key, now + Math.max(hook.cooldown(), event == MonsterDefinitions.Trigger.INTERVAL ? hook.interval() : 1));
                        for (var action : hook.actions()) { action.stage(transaction); }
                        applying = true; transaction.commit(); triggers++;
                    } finally { applying = false; }
                } catch (RuntimeException failure) { error("affix:" + id, failure); }
            }
        }
    }
    public boolean confirmDeath(Mob mob, LivingEntity killer) {
        thread(); var state = peek(mob); if (state == null || state.rewarded()) { return false; }
        state.rewarded(true); persist(mob, state); unschedule(mob.getUUID());
        // Boss payouts reach every qualifying contributor, not only the player who struck last.
        try { kernel.bosses().reward(mob, state); } catch (RuntimeException failure) { error("boss-payout", failure); }
        if (applying) {
            if (deaths.size() < 4096) { deaths.addLast(new Death(mob, state, killer)); }
            else { error("death-budget", new IllegalStateException("Deferred monster death budget exceeded")); }
        } else { trigger(mob, state, killer, MonsterDefinitions.Trigger.DEATH, Map.of()); }
        return true;
    }
    public void grantKillReward(Mob mob, ServerPlayer killer) {
        var state = peek(mob); if (state == null) { return; }
        if (state.reward() != null) {
            try { kernel.grant(killer, state.reward(), "monster:" + mob.getUUID()); }
            catch (RuntimeException failure) { error("monster-reward:" + state.reward(), failure); }
        }
        var profile = kernel.content().monsters().profiles().get(state.profile());
        if (profile != null && profile.loot() != null) {
            // The tier loot multiplier scales the rolled count; the receipt keeps a retry honest.
            try { LootService.grantOnce(killer, RotasData.get(server), profile.loot(), mob.getUUID().toString(), state.level(), state.lootMultiplier()); }
            catch (RuntimeException failure) { error("monster-loot:" + profile.loot(), failure); }
        }
        // Rank drops come last so an authored table, when there is one, is rolled first.
        try { DropService.onMonsterKilled(killer, mob, state); }
        catch (RuntimeException failure) { error("monster-drop", failure); }
        RpgKernel.emit(killer, "rotas:monster_defeated", mob.getUUID().toString(), Map.of("event.monster_profile", state.profile().value(),
                "event.monster_level", Integer.toString(state.level()), "event.monster_tier", state.tier().value()));
    }
    public void persist(Mob mob, MonsterState state) { MonsterStorage.write(mob, state.save()); }
    public void close() { loaded.clear(); pending.clear(); bosses.clear(); ownedScales.clear(); timers.clear(); scheduled.clear(); deaths.clear(); errors.clear(); }
    public String diagnostics() { return "Monsters loaded=" + loaded.size() + " bosses=" + bosses.size() + " pending=" + pending.size() + " scheduled=" + scheduled.size()
            + " assigned=" + assignments + " restored=" + restored + " triggers=" + triggers + " rejected=" + rejected; }
    private void error(String id, RuntimeException failure) {
        // De-duplicates identical failures so one broken profile cannot spam the log every tick.
        // The set evicts its oldest entry instead of refusing new ones: a hard 64 cap meant that
        // after 64 distinct failures every later monster error was silently swallowed for the rest
        // of the server's life, including ones from a completely unrelated pack change.
        rejected++;
        if (errors.add(id + ":" + failure.getMessage())) {
            if (errors.size() > 64) { var oldest = errors.iterator(); oldest.next(); oldest.remove(); }
            Rotasutils.LOG.error("RPG monster {}: {}", id, failure.getMessage(), failure);
        }
    }
}
