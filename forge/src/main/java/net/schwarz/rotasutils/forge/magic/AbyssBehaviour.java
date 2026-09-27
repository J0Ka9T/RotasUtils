package net.schwarz.rotasutils.forge.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.CelestialFxEntity;
import org.jetbrains.annotations.Nullable;

/** Server-side consequences of Abyss visuals: shadow bolt hits, grasp roots, the pit's pull, Oblivion's blast. */
final class AbyssBehaviour implements CelestialFxEntity.Behaviour {
    @Override
    public void impact(CelestialFxEntity fx, Vec3 at, @Nullable Entity hit) {
        if (fx.kind() != CelestialFxEntity.SHADOW_BOLT) return;
        Level level = fx.level();
        LivingEntity owner = fx.owner() instanceof LivingEntity living ? living : null;
        if (hit instanceof LivingEntity target && CelestialBehaviour.enemy(owner, target)) {
            CelestialBehaviour.hurt(AbyssMagic.SHADOW_BOLT.get(), target, fx.damage, fx, owner);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
        }
        level.addFreshEntity(CelestialFxEntity.create(level, CelestialFxEntity.INK_SPLASH, owner, at, 70, 1.2f));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLOW_SQUID_SQUIRT, SoundSource.PLAYERS, 1f, 0.7f);
    }

    @Override
    public void tick(CelestialFxEntity fx) {
        Level level = fx.level();
        LivingEntity owner = fx.owner() instanceof LivingEntity living ? living : null;
        switch (fx.kind()) {
            case CelestialFxEntity.GRASP -> {
                for (LivingEntity target : CelestialBehaviour.victims(level, owner, fx.position(), fx.radius())) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6, false, false, true));
                    if (fx.tickCount == 13 || (fx.tickCount > 13 && fx.tickCount % 20 == 0)) {
                        CelestialBehaviour.hurt(AbyssMagic.UMBRAL_GRASP.get(), target,
                                fx.tickCount == 13 ? fx.damage : fx.damage * 0.3f, fx, owner);
                    }
                }
            }
            case CelestialFxEntity.PIT -> {
                Vec3 heart = fx.position().add(0, 0.4, 0);
                boolean collapse = fx.tickCount == fx.life() - 2;
                for (LivingEntity target : CelestialBehaviour.victims(level, owner, heart, fx.radius())) {
                    Vec3 pull = heart.subtract(target.position());
                    double d = Math.max(0.6, pull.length());
                    Vec3 step = pull.normalize().scale(Math.min(0.35, 0.9 / d));
                    target.setDeltaMovement(target.getDeltaMovement().scale(0.6).add(step));
                    target.hurtMarked = true;
                    if (fx.tickCount % 10 == 0) CelestialBehaviour.hurt(AbyssMagic.ABYSSAL_PIT.get(), target, fx.damage * 0.5f, fx, owner);
                    if (collapse) CelestialBehaviour.hurt(AbyssMagic.ABYSSAL_PIT.get(), target, fx.damage * 3, fx, owner);
                }
                if (collapse) burst(level, heart, 2.5f, owner);
            }
            case CelestialFxEntity.ECLIPSE -> {
                // The Watcher blinks at tick 26: darkness bursts out with the blink.
                if (fx.tickCount != 26) return;
                for (LivingEntity target : CelestialBehaviour.victims(level, owner, fx.position(), fx.radius())) {
                    CelestialBehaviour.hurt(AbyssMagic.ECLIPSE_NOVA.get(), target, fx.damage, fx, owner);
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    Vec3 push = target.position().subtract(fx.position()).normalize();
                    target.push(push.x, 0.35, push.z);
                    target.hurtMarked = true;
                }
                level.playSound(null, fx.getX(), fx.getY(), fx.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 0.7f);
            }
            case CelestialFxEntity.OBLIVION -> {
                float r = fx.radius();
                Vec3 heart = fx.position().add(0, 3, 0);
                if (fx.tickCount < 25) {
                    for (LivingEntity target : CelestialBehaviour.victims(level, owner, heart, r * 1.3)) {
                        Vec3 pull = heart.subtract(target.position()).normalize().scale(0.08);
                        target.setDeltaMovement(target.getDeltaMovement().add(pull));
                        target.hurtMarked = true;
                    }
                    return;
                }
                if (fx.tickCount != 26) return;
                for (LivingEntity target : CelestialBehaviour.victims(level, owner, fx.position(), r)) {
                    double d = target.position().distanceTo(fx.position());
                    float falloff = (float) (1 - 0.5 * Math.min(1, d / r));
                    CelestialBehaviour.hurt(AbyssMagic.OBLIVION.get(), target, fx.damage * falloff, fx, owner);
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                    Vec3 away = target.position().subtract(fx.position()).normalize();
                    target.push(away.x * 1.8, 0.9, away.z * 1.8);
                    target.hurtMarked = true;
                }
                level.playSound(null, fx.getX(), fx.getY(), fx.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5f, 0.5f);
                level.playSound(null, fx.getX(), fx.getY(), fx.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4f, 0.5f);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SCULK_SOUL, fx.getX(), fx.getY() + 2, fx.getZ(), 80, r * 0.4, 1.5, r * 0.4, 0.15);
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, fx.getX(), fx.getY() + 1, fx.getZ(), 120, r * 0.5, 1, r * 0.5, 0.1);
                }
            }
            default -> {
            }
        }
    }

    private static void burst(Level level, Vec3 at, float radius, @Nullable LivingEntity owner) {
        level.addFreshEntity(CelestialFxEntity.create(level, CelestialFxEntity.VOID_BURST, owner, at, 14, radius));
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
        }
    }
}
