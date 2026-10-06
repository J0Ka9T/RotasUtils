package net.schwarz.rotasutils.forge.magic;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.CelestialFxEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CelestialSpells {
    private CelestialSpells() {
    }

    abstract static class Base extends AbstractSpell {
        private final ResourceLocation id;
        private final DefaultConfig config;
        private final CastType castType;

        Base(String name, CastType castType, SpellRarity rarity, int maxLevel, double cooldown,
             int baseMana, int manaPerLevel, int basePower, int powerPerLevel, int castTime) {
            this(CelestialMagic.SCHOOL_ID, name, castType, rarity, maxLevel, cooldown, baseMana, manaPerLevel,
                    basePower, powerPerLevel, castTime);
        }

        Base(ResourceLocation school, String name, CastType castType, SpellRarity rarity, int maxLevel, double cooldown,
             int baseMana, int manaPerLevel, int basePower, int powerPerLevel, int castTime) {
            this.id = new ResourceLocation(Rotasutils.MOD_ID, name);
            this.castType = castType;
            this.config = new DefaultConfig().setMinRarity(rarity).setSchoolResource(school)
                    .setMaxLevel(maxLevel).setCooldownSeconds(cooldown).build();
            this.baseManaCost = baseMana;
            this.manaCostPerLevel = manaPerLevel;
            this.baseSpellPower = basePower;
            this.spellPowerPerLevel = powerPerLevel;
            this.castTime = castTime;
        }

        @Override
        public ResourceLocation getSpellResource() {
            return id;
        }

        @Override
        public DefaultConfig getDefaultConfig() {
            return config;
        }

        @Override
        public CastType getCastType() {
            return castType;
        }

        @Override
        public Optional<SoundEvent> getCastFinishSound() {
            return Optional.of(finishSound());
        }

        SoundEvent finishSound() {
            return SoundEvents.AMETHYST_BLOCK_RESONATE;
        }

        float damage(int level, LivingEntity caster) {
            return getSpellPower(level, caster);
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage",
                    Utils.stringTruncation(damage(level, caster), 1)));
        }

        CelestialFxEntity spawn(Level level, int kind, Entity owner, Vec3 at, int life, float radius, float damage, int spellLevel) {
            CelestialFxEntity fx = CelestialFxEntity.create(level, kind, owner, at, life, radius);
            fx.damage = damage;
            fx.spellLevel = spellLevel;
            level.addFreshEntity(fx);
            return fx;
        }

        static Vec3 aim(Level level, LivingEntity caster, float range) {
            HitResult hit = Utils.raycastForEntity(level, caster, range, true);
            if (hit instanceof EntityHitResult entityHit) return entityHit.getEntity().position();
            Vec3 at = hit.getLocation().subtract(caster.getLookAngle().scale(0.3));
            return ground(level, at);
        }

        static Vec3 ground(Level level, Vec3 at) {
            Vec3 from = at.add(0, 0.5, 0);
            HitResult down = level.clip(new net.minecraft.world.level.ClipContext(from, from.add(0, -48, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    (Entity) null));
            return down.getType() == HitResult.Type.MISS ? at : down.getLocation();
        }

        static Vec3 assist(Level level, LivingEntity caster, Vec3 start, float range) {
            Vec3 look = caster.getLookAngle();
            LivingEntity best = null;
            double bestDot = Math.cos(Math.toRadians(6));
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                    caster.getBoundingBox().expandTowards(look.scale(range)).inflate(3))) {
                if (!CelestialBehaviour.enemy(caster, candidate) || !caster.hasLineOfSight(candidate)) continue;
                Vec3 to = candidate.getBoundingBox().getCenter().subtract(start);
                if (to.lengthSqr() > range * range) continue;
                double dot = to.normalize().dot(look);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = candidate;
                }
            }
            return best == null ? look : best.getBoundingBox().getCenter().subtract(start).normalize();
        }

        static void sound(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    static final class AstralBolt extends Base {
        AstralBolt() {
            super("astral_bolt", CastType.INSTANT, SpellRarity.COMMON, 10, 1.0, 12, 2, 7, 1, 0);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 look = caster.getLookAngle();
            Vec3 start = caster.getEyePosition().add(look.scale(0.6)).add(0, -0.2, 0);
            CelestialFxEntity fx = spawn(level, CelestialFxEntity.BOLT, caster, start, 30, 1f, damage(spellLevel, caster), spellLevel);
            fx.target(assist(level, caster, start, 48));
            sound(level, start, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.6f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class NovaBurst extends Base {
        NovaBurst() {
            super("nova_burst", CastType.INSTANT, SpellRarity.UNCOMMON, 8, 10, 30, 5, 6, 1, 0);
        }

        float radius(int level) {
            return 4.5f + 0.5f * level;
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.SELF_CAST_TWO_HANDS;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage(level, caster), 1)),
                    Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(radius(level), 1)));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            float r = radius(spellLevel);
            spawn(level, CelestialFxEntity.NOVA, caster, caster.position(), 22, r, 0, spellLevel);
            float amount = damage(spellLevel, caster);
            for (LivingEntity target : CelestialBehaviour.victims(level, caster, caster.position(), r)) {
                CelestialBehaviour.hurt(this, target, amount, null, caster);
                Vec3 push = target.position().subtract(caster.position()).normalize().scale(1.1);
                target.push(push.x, 0.45, push.z);
                target.hurtMarked = true;
            }
            sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 2f, 0.6f);
            sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 1.8f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class PrismRay extends Base {
        static final float RANGE = 28f;
        private static final Map<UUID, CelestialFxEntity> BEAMS = new ConcurrentHashMap<>();

        PrismRay() {
            super("prism_ray", CastType.CONTINUOUS, SpellRarity.RARE, 8, 12, 8, 1, 2, 1, 80);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.ANIMATION_CONTINUOUS_CAST_ONE_HANDED;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.rotasutils.damage_per_second",
                    Utils.stringTruncation(damage(level, caster) * 4, 1)));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            CelestialFxEntity old = BEAMS.remove(caster.getUUID());
            if (old != null) old.discard();
            CelestialFxEntity beam = spawn(level, CelestialFxEntity.RAY, caster, caster.position(), 20 * 60, RANGE,
                    damage(spellLevel, caster), spellLevel);
            beam.refresh();
            BEAMS.put(caster.getUUID(), beam);
            sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.9f);
            super.onCast(level, spellLevel, caster, source, data);
        }

        @Override
        public void onServerCastTick(Level level, int spellLevel, LivingEntity caster, MagicData data) {
            CelestialFxEntity beam = BEAMS.get(caster.getUUID());
            if (beam != null && beam.isAlive()) beam.refresh();
            if (caster.tickCount % 5 != 0) return;
            Vec3 eye = caster.getEyePosition();
            Vec3 end = Utils.raycastForBlock(level, eye, eye.add(caster.getLookAngle().scale(RANGE)),
                    net.minecraft.world.level.ClipContext.Fluid.NONE).getLocation();
            float amount = damage(spellLevel, caster);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(eye, end).inflate(1))) {
                if (target == caster) continue;
                if (target.getBoundingBox().inflate(0.6).clip(eye, end).isEmpty()) continue;
                if (!CelestialBehaviour.enemy(caster, target)) continue;
                CelestialBehaviour.hurt(this, target, amount, beam, caster);
            }
            if (caster.tickCount % 10 == 0) sound(level, end, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
        }

        @Override
        public void onServerCastComplete(Level level, int spellLevel, LivingEntity caster, MagicData data, boolean cancelled) {
            CelestialFxEntity beam = BEAMS.remove(caster.getUUID());
            if (beam != null) beam.discard();
            super.onServerCastComplete(level, spellLevel, caster, data, cancelled);
        }
    }

    static final class StarlanceVolley extends Base {
        StarlanceVolley() {
            super("starlance_volley", CastType.LONG, SpellRarity.RARE, 8, 14, 45, 6, 5, 1, 20);
        }

        int count(int level) {
            return Math.min(8, 3 + level / 2);
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage(level, caster), 1)),
                    Component.translatable("ui.irons_spellbooks.projectile_count", count(level)));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 target = aim(level, caster, 32);
            Vec3 back = caster.getLookAngle().multiply(1, 0, 1).normalize().scale(-1.3);
            Vec3 side = new Vec3(-back.z, 0, back.x).normalize();
            int n = count(spellLevel);
            float amount = damage(spellLevel, caster);
            for (int i = 0; i < n; i++) {
                float spread = n == 1 ? 0 : (i / (float) (n - 1) - 0.5f) * 2;
                Vec3 rack = caster.position().add(back).add(side.scale(spread * 2.4)).add(0, 2.4 + 0.7 * (1 - Math.abs(spread)), 0);
                Vec3 aimAt = target.add((level.random.nextFloat() - 0.5) * 1.6, 0, (level.random.nextFloat() - 0.5) * 1.6);
                int delay = 6 + i * 3;
                CelestialFxEntity lance = spawn(level, CelestialFxEntity.LANCE, caster, rack, delay + 16, 2.2f, amount, spellLevel);
                lance.target(aimAt).delay(delay).seed(level.random.nextInt());
            }
            sound(level, caster.position(), SoundEvents.TRIDENT_RIPTIDE_3, 1f, 1.4f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class CometDash extends Base {
        CometDash() {
            super("comet_dash", CastType.INSTANT, SpellRarity.UNCOMMON, 6, 7, 25, 3, 5, 1, 0);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.ANIMATION_INSTANT_CAST;
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 look = caster.getLookAngle();
            float speed = 2.0f + 0.12f * spellLevel;
            caster.setDeltaMovement(look.x * speed, Math.max(0.25, look.y * speed * 0.6 + 0.2), look.z * speed);
            caster.hurtMarked = true;
            caster.fallDistance = 0;
            caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, false));
            spawn(level, CelestialFxEntity.COMET, caster, caster.position(), 14, 2f, damage(spellLevel, caster), spellLevel);
            sound(level, caster.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 1.2f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class ConstellationBind extends Base {
        ConstellationBind() {
            super("constellation_bind", CastType.INSTANT, SpellRarity.EPIC, 6, 18, 55, 6, 5, 1, 0);
        }

        float radius(int level) {
            return 4f + 0.35f * level;
        }

        int duration(int level) {
            return 60 + 10 * level;
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.TOUCH_GROUND_ANIMATION;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage(level, caster), 1)),
                    Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(radius(level), 1)),
                    Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks(duration(level), 1)));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 at = aim(level, caster, 24);
            spawn(level, CelestialFxEntity.BIND, caster, at, duration(spellLevel), radius(spellLevel),
                    damage(spellLevel, caster), spellLevel);
            sound(level, at, SoundEvents.ENCHANTMENT_TABLE_USE, 1.5f, 0.7f);
            sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 0.5f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class AuroraWard extends Base {
        AuroraWard() {
            super("aurora_ward", CastType.INSTANT, SpellRarity.RARE, 5, 30, 40, 5, 1, 1, 0);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.SELF_CAST_ANIMATION;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.absorption", Utils.stringTruncation(absorb(level), 0)),
                    Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks(200, 1)));
        }

        int absorb(int level) {
            return 4 * (level + 1);
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, Math.max(0, spellLevel), false, false, true));
            caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, spellLevel >= 4 ? 1 : 0, false, false, true));
            caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
            spawn(level, CelestialFxEntity.WARD, caster, caster.position(), 200, 1f, 0, spellLevel);
            sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.5f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class Supernova extends Base {
        private static final Map<UUID, CelestialFxEntity> CHARGES = new ConcurrentHashMap<>();

        Supernova() {
            super("supernova", CastType.LONG, SpellRarity.LEGENDARY, 5, 60, 120, 15, 30, 6, 50);
        }

        float radius(int level) {
            return 7f + 0.6f * level;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage(level, caster), 1)),
                    Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(radius(level), 1)));
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.CHARGE_RAISED_HAND;
        }

        @Override
        public void onServerPreCast(Level level, int spellLevel, LivingEntity caster, MagicData data) {
            CelestialFxEntity old = CHARGES.remove(caster.getUUID());
            if (old != null) old.discard();
            CHARGES.put(caster.getUUID(), spawn(level, CelestialFxEntity.CHARGE, caster, caster.position(),
                    getEffectiveCastTime(spellLevel, caster) + 2, 1f, 0, spellLevel));
            sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 2f, 1.6f);
            super.onServerPreCast(level, spellLevel, caster, data);
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 at = aim(level, caster, 30);
            spawn(level, CelestialFxEntity.SUPERNOVA, caster, at, 40, radius(spellLevel), damage(spellLevel, caster), spellLevel);
            if (level instanceof ServerLevel) {
                sound(level, at, SoundEvents.GENERIC_EXPLODE, 4f, 0.6f);
                sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 1.4f);
                sound(level, at, SoundEvents.BEACON_ACTIVATE, 3f, 2f);
            }
            super.onCast(level, spellLevel, caster, source, data);
        }

        @Override
        public void onServerCastComplete(Level level, int spellLevel, LivingEntity caster, MagicData data, boolean cancelled) {
            CelestialFxEntity charge = CHARGES.remove(caster.getUUID());
            if (charge != null) charge.discard();
            super.onServerCastComplete(level, spellLevel, caster, data, cancelled);
        }
    }

    static float clamp01(float v) {
        return Mth.clamp(v, 0, 1);
    }
}
