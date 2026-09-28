package net.schwarz.rotasutils.event;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.compat.EasyNpcCompat;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.objective.EventKind;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.server.AntiFarm;
import net.schwarz.rotasutils.server.AdventureProgressService;
import net.schwarz.rotasutils.server.MonsterXpService;
import net.schwarz.rotasutils.server.ObjectiveEngine;
import net.schwarz.rotasutils.server.ProgressService;
import net.schwarz.rotasutils.server.QuestEvent;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.server.SkillService;
import net.schwarz.rotasutils.server.WorldPicker;

/**
 * All game-event wiring.
 *
 * <p>Every hook is edge triggered. The only periodic work is a coarse server tick
 * that runs once a second and only touches players who actually have timed or
 * location objectives.
 */
public final class RotasEvents {
    /** Entity tag admins can populate to mark extra bosses; empty by default. */
    public static final TagKey<EntityType<?>> BOSS_TAG =
            TagKey.create(Registries.ENTITY_TYPE, Rotasutils.id("bosses"));

    private static int tickCounter;
    private static int autosaveCounter;

    private RotasEvents() {
    }

    public static void init() {
        LifecycleEvent.SERVER_STARTED.register(RotasEvents::onServerStarted);
        LifecycleEvent.SERVER_STOPPING.register(RotasEvents::onServerStopping);
        PlayerEvent.PLAYER_JOIN.register(RotasEvents::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(RotasEvents::onPlayerQuit);
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd) -> {
            SkillService.recalculate(player, RotasData.get(player.server));
            // A respawned player is a new entity without the transient stat modifiers.
            net.schwarz.rotasutils.server.CharacterStatService.apply(player, RotasData.get(player.server));
            net.schwarz.rotasutils.server.RpgKernel.emit(player, "rotas:player_respawned",
                    java.util.UUID.randomUUID().toString(), java.util.Map.of());
            RotasNetwork.syncProgress(player);
            net.schwarz.rotasutils.sky.EldritchSkyService.syncTo(player);
        });
        PlayerEvent.CHANGE_DIMENSION.register((player, from, to) -> {
            // A last-allowed position is only meaningful in the dimension it was recorded in.
            net.schwarz.rotasutils.server.ZoneGateService.forget(player.getUUID());
            net.schwarz.rotasutils.server.ZonePresenceService.forget(player.getUUID());
        net.schwarz.rotasutils.house.HousePresenceService.forget(player.getUUID());
            net.schwarz.rotasutils.house.HousePresenceService.forget(player.getUUID());
            RotasNetwork.syncProgress(player);
            net.schwarz.rotasutils.sky.EldritchSkyService.syncTo(player);
        });

        // Stable horses are knocked out instead of dying; registered first so nothing else sees the death.
        EntityEvent.LIVING_DEATH.register((entity, source) ->
                net.schwarz.rotasutils.server.horse.HorseService.onDeath(entity));
        EntityEvent.ADD.register(net.schwarz.rotasutils.server.horse.HorseService::onAdd);
        EntityEvent.ADD.register(net.schwarz.rotasutils.server.CompanionService::onAdd);
        EntityEvent.LIVING_DEATH.register((entity, source) -> net.schwarz.rotasutils.server.CompanionService.onDeath(entity));
        // Owners cannot hurt their own companion, and nothing the companion does can be pinned on a player.
        EntityEvent.LIVING_HURT.register((entity, source, amount) ->
                net.schwarz.rotasutils.server.CompanionService.protectedFrom(entity, source.getEntity())
                        ? EventResult.interruptFalse() : EventResult.pass());
        EntityEvent.LIVING_HURT.register(net.schwarz.rotasutils.server.horse.HorseTraitEffects::onHurt);
        PlayerEvent.PLAYER_ADVANCEMENT.register(net.schwarz.rotasutils.server.ExplorationService::onAdvancement);
        // Deaths are a title too: some players earn their name by never staying down.
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim && !victim.level().isClientSide) {
                net.schwarz.rotasutils.server.TitleService.count(victim.server, RotasData.get(victim.server),
                        victim.getUUID(), net.schwarz.rotasutils.title.TitleCounters.DEATHS, 1);
            }
            return EventResult.pass();
        });
        EntityEvent.LIVING_DEATH.register(RotasEvents::onLivingDeath);
        // A volatile elite bursts where it falls.
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            try {
                net.schwarz.rotasutils.server.MobAffixService.onDeath(entity);
            } catch (RuntimeException failure) {
                net.schwarz.rotasutils.Rotasutils.LOG.error("Affix death failed: {}", failure.getMessage());
            }
            return EventResult.pass();
        });
        // Zone rule: Rotas XP lost on death, whoever or whatever killed the player.
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && !player.level().isClientSide) {
                net.schwarz.rotasutils.server.ZoneRuleService.applyXpLoss(player, RotasData.get(player.server));
            }
            // A zone spawn point's mob died: start that point's respawn cooldown.
            if (entity instanceof net.minecraft.world.entity.Mob && !entity.level().isClientSide) {
                RotasData data = RotasData.instance();
                if (data != null && data.kernel() != null) {
                    data.kernel().encounters().onDeath(entity);
                }
            }
            return EventResult.pass();
        });
        // Mob Setup spawn rules: cancels natural spawns a setup forbids (zone, dimension, time, height, crowd).
        EntityEvent.LIVING_CHECK_SPAWN.register((entity, level, x, y, z, type, spawner) ->
                net.schwarz.rotasutils.server.MobSpawnDirector.checkSpawn(entity, level, x, y, z, type));
        BlockEvent.BREAK.register(RotasEvents::onBlockBreak);
        BlockEvent.PLACE.register(RotasEvents::onBlockPlace);
        PlayerEvent.CRAFT_ITEM.register(RotasEvents::onCraft);
        PlayerEvent.SMELT_ITEM.register(RotasEvents::onSmelt);
        PlayerEvent.PICKUP_ITEM_POST.register(RotasEvents::onPickup);
        PlayerEvent.PICKUP_ITEM_PRE.register((player, entity, stack) ->
                player instanceof ServerPlayer serverPlayer
                        && net.schwarz.rotasutils.server.GoldCoinService.pickUp(serverPlayer, entity)
                        ? EventResult.interruptFalse() : EventResult.pass());
        InteractionEvent.RIGHT_CLICK_BLOCK.register(RotasEvents::onRightClickBlock);
        InteractionEvent.INTERACT_ENTITY.register(RotasEvents::onInteractEntity);
        TickEvent.SERVER_POST.register(RotasEvents::onServerTick);
        TickEvent.SERVER_POST.register(net.schwarz.rotasutils.server.MobSetupService::tick);
        // A gated level zone pushes a player back out; checked every tick so it cannot be walked through.
        TickEvent.PLAYER_POST.register(net.schwarz.rotasutils.server.ZoneGateService::onPlayerTick);
        // Zone titles, effects and movement rules; checked every 10 ticks per player.
        TickEvent.PLAYER_POST.register(net.schwarz.rotasutils.server.ZonePresenceService::onPlayerTick);
        TickEvent.PLAYER_POST.register(net.schwarz.rotasutils.house.HousePresenceService::onPlayerTick);
        // A nemesis body that reloads with an old chunk after its record moved on is refused.
        EntityEvent.ADD.register(net.schwarz.rotasutils.server.NemesisService::onAdd);
        EntityEvent.ADD.register((entity, level) -> {
            net.schwarz.rotasutils.server.FarmingService.clearLegacyGlow(entity);
            net.schwarz.rotasutils.server.DungeonService.onEntityAdd(entity);
            return dev.architectury.event.EventResult.pass();
        });
        // Registered last, so a death another listener cancelled never raises or ends a nemesis.
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity.level().isClientSide) {
                return EventResult.pass();
            }
            try {
                if (entity instanceof ServerPlayer player) {
                    net.schwarz.rotasutils.server.NemesisService.onPlayerKilled(player, source);
                    return EventResult.pass();
                }
                return net.schwarz.rotasutils.server.NemesisService.onDeath(entity, source);
            } catch (RuntimeException failure) {
                Rotasutils.LOG.error("Nemesis hook failed: {}", failure.getMessage(), failure);
                return EventResult.pass();
            }
        });
    }

    // Lifecycle ------------------------------------------------------------

    private static void onServerStarted(MinecraftServer server) {
        RotasData.get(server).setKernel(new net.schwarz.rotasutils.server.RpgKernel(server));
        net.schwarz.rotasutils.server.SeasonConfigFile.load(server, RotasData.get(server));
        ProgressService.migrateStatSystem(RotasData.get(server));
        net.schwarz.rotasutils.server.TitleService.seedDefaults(RotasData.get(server));
        net.schwarz.rotasutils.server.JobService.seedDefaults(RotasData.get(server));
        net.schwarz.rotasutils.server.CardService.refreshIndex(RotasData.get(server));
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof net.minecraft.world.entity.Mob mob) { RotasData.get(server).kernel().monsters().enqueue(mob, "LOADED", true); }
            }
        }
        net.schwarz.rotasutils.sky.EldritchSkyService.settleAll(server);
        Rotasutils.LOG.info("RotasUtils content store loaded");
    }

    private static void onServerStopping(MinecraftServer server) {
        net.schwarz.rotasutils.sky.SkyClash.clear();
        net.schwarz.rotasutils.sky.SkySunder.clear();
        net.schwarz.rotasutils.server.CompanionService.clear();
        net.schwarz.rotasutils.server.NpcSocial.clear();
        RotasData data = RotasData.instance();
        if (data != null) {
            if (data.kernel() != null) {
                data.kernel().close();
                data.setKernel(null);
            }
            data.setDirty();
        }
        AntiFarm.clear();
        net.schwarz.rotasutils.server.VanillaDropScan.clear();
        net.schwarz.rotasutils.server.MiningService.clear();
        net.schwarz.rotasutils.server.FarmingService.clear();
        net.schwarz.rotasutils.server.NemesisService.clear();
        net.schwarz.rotasutils.server.WorldEventService.clear();
        net.schwarz.rotasutils.server.ProductionService.clear();
        net.schwarz.rotasutils.server.PartyService.clear();
        // Everything below is static per-player state. On an integrated server the JVM outlives the
        // world, so anything left here is carried into the next world the player opens.
        net.schwarz.rotasutils.server.CombatStats.clear();
        net.schwarz.rotasutils.server.DungeonService.clearAll();
        net.schwarz.rotasutils.server.MobSpawnDirector.clearCaches();
        net.schwarz.rotasutils.server.ZoneWandService.clear();
        net.schwarz.rotasutils.server.ZoneRuleService.clear();
        net.schwarz.rotasutils.server.ZoneVisibilityService.clear();
        net.schwarz.rotasutils.server.NpcConversations.clear();
        net.schwarz.rotasutils.server.ObjectiveEngine.clear();
        net.schwarz.rotasutils.server.BoardService.clear();
        net.schwarz.rotasutils.server.WorldPicker.clear();
        net.schwarz.rotasutils.network.RotasNetwork.clear();
        net.schwarz.rotasutils.network.ProgressSync.clear();
        net.schwarz.rotasutils.network.AdminNetwork.clear();
        RotasData.clearInstance();
    }

    private static void onPlayerJoin(ServerPlayer player) {
        RotasData data = RotasData.get(player.server);
        PlayerProgress progress = data.progress(player.getUUID());
        progress.setLastKnownName(player.getGameProfile().getName());
        if(progress.mainJob().isEmpty() && data.levelConfig().firstJoinMode()==net.schwarz.rotasutils.level.LevelConfig.FirstJoinMode.ASSIGN) {
            var main=data.job(data.levelConfig().firstJoinMainJob());
            if(main!=null&&main.enabled()&&main.mainAllowed()) net.schwarz.rotasutils.server.JobService.assignSlot(progress,main,net.schwarz.rotasutils.job.JobSlot.MAIN);
            var sub=data.job(data.levelConfig().firstJoinSubJob());
            if(sub!=null&&sub.enabled()&&sub.subAllowed()&&!sub.id().equals(progress.mainJob())) net.schwarz.rotasutils.server.JobService.assignSlot(progress,sub,net.schwarz.rotasutils.job.JobSlot.SUB);
        }
        if (data.saveBlocked() && player.hasPermissions(2)) {
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.admin.save_blocked")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        ProgressService.refreshClearance(player, data, progress);
        ProgressService.syncVanillaLevelMirror(player, data);
        SkillService.recalculate(player, data);
        net.schwarz.rotasutils.server.CharacterStatService.grantLevelPoints(progress, data);
        net.schwarz.rotasutils.server.CharacterStatService.apply(player, data);
        net.schwarz.rotasutils.server.CharacterStatService.restoreHealth(player, progress);
        net.schwarz.rotasutils.server.DailyService.onJoin(player, data);
        net.schwarz.rotasutils.server.EventService.fire(player, data,
                net.schwarz.rotasutils.event.EventType.PLAYER_LOGIN, "");
        net.schwarz.rotasutils.server.RpgKernel.emit(player, "rotas:player_login",
                java.util.UUID.randomUUID().toString(), java.util.Map.of());
        RotasNetwork.syncContent(player);
        RotasNetwork.syncProgress(player);
        net.schwarz.rotasutils.network.KernelUi.sync(player);
        RotasNetwork.syncParty(player);
        net.schwarz.rotasutils.sky.EldritchSkyService.syncTo(player);
        promptMainJob(player, data);
    }

    /** In CHOOSE mode a player without a main job gets the job picker, which stays up until they pick. */
    public static void promptMainJob(ServerPlayer player, RotasData data) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (!progress.mainJob().isEmpty()
                || data.levelConfig().firstJoinMode() != net.schwarz.rotasutils.level.LevelConfig.FirstJoinMode.CHOOSE) {
            return;
        }
        // Never lock a player in a picker with nothing they can pick.
        boolean pickable = data.jobs().values().stream()
                .anyMatch(job -> job.enabled() && job.mainAllowed() && job.minLevel() <= progress.level());
        if (pickable) {
            RotasNetwork.openScreen(player, "job_select", new net.minecraft.nbt.CompoundTag());
        }
    }

    private static void onPlayerQuit(ServerPlayer player) {
        net.schwarz.rotasutils.server.RpgKernel.emit(player, "rotas:player_logout",
                java.util.UUID.randomUUID().toString(), java.util.Map.of());
        WorldPicker.clear(player);
        net.schwarz.rotasutils.server.CombatStats.forget(player.getUUID());
        PlayerProgress leaving = RotasData.get(player.server).peek(player.getUUID());
        if (leaving != null && player.isAlive()) {
            leaving.setLastHealth(player.getHealth());
        }
        net.schwarz.rotasutils.server.BoardService.forget(player.getUUID());
        net.schwarz.rotasutils.server.ZoneWandService.forget(player.getUUID());
        net.schwarz.rotasutils.server.ZoneGateService.logout(player.getUUID());
        net.schwarz.rotasutils.server.ZonePresenceService.forget(player.getUUID());
        net.schwarz.rotasutils.house.HousePresenceService.forget(player.getUUID());
        net.schwarz.rotasutils.server.NpcConversations.close(player.getUUID());
        net.schwarz.rotasutils.server.WorldEventService.forget(player);
        ObjectiveEngine.forget(player.getUUID());
        RotasNetwork.forget(player);
        if (RotasData.get(player.server).kernel() != null) {
            RotasData.get(player.server).kernel().forgetPlayer(player.getUUID());
        }
        // Party membership persists; only the pending invitation is dropped.
        net.schwarz.rotasutils.server.PartyService.forget(player);
        RotasData data = RotasData.instance();
        if (data != null) {
            data.setDirty();
        }
    }

    // Combat ---------------------------------------------------------------

    private static EventResult onLivingDeath(LivingEntity entity, DamageSource source) {
        return handleLivingDeath(entity, source, false);
    }

    public static void confirmedMonsterDeath(net.minecraft.world.entity.Mob mob, DamageSource source) {
        handleLivingDeath(mob, source, true);
    }

    private static EventResult handleLivingDeath(LivingEntity entity, DamageSource source, boolean confirmed) {
        if (entity.level().isClientSide) {
            return EventResult.pass();
        }
        var monster = net.schwarz.rotasutils.server.MonsterService.state(entity);
        if (monster != null && !confirmed) { return EventResult.pass(); }
        ServerPlayer killer = resolveKiller(source);
        if (killer == null) {
            return EventResult.pass();
        }
        RotasData data = RotasData.get(killer.server);

        if (entity instanceof ServerPlayer victim) {
            QuestEvent event = new QuestEvent(EventKind.KILL_PLAYER)
                    .victim(victim)
                    .entityType(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()))
                    .entityName(victim.getGameProfile().getName())
                    .entityUuid(victim.getUUID())
                    .dimension(victim.level().dimension().location().toString())
                    .pos(victim.blockPosition())
                    .weapon(heldItemId(killer))
                    .damageType(source.type().msgId());
            ObjectiveEngine.handle(killer, data, event);
            ProgressService.awardFromSource(killer, data, XpSource.PLAYER_KILL, victim.getUUID().toString());
            return EventResult.pass();
        }

        boolean boss = isBoss(entity) || (monster != null && monster.boss());
        // The chain is extended before anything is paid, so the kill that grows it is paid at the new rate.
        if (net.schwarz.rotasutils.server.BestiaryService.recordable(entity)) {
            net.schwarz.rotasutils.server.FarmingService.onKill(killer, data);
        }
        net.schwarz.rotasutils.server.BountyService.onKill(killer, data, entity);
        QuestEvent event = new QuestEvent(EventKind.KILL_ENTITY)
                .entityType(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
                .entityName(entity.hasCustomName() ? entity.getCustomName().getString() : "")
                .entityUuid(entity.getUUID())
                .boss(boss)
                // Scoreboard tags are loader neutral, so quest-spawned mobs can be marked
                // by this mod, by a datapack, or by /summon without a Forge-only NBT hook.
                .questSpawned(entity.getTags().contains("rotasutils_quest_spawned"))
                .dimension(entity.level().dimension().location().toString())
                .biome(biomeOf(entity.level(), entity.blockPosition()))
                .pos(entity.blockPosition())
                .weapon(heldItemId(killer))
                .damageType(source.type().msgId())
                .projectile(source.getDirectEntity() instanceof Projectile)
                .petKill(source.getEntity() instanceof TamableAnimal)
                .partyKill(false);
        ObjectiveEngine.handle(killer, data, event);
        String monsterIdentity = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        XpSource killSource = boss ? XpSource.BOSS_KILL : XpSource.MOB_KILL;
        // A world event pays by where the monster died, like a zone's own multiplier.
        double worldEventXp = net.schwarz.rotasutils.server.WorldEventService.xpMultiplier(entity);
        if (net.schwarz.rotasutils.server.SeasonService.active(data)) {
            var award = monster == null
                    ? new ProgressService.CombatAward(MonsterXpService.calculate(entity, data.levelConfig(), boss), 1.0, 0)
                    : AdventureProgressService.seasonAward(killer, data, monster, entity, monsterIdentity);
            if (worldEventXp != 1.0) {
                award = new ProgressService.CombatAward(award.baseXp(), award.multiplier() * worldEventXp, award.monsterLevel());
            }
            ProgressService.awardCombat(killer, data, killSource, monsterIdentity, award,
                    monster == null ? null : entity.getUUID());
            RotasNetwork.syncProgress(killer);
        }
        long combatXp = net.schwarz.rotasutils.server.SeasonService.active(data) ? 0
                : monster == null ? MonsterXpService.calculate(entity, data.levelConfig(), boss)
                : AdventureProgressService.combatXp(killer, data, monster, entity, monsterIdentity);
        combatXp = Math.max(0, Math.min(1_000_000_000L, Math.round(combatXp * worldEventXp)));
        if (combatXp > 0) {
            if (monster == null) {
                ProgressService.awardFromSource(killer, data, boss ? XpSource.BOSS_KILL : XpSource.MOB_KILL,
                        monsterIdentity, combatXp);
            } else {
                ProgressService.awardFromSourceOnce(killer, data, boss ? XpSource.BOSS_KILL : XpSource.MOB_KILL,
                        monsterIdentity, combatXp, entity.getUUID());
            }
            RotasNetwork.syncProgress(killer);
        }
        if (monster != null && entity instanceof net.minecraft.world.entity.Mob mob) {
            data.kernel().monsters().grantKillReward(mob, killer);
            RotasNetwork.syncProgress(killer);
        }
        try {
            net.schwarz.rotasutils.server.BestiaryService.onKill(killer, data, entity, monsterIdentity);
            if (monster != null) {
                net.schwarz.rotasutils.server.FarmingService.onRankedKill(killer, data, monster.rank());
            }
        } catch (RuntimeException failure) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("Bestiary failed: {}", failure.getMessage());
        }
        try {
            net.schwarz.rotasutils.server.WeaponMemoryService.onKill(killer, data, entity, monsterIdentity, boss, source);
            net.schwarz.rotasutils.server.WorldEventService.onKill(killer, entity);
        } catch (RuntimeException failure) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("Weapon memory / world event failed: {}", failure.getMessage());
        }
        // The rarest thing a kill can leave. Never let it reach the kill path if a card is misconfigured.
        try { net.schwarz.rotasutils.server.CardService.onKill(killer, data, entity, monsterIdentity); }
        catch (RuntimeException failure) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("Card drop failed: {}", failure.getMessage());
        }
        // A kill is also how most titles are earned; the tally only counts what some title asks for.
        try { net.schwarz.rotasutils.server.TitleService.onKill(killer, data, monsterIdentity, boss); }
        catch (RuntimeException failure) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("Title tally failed: {}", failure.getMessage());
        }
        if (monster == null) {
            // A mob nobody set up still pays what the plain drop rules say. Never let a drop failure
            // reach the kill path: the experience and the quest event above are already committed.
            try { net.schwarz.rotasutils.server.DropService.onPlainMobKilled(killer, entity); }
            catch (RuntimeException failure) {
                net.schwarz.rotasutils.Rotasutils.LOG.error("Plain mob drop failed: {}", failure.getMessage());
            }
        }
        return EventResult.pass();
    }

    /** The player credited with a kill: the striker, a projectile's owner, or a pet's owner. */
    public static ServerPlayer resolveKiller(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (source.getEntity() instanceof TamableAnimal pet
                && pet.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof Warden
                || entity.getType().is(BOSS_TAG)
                // Many modded bosses do not expose a Forge/vanilla boss marker. A very large
                // live health pool is a reliable fallback and still leaves minibosses on the
                // continuous dynamic-threat curve rather than hard-coding mod ids.
                || (entity.getMaxHealth() >= 250.0f
                && entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER);
    }

    // World ----------------------------------------------------------------

    private static EventResult onBlockBreak(Level level, BlockPos pos, BlockState state,
                                            ServerPlayer player, dev.architectury.utils.value.IntValue xp) {
        if (level.isClientSide || player == null) {
            return EventResult.pass();
        }
        if (player.getMainHandItem().is(RotasRegistry.HOUSE_WAND.get())) {
            return net.schwarz.rotasutils.house.HouseWandService.selectFirst(player, player.getMainHandItem(), pos)
                    ? EventResult.interruptFalse() : EventResult.pass();
        }
        if (!net.schwarz.rotasutils.house.HouseProtectionService.canModify(player, pos)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("rotasutils.msg.house.protected"), true);
            return EventResult.interruptFalse();
        }
        // A spent mining node refuses the break before anything else can count it.
        if (net.schwarz.rotasutils.server.MiningService.onBreak(player, level, pos, state)
                == net.schwarz.rotasutils.server.MiningService.Break.BLOCKED) {
            return EventResult.interruptFalse();
        }
        RotasData data = RotasData.get(player.server);
        try {
            net.schwarz.rotasutils.server.WorldEventService.onBlockBreak(player, level, pos, state);
        } catch (RuntimeException failure) {
            Rotasutils.LOG.error("World event ore failed: {}", failure.getMessage());
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ObjectiveEngine.handle(player, data, new QuestEvent(EventKind.BLOCK_BREAK)
                .blockId(blockId)
                .pos(pos)
                .dimension(level.dimension().location().toString()));
        ProgressService.awardFromSource(player, data, XpSource.MINING, String.valueOf(blockId));
        if (!net.schwarz.rotasutils.server.ProductionService.recentlyPlaced(level, pos)) {
            var target = net.schwarz.rotasutils.server.ProductionService.block(state);
            net.schwarz.rotasutils.server.ProductionService.produced(player,
                    net.schwarz.rotasutils.job.JobDef.ProductionEntry.Activity.MINE, target, 1);
            if (net.schwarz.rotasutils.server.ProductionService.harvestable(state)) {
                net.schwarz.rotasutils.server.ProductionService.produced(player,
                        net.schwarz.rotasutils.job.JobDef.ProductionEntry.Activity.HARVEST, target, 1);
            }
        }
        return EventResult.pass();
    }

    private static EventResult onBlockPlace(Level level, BlockPos pos, BlockState state, Entity placer) {
        if (level.isClientSide || !(placer instanceof ServerPlayer player)) {
            return EventResult.pass();
        }
        if (!net.schwarz.rotasutils.house.HouseProtectionService.canModify(player, pos)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("rotasutils.msg.house.protected"), true);
            return EventResult.interruptFalse();
        }
        net.schwarz.rotasutils.server.ProductionService.rememberPlaced(level, pos);
        ObjectiveEngine.handle(player, RotasData.get(player.server), new QuestEvent(EventKind.BLOCK_PLACE)
                .blockId(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                .pos(pos)
                .dimension(level.dimension().location().toString()));
        return EventResult.pass();
    }

    private static void onCraft(net.minecraft.world.entity.player.Player player, ItemStack stack,
                                net.minecraft.world.Container container) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) {
            return;
        }
        RotasData data = RotasData.get(serverPlayer.server);
        ObjectiveEngine.handle(serverPlayer, data, new QuestEvent(EventKind.CRAFT_ITEM)
                .stack(stack)
                .amount(stack.getCount())
                .dimension(player.level().dimension().location().toString()));
        ProgressService.awardFromSource(serverPlayer, data, XpSource.CRAFTING,
                String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        net.schwarz.rotasutils.server.ProductionService.produced(serverPlayer,
                net.schwarz.rotasutils.job.JobDef.ProductionEntry.Activity.CRAFT,
                net.schwarz.rotasutils.server.ProductionService.item(stack), 1);
    }

    private static void onSmelt(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) {
            return;
        }
        RotasData data = RotasData.get(serverPlayer.server);
        ObjectiveEngine.handle(serverPlayer, data, new QuestEvent(EventKind.SMELT_ITEM)
                .stack(stack)
                .amount(stack.getCount())
                .dimension(player.level().dimension().location().toString()));
        ProgressService.awardFromSource(serverPlayer, data, XpSource.SMELTING,
                String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        net.schwarz.rotasutils.server.ProductionService.produced(serverPlayer,
                net.schwarz.rotasutils.job.JobDef.ProductionEntry.Activity.SMELT,
                net.schwarz.rotasutils.server.ProductionService.item(stack), stack.getCount());
    }

    private static void onPickup(net.minecraft.world.entity.player.Player player,
                                 net.minecraft.world.entity.item.ItemEntity itemEntity, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) {
            return;
        }
        ObjectiveEngine.handle(serverPlayer, RotasData.get(serverPlayer.server),
                new QuestEvent(EventKind.COLLECT_ITEM)
                        .stack(stack)
                        .amount(stack.getCount())
                        .dimension(player.level().dimension().location().toString()));
    }

    private static EventResult onRightClickBlock(net.minecraft.world.entity.player.Player player,
                                                 net.minecraft.world.InteractionHand hand,
                                                 BlockPos pos, net.minecraft.core.Direction face) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return EventResult.pass();
        }
        // Position and board picks are tooling too; they must not advance objectives.
        if (WorldPicker.isPending(serverPlayer)
                && RotasRegistry.ADMIN_TOOL.get().equals(player.getItemInHand(hand).getItem())) {
            return EventResult.pass();
        }
        // Tracing a level zone is administration, not a world interaction.
        if (player.getItemInHand(hand).is(RotasRegistry.ZONE_WAND.get())) {
            return EventResult.pass();
        }
        if (player.getItemInHand(hand).is(RotasRegistry.HOUSE_WAND.get())) return EventResult.pass();
        if (!net.schwarz.rotasutils.house.HouseProtectionService.canInteract(serverPlayer, pos)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("rotasutils.msg.house.protected"), true);
            return EventResult.interruptFalse();
        }
        BlockState state = player.level().getBlockState(pos);
        // Furnaces and brewing stands remember who opened them; their hoppers take sealed results for that player.
        var blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                || blockEntity instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) {
            net.schwarz.rotasutils.server.ProductionService.rememberStation(player.level(), pos, serverPlayer.getUUID());
        }
        ObjectiveEngine.handle(serverPlayer, RotasData.get(serverPlayer.server),
                new QuestEvent(EventKind.BLOCK_INTERACT)
                        .blockId(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                        .pos(pos)
                        .dimension(player.level().dimension().location().toString()));
        return EventResult.pass();
    }

    private static EventResult onInteractEntity(net.minecraft.world.entity.player.Player player,
                                                Entity entity, net.minecraft.world.InteractionHand hand) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return EventResult.pass();
        }
        // A world-selection click with the admin tool is tooling, not gameplay: the
        // picker needs the entity interaction chain, and no objectives may fire for it.
        if (WorldPicker.isPending(serverPlayer)
                && RotasRegistry.ADMIN_TOOL.get().equals(player.getItemInHand(hand).getItem())) {
            return EventResult.pass();
        }
        if (net.schwarz.rotasutils.server.horse.HorseService.blockPotion(serverPlayer, entity, hand)) {
            return EventResult.interruptTrue();
        }
        // The whistle on a SWEM horse stables it. Handled here because the horse's own click would mount it first.
        if (player.getItemInHand(hand).is(RotasRegistry.HORSE_WHISTLE.get())
                && net.schwarz.rotasutils.server.horse.SwemCompat.isHorse(entity)) {
            var result = net.schwarz.rotasutils.server.horse.HorseService.adopt(serverPlayer, entity);
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(result.message())
                    .withStyle(result.ok() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED), true);
            return EventResult.interruptTrue();
        }
        // The NPC Wand edits the clicked mob. This event runs before the mob's own interaction,
        // so interrupting here also keeps villager trades and Easy NPC dialogs from opening.
        if (player.getItemInHand(hand).is(RotasRegistry.NPC_WAND.get()) && entity instanceof LivingEntity living) {
            net.schwarz.rotasutils.server.NpcWandService.use(serverPlayer, living);
            return EventResult.interruptTrue();
        }
        // The Zone Wand re-levels a mob to its zone; that is tooling, so no quest objective fires.
        if (player.getItemInHand(hand).is(RotasRegistry.ZONE_WAND.get())) {
            return EventResult.pass();
        }
        // Easy NPC editor tools stay with the upstream mod, which owns their access
        // checks; Rotas objectives and dialogue stand down for those clicks so the
        // wand keeps working even on a bound NPC.
        if (EasyNpcCompat.isEditorInteraction(player, entity, hand)) {
            return EventResult.pass();
        }
        RotasData data = RotasData.get(serverPlayer.server);
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String name = entity.hasCustomName() ? entity.getCustomName().getString() : entity.getName().getString();

        // A configured NPC answers first, and the talk objectives below still fire, so
        // "talk to the clerk" completes on the same click that opens the clerk's dialogue.
        boolean captured = net.schwarz.rotasutils.server.NpcService.onInteract(serverPlayer, data, entity);

        ObjectiveEngine.handle(serverPlayer, data, new QuestEvent(EventKind.ENTITY_INTERACT)
                .entityType(typeId)
                .entityUuid(entity.getUUID())
                .entityName(name)
                .dimension(player.level().dimension().location().toString()));
        // The same interaction also satisfies talk-to-NPC objectives, which is what
        // makes plain villagers and modded NPCs work without a compat layer.
        ObjectiveEngine.handle(serverPlayer, data, new QuestEvent(EventKind.TALK_NPC)
                .entityType(typeId)
                .entityUuid(entity.getUUID())
                .entityName(name)
                .dimension(player.level().dimension().location().toString()));
        // Interrupting stops the entity's own menu (villager trades, for one) from opening
        // on top of the dialogue screen the NPC just pushed.
        return captured ? EventResult.interruptTrue() : EventResult.pass();
    }

    // Tick -----------------------------------------------------------------

    private static void onServerTick(MinecraftServer server) {
        RotasData data = RotasData.instance();
        if (data == null) {
            return;
        }
        if (data.kernel() != null) {
            data.kernel().monsters().tick();
            data.kernel().equipment().tick(server, data);
        }
        net.schwarz.rotasutils.server.MiningService.tick(server, data);

        // Vanilla XP is only a compatibility mirror. Restore it every tick so mods that
        // directly mutate Player.experienceLevel cannot create a second level economy.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ProgressService.syncVanillaLevelMirror(player, data);
        }

        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        try {
            net.schwarz.rotasutils.server.NemesisService.tick(server, data);
            net.schwarz.rotasutils.server.WorldEventService.tick(server, data);
        } catch (RuntimeException failure) {
            Rotasutils.LOG.error("Nemesis / world event tick failed: {}", failure.getMessage(), failure);
        }
        net.schwarz.rotasutils.house.HouseBillingService.tick(server, data, System.currentTimeMillis());
        try {
            net.schwarz.rotasutils.server.DungeonService.tick(server);
        } catch (RuntimeException failure) {
            Rotasutils.LOG.error("Dungeon tick failed: {}", failure.getMessage(), failure);
        }
        net.schwarz.rotasutils.server.horse.HorseService.tick(server);
        net.schwarz.rotasutils.server.TitleService.tickAura(server, data);
        net.schwarz.rotasutils.server.ExplorationService.tick(server, data);
        net.schwarz.rotasutils.server.CompanionService.tick(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerProgress progress = data.peek(player.getUUID());
            if (progress == null) {
                continue;
            }
            if (!progress.activeQuests().isEmpty()) {
                QuestService.checkTimeLimits(player, data);
                ObjectiveEngine.handle(player, data, new QuestEvent(EventKind.LOCATION)
                        .pos(player.blockPosition())
                        .dimension(player.level().dimension().location().toString()));
            }
            if (progress.dirty()) {
                RotasNetwork.syncProgress(player);
            }
        }
        // Batched save for everything that is not a reward transaction.
        if (++autosaveCounter >= Math.max(1, data.serverSettings().autosaveIntervalSeconds())) {
            autosaveCounter = 0;
            data.setDirty();
        }
    }

    private static ResourceLocation heldItemId(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
    }

    private static String biomeOf(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return "";
        }
        return serverLevel.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
    }
}
