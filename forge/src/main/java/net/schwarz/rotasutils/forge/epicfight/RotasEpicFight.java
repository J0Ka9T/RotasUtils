package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.schwarz.rotasutils.Rotasutils;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.api.forgeevent.SkillLootTableRegistryEvent;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.data.loot.function.SetSkillFunction;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.passive.PassiveSkill;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;
import yesman.epicfight.world.item.EpicFightItems;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RotasEpicFight {
    private RotasEpicFight() {
    }

    public static void init(IEventBus modBus) {
        CombatSkillEffects.init();
        MinecraftForge.EVENT_BUS.addListener(MyriadSwordsSkill::logout);
        MinecraftForge.EVENT_BUS.addListener(DharmakayaSkill::logout);
        MinecraftForge.EVENT_BUS.addListener(WanJianSkill::logout);
        modBus.addListener(RotasEpicFight::buildSkills);
        modBus.addListener(RotasEpicFight::addLoot);
        MinecraftForge.EVENT_BUS.addListener(HuntersPatience::onHurt);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent setup) -> setup.enqueueWork(FixedDamage::install));
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW, false,
                LivingHurtEvent.class, FixedDamage::onHurt);
        ZenithMoveset.init(modBus);
        ExoMoveset.init(modBus);
        CapoeiraMoveset.init(modBus);
    }

    private static void buildSkills(SkillBuildEvent event) {
        SkillBuildEvent.ModRegistryWorker worker = event.createRegistryWorker(Rotasutils.MOD_ID);
        worker.build("momentum", Momentum::new, PassiveSkill.createPassiveBuilder());
        worker.build("last_bastion", LastBastion::new, PassiveSkill.createPassiveBuilder());
        worker.build("predators_mark", PredatorsMark::new, PassiveSkill.createPassiveBuilder());
        worker.build("afterimage", Afterimage::new, PassiveSkill.createPassiveBuilder());
        worker.build("arcane_edge", ArcaneEdge::new, PassiveSkill.createPassiveBuilder());
        worker.build("hunters_patience", HuntersPatience::new, PassiveSkill.createPassiveBuilder());
        AdditionalEpicFightSkills.build(worker);
        MyriadSwordsSkill.build(worker);
        DharmakayaSkill.build(worker);
        WanJianSkill.build(worker);
        ExoMoveset.buildSkills(worker);
        CapoeiraMoveset.buildSkills(worker);
    }

    private static void addLoot(SkillLootTableRegistryEvent event) {
        AdditionalEpicFightSkills.addLoot(event);
        books(event, EntityType.VINDICATOR, 0.04f, "momentum", "last_bastion");
        books(event, EntityType.PIGLIN_BRUTE, 0.04f, "momentum", "last_bastion");
        books(event, EntityType.RAVAGER, 0.10f, "last_bastion");
        books(event, EntityType.ENDERMAN, 0.03f, "predators_mark", "afterimage");
        books(event, EntityType.SPIDER, 0.02f, "predators_mark");
        books(event, EntityType.PHANTOM, 0.03f, "afterimage");
        books(event, EntityType.WITCH, 0.04f, "arcane_edge");
        books(event, EntityType.EVOKER, 0.15f, "arcane_edge", "afterimage");
        books(event, EntityType.EVOKER, 0.10f, MyriadSwordsSkill.ID);
        books(event, EntityType.WARDEN, 0.15f, DharmakayaSkill.ID);
        books(event, EntityType.WITHER, 0.20f, WanJianSkill.ID);
        books(event, EntityType.SKELETON, 0.02f, "hunters_patience");
        books(event, EntityType.PILLAGER, 0.04f, "hunters_patience");
    }

    private static void books(SkillLootTableRegistryEvent event, EntityType<?> type, float chance, String... skills) {
        String[] ids = java.util.Arrays.stream(skills).map(skill -> Rotasutils.MOD_ID + ":" + skill).toArray(String[]::new);
        event.add(type, LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(LootItem.lootTableItem(EpicFightItems.SKILLBOOK.get()).apply(SetSkillFunction.builder(ids))));
    }

abstract static class RotasPassive<S> extends PassiveSkill {
        private final Map<UUID, S> states = new ConcurrentHashMap<>();
        private UUID listenerId;

        RotasPassive(SkillBuilder<? extends PassiveSkill> builder) {
            super(builder);
        }

        UUID listenerId() {
            if (listenerId == null) {
                listenerId = UUID.nameUUIDFromBytes(getRegistryName().toString().getBytes(StandardCharsets.UTF_8));
            }
            return listenerId;
        }

        abstract S newState();

        S state(Player player) {
            return states.computeIfAbsent(player.getUUID(), id -> newState());
        }

        static boolean server(SkillContainer container) {
            return !container.getExecutor().isLogicalClient();
        }

        @Override
        public void onRemoved(SkillContainer container) {
            super.onRemoved(container);
            for (EventType<?> type : listenedEvents()) {
                container.getExecutor().getEventListener().removeListener(type, listenerId());
            }
            if (server(container)) {
                states.remove(container.getExecutor().getOriginal().getUUID());
                CombatSkillEffects.clear(container.getExecutor().getOriginal(), getRegistryName().getPath());
            }
        }

        abstract List<EventType<?>> listenedEvents();

        static float param(CompoundTag tag, String key, float fallback) {
            return tag.contains(key) ? tag.getFloat(key) : fallback;
        }

        static String percent(float value) {
            return Integer.toString(Math.round(value * 100));
        }
    }

    static void addStamina(PlayerPatch<?> patch, float amount) {
        patch.setStamina(Math.min(patch.getMaxStamina(), patch.getStamina() + amount));
    }

    static void burst(Entity at, ParticleOptions particle, int count, double spread, SoundEvent sound, float pitch) {
        if (at.level() instanceof ServerLevel level) {
            level.sendParticles(particle, at.getX(), at.getY() + at.getBbHeight() * 0.6, at.getZ(),
                    count, spread, spread, spread, 0.15);
            level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, 0.9f, pitch);
        }
    }

static final class Momentum extends RotasPassive<Momentum.State> {
        static final class State {
            int target = -1;
            int stacks;
            int lastHit;
        }

        private float perStack = 0.06f;
        private int maxStacks = 5;
        private int window = 60;
        private float crescendo = 0.4f;
        private float crescendoStamina = 3f;

        Momentum(SkillBuilder<? extends PassiveSkill> builder) {
            super(builder);
        }

        @Override
        State newState() {
            return new State();
        }

        @Override
        List<EventType<?>> listenedEvents() {
            return List.of(EventType.DEAL_DAMAGE_EVENT_HURT, EventType.TAKE_DAMAGE_EVENT_HURT);
        }

        @Override
        public void setParams(CompoundTag tag) {
            super.setParams(tag);
            perStack = param(tag, "damage_per_stack", perStack);
            maxStacks = (int) param(tag, "max_momentum", maxStacks);
            window = (int) param(tag, "window_ticks", window);
            crescendo = param(tag, "crescendo_bonus", crescendo);
            crescendoStamina = param(tag, "crescendo_stamina", crescendoStamina);
        }

        @Override
        public void onInitiate(SkillContainer container) {
            super.onInitiate(container);
            var listener = container.getExecutor().getEventListener();
            listener.addEventListener(EventType.DEAL_DAMAGE_EVENT_HURT, listenerId(), event -> {
                ServerPlayer player = event.getPlayerPatch().getOriginal();
                State state = state(player);
                int now = player.tickCount;
                if (state.target != event.getTarget().getId() || now - state.lastHit > window) {
                    state.target = event.getTarget().getId();
                    state.stacks = 0;
                }
                state.lastHit = now;
                float multiplier;
                if (state.stacks >= maxStacks) {
                    multiplier = 1 + perStack * maxStacks + crescendo;
                    state.stacks = 0;
                    addStamina(event.getPlayerPatch(), crescendoStamina);
                    burst(event.getTarget(), ParticleTypes.CRIT, 18, 0.4, SoundEvents.PLAYER_ATTACK_CRIT, 0.6f);
                    CombatSkillEffects.emit(player, event.getTarget(), "momentum", 6, 1);
                } else {
                    multiplier = 1 + perStack * state.stacks;
                    state.stacks++;
                    CombatSkillEffects.emit(player, event.getTarget(), "momentum", Math.min(5, state.stacks), 1);
                }
                event.getDamageSource().attachDamageModifier(ValueModifier.multiplier(multiplier));
            });
            listener.addEventListener(EventType.TAKE_DAMAGE_EVENT_HURT, listenerId(), event -> {
                State state = state(event.getPlayerPatch().getOriginal());
                state.stacks = Math.max(0, state.stacks - 2);
            });
        }

        @Override
        public List<Object> getTooltipArgsOfScreen(List<Object> list) {
            list.add(percent(perStack));
            list.add(maxStacks);
            list.add(percent(crescendo));
            list.add(Math.round(crescendoStamina));
            return list;
        }
    }

static final class LastBastion extends RotasPassive<LastBastion.State> {
        static final class State {
            long readyAt;
        }

        private float threshold = 0.35f;
        private float reduction = 0.3f;
        private float staminaPerHit = 1.5f;
        private int cooldown = 1800;

        LastBastion(SkillBuilder<? extends PassiveSkill> builder) {
            super(builder);
        }

        @Override
        State newState() {
            return new State();
        }

        @Override
        List<EventType<?>> listenedEvents() {
            return List.of(EventType.TAKE_DAMAGE_EVENT_HURT);
        }

        @Override
        public void setParams(CompoundTag tag) {
            super.setParams(tag);
            threshold = param(tag, "health_threshold", threshold);
            reduction = param(tag, "damage_reduction", reduction);
            staminaPerHit = param(tag, "stamina_per_hit", staminaPerHit);
            cooldown = (int) param(tag, "cheat_death_cooldown", cooldown);
        }

        @Override
        public void onInitiate(SkillContainer container) {
            super.onInitiate(container);
            container.getExecutor().getEventListener().addEventListener(EventType.TAKE_DAMAGE_EVENT_HURT, listenerId(), event -> {
                ServerPlayer player = event.getPlayerPatch().getOriginal();
                if (event.getDamageSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                    return;
                }
                State state = state(player);
                float health = player.getHealth();
                boolean low = health <= player.getMaxHealth() * threshold;
                float incoming = event.getDamage() * (low ? 1 - reduction : 1);
                long now = player.level().getGameTime();
                if (incoming >= health && now >= state.readyAt) {
                    event.attachValueModifier(ValueModifier.setter(Math.max(0, health - 1)));
                    state.readyAt = now + cooldown;
                    player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1));
                    burst(player, ParticleTypes.TOTEM_OF_UNDYING, 40, 0.6, SoundEvents.TOTEM_USE, 1.3f);
                    CombatSkillEffects.emit(player, player, "last_bastion", 1, 1);
                    return;
                }
                if (low) {
                    event.attachValueModifier(ValueModifier.multiplier(1 - reduction));
                    addStamina(event.getPlayerPatch(), staminaPerHit);
                }
            });
        }

        @Override
        public List<Object> getTooltipArgsOfScreen(List<Object> list) {
            list.add(percent(threshold));
            list.add(percent(reduction));
            list.add(cooldown / 20);
            return list;
        }
    }

static final class PredatorsMark extends RotasPassive<PredatorsMark.State> {
        static final class State {
            int marked = -1;
            int markedAt;
        }

        private float ambushBonus = 0.4f;
        private int markTicks = 100;

        PredatorsMark(SkillBuilder<? extends PassiveSkill> builder) {
            super(builder);
        }

        @Override
        State newState() {
            return new State();
        }

        @Override
        List<EventType<?>> listenedEvents() {
            return List.of(EventType.DEAL_DAMAGE_EVENT_HURT, EventType.PLAYER_KILLED_EVENT);
        }

        @Override
        public void setParams(CompoundTag tag) {
            super.setParams(tag);
            ambushBonus = param(tag, "ambush_bonus", ambushBonus);
            markTicks = (int) param(tag, "mark_ticks", markTicks);
        }

        static boolean ambush(Player player, LivingEntity target) {
            if (target instanceof Mob mob && mob.getTarget() != player) {
                return true;
            }
            Vec3 facing = target.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 toPlayer = player.position().subtract(target.position()).multiply(1, 0, 1).normalize();
            return facing.dot(toPlayer) < -0.3;
        }

        @Override
        public void onInitiate(SkillContainer container) {
            super.onInitiate(container);
            var listener = container.getExecutor().getEventListener();
            listener.addEventListener(EventType.DEAL_DAMAGE_EVENT_HURT, listenerId(), event -> {
                ServerPlayer player = event.getPlayerPatch().getOriginal();
                if (!ambush(player, event.getTarget())) {
                    return;
                }
                State state = state(player);
                state.marked = event.getTarget().getId();
                state.markedAt = player.tickCount;
                event.getDamageSource().attachDamageModifier(ValueModifier.multiplier(1 + ambushBonus));
                burst(event.getTarget(), ParticleTypes.DAMAGE_INDICATOR, 6, 0.3, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f);
                CombatSkillEffects.emit(player, event.getTarget(), "predators_mark", 0, 1, markTicks);
            });
            listener.addEventListener(EventType.PLAYER_KILLED_EVENT, listenerId(), event -> {
                ServerPlayer player = event.getPlayerPatch().getOriginal();
                State state = state(player);
                if (state.marked != event.getKilledEntity().getId() || player.tickCount - state.markedAt > markTicks) {
                    return;
                }
                state.marked = -1;
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false, true));
                addStamina(event.getPlayerPatch(), 4);
                burst(player, ParticleTypes.LARGE_SMOKE, 24, 0.4, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2f);
                CombatSkillEffects.emit(player, player, "predators_mark", 1, 1);
            });
        }

        @Override
        public List<Object> getTooltipArgsOfScreen(List<Object> list) {
            list.add(percent(ambushBonus));
            list.add(markTicks / 20);
            return list;
        }
    }

static final class Afterimage extends RotasPassive<Afterimage.State> {
        static final class State {
            int readyUntil = -1;
        }

        private float bonus = 0.45f;
        private int window = 60;
        private float stamina = 3f;

        Afterimage(SkillBuilder<? extends PassiveSkill> builder) {
            super(builder);
        }

        @Override
        State newState() {
            return new State();
        }

        @Override
        List<EventType<?>> listenedEvents() {
            return List.of(EventType.DODGE_SUCCESS_EVENT, EventType.DEAL_DAMAGE_EVENT_HURT);
        }

        @Override
        public void setParams(CompoundTag tag) {
            super.setParams(tag);
            bonus = param(tag, "counter_bonus", bonus);
            window = (int) param(tag, "window_ticks", window);
            stamina = param(tag, "stamina_restore", stamina);
        }

        @Override
        public void onInitiate(SkillContainer container) {
            super.onInitiate(container);
            var listener = container.getExecutor().getEventListener();
            listener.addEventListener(EventType.DODGE_SUCCESS_EVENT, listenerId(), event -> {
                ServerPlayer player = event.getPlayerPatch().getOriginal();
                state(player).readyUntil = player.tickCount + window;
                burst(player, ParticleTypes.CLOUD, 14, 0.35, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4f);
                CombatSkillEffects.emit(player, player, "afterimage", 0, 1, window);
            });
            listener.addEventListener(EventType.DEAL_DAMAGE_EVENT_HURT, listenerId(), event -> {
                ServerPlayer player = event.getPlayerPatch().getOriginal();
                State state = state(player);
                if (player.tickCount > state.readyUntil) {
                    return;
                }
                state.readyUntil = -1;
                event.getDamageSource().attachDamageModifier(ValueModifier.multiplier(1 + bonus));
                addStamina(event.getPlayerPatch(), stamina);
                burst(event.getTarget(), ParticleTypes.ENCHANTED_HIT, 20, 0.4, SoundEvents.ILLUSIONER_CAST_SPELL, 1.5f);
                CombatSkillEffects.emit(player, event.getTarget(), "afterimage", 1, 1);
            });
        }

        @Override
        public List<Object> getTooltipArgsOfScreen(List<Object> list) {
            list.add(window / 20);
            list.add(percent(bonus));
            list.add(Math.round(stamina));
            return list;
        }
    }

static final class ArcaneEdge extends RotasPassive<ArcaneEdge.State> {
        static final class State {
            int hits;
        }

        private static final ResourceLocation SPELL_POWER = ResourceLocation.tryParse("irons_spellbooks:spell_power");
        private int every = 4;
        private float share = 0.35f;
        private float radius = 3.5f;

        ArcaneEdge(SkillBuilder<? extends PassiveSkill> builder) {
            super(builder);
        }

        @Override
        State newState() {
            return new State();
        }

        @Override
        List<EventType<?>> listenedEvents() {
            return List.of(EventType.DEAL_DAMAGE_EVENT_HURT);
        }

        @Override
        public void setParams(CompoundTag tag) {
            super.setParams(tag);
            every = Math.max(1, (int) param(tag, "hits_per_burst", every));
            share = param(tag, "burst_share", share);
            radius = param(tag, "burst_radius", radius);
        }

        static double spellPower(Player player) {
            Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(SPELL_POWER);
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            return instance == null ? 1.0 : Math.max(0, instance.getValue());
        }

        @Override
        public void onInitiate(SkillContainer container) {
            super.onInitiate(container);
            container.getExecutor().getEventListener().addEventListener(EventType.DEAL_DAMAGE_EVENT_HURT, listenerId(), event -> {
                ServerPlayer player = event.getPlayerPatch().getOriginal();
                State state = state(player);
                if (++state.hits < every) {
                    return;
                }
                state.hits = 0;
                LivingEntity target = event.getTarget();
                float damage = (float) (event.getAttackDamage() * share * spellPower(player));
                for (LivingEntity nearby : target.level().getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(radius), entity -> entity != player && entity != target
                                && entity.isAlive() && !entity.isAlliedTo(player)
                                && !(entity instanceof Player other && !player.canHarmPlayer(other)))) {
                    nearby.hurt(player.damageSources().indirectMagic(player, player), damage);
                }
                burst(target, ParticleTypes.WITCH, 30, radius * 0.4, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f);
                CombatSkillEffects.emit(player, target, "arcane_edge", 1, radius);
            });
        }

        @Override
        public List<Object> getTooltipArgsOfScreen(List<Object> list) {
            list.add(every);
            list.add(percent(share));
            list.add(radius);
            return list;
        }
    }

static final class HuntersPatience extends RotasPassive<HuntersPatience.State> {
        static final class State {
            Vec3 lastPos = Vec3.ZERO;
            int stillTicks;
        }

        private static final Set<UUID> EQUIPPED = ConcurrentHashMap.newKeySet();
        private static HuntersPatience instance;
        private float focusBonus = 0.4f;
        private int focusTicks = 30;
        private float perBlock = 0.015f;
        private float rangeCap = 0.3f;

        HuntersPatience(SkillBuilder<? extends PassiveSkill> builder) {
            super(builder);
            instance = this;
        }

        @Override
        State newState() {
            return new State();
        }

        @Override
        List<EventType<?>> listenedEvents() {
            return List.of();
        }

        @Override
        public void setParams(CompoundTag tag) {
            super.setParams(tag);
            focusBonus = param(tag, "focus_bonus", focusBonus);
            focusTicks = (int) param(tag, "focus_ticks", focusTicks);
            perBlock = param(tag, "bonus_per_block", perBlock);
            rangeCap = param(tag, "range_bonus_cap", rangeCap);
        }

        @Override
        public void onInitiate(SkillContainer container) {
            super.onInitiate(container);
            if (server(container)) {
                EQUIPPED.add(container.getExecutor().getOriginal().getUUID());
            }
        }

        @Override
        public void onRemoved(SkillContainer container) {
            super.onRemoved(container);
            if (server(container)) {
                EQUIPPED.remove(container.getExecutor().getOriginal().getUUID());
            }
        }

        @Override
        public void updateContainer(SkillContainer container) {
            super.updateContainer(container);
            if (!server(container) || !(container.getExecutor().getOriginal() instanceof ServerPlayer player)) {
                return;
            }
            State state = state(player);
            boolean still = player.onGround() && player.position().distanceToSqr(state.lastPos) < 0.0004;
            state.lastPos = player.position();
            if (!still) {
                if (state.stillTicks >= focusTicks) CombatSkillEffects.clear(player, "hunters_patience");
                state.stillTicks = 0;
            } else if (++state.stillTicks == focusTicks) {
                player.playNotifySound(SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.7f, 1.6f);
                CombatSkillEffects.emit(player, player, "hunters_patience", 0, 1);
            } else if (state.stillTicks > focusTicks && (state.stillTicks - focusTicks) % 60 == 0) {
                CombatSkillEffects.emit(player, player, "hunters_patience", 0, 1);
            }
        }

        static void onHurt(LivingHurtEvent event) {
            if (instance == null || !(event.getSource().getDirectEntity() instanceof Projectile)
                    || !(event.getSource().getEntity() instanceof ServerPlayer player)
                    || !EQUIPPED.contains(player.getUUID())) {
                return;
            }
            State state = instance.state(player);
            double distance = player.distanceTo(event.getEntity());
            float bonus = (float) Math.min(instance.rangeCap, Math.max(0, distance - 10) * instance.perBlock);
            if (state.stillTicks >= instance.focusTicks) {
                bonus += instance.focusBonus;
                state.stillTicks = 0;
                burst(event.getEntity(), ParticleTypes.CRIT, 16, 0.3, SoundEvents.ARROW_HIT_PLAYER, 0.8f);
                CombatSkillEffects.emit(player, event.getEntity(), "hunters_patience", 1, 1);
            }
            if (bonus > 0) {
                event.setAmount(event.getAmount() * (1 + bonus));
            }
        }

        @Override
        public List<Object> getTooltipArgsOfScreen(List<Object> list) {
            list.add(String.format(java.util.Locale.ROOT, "%.1f", focusTicks / 20f));
            list.add(percent(focusBonus));
            list.add(String.format(java.util.Locale.ROOT, "%.1f", perBlock * 100));
            list.add(percent(rangeCap));
            return list;
        }
    }
}
