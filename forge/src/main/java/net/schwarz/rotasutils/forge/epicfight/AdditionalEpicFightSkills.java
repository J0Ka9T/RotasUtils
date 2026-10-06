package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.ZoneRuleService;
import net.schwarz.rotasutils.skill.PassiveHitWindow;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.api.forgeevent.SkillLootTableRegistryEvent;
import yesman.epicfight.api.utils.AttackResult;
import yesman.epicfight.data.loot.function.SetSkillFunction;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.passive.PassiveSkill;
import yesman.epicfight.world.entity.eventlistener.DealDamageEvent;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;
import yesman.epicfight.world.item.EpicFightItems;

import java.util.List;
import java.util.UUID;

public final class AdditionalEpicFightSkills {
    private AdditionalEpicFightSkills() {}

    private enum Kind {
        IRON_REBOUND, STORM_STEP, EXECUTIONERS_OATH, BLOOD_TITHE,
        FROSTBIND, GRAVITY_WELL, SUNFIRE_BRAND, ECHO_STRIKE;
        String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static void build(SkillBuildEvent.ModRegistryWorker worker) {
        for (Kind kind : Kind.values()) {
            worker.build(kind.id(), builder -> new CombatPassive(builder, kind), PassiveSkill.createPassiveBuilder());
        }
    }

    public static void addLoot(SkillLootTableRegistryEvent event) {
        book(event, EntityType.IRON_GOLEM, .04f, "iron_rebound");
        book(event, EntityType.PHANTOM, .03f, "storm_step");
        book(event, EntityType.WITHER_SKELETON, .04f, "executioners_oath");
        book(event, EntityType.HUSK, .02f, "blood_tithe");
        book(event, EntityType.STRAY, .03f, "frostbind");
        book(event, EntityType.ENDERMAN, .03f, "gravity_well");
        book(event, EntityType.BLAZE, .03f, "sunfire_brand");
        book(event, EntityType.VINDICATOR, .04f, "echo_strike");
    }

    private static void book(SkillLootTableRegistryEvent event, EntityType<?> type, float chance, String id) {
        event.add(type, LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(LootItem.lootTableItem(EpicFightItems.SKILLBOOK.get())
                        .apply(SetSkillFunction.builder(Rotasutils.MOD_ID + ":" + id))));
    }

    static final class State {
        final PassiveHitWindow hits = new PassiveHitWindow();
        long armedUntil;
        long readyAt;
        Receipt pending;
    }

    private record Receipt(LivingDamageEvent event, LivingEntity target, long tick, boolean payoff, long charge) {}

    static final class CombatPassive extends RotasEpicFight.RotasPassive<State> {
        private final Kind kind;
        private float bonus;
        private float threshold = .30f;
        private float heal = 2f;
        private float knockback = .8f;
        private float radius = 3f;
        private float pull = .35f;
        private float stamina = 2f;
        private int hitsRequired;
        private int window;
        private int cooldown;
        private int slowTicks = 40;
        private int slowAmplifier;
        private int fireSeconds = 2;

        CombatPassive(SkillBuilder<? extends PassiveSkill> builder, Kind kind) {
            super(builder);
            this.kind = kind;
            bonus = switch (kind) { case IRON_REBOUND -> .30f; case EXECUTIONERS_OATH -> .35f; default -> .25f; };
            hitsRequired = switch (kind) { case BLOOD_TITHE, GRAVITY_WELL -> 4; default -> 3; };
            window = switch (kind) { case IRON_REBOUND, STORM_STEP, ECHO_STRIKE -> 60; default -> 100; };
            cooldown = switch (kind) {
                case IRON_REBOUND, STORM_STEP -> 40;
                case FROSTBIND, ECHO_STRIKE -> 80;
                case GRAVITY_WELL -> 120;
                default -> 100;
            };
        }

        @Override State newState() { return new State(); }
        @Override List<EventType<?>> listenedEvents() {
            return switch (kind) {
                case IRON_REBOUND -> List.of(EventType.DEAL_DAMAGE_EVENT_DAMAGE, EventType.TAKE_DAMAGE_EVENT_ATTACK);
                case STORM_STEP -> List.of(EventType.DEAL_DAMAGE_EVENT_DAMAGE, EventType.DODGE_SUCCESS_EVENT);
                default -> List.of(EventType.DEAL_DAMAGE_EVENT_DAMAGE);
            };
        }

        @Override public void setParams(CompoundTag tag) {
            super.setParams(tag);
            bonus = bounded(tag, "bonus_damage", bonus, 0, 2);
            threshold = bounded(tag, "health_threshold", threshold, .01f, 1);
            heal = bounded(tag, "heal_amount", heal, 0, 6);
            knockback = bounded(tag, "knockback", knockback, 0, 2);
            radius = bounded(tag, "radius", radius, .5f, 6);
            pull = bounded(tag, "pull_strength", pull, 0, .6f);
            stamina = bounded(tag, "stamina_restore", stamina, 0, 6);
            hitsRequired = (int) bounded(tag, "hits_required", hitsRequired, 2, 20);
            window = (int) bounded(tag, "window_ticks", window, 10, 400);
            cooldown = (int) bounded(tag, "cooldown_ticks", cooldown, 20, 2400);
            slowTicks = (int) bounded(tag, "slow_ticks", slowTicks, 1, 100);
            slowAmplifier = (int) bounded(tag, "slow_amplifier", slowAmplifier, 0, 1);
            fireSeconds = (int) bounded(tag, "fire_seconds", fireSeconds, 1, 4);
        }

        private static float bounded(CompoundTag tag, String key, float fallback, float min, float max) {
            float value = param(tag, key, fallback);
            return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
        }

        @Override public void onInitiate(SkillContainer container) {
            super.onInitiate(container);
            if (!server(container)) return;
            var listener = container.getExecutor().getEventListener();
            if (kind == Kind.IRON_REBOUND) {
                listener.addEventListener(EventType.TAKE_DAMAGE_EVENT_ATTACK, listenerId(), event -> {
                    if (event.getResult() == AttackResult.ResultType.BLOCKED && event.getDamage() > 0
                            && !event.getDamageSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                        arm(event.getPlayerPatch().getOriginal());
                    }
                });
            }
            if (kind == Kind.STORM_STEP) {
                listener.addEventListener(EventType.DODGE_SUCCESS_EVENT, listenerId(), event -> {
                    if (!event.getDamageSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                        arm(event.getPlayerPatch().getOriginal());
                    }
                });
            }
            listener.addEventListener(EventType.DEAL_DAMAGE_EVENT_DAMAGE, listenerId(), this::reserve);
        }

        private void arm(ServerPlayer player) {
            State state = state(player);
            long now = player.level().getGameTime();
            if (now < state.readyAt) return;
            state.armedUntil = now + window;
            state.readyAt = now + cooldown;
            CombatSkillEffects.emit(player, player, kind.id(), 0, 1, window);
            player.level().playSound(null, player.blockPosition(), kind == Kind.IRON_REBOUND
                    ? SoundEvents.SHIELD_BLOCK : SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, .45f, 1.3f);
        }

        private void reserve(DealDamageEvent.Damage event) {
            ServerPlayer player = event.getPlayerPatch().getOriginal();
            LivingEntity target = event.getTarget();
            LivingDamageEvent damage = event.getForgeEvent();
            if (damage.isCanceled() || damage.getAmount() <= 0 || !Float.isFinite(damage.getAmount())
                    || event.getDamageSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    || !legalTarget(player, target)) return;
            State state = state(player);
            if (state.pending != null) return;
            long now = player.level().getGameTime();
            boolean payoff = false;
            switch (kind) {
                case IRON_REBOUND, STORM_STEP -> {
                    if (state.armedUntil == 0 || now > state.armedUntil) return;
                    payoff = true;
                }
                case EXECUTIONERS_OATH -> {
                    if (now < state.readyAt || target.getHealth() > target.getMaxHealth() * threshold) return;
                    payoff = true;
                }
                case ECHO_STRIKE -> payoff = state.hits.finisherReady(target.getUUID(), now, hitsRequired, window);
                case SUNFIRE_BRAND -> { if (target.fireImmune()) return; }
                default -> { }
            }
            if (payoff && (kind == Kind.IRON_REBOUND || kind == Kind.EXECUTIONERS_OATH || kind == Kind.ECHO_STRIKE)) {
                damage.setAmount(damage.getAmount() * (1 + bonus));
            }
            state.pending = new Receipt(damage, target, now, payoff, state.armedUntil);
        }

        @Override public void updateContainer(SkillContainer container) {
            super.updateContainer(container);
            if (!server(container)) return;
            ServerPlayer player = (ServerPlayer) container.getExecutor().getOriginal();
            State state = state(player);
            Receipt receipt = state.pending;
            long now = player.level().getGameTime();
            if (receipt == null || now <= receipt.tick()) return;
            state.pending = null;
            if (!player.isAlive() || receipt.event().isCanceled() || receipt.event().getAmount() <= 0
                    || !Float.isFinite(receipt.event().getAmount())) return;
            LivingEntity target = receipt.target();
            boolean payoff = receipt.payoff();
            switch (kind) {
                case IRON_REBOUND, STORM_STEP -> {
                    if (state.armedUntil == receipt.charge()) state.armedUntil = 0;
                    if (kind == Kind.STORM_STEP && target.isAlive() && legalTarget(player, target)) {
                        Vec3 away = player.position().subtract(target.position());
                        target.knockback(knockback, away.x, away.z);
                    }
                }
                case EXECUTIONERS_OATH -> state.readyAt = receipt.tick() + cooldown;
                case ECHO_STRIKE -> {
                    payoff = state.hits.hit(target.getUUID(), receipt.tick(), hitsRequired + 1, window, cooldown, true);
                    if (payoff) RotasEpicFight.addStamina(container.getExecutor(), stamina);
                }
                default -> {
                    payoff = state.hits.hit(target.getUUID(), receipt.tick(), hitsRequired, window, cooldown, false);
                    if (payoff) applyUtility(player, target);
                }
            }
            if (payoff) {
                LivingEntity visualTarget = kind == Kind.BLOOD_TITHE ? player : target;
                CombatSkillEffects.emit(player, visualTarget, kind.id(), 1, kind == Kind.GRAVITY_WELL ? radius : 1);
                var sound = switch (kind) {
                    case IRON_REBOUND -> SoundEvents.SHIELD_BREAK;
                    case STORM_STEP -> SoundEvents.TRIDENT_RIPTIDE_1;
                    case EXECUTIONERS_OATH -> SoundEvents.PLAYER_ATTACK_CRIT;
                    case BLOOD_TITHE -> SoundEvents.BEACON_POWER_SELECT;
                    case FROSTBIND -> SoundEvents.GLASS_BREAK;
                    case GRAVITY_WELL -> SoundEvents.ENDER_EYE_DEATH;
                    case SUNFIRE_BRAND -> SoundEvents.BLAZE_SHOOT;
                    case ECHO_STRIKE -> SoundEvents.PLAYER_ATTACK_SWEEP;
                };
                player.level().playSound(null, visualTarget.blockPosition(), sound, SoundSource.PLAYERS, .6f, 1.25f);
            }
        }

        private void applyUtility(ServerPlayer player, LivingEntity target) {
            if (kind == Kind.BLOOD_TITHE) {
                player.heal(Math.min(heal, Math.max(0, player.getMaxHealth() - player.getHealth())));
                return;
            }
            if (!target.isAlive() || target.level() != player.level() || !legalTarget(player, target)) return;
            switch (kind) {
                case FROSTBIND -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        slowTicks, slowAmplifier, false, false, true));
                case SUNFIRE_BRAND -> { if (!target.fireImmune()) target.setSecondsOnFire(fireSeconds); }
                case GRAVITY_WELL -> {
                    for (LivingEntity enemy : player.level().getEntitiesOfClass(LivingEntity.class,
                            target.getBoundingBox().inflate(radius))) {
                        if (enemy == target || !enemy.isAlive() || !legalTarget(player, enemy)
                                || !(enemy instanceof Enemy || enemy instanceof Player
                                    || enemy instanceof Mob mob && mob.getTarget() == player)
                                || enemy.distanceToSqr(target) > radius * radius
                                || !player.hasLineOfSight(enemy) || !target.hasLineOfSight(enemy)) continue;
                        Vec3 offset = target.position().subtract(enemy.position()).multiply(1, 0, 1);
                        if (offset.lengthSqr() < .01) continue;
                        double resistance = Math.min(1, Math.max(0, enemy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
                        Vec3 force = offset.normalize().scale(pull * (1 - resistance));
                        enemy.push(force.x, 0, force.z);
                        enemy.hurtMarked = true;
                    }
                }
                default -> { }
            }
        }

        @Override public List<Object> getTooltipArgsOfScreen(List<Object> list) {
            switch (kind) {
                case IRON_REBOUND -> { list.add(window / 20f); list.add(percent(bonus)); list.add(cooldown / 20f); }
                case STORM_STEP -> { list.add(window / 20f); list.add(knockback); list.add(cooldown / 20f); }
                case EXECUTIONERS_OATH -> { list.add(percent(threshold)); list.add(percent(bonus)); list.add(cooldown / 20f); }
                case BLOOD_TITHE -> { list.add(hitsRequired); list.add(window / 20f); list.add(heal); list.add(cooldown / 20f); }
                case FROSTBIND -> { list.add(hitsRequired); list.add(window / 20f); list.add(slowTicks / 20f); list.add(cooldown / 20f); }
                case GRAVITY_WELL -> { list.add(hitsRequired); list.add(window / 20f); list.add(radius); list.add(cooldown / 20f); }
                case SUNFIRE_BRAND -> { list.add(hitsRequired); list.add(window / 20f); list.add(fireSeconds); list.add(cooldown / 20f); }
                case ECHO_STRIKE -> { list.add(hitsRequired); list.add(window / 20f); list.add(percent(bonus)); list.add(stamina); list.add(cooldown / 20f); }
            }
            return list;
        }
    }

    static boolean legalTarget(ServerPlayer source, LivingEntity target) {
        if (source == target || target.isRemoved() || target.level() != source.level()
                || target.isAlliedTo(source) || source.isAlliedTo(target)
                || target.isSpectator() || target instanceof Player player && player.isCreative()) return false;
        if (target instanceof TamableAnimal pet && pet.isTame()) return false;
        if (target instanceof Player player && (!source.canHarmPlayer(player)
                || !source.server.isPvpAllowed() || ZoneRuleService.blocksPvp(target, source.damageSources().playerAttack(source)))) return false;
        if (target instanceof ServerPlayer other && RotasData.instance() != null) {
            var own = RotasData.instance().peek(source.getUUID());
            var theirs = RotasData.instance().peek(other.getUUID());
            UUID party = own == null ? null : own.partyId();
            if (party != null && theirs != null && party.equals(theirs.partyId())) return false;
        }
        return true;
    }
}

