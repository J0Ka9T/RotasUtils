package net.schwarz.rotasutils.forge.magic;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.CelestialFxEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AbyssSpells {
    private AbyssSpells() {
    }

    abstract static class Dark extends CelestialSpells.Base {
        Dark(String name, CastType castType, SpellRarity rarity, int maxLevel, double cooldown,
             int baseMana, int manaPerLevel, int basePower, int powerPerLevel, int castTime) {
            super(AbyssMagic.SCHOOL_ID, name, castType, rarity, maxLevel, cooldown, baseMana, manaPerLevel,
                    basePower, powerPerLevel, castTime);
        }

        @Override
        SoundEvent finishSound() {
            return SoundEvents.SCULK_SHRIEKER_SHRIEK;
        }
    }

    static final class ShadowBolt extends Dark {
        ShadowBolt() {
            super("shadow_bolt", CastType.INSTANT, SpellRarity.COMMON, 10, 1.0, 12, 2, 7, 1, 0);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 look = caster.getLookAngle();
            Vec3 start = caster.getEyePosition().add(look.scale(0.6)).add(0, -0.2, 0);
            spawn(level, CelestialFxEntity.SHADOW_BOLT, caster, start, 30, 1.2f, damage(spellLevel, caster), spellLevel)
                    .target(assist(level, caster, start, 48));
            sound(level, start, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 1.6f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class UmbralGrasp extends Dark {
        UmbralGrasp() {
            super("umbral_grasp", CastType.INSTANT, SpellRarity.UNCOMMON, 8, 12, 35, 5, 5, 1, 0);
        }

        float radius(int level) {
            return 3.5f + 0.25f * level;
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.TOUCH_GROUND_ANIMATION;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage(level, caster), 1)),
                    Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(radius(level), 1)));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 at = aim(level, caster, 24);
            spawn(level, CelestialFxEntity.GRASP, caster, at, 50 + 5 * spellLevel, radius(spellLevel), damage(spellLevel, caster), spellLevel);
            sound(level, at, SoundEvents.BUCKET_EMPTY, 1.5f, 0.4f);
            sound(level, at, SoundEvents.WARDEN_EMERGE, 1f, 1.5f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class VoidRay extends Dark {
        static final float RANGE = 26f;
        private static final Map<UUID, CelestialFxEntity> BEAMS = new ConcurrentHashMap<>();

        VoidRay() {
            super("void_ray", CastType.CONTINUOUS, SpellRarity.RARE, 8, 12, 8, 1, 2, 1, 80);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.ANIMATION_CONTINUOUS_CAST_ONE_HANDED;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.rotasutils.damage_per_second", Utils.stringTruncation(damage(level, caster) * 4, 1)));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            CelestialFxEntity old = BEAMS.remove(caster.getUUID());
            if (old != null) old.discard();
            CelestialFxEntity beam = spawn(level, CelestialFxEntity.VOID_RAY, caster, caster.position(), 20 * 60, RANGE,
                    damage(spellLevel, caster), spellLevel);
            beam.refresh();
            BEAMS.put(caster.getUUID(), beam);
            sound(level, caster.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1f, 1.4f);
            super.onCast(level, spellLevel, caster, source, data);
        }

        @Override
        public void onServerCastTick(Level level, int spellLevel, LivingEntity caster, MagicData data) {
            CelestialFxEntity beam = BEAMS.get(caster.getUUID());
            if (beam != null && beam.isAlive()) beam.refresh();
            if (caster.tickCount % 5 != 0) return;
            Vec3 eye = caster.getEyePosition();
            Vec3 end = Utils.raycastForBlock(level, eye, eye.add(caster.getLookAngle().scale(RANGE)), ClipContext.Fluid.NONE).getLocation();
            float amount = damage(spellLevel, caster);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1))) {
                if (target == caster || target.getBoundingBox().inflate(0.6).clip(eye, end).isEmpty()) continue;
                if (!CelestialBehaviour.enemy(caster, target)) continue;
                CelestialBehaviour.hurt(this, target, amount, beam, caster);
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
            }
        }

        @Override
        public void onServerCastComplete(Level level, int spellLevel, LivingEntity caster, MagicData data, boolean cancelled) {
            CelestialFxEntity beam = BEAMS.remove(caster.getUUID());
            if (beam != null) beam.discard();
            super.onServerCastComplete(level, spellLevel, caster, data, cancelled);
        }
    }

    static final class EclipseNova extends Dark {
        EclipseNova() {
            super("eclipse_nova", CastType.INSTANT, SpellRarity.RARE, 8, 14, 40, 5, 6, 1, 0);
        }

        float radius(int level) {
            return 5f + 0.5f * level;
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
            spawn(level, CelestialFxEntity.ECLIPSE, caster, caster.position(), 46, radius(spellLevel), damage(spellLevel, caster), spellLevel);
            sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 2f, 0.5f);
            sound(level, caster.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 1f, 0.5f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class ShadeStep extends Dark {
        ShadeStep() {
            super("shade_step", CastType.INSTANT, SpellRarity.UNCOMMON, 6, 6, 25, 3, 4, 1, 0);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.ANIMATION_INSTANT_CAST;
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 from = caster.position();
            double reach = 9 + spellLevel;
            Vec3 eye = caster.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(reach)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 land = hit.getType() == HitResult.Type.MISS ? hit.getLocation()
                    : hit.getLocation().subtract(caster.getLookAngle().scale(0.8));
            Vec3 to = new Vec3(land.x, land.y - caster.getEyeHeight() + 0.1, land.z);
            caster.teleportTo(to.x, to.y, to.z);
            caster.fallDistance = 0;
            CelestialFxEntity fx = spawn(level, CelestialFxEntity.SHADE_STEP, caster, from, 18, 1f, 0, spellLevel);
            fx.target(to);
            for (LivingEntity target : CelestialBehaviour.victims(level, caster, to, 2.5)) {
                CelestialBehaviour.hurt(this, target, damage(spellLevel, caster), null, caster);
            }
            sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
            sound(level, to, SoundEvents.SCULK_CLICKING, 1.5f, 0.8f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class AbyssalPit extends Dark {
        AbyssalPit() {
            super("abyssal_pit", CastType.INSTANT, SpellRarity.EPIC, 6, 25, 70, 8, 2, 1, 0);
        }

        int duration(int level) {
            return 80 + 10 * level;
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.CHARGE_RAISED_HAND;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage(level, caster), 1)),
                    Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks(duration(level), 1)));
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 at = aim(level, caster, 24);
            spawn(level, CelestialFxEntity.PIT, caster, at, duration(spellLevel), 6f + 0.4f * spellLevel, damage(spellLevel, caster), spellLevel);
            sound(level, at, SoundEvents.RAVAGER_ROAR, 1.5f, 0.5f);
            sound(level, at, SoundEvents.GRINDSTONE_USE, 1.5f, 0.4f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class NightVeil extends Dark {
        NightVeil() {
            super("night_veil", CastType.INSTANT, SpellRarity.RARE, 5, 30, 40, 5, 1, 1, 0);
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.SELF_CAST_ANIMATION;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks(duration(level), 1)));
        }

        int duration(int level) {
            return 140 + 30 * level;
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            int d = duration(spellLevel);
            caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, d, 0, false, false, true));
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, d, spellLevel >= 3 ? 1 : 0, false, false, true));
            caster.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, d + 200, 0, false, false, true));
            for (LivingEntity target : CelestialBehaviour.victims(level, caster, caster.position(), 8)) {
                if (target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == caster) mob.setTarget(null);
            }
            spawn(level, CelestialFxEntity.VEIL, caster, caster.position(), d, 1f, 0, spellLevel);
            sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.7f);
            super.onCast(level, spellLevel, caster, source, data);
        }
    }

    static final class Oblivion extends Dark {
        private static final Map<UUID, CelestialFxEntity> CHARGES = new ConcurrentHashMap<>();

        Oblivion() {
            super("oblivion", CastType.LONG, SpellRarity.LEGENDARY, 5, 90, 150, 20, 32, 7, 60);
        }

        float radius(int level) {
            return 9f + 0.6f * level;
        }

        @Override
        public AnimationHolder getCastStartAnimation() {
            return SpellAnimations.CHARGE_RAISED_HAND;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(damage(level, caster), 1)),
                    Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(radius(level), 1)));
        }

        @Override
        public void onServerPreCast(Level level, int spellLevel, LivingEntity caster, MagicData data) {
            CelestialFxEntity old = CHARGES.remove(caster.getUUID());
            if (old != null) old.discard();
            CHARGES.put(caster.getUUID(), spawn(level, CelestialFxEntity.OBLIVION_CHARGE, caster, caster.position(),
                    getEffectiveCastTime(spellLevel, caster) + 2, 1f, 0, spellLevel));
            sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 3f, 0.6f);
            super.onServerPreCast(level, spellLevel, caster, data);
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
            Vec3 at = aim(level, caster, 30);
            spawn(level, CelestialFxEntity.OBLIVION, caster, at, 60, radius(spellLevel), damage(spellLevel, caster), spellLevel);
            sound(level, at, SoundEvents.WARDEN_SONIC_CHARGE, 4f, 0.4f);
            super.onCast(level, spellLevel, caster, source, data);
        }

        @Override
        public void onServerCastComplete(Level level, int spellLevel, LivingEntity caster, MagicData data, boolean cancelled) {
            CelestialFxEntity charge = CHARGES.remove(caster.getUUID());
            if (charge != null) charge.discard();
            super.onServerCastComplete(level, spellLevel, caster, data, cancelled);
        }
    }
}
