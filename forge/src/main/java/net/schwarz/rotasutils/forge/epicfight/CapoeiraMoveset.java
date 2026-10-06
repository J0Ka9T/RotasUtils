package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.schwarz.rotasutils.Rotasutils;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.OBBCollider;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.gameasset.ColliderPreset;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.weaponinnate.SimpleWeaponInnateSkill;
import yesman.epicfight.skill.weaponinnate.WeaponInnateSkill;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;

public final class CapoeiraMoveset {
    public static AnimationManager.AnimationAccessor<StaticAnimation> GINGA;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO1;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO2;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO3;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO4;
    public static AnimationManager.AnimationAccessor<DashAttackAnimation> DASH;
    public static AnimationManager.AnimationAccessor<AttackAnimation> RODA;
    public static Skill RODA_SKILL;

    private static final float BASIS_SPEED = 4.0F;

    private CapoeiraMoveset() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(CapoeiraMoveset::registerPreset);
    }

    private static HumanoidArmature biped() {
        return (HumanoidArmature) Armatures.BIPED.get();
    }

    private static Collider leg(double reach) {
        return new OBBCollider(0.3, reach, 0.3, 0.0, 0.2, 0.0);
    }

    static void build(AnimationManager.AnimationBuilder builder) {
        Joint legR = biped().legR, legL = biped().legL;
        GINGA = builder.nextAccessor("biped/living/capoeira_ginga", accessor -> new StaticAnimation(true, accessor, Armatures.BIPED));
        AUTO1 = builder.nextAccessor("biped/combat/capoeira_auto1", accessor ->
                new BasicAttackAnimation(0.1F, 0.36F, 0.55F, 0.8F, leg(0.6), legR, accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, BASIS_SPEED));
        AUTO2 = builder.nextAccessor("biped/combat/capoeira_auto2", accessor ->
                new BasicAttackAnimation(0.1F, 0.36F, 0.52F, 0.8F, leg(0.6), legL, accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, BASIS_SPEED));
        AUTO3 = builder.nextAccessor("biped/combat/capoeira_auto3", accessor ->
                new BasicAttackAnimation(0.1F, 0.3F, 0.5F, 0.8F, leg(0.7), legR, accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, BASIS_SPEED));
        AUTO4 = builder.nextAccessor("biped/combat/capoeira_auto4", accessor ->
                new BasicAttackAnimation(0.1F, 0.6F, 0.82F, 1.0F, leg(0.7), legR, accessor, Armatures.BIPED)
                        .<ValueModifier, BasicAttackAnimation>addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.4F))
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, BASIS_SPEED));
        DASH = builder.nextAccessor("biped/combat/capoeira_dash", accessor ->
                new DashAttackAnimation(0.1F, accessor, Armatures.BIPED,
                        new AttackAnimation.Phase(0.0F, 0.4F, 0.85F, 1.1F, Float.MAX_VALUE, legR, leg(0.7)))
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, BASIS_SPEED));
        RODA = builder.nextAccessor("biped/skill/capoeira_skill", accessor ->
                new AttackAnimation(0.12F, accessor, Armatures.BIPED,
                        new AttackAnimation.Phase(0.0F, 0.35F, 0.9F, 1.0F, 1.0F, legR, leg(0.7)),
                        new AttackAnimation.Phase(1.0F, 1.55F, 1.82F, 2.0F, Float.MAX_VALUE, legR, leg(0.8)))
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, BASIS_SPEED)
                        .addEvents(
                                AnimationEvent.InTimeEvent.create(0.5F, (patch, animation, params) -> whirl(patch.getOriginal(), 0.2), AnimationEvent.Side.SERVER),
                                AnimationEvent.InTimeEvent.create(0.75F, (patch, animation, params) -> whirl(patch.getOriginal(), 0.2), AnimationEvent.Side.SERVER),
                                AnimationEvent.InTimeEvent.create(1.65F, (patch, animation, params) -> whirl(patch.getOriginal(), 1.0), AnimationEvent.Side.SERVER),
                                AnimationEvent.InTimeEvent.create(2.0F, (patch, animation, params) -> land(patch.getOriginal()), AnimationEvent.Side.SERVER)));
    }

    private static void whirl(LivingEntity user, double height) {
        if (user.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, user.getX(), user.getY() + height, user.getZ(), 3, 0.7, 0.05, 0.7, 0);
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.1F);
        }
    }

    private static void land(LivingEntity user) {
        if (user.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, user.getX(), user.getY() + 0.1, user.getZ(), 14, 0.7, 0.05, 0.7, 0.04);
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 0.5F, 1.3F);
        }
    }

    static void buildSkills(SkillBuildEvent.ModRegistryWorker worker) {
        WeaponInnateSkill roda = worker.build("capoeira_roda", SimpleWeaponInnateSkill::new,
                SimpleWeaponInnateSkill.createSimpleWeaponInnateBuilder().setAnimations(RODA));
        roda.newProperty()
                .addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.1F))
                .addProperty(AnimationProperty.AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.multiplier(1.5F))
                .addProperty(AnimationProperty.AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(2.0F));
        RODA_SKILL = roda;
    }

    private static void registerPreset(WeaponCapabilityPresetRegistryEvent event) {
        event.getTypeEntry().put(ResourceLocation.fromNamespaceAndPath(Rotasutils.MOD_ID, "capoeira"), item -> WeaponCapability.builder()
                .category(CapabilityItem.WeaponCategories.FIST)
                .styleProvider(patch -> CapabilityItem.Styles.COMMON)
                .collider(ColliderPreset.FIST)
                .canBePlacedOffhand(false)
                .swingSound(EpicFightSounds.WHOOSH.get())
                .hitSound(EpicFightSounds.BLUNT_HIT.get())
                .hitParticle(EpicFightParticles.HIT_BLUNT.get())
                .newStyleCombo(CapabilityItem.Styles.COMMON, AUTO1, AUTO2, AUTO3, AUTO4, DASH, Animations.FIST_AIR_SLASH)
                .livingMotionModifier(CapabilityItem.Styles.COMMON, LivingMotions.IDLE, GINGA)
                .innateSkill(CapabilityItem.Styles.COMMON, stack -> RODA_SKILL));
    }
}
