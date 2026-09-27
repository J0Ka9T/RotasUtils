package net.schwarz.rotasutils.forge.magic;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.CelestialFxEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side consequences of Celestial visuals: bolt and lance hits, the comet's path, bind, supernova. */
final class CelestialBehaviour implements CelestialFxEntity.Behaviour {
    /** Creatures each comet already struck, so a dash hits a target once. */
    private final Map<Integer, Set<Integer>> cometHits = new ConcurrentHashMap<>();

    @Override
    public void impact(CelestialFxEntity fx, Vec3 at, @Nullable Entity hit) {
        Level level = fx.level();
        LivingEntity owner = fx.owner() instanceof LivingEntity living ? living : null;
        if (fx.kind() == CelestialFxEntity.BOLT) {
            AbstractSpell spell = CelestialMagic.ASTRAL_BOLT.get();
            if (hit instanceof LivingEntity target && enemy(owner, target)) hurt(spell, target, fx.damage, fx, owner);
            burst(level, at, 1.1f, owner);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.6f);
        } else if (fx.kind() == CelestialFxEntity.LANCE) {
            AbstractSpell spell = CelestialMagic.STARLANCE_VOLLEY.get();
            for (LivingEntity target : victims(level, owner, at, fx.radius())) hurt(spell, target, fx.damage, fx, owner);
            burst(level, at, 1.6f, owner);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, 1.3f);
        }
    }

    @Override
    public void tick(CelestialFxEntity fx) {
        Level level = fx.level();
        LivingEntity owner = fx.owner() instanceof LivingEntity living ? living : null;
        switch (fx.kind()) {
            case CelestialFxEntity.COMET -> {
                if (owner == null) return;
                owner.fallDistance = 0;
                Set<Integer> hits = cometHits.computeIfAbsent(fx.getId(), id -> new HashSet<>());
                for (LivingEntity target : victims(level, owner, owner.position().add(0, 1, 0), fx.radius())) {
                    if (!hits.add(target.getId())) continue;
                    hurt(CelestialMagic.COMET_DASH.get(), target, fx.damage, fx, owner);
                    Vec3 away = target.position().subtract(owner.position()).normalize();
                    target.push(away.x * 0.9, 0.5, away.z * 0.9);
                    target.hurtMarked = true;
                    burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0), 0.9f, owner);
                }
                if (fx.tickCount >= fx.life()) cometHits.remove(fx.getId());
            }
            case CelestialFxEntity.BIND -> {
                for (LivingEntity target : victims(level, owner, fx.position(), fx.radius())) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6, false, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 6, 0, false, false, false));
                    if (fx.tickCount % 20 == 0) hurt(CelestialMagic.CONSTELLATION_BIND.get(), target, fx.damage * 0.6f, fx, owner);
                }
            }
            case CelestialFxEntity.SUPERNOVA -> {
                if (fx.tickCount != 3) return;
                float r = fx.radius();
                for (LivingEntity target : victims(level, owner, fx.position(), r)) {
                    double d = target.position().distanceTo(fx.position());
                    float falloff = (float) (1 - 0.5 * Math.min(1, d / r));
                    hurt(CelestialMagic.SUPERNOVA.get(), target, fx.damage * falloff, fx, owner);
                    Vec3 away = target.position().subtract(fx.position()).normalize();
                    target.push(away.x * 1.6, 0.8, away.z * 1.6);
                    target.hurtMarked = true;
                }
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.FLASH, fx.getX(), fx.getY() + 1, fx.getZ(), 1, 0, 0, 0, 0);
                    server.sendParticles(ParticleTypes.END_ROD, fx.getX(), fx.getY() + 1, fx.getZ(), 120, r * 0.4, 1.5, r * 0.4, 0.4);
                }
            }
            default -> {
            }
        }
    }

    /** Living, hostile-to-the-caster creatures within {@code r} of {@code at}. */
    static List<LivingEntity> victims(Level level, @Nullable LivingEntity owner, Vec3 at, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> e.isAlive() && e.position().distanceToSqr(at) <= r * r && enemy(owner, e));
    }

    static boolean enemy(@Nullable LivingEntity owner, LivingEntity target) {
        if (target == owner || target instanceof ArmorStand || target.isSpectator()) return false;
        return owner == null || !DamageSources.isFriendlyFireBetween(owner, target);
    }

    static void hurt(AbstractSpell spell, LivingEntity target, float amount, @Nullable Entity via, @Nullable LivingEntity owner) {
        if (amount <= 0) return;
        DamageSources.applyDamage(target, amount, spell.getDamageSource(via == null ? owner : via, owner));
    }

    private static void burst(Level level, Vec3 at, float radius, @Nullable LivingEntity owner) {
        CelestialFxEntity fx = CelestialFxEntity.create(level, CelestialFxEntity.IMPACT, owner, at, 12, radius);
        level.addFreshEntity(fx);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.12);
        }
    }
}
