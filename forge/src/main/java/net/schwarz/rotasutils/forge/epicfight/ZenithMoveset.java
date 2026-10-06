package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.ZenithBladeEntity;
import net.schwarz.rotasutils.item.ZenithItem;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.gameasset.ColliderPreset;
import yesman.epicfight.gameasset.EpicFightSkills;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;

public final class ZenithMoveset {
    private static final float SLAM_TIME = 0.7F;
    private static final int RING_BLADES = 8;
    private static final double RING_RADIUS = 7.0;

    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO1;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO2;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO3;

    private ZenithMoveset() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ZenithMoveset::registerAnimations);
        modBus.addListener(ZenithMoveset::registerPreset);
    }

    private static void registerAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(Rotasutils.MOD_ID, builder -> {
            build(builder);
            ExoMoveset.build(builder);
            CapoeiraMoveset.build(builder);
        });
    }

    private static void build(AnimationManager.AnimationBuilder builder) {
        AUTO1 = builder.nextAccessor("biped/combat/zenith_auto1", accessor ->
                new BasicAttackAnimation(0.1F, 0.22F, 0.36F, 0.55F, null, toolR(), accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 2.4F));
        AUTO2 = builder.nextAccessor("biped/combat/zenith_auto2", accessor ->
                new BasicAttackAnimation(0.1F, 0.23F, 0.37F, 0.52F, null, toolR(), accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 2.4F));
        AUTO3 = builder.nextAccessor("biped/combat/zenith_auto3", accessor ->
                new BasicAttackAnimation(0.12F, accessor, Armatures.BIPED,
                        new AttackAnimation.Phase(0.0F, 0.25F, 0.48F, 0.5F, 0.5F, toolR(), null),
                        new AttackAnimation.Phase(0.5F, 0.55F, 0.72F, 1.0F, Float.MAX_VALUE, toolR(), null))
                        .<ValueModifier, BasicAttackAnimation>addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.3F))
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 2.4F)
                        .addEvents(
                                AnimationEvent.InTimeEvent.create(SLAM_TIME, Animations.ReusableSources.FRACTURE_GROUND_SIMPLE, AnimationEvent.Side.CLIENT)
                                        .params(new Vec3f(0.0F, -0.2F, -1.0F), toolR(), 1.3D, SLAM_TIME),
                                AnimationEvent.InTimeEvent.create(SLAM_TIME, (entitypatch, animation, params) ->
                                        bladeRing(entitypatch.getOriginal()), AnimationEvent.Side.SERVER)));
    }

    private static Joint toolR() {
        return ((HumanoidArmature) Armatures.BIPED.get()).toolR;
    }

    private static void bladeRing(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        float damage = (float) (user.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? user.getAttributeValue(Attributes.ATTACK_DAMAGE) : 8.0) * 0.5F;
        Vec3 center = user.position().add(0, 1.0, 0);
        int salt = user.getRandom().nextInt();
        float yaw = (float) Math.toRadians(user.getYRot());
        for (int i = 0; i < RING_BLADES; i++) {
            double angle = yaw + i * (Math.PI * 2 / RING_BLADES);
            Vec3 target = center.add(-Math.sin(angle) * RING_RADIUS, 0, Math.cos(angle) * RING_RADIUS);
            ZenithBladeEntity.loose(level, user, target, ZenithItem.bladeFor(i, salt), damage);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, user.getX(), user.getY() + 0.2, user.getZ(), 12, 1.6, 0.05, 1.6, 0);
        level.sendParticles(ParticleTypes.END_ROD, user.getX(), user.getY() + 0.3, user.getZ(), 40, 0.4, 0.1, 0.4, 0.35);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    private static void registerPreset(WeaponCapabilityPresetRegistryEvent event) {
        event.getTypeEntry().put(new ResourceLocation(Rotasutils.MOD_ID, "zenith"), item -> WeaponCapability.builder()
                .category(CapabilityItem.WeaponCategories.SWORD)
                .styleProvider(patch -> CapabilityItem.Styles.ONE_HAND)
                .collider(ColliderPreset.LONGSWORD)
                .canBePlacedOffhand(false)
                .swingSound(EpicFightSounds.WHOOSH_SHARP.get())
                .hitSound(EpicFightSounds.BLADE_HIT.get())
                .hitParticle(EpicFightParticles.HIT_BLADE.get())
                .newStyleCombo(CapabilityItem.Styles.ONE_HAND, AUTO1, AUTO2, AUTO3, Animations.SWORD_DASH, Animations.SWORD_AIR_SLASH)
                .newStyleCombo(CapabilityItem.Styles.MOUNT, Animations.SWORD_MOUNT_ATTACK)
                .innateSkill(CapabilityItem.Styles.ONE_HAND, stack -> EpicFightSkills.SWEEPING_EDGE));
    }
}
