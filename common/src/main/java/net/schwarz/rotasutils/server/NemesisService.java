package net.schwarz.rotasutils.server;

import dev.architectury.event.EventResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.DropGrade;
import net.schwarz.rotasutils.core.MonsterDefinitions;
import net.schwarz.rotasutils.core.MonsterLevels;
import net.schwarz.rotasutils.core.MonsterRank;
import net.schwarz.rotasutils.core.MonsterState;
import net.schwarz.rotasutils.core.NemesisMath;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.item.ItemRefine;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.nemesis.Nemesis;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.title.TitleCounters;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class NemesisService {
    public static final String TAG_PREFIX = "rotas_nemesis:";
    private static final String OWNER = "nemesis";
    private static final String HEALTH = "minecraft:generic.max_health";
    private static final String DAMAGE = "minecraft:generic.attack_damage";
    private static final String LAST_RISE = "rpg.nemesis.last_rise";

    private static final Map<String, Long> TAUNTS = new HashMap<>();
    private static int sweepTicks;

    private NemesisService() {
    }

    public static void clear() {
        TAUNTS.clear();
        sweepTicks = 0;
    }

    private static SeasonRules.NemesisRules rules(RotasData data) {
        return SeasonService.rules(data).nemesis;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    public static int idOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(TAG_PREFIX.length()));
                } catch (NumberFormatException malformed) {
                    return -1;
                }
            }
        }
        return 0;
    }

    public static EventResult onAdd(Entity entity, Level level) {
        if (level.isClientSide || level.getServer() == null) {
            return EventResult.pass();
        }
        int id = idOf(entity);
        if (id == 0) {
            return EventResult.pass();
        }
        Nemesis nemesis = id < 0 ? null : RotasData.get(level.getServer()).nemesis(id);
        return nemesis != null && entity.getUUID().equals(nemesis.body()) ? EventResult.pass() : EventResult.interruptFalse();
    }

public static void onPlayerKilled(ServerPlayer victim, DamageSource source) {
        RotasData data = RotasData.get(victim.server);
        SeasonRules.NemesisRules rules = rules(data);
        if (rules == null || !rules.enabled || data.kernel() == null
                || !(source.getEntity() instanceof Mob mob) || !mob.isAlive() || mob.level().isClientSide) {
            return;
        }
        int id = idOf(mob);
        if (id != 0) {
            Nemesis existing = id < 0 ? null : data.nemesis(id);
            if (existing != null && mob.getUUID().equals(existing.body())) {
                grow(victim, mob, existing, data, rules);
            }
            return;
        }
        MonsterState state = data.kernel().monsters().peek(mob);
        var progress = data.progress(victim.getUUID());
        long lastRise = TitleCounters.read(progress.questVariables(), LAST_RISE);
        if (!eligible(mob, state) || data.nemeses().size() >= rules.maxActive
                || hunting(data, victim.getUUID()) >= rules.maxPerPlayer
                || now() - lastRise < rules.riseCooldownMinutes * 60L
                || mob.getRandom().nextDouble() >= rules.riseChance) {
            return;
        }
        progress.questVariables().put(LAST_RISE, Long.toString(now()));
        progress.markDirty();
        rise(victim, mob, state, source, data, rules);
    }

    private static boolean eligible(Mob mob, MonsterState state) {
        if (!BestiaryService.recordable(mob) || mob.getMaxHealth() >= 250.0f
                || mob.getType().is(net.schwarz.rotasutils.event.RotasEvents.BOSS_TAG)
                || mob.getTags().contains("rotasutils_quest_spawned")
                || (mob instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null)) {
            return false;
        }
        if (state != null && (state.boss() || state.rank().ordinal() >= MonsterRank.MINIBOSS.ordinal())) {
            return false;
        }
        return !mob.hasCustomName() || (state != null && mob.getCustomName().getString().equals(state.name()));
    }

    public static int hunting(RotasData data, UUID player) {
        int count = 0;
        for (Nemesis nemesis : data.nemeses().values()) {
            if (nemesis.hasVictim(player)) {
                count++;
            }
        }
        return count;
    }

    private static NemesisMath.Style style(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return NemesisMath.Style.EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return NemesisMath.Style.FIRE;
        }
        if (source.getDirectEntity() instanceof Projectile) {
            return NemesisMath.Style.RANGED;
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return NemesisMath.Style.MAGIC;
        }
        return source.getDirectEntity() != null && source.getDirectEntity() == source.getEntity()
                ? NemesisMath.Style.MELEE : NemesisMath.Style.OTHER;
    }

    private static void rise(ServerPlayer victim, Mob mob, MonsterState state, DamageSource source,
                             RotasData data, SeasonRules.NemesisRules rules) {
        long now = now();
        int id = data.nextNemesisId();
        NemesisMath.Style style = style(source);
        String name = NemesisMath.name(rules.names, rules.epithets, style,
                mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits() ^ id);
        Nemesis nemesis = new Nemesis(id, String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())), name, style, now);
        nemesis.setLevel(NemesisMath.nextLevel(state == null ? 1 : state.level(), rules.levelsPerRank, MonsterLevels.ABSOLUTE_MAX));
        nemesis.recordVictim(victim.getUUID(), victim.getGameProfile().getName());
        nemesis.setBody(mob.getUUID());
        nemesis.seen(mob.level().dimension().location().toString(), mob.blockPosition().asLong(), now);
        nemesis.setNextAmbushAt(now + rules.ambushCooldownMinutes * 60L);
        data.putNemesis(nemesis);
        mob.addTag(TAG_PREFIX + id);
        dress(mob, nemesis, data, rules, true, true);
        victim.sendSystemMessage(ThaiText.c("rotasutils.msg.nemesis.rise", nemesis.displayName())
                .withStyle(ChatFormatting.DARK_RED));
        if (mob.level() instanceof ServerLevel level) {
            for (ServerPlayer other : level.players()) {
                if (other != victim && other.distanceToSqr(mob) <= 48 * 48) {
                    other.sendSystemMessage(ThaiText.c("rotasutils.msg.nemesis.rise_nearby", nemesis.displayName(),
                            victim.getGameProfile().getName()).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    private static void grow(ServerPlayer victim, Mob mob, Nemesis nemesis, RotasData data, SeasonRules.NemesisRules rules) {
        nemesis.recordVictim(victim.getUUID(), victim.getGameProfile().getName());
        if (nemesis.rank() < rules.maxRank) {
            nemesis.setRank(nemesis.rank() + 1, rules.maxRank);
            nemesis.setLevel(NemesisMath.nextLevel(nemesis.level(), rules.levelsPerRank, MonsterLevels.ABSOLUTE_MAX));
        }
        nemesis.seen(mob.level().dimension().location().toString(), mob.blockPosition().asLong(), now());
        data.setDirty();
        dress(mob, nemesis, data, rules, true, true);
        victim.sendSystemMessage(ThaiText.c("rotasutils.msg.nemesis.grow", nemesis.displayName(),
                nemesis.timesKilled(victim.getUUID())).withStyle(ChatFormatting.DARK_RED));
    }

    private static void dress(Mob mob, Nemesis nemesis, RotasData data, SeasonRules.NemesisRules rules,
                              boolean force, boolean heal) {
        MonsterService monsters = data.kernel() == null ? null : data.kernel().monsters();
        if (monsters == null) {
            return;
        }
        MonsterState state = monsters.peek(mob);
        if (state == null && mob.tickCount < 40 && !force) {
            return;
        }
        if (state != null && state.level() != nemesis.level()) {
            MonsterState next = monsters.relevel(mob, nemesis.level(), false);
            if (next != null) {
                state = next;
            }
        }
        if (state != null && state.level() != nemesis.level()) {
            nemesis.setLevel(state.level());
            data.setDirty();
        }
        var config = data.levelConfig().mobLevel();
        String plate = config.nameVisible() ? config.formatName(nemesis.displayName(), nemesis.level()) : nemesis.displayName();
        if (state != null) {
            monsters.rename(mob, plate);
        } else if (!mob.hasCustomName() || !plate.equals(mob.getCustomName().getString())) {
            mob.setCustomName(Component.literal(plate));
            mob.setCustomNameVisible(true);
        }
        if (force || !MonsterService.carriesOwned(mob, OWNER, HEALTH)) {
            float share = mob.getMaxHealth() <= 0 ? 1f : mob.getHealth() / mob.getMaxHealth();
            Map<String, MonsterDefinitions.Scale> scales = new TreeMap<>();
            scales.put(HEALTH, new MonsterDefinitions.Scale(NemesisMath.scale(nemesis.rank(), rules.healthPerRank), 0, 0));
            scales.put(DAMAGE, new MonsterDefinitions.Scale(NemesisMath.scale(nemesis.rank(), rules.damagePerRank), 0, 0));
            monsters.applyOwnedScales(mob, OWNER, scales);
            if (mob.isAlive()) {
                mob.setHealth(Math.max(1f, Math.min(1f, share) * mob.getMaxHealth()));
            }
        }
        mob.setPersistenceRequired();
        if (!mob.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
        if (heal) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

public static EventResult onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob mob) || mob.level().isClientSide || mob.getServer() == null) {
            return EventResult.pass();
        }
        int id = idOf(mob);
        if (id <= 0) {
            return EventResult.pass();
        }
        RotasData data = RotasData.get(mob.getServer());
        Nemesis nemesis = data.nemesis(id);
        if (nemesis == null || !mob.getUUID().equals(nemesis.body())) {
            return EventResult.pass();
        }
        SeasonRules.NemesisRules rules = rules(data);
        ServerPlayer killer = net.schwarz.rotasutils.event.RotasEvents.resolveKiller(source);
        try {
            if (killer == null) {
                fled(mob.getServer(), nemesis, data, rules);
            } else {
                slay(killer, mob, nemesis, data, rules);
            }
        } catch (RuntimeException failure) {
            Rotasutils.LOG.error("Nemesis death failed: {}", failure.getMessage(), failure);
        }
        return EventResult.pass();
    }

    private static void fled(MinecraftServer server, Nemesis nemesis, RotasData data, SeasonRules.NemesisRules rules) {
        nemesis.setBody(null);
        nemesis.setNextAmbushAt(now() + rules.ambushCooldownMinutes * 60L);
        data.setDirty();
        ServerPlayer target = nemesis.target() == null ? null : server.getPlayerList().getPlayer(nemesis.target());
        if (target != null) {
            target.sendSystemMessage(ThaiText.c("rotasutils.msg.nemesis.fled", nemesis.displayName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void slay(ServerPlayer killer, Mob mob, Nemesis nemesis, RotasData data, SeasonRules.NemesisRules rules) {
        data.removeNemesis(nemesis.id());
        forgetTaunts(nemesis.id());
        boolean revenge = nemesis.hasVictim(killer.getUUID());
        SeasonRules.TrackReward bounty = new SeasonRules.TrackReward();
        bounty.gold = NemesisMath.bounty(rules.goldPerRank, nemesis.rank(), revenge, rules.revengeMultiplier);
        bounty.rankPoints = NemesisMath.bounty(rules.rankPointsPerRank, nemesis.rank(), revenge, rules.revengeMultiplier);
        TrackRewards.Paid paid = TrackRewards.pay(killer, data, bounty, "nemesis:" + nemesis.id());

        List<ItemStack> loot = new ArrayList<>();
        DropGrade grade = DropGrade.byKey(NemesisMath.grade(rules.gradeByRank, nemesis.rank()));
        if (grade != null) {
            loot.addAll(DropLoot.roll(data, grade, "nemesis|" + nemesis.id() + "|" + mob.getUUID()));
        }
        if (rules.trophyEnabled) {
            loot.add(trophy(nemesis, rules, data, killer));
        }
        int mailed = loot.isEmpty() ? 0 : LootService.deliver(killer, loot);

        List<String> parts = new ArrayList<>();
        String money = TrackRewards.describe(paid);
        if (!money.isBlank()) {
            parts.add(money);
        }
        for (ItemStack stack : loot) {
            parts.add(stack.getCount() + "x " + stack.getHoverName().getString());
        }
        killer.sendSystemMessage(ThaiText.c("rotasutils.msg.nemesis.bounty", String.join(", ", parts))
                .withStyle(ChatFormatting.GOLD));
        if (mailed > 0) {
            killer.sendSystemMessage(ThaiText.c("rotasutils.msg.drop.mailed", mailed).withStyle(ChatFormatting.GRAY));
        }

        var progress = data.progress(killer.getUUID());
        TitleCounters.add(progress.questVariables(), TitleCounters.NEMESIS_SLAIN, 1);
        progress.markDirty();
        data.setDirty();
        TitleService.onProgress(killer, data);

        String killerName = killer.getGameProfile().getName();
        net.minecraft.network.chat.MutableComponent line;
        if (revenge) {
            line = ThaiText.c("rotasutils.msg.nemesis.revenge", killerName, nemesis.displayName());
        } else {
            List<String> names = new ArrayList<>();
            for (Nemesis.Victim victim : nemesis.victims()) {
                if (names.size() < 3) {
                    names.add(victim.name());
                }
            }
            line = ThaiText.c("rotasutils.msg.nemesis.avenged", killerName, nemesis.displayName(), String.join(", ", names));
        }
        killer.server.getPlayerList().broadcastSystemMessage(line.withStyle(ChatFormatting.GOLD), false);
        RotasNetwork.syncProgress(killer);
    }

    private static ItemStack trophy(Nemesis nemesis, SeasonRules.NemesisRules rules, RotasData data, ServerPlayer killer) {
        String configured = nemesis.style() == NemesisMath.Style.RANGED ? rules.trophyRangedItem : rules.trophyItem;
        ResourceLocation id = ResourceLocation.tryParse(configured == null ? "" : configured);
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        ItemStack stack = new ItemStack(item == Items.AIR ? Items.IRON_SWORD : item);
        Component base = Component.translatable(stack.getDescriptionId());
        stack.setHoverName(Component.empty().append(base)
                .append(Component.literal(ThaiText.t("rotasutils.nemesis.trophy_suffix", nemesis.name())))
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false)));
        var refine = SeasonService.rules(data).refine;
        if (rules.trophyRefine && refine != null && refine.enabled && ItemRefine.categoryOf(stack).refinable()) {
            int level = Math.min(nemesis.rank(), refine.safeLevel);
            if (level > 0) {
                ItemRefine.setLevel(stack, level);
            }
        }
        ListTag lore = new ListTag();
        lore.add(loreLine(ThaiText.t("rotasutils.nemesis.trophy_lore.rank", NemesisMath.stars(nemesis.rank())), ChatFormatting.DARK_RED));
        lore.add(loreLine(ThaiText.t("rotasutils.nemesis.trophy_lore.kills", nemesis.kills()), ChatFormatting.GRAY));
        lore.add(loreLine(ThaiText.t("rotasutils.nemesis.trophy_lore.slain", killer.getGameProfile().getName()), ChatFormatting.GRAY));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        return stack;
    }

    private static StringTag loreLine(String text, ChatFormatting colour) {
        return StringTag.valueOf(Component.Serializer.toJson(Component.literal(text)
                .withStyle(style -> style.withColor(colour).withItalic(false))));
    }

public static void tick(MinecraftServer server, RotasData data) {
        SeasonRules.NemesisRules rules = rules(data);
        if (rules == null || !rules.enabled || data.nemeses().isEmpty() || data.kernel() == null) {
            return;
        }
        long now = now();
        boolean sweep = ++sweepTicks >= 60;
        if (sweep) {
            sweepTicks = 0;
        }
        for (Nemesis nemesis : List.copyOf(data.nemeses().values())) {
            try {
                Mob body = body(server, nemesis);
                if (body != null) {
                    nemesis.seen(body.level().dimension().location().toString(), body.blockPosition().asLong(), now);
                    dress(body, nemesis, data, rules, false, false);
                    taunt(body, nemesis, rules, now);
                    continue;
                }
                if (sweep && now - nemesis.lastSeenAt() > rules.forgetAfterDays * 86400L) {
                    data.removeNemesis(nemesis.id());
                    forgetTaunts(nemesis.id());
                    continue;
                }
                ambush(server, data, nemesis, rules, now);
            } catch (RuntimeException failure) {
                Rotasutils.LOG.error("Nemesis #{} failed: {}", nemesis.id(), failure.getMessage());
            }
        }
    }

    public static Mob body(MinecraftServer server, Nemesis nemesis) {
        if (nemesis.body() == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(nemesis.body());
            if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
                return mob;
            }
        }
        return null;
    }

    private static void taunt(Mob body, Nemesis nemesis, SeasonRules.NemesisRules rules, long now) {
        if (rules.tauntRadius <= 0 || !(body.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = (double) rules.tauntRadius * rules.tauntRadius;
        for (ServerPlayer player : level.players()) {
            if (!nemesis.hasVictim(player.getUUID()) || player.isSpectator() || player.distanceToSqr(body) > radius) {
                continue;
            }
            String key = nemesis.id() + "|" + player.getUUID();
            if (now - TAUNTS.getOrDefault(key, 0L) < rules.tauntCooldownSeconds) {
                continue;
            }
            TAUNTS.put(key, now);
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.nemesis.taunt", nemesis.name(),
                    player.getGameProfile().getName()).withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static void forgetTaunts(int id) {
        String prefix = id + "|";
        TAUNTS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static void ambush(MinecraftServer server, RotasData data, Nemesis nemesis, SeasonRules.NemesisRules rules, long now) {
        if (!rules.ambushEnabled || now < nemesis.nextAmbushAt() || nemesis.target() == null) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(nemesis.target());
        if (target == null || !target.isAlive() || target.isSpectator() || target.isCreative()
                || !target.level().dimension().location().toString().equals(nemesis.dimension())
                || target.getRandom().nextDouble() >= NemesisMath.perSecond(rules.ambushChancePerMinute)) {
            return;
        }
        ServerLevel level = target.serverLevel();
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || ZoneService.region(data, level, target.getX(), target.getY(), target.getZ()).safe()) {
            nemesis.setNextAmbushAt(now + 60);
            return;
        }
        Mob mob = summon(level, target.blockPosition(), nemesis, data, rules);
        if (mob == null) {
            nemesis.setNextAmbushAt(now + 60);
            return;
        }
        mob.setTarget(target);
        target.sendSystemMessage(ThaiText.c("rotasutils.msg.nemesis.ambush", nemesis.displayName())
                .withStyle(ChatFormatting.DARK_RED));
        level.playSound(null, target.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 0.8f);
    }

    public static Mob summon(ServerLevel level, BlockPos center, Nemesis nemesis, RotasData data, SeasonRules.NemesisRules rules) {
        EntityType<?> type = SpawnPlacer.mobType(nemesis.entityType());
        if (type == null) {
            data.removeNemesis(nemesis.id());
            return null;
        }
        BlockPos pos = SpawnPlacer.find(level, center, rules.ambushMinDistance, rules.ambushMaxDistance, type,
                level.getRandom(), spot -> !ZoneService.region(data, level, spot.getX(), spot.getY(), spot.getZ()).safe());
        Mob mob = pos == null ? null : SpawnPlacer.create(level, type, pos, level.getRandom());
        if (mob == null) {
            return null;
        }
        mob.addTag(TAG_PREFIX + nemesis.id());
        UUID previous = nemesis.body();
        nemesis.setBody(mob.getUUID());
        if (!level.addFreshEntity(mob)) {
            nemesis.setBody(previous);
            return null;
        }
        long now = now();
        nemesis.seen(level.dimension().location().toString(), pos.asLong(), now);
        nemesis.setNextAmbushAt(now + rules.ambushCooldownMinutes * 60L);
        data.setDirty();
        return mob;
    }

public static String whereabouts(MinecraftServer server, ServerPlayer viewer, Nemesis nemesis) {
        Mob body = body(server, nemesis);
        boolean here = viewer.level().dimension().location().toString().equals(
                body != null ? body.level().dimension().location().toString() : nemesis.dimension());
        if (!here) {
            return ThaiText.t("rotasutils.nemesis.elsewhere");
        }
        BlockPos pos = body != null ? body.blockPosition() : BlockPos.of(nemesis.pos());
        double dx = pos.getX() + 0.5 - viewer.getX();
        double dz = pos.getZ() + 0.5 - viewer.getZ();
        String direction = ThaiText.t("rotasutils.nemesis.direction", NemesisMath.roughDistance(Math.sqrt(dx * dx + dz * dz)),
                ThaiText.t("rotasutils.compass." + NemesisMath.compass(dx, dz)));
        return body != null ? direction : ThaiText.t("rotasutils.nemesis.lurking", direction);
    }

    public static boolean remove(MinecraftServer server, RotasData data, int id) {
        Nemesis nemesis = data.nemesis(id);
        if (nemesis == null) {
            return false;
        }
        Mob body = body(server, nemesis);
        data.removeNemesis(id);
        forgetTaunts(id);
        if (body != null) {
            body.discard();
        }
        return true;
    }
}
