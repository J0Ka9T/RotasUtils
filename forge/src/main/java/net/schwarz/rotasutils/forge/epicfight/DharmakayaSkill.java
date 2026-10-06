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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.schwarz.rotasutils.core.DharmakayaRules;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillBuilder;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.world.entity.eventlistener.DealDamageEvent;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DharmakayaSkill extends Skill {
    public static final String ID = "dharmakaya";
    static final Set<UUID> ACTIVE = new java.util.HashSet<>();
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Set<UUID> APPLYING = new java.util.HashSet<>();
    private static final UUID LISTENER = UUID.nameUUIDFromBytes(("rotasutils:" + ID).getBytes(StandardCharsets.UTF_8));

    private int radius = DharmakayaRules.DOMAIN_RADIUS;
    private int duration = DharmakayaRules.DURATION_TICKS;
    private int cooldown = DharmakayaRules.COOLDOWN_SECONDS;

    private record Echo(int targetId, long due, float damage) {
    }

    private static final class State {
        final List<Echo> echoes = new ArrayList<>();
    }

    public DharmakayaSkill(SkillBuilder<? extends Skill> builder) {
        super(builder);
        consumption = cooldown;
        maxDuration = duration;
        maxStackSize = 1;
    }

    static void build(SkillBuildEvent.ModRegistryWorker worker) {
        worker.build(ID, DharmakayaSkill::new, Skill.createIdentityBuilder()
                .setActivateType(ActivateType.DURATION).setResource(Resource.COOLDOWN));
    }

    @Override public void setParams(CompoundTag tag) {
        super.setParams(tag);
        if (tag.contains("radius")) radius = DharmakayaRules.clampRadius(tag.getInt("radius"));
        if (tag.contains("duration")) duration = Math.max(60, Math.min(DharmakayaRules.DURATION_TICKS, tag.getInt("duration")));
        if (tag.contains("cooldown")) cooldown = Math.max(10, Math.min(600, tag.getInt("cooldown")));
        consumption = cooldown;
        maxDuration = duration;
        maxStackSize = 1;
    }

    @Override public boolean isExecutableState(PlayerPatch<?> p) {
        return p.isEpicFightMode() && super.isExecutableState(p) && p.getOriginal().isAlive();
    }

    @Override public boolean checkExecuteCondition(SkillContainer c) {
        if (c.isActivated()) return false;
        var p = c.getExecutor();
        if (p.isLogicalClient()) return true;
        return !ACTIVE.contains(((ServerPlayer) p.getOriginal()).getUUID());
    }

    @Override public void onInitiate(SkillContainer c) {
        super.onInitiate(c);
        if (c.getExecutor().isLogicalClient()) return;
        states(c.getExecutor().getOriginal().getUUID());
        c.getExecutor().getEventListener().addEventListener(EventType.DEAL_DAMAGE_EVENT_HURT, LISTENER, this::onDealt);
    }

    @Override public void executeOnServer(SkillContainer c, FriendlyByteBuf args) {
        var patch = c.getServerExecutor();
        var player = patch.getOriginal();
        if (c.isActivated()) return;
        super.executeOnServer(c, args);
        c.activate();
        ACTIVE.add(player.getUUID());
        states(player.getUUID());
        CombatSkillEffects.emit(player, player, ID, 0, 1, duration, radius);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.1f, .55f);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, .7f);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .9f, .5f);
        }
        patch.playAnimationSynchronized(Animations.SWORD_GUARD, .2f);
    }

    private void onDealt(DealDamageEvent.Hurt event) {
        var patch = event.getPlayerPatch();
        if (patch.isLogicalClient()) return;
        ServerPlayer player = (ServerPlayer) patch.getOriginal();
        UUID id = player.getUUID();
        if (!ACTIVE.contains(id) || APPLYING.contains(id)) return;
        LivingEntity target = event.getTarget();
        if (!AdditionalEpicFightSkills.legalTarget(player, target)
                || !DharmakayaRules.inside(target.distanceToSqr(player), radius)) return;
        float amount = (float) DharmakayaRules.echoDamage(event.getAttackDamage());
        states(id).echoes.add(new Echo(target.getId(), player.level().getGameTime() + DharmakayaRules.ECHO_DELAY_TICKS, amount));
    }

    @Override public void updateContainer(SkillContainer c) {
        super.updateContainer(c);
        if (c.getExecutor().isLogicalClient()) return;
        ServerPlayer player = c.getServerExecutor().getOriginal();
        long now = player.level().getGameTime();
        State state = STATES.get(player.getUUID());
        if (state != null && !state.echoes.isEmpty()) {
            Iterator<Echo> it = state.echoes.iterator();
            while (it.hasNext()) {
                Echo echo = it.next();
                if (now < echo.due()) continue;
                it.remove();
                if (!(player.level().getEntity(echo.targetId()) instanceof LivingEntity target) || !target.isAlive()
                        || !AdditionalEpicFightSkills.legalTarget(player, target)) continue;
                UUID id = player.getUUID();
                APPLYING.add(id);
                try {
                    var source = c.getServerExecutor().getDamageSource(Animations.SWORD_AUTO1, InteractionHand.MAIN_HAND)
                            .setStunType(StunType.NONE)
                            .addRuntimeTag(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN);
                    target.hurt(source, echo.damage());
                } finally {
                    APPLYING.remove(id);
                }
                if (target.level() instanceof ServerLevel level) {
                    level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, .6f);
                    level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .7f, .7f);
                    Vec3 away = target.position().subtract(player.position());
                    if (away.lengthSqr() > 1e-6) target.knockback((float) DharmakayaRules.ECHO_MULTIPLIER * .2f, away.x, away.z);
                }
                CombatSkillEffects.emit(player, target, ID, 1, 1, 12);
            }
        }
        if (c.isActivated()) {
            ACTIVE.add(player.getUUID());
        } else if (ACTIVE.contains(player.getUUID())) {
            close(player);
        }
    }

    @Override public void cancelOnServer(SkillContainer c, FriendlyByteBuf args) {
        if (!c.getExecutor().isLogicalClient()) close(c.getServerExecutor().getOriginal());
        super.cancelOnServer(c, args);
    }

    @Override public void onRemoved(SkillContainer c) {
        if (!c.getExecutor().isLogicalClient()) close(c.getExecutor().getOriginal());
        c.getExecutor().getEventListener().removeListener(EventType.DEAL_DAMAGE_EVENT_HURT, LISTENER);
        super.onRemoved(c);
    }

    private static void close(net.minecraft.world.entity.player.Player player) {
        ACTIVE.remove(player.getUUID());
        State state = STATES.remove(player.getUUID());
        if (state != null) state.echoes.clear();
        APPLYING.remove(player.getUUID());
        CombatSkillEffects.clear(player, ID);
    }

    private static State states(UUID id) {
        return STATES.computeIfAbsent(id, key -> new State());
    }

    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player);
            player.getPersistentData().remove("rotasutils.dharmakayaRadius");
        }
    }

    @Override public List<Object> getTooltipArgsOfScreen(List<Object> list) {
        list.add(radius);
        list.add(duration / 20f);
        list.add(DharmakayaRules.ECHO_DELAY_TICKS / 20f);
        list.add(DharmakayaRules.ECHO_MULTIPLIER);
        return list;
    }
}
