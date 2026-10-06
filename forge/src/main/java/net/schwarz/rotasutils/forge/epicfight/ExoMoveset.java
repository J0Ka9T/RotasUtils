package net.schwarz.rotasutils.forge.epicfight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.ExoBeamEntity;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.AimAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.MultiOBBCollider;
import yesman.epicfight.api.collider.OBBCollider;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.weaponinnate.SimpleWeaponInnateSkill;
import yesman.epicfight.skill.weaponinnate.WeaponInnateSkill;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;

public final class ExoMoveset {
    private static final float BLAST_TIME = 0.28F;
    private static final double BLAST_RANGE = 4.5;
    private static final float LANCE_TIME = 0.2F;

    public static AnimationManager.AnimationAccessor<StaticAnimation> HOLD;
    public static AnimationManager.AnimationAccessor<StaticAnimation> HOLD_RUN;
    public static AnimationManager.AnimationAccessor<AimAnimation> AIM;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO1;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO2;
    public static AnimationManager.AnimationAccessor<BasicAttackAnimation> AUTO3;
    public static AnimationManager.AnimationAccessor<AttackAnimation> OVERDRIVE;
    public static Skill OVERDRIVE_SKILL;

    private ExoMoveset() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ExoMoveset::registerPreset);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ExoMotionClient.init();
        }
    }

    private static Joint toolR() {
        return ((HumanoidArmature) Armatures.BIPED.get()).toolR;
    }

    private static Collider gun() {
        return new MultiOBBCollider(3, 0.25, 0.5, 0.25, 0.0, 0.07, -0.25);
    }

    static void build(AnimationManager.AnimationBuilder builder) {
        HOLD = builder.nextAccessor("biped/living/exo_hold", accessor -> new StaticAnimation(true, accessor, Armatures.BIPED));
        HOLD_RUN = builder.nextAccessor("biped/living/exo_hold_run", accessor -> new StaticAnimation(true, accessor, Armatures.BIPED));
        AIM = builder.nextAccessor("biped/combat/exo_aim", accessor -> new AimAnimation(true, accessor,
                "biped/combat/exo_aim_mid", "biped/combat/exo_aim_up", "biped/combat/exo_aim_down", "biped/combat/exo_aim_lying",
                Armatures.BIPED));
        AUTO1 = builder.nextAccessor("biped/combat/exo_auto1", accessor ->
                new BasicAttackAnimation(0.12F, 0.17F, 0.27F, 0.5F, gun(), toolR(), accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 1.0F));
        AUTO2 = builder.nextAccessor("biped/combat/exo_auto2", accessor ->
                new BasicAttackAnimation(0.12F, 0.2F, 0.35F, 0.55F, gun(), toolR(), accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 1.0F));
        AUTO3 = builder.nextAccessor("biped/combat/exo_auto3", accessor ->
                new BasicAttackAnimation(0.12F, 0.18F, 0.3F, 0.62F, gun(), toolR(), accessor, Armatures.BIPED)
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 1.0F)
                        .addEvents(AnimationEvent.InTimeEvent.create(BLAST_TIME, (entitypatch, animation, params) ->
                                pointBlank(entitypatch.getOriginal()), AnimationEvent.Side.SERVER)));
        Collider blast = new OBBCollider(0.9, 1.4, 0.9, 0.0, 1.6, -0.3);
        OVERDRIVE = builder.nextAccessor("biped/skill/exo_overdrive", accessor ->
                new AttackAnimation(0.15F, accessor, Armatures.BIPED,
                        new AttackAnimation.Phase(0.0F, 1.28F, 1.4F, 1.9F, Float.MAX_VALUE, toolR(), blast))
                        .addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, 1.0F)
                        .addEvents(AnimationEvent.InTimeEvent.create(LANCE_TIME, (entitypatch, animation, params) -> {
                            if (entitypatch.getOriginal().level() instanceof ServerLevel level) {
                                ExoBeamEntity.overdrive(level, entitypatch.getOriginal());
                            }
                        }, AnimationEvent.Side.SERVER)));
    }

    private static void pointBlank(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getViewVector(1f);
        float damage = (float) (user.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? user.getAttributeValue(Attributes.ATTACK_DAMAGE) : 6.0) * 0.8F;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                user.getBoundingBox().expandTowards(look.scale(BLAST_RANGE)).inflate(1.2),
                e -> e != user && e.isAlive() && !e.isAlliedTo(user)
                        && !(e instanceof Player other && user instanceof Player p && !p.canHarmPlayer(other)))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (to.length() <= BLAST_RANGE + 1 && to.normalize().dot(look) > 0.6) {
                target.hurt(user.damageSources().indirectMagic(user, user), damage);
                target.knockback(0.6, -look.x, -look.z);
            }
        }
        Vec3 muzzle = eye.add(look.scale(1.2)).add(0, -0.35, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 30, 0.35, 0.35, 0.35, 0.4);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, muzzle.x, muzzle.y, muzzle.z, 14, 0.25, 0.25, 0.25, 0.08);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.7F);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.9F);
    }

    static void buildSkills(SkillBuildEvent.ModRegistryWorker worker) {
        WeaponInnateSkill overdrive = worker.build("exo_overdrive", SimpleWeaponInnateSkill::new,
                SimpleWeaponInnateSkill.createSimpleWeaponInnateBuilder().setAnimations(OVERDRIVE));
        overdrive.newProperty()
                .addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.5F))
                .addProperty(AnimationProperty.AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.multiplier(2.0F))
                .addProperty(AnimationProperty.AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(4.0F));
        OVERDRIVE_SKILL = overdrive;
    }

    private static void registerPreset(WeaponCapabilityPresetRegistryEvent event) {
        event.getTypeEntry().put(ResourceLocation.fromNamespaceAndPath(Rotasutils.MOD_ID, "exo_disintegrator"), item -> {
            WeaponCapability.Builder builder = WeaponCapability.builder()
                    .category(CapabilityItem.WeaponCategories.SPEAR)
                    .styleProvider(patch -> CapabilityItem.Styles.TWO_HAND)
                    .collider(gun())
                    .canBePlacedOffhand(false)
                    .swingSound(EpicFightSounds.WHOOSH_BIG.get())
                    .hitSound(EpicFightSounds.BLUNT_HIT.get())
                    .hitParticle(EpicFightParticles.HIT_BLUNT.get())
                    .newStyleCombo(CapabilityItem.Styles.TWO_HAND, AUTO1, AUTO2, AUTO3, Animations.SPEAR_DASH, Animations.SPEAR_TWOHAND_AIR_SLASH)
                    .innateSkill(CapabilityItem.Styles.TWO_HAND, stack -> OVERDRIVE_SKILL);
            for (var motion : new yesman.epicfight.api.animation.LivingMotion[]{LivingMotions.IDLE, LivingMotions.WALK,
                    LivingMotions.CHASE, LivingMotions.SNEAK, LivingMotions.KNEEL, LivingMotions.FLOAT, LivingMotions.FALL,
                    LivingMotions.JUMP, LivingMotions.SWIM, LivingMotions.CREATIVE_IDLE, LivingMotions.CREATIVE_FLY}) {
                builder.livingMotionModifier(CapabilityItem.Styles.TWO_HAND, motion, HOLD);
            }
            builder.livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.RUN, HOLD_RUN);
            builder.livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.AIM, AIM);
            return builder;
        });
    }
}
