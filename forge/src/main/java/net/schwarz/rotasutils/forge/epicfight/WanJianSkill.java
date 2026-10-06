package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.schwarz.rotasutils.core.WanJianTimeline;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WanJianSkill extends Skill {
    public static final String ID = "wanjian";

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        long castAt = Long.MIN_VALUE;
        int pulse;
        boolean commanded;
        Vec3 center = Vec3.ZERO;
    }

    private float damage = 6f;
    private float radius = (float) WanJianTimeline.RAIN_RADIUS;

    public WanJianSkill(SkillBuilder<? extends Skill> builder) {
        super(builder);
        consumption = 30;
        maxDuration = WanJianTimeline.LIFE;
        maxStackSize = 1;
    }

    static void build(SkillBuildEvent.ModRegistryWorker worker) {
        worker.build(ID, WanJianSkill::new, Skill.createIdentityBuilder()
                .setActivateType(ActivateType.DURATION).setResource(Resource.COOLDOWN));
    }

    @Override public void setParams(CompoundTag tag) {
        super.setParams(tag);
        if (tag.contains("damage")) {
            float n = tag.getFloat("damage");
            damage = Float.isFinite(n) ? Math.max(1, Math.min(40, n)) : 6f;
        }
        if (tag.contains("radius")) {
            float n = tag.getFloat("radius");
            radius = Float.isFinite(n) ? Math.max(8, Math.min(48, n)) : (float) WanJianTimeline.RAIN_RADIUS;
        }
        consumption = 30;
        maxDuration = WanJianTimeline.LIFE;
        maxStackSize = 1;
    }

    @Override public boolean isExecutableState(PlayerPatch<?> p) {
        return p.isEpicFightMode() && super.isExecutableState(p) && p.getOriginal().isAlive();
    }

    @Override public boolean checkExecuteCondition(SkillContainer c) {
        return !c.isActivated();
    }

    @Override public void executeOnServer(SkillContainer c, FriendlyByteBuf args) {
        var patch = c.getServerExecutor();
        var player = patch.getOriginal();
        if (c.isActivated()) return;
        super.executeOnServer(c, args);
        c.activate();
        State state = states(player.getUUID());
        state.castAt = player.level().getGameTime();
        state.pulse = 0;
        state.commanded = false;
        state.center = player.position();
        CombatSkillEffects.emit(player, player, ID, 0, 1, WanJianTimeline.LIFE, radius);
        CombatSkillEffects.emit(player, player, ID, HitVfx.SUMMON, 1, HitVfx.SUMMON_LIFE);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, .6f);
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, .8f, .8f);
        }
        patch.playAnimationSynchronized(Animations.SWORD_GUARD, .2f);
    }

    @Override public void updateContainer(SkillContainer c) {
        super.updateContainer(c);
        if (c.getExecutor().isLogicalClient()) return;
        ServerPlayer player = c.getServerExecutor().getOriginal();
        UUID id = player.getUUID();
        if (!c.isActivated()) {
            if (STATES.containsKey(id)) close(player);
            return;
        }
        State state = STATES.get(id);
        if (state == null || state.castAt == Long.MIN_VALUE) return;
        long age = player.level().getGameTime() - state.castAt;

        Vec3 center = state.center;
        if (!state.commanded && age >= WanJianTimeline.COMMAND) {
            state.commanded = true;
            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, net.minecraft.core.BlockPos.containing(center), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.1f, .7f);
            }
        }
        while (state.pulse < WanJianTimeline.RAIN_PULSES && age >= WanJianTimeline.damageTick(state.pulse)) {
            state.pulse++;
            boolean last = state.pulse == WanJianTimeline.RAIN_PULSES;
            int hits = 0;
            for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(center, center).inflate(radius, 12, radius))) {
                if (!AdditionalEpicFightSkills.legalTarget(player, target)
                        || target.distanceToSqr(center) > (double) radius * radius) continue;
                var source = c.getServerExecutor().getDamageSource(Animations.SWORD_AUTO1, InteractionHand.MAIN_HAND)
                        .setStunType(StunType.NONE)
                        .addRuntimeTag(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN);
                target.hurt(source, damage);
                CombatSkillEffects.emit(player, target, ID, last ? HitVfx.HEAVY : HitVfx.HIT, 1,
                        last ? HitVfx.HIT_LIFE + 6 : HitVfx.HIT_LIFE);
                hits++;
                if (last) {
                    Vec3 away = target.position().subtract(center);
                    if (away.lengthSqr() > 1e-6) target.knockback(.7f, away.x, away.z);
                }
            }
            if (player.level() instanceof ServerLevel level) {
                if (hits > 0) level.playSound(null, net.minecraft.core.BlockPos.containing(center), SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.PLAYERS, .55f, .8f + level.random.nextFloat() * .3f);
                if (last) {
                level.playSound(null, net.minecraft.core.BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 1.8f, .6f);
                level.playSound(null, net.minecraft.core.BlockPos.containing(center),
                        SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.2f, 1.4f);
            } else if (state.pulse % 5 == 0) {
                level.playSound(null, net.minecraft.core.BlockPos.containing(center), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 1.1f, .5f + state.pulse * .02f);
            }
            }
        }
        if (age >= WanJianTimeline.LIFE) c.deactivate();
    }

    @Override public void cancelOnServer(SkillContainer c, FriendlyByteBuf args) {
        if (!c.getExecutor().isLogicalClient()) close(c.getServerExecutor().getOriginal());
        super.cancelOnServer(c, args);
    }

    @Override public void onRemoved(SkillContainer c) {
        if (!c.getExecutor().isLogicalClient()) close(c.getExecutor().getOriginal());
        super.onRemoved(c);
    }

    private static void close(net.minecraft.world.entity.player.Player player) {
        STATES.remove(player.getUUID());
        CombatSkillEffects.clear(player, ID);
    }

    private static State states(UUID id) {
        return STATES.computeIfAbsent(id, key -> new State());
    }

    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) close(player);
    }

    @Override public java.util.List<Object> getTooltipArgsOfScreen(java.util.List<Object> list) {
        list.add(WanJianTimeline.SWORDS);
        list.add(radius);
        list.add(damage * WanJianTimeline.RAIN_PULSES);
        list.add(WanJianTimeline.LIFE / 20);
        return list;
    }
}
