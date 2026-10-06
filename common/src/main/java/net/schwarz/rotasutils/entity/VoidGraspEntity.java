package net.schwarz.rotasutils.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.UUID;

public class VoidGraspEntity extends Entity {
    public static final int ERUPT = 24;
    public static final int END = 50;
    public static final double RADIUS = 2.2;
    private static final float DAMAGE = 12f;

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(VoidGraspEntity.class, EntityDataSerializers.INT);

    private UUID caster;

    public VoidGraspEntity(EntityType<? extends VoidGraspEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void erupt(ServerLevel level, Vec3 at, LivingEntity caster) {
        VoidGraspEntity grasp = new VoidGraspEntity(RotasRegistry.VOID_GRASP.get(), level);
        grasp.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
        grasp.caster = caster.getUUID();
        level.addFreshEntity(grasp);
        level.playSound(null, grasp.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8f, 0.6f);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AGE, 0);
    }

    private final SmoothAge smoothAge = new SmoothAge();

    public float age(float partialTick) {
        int age = level().isClientSide ? smoothAge.value(entityData.get(AGE)) : entityData.get(AGE);
        return age + partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        int age = entityData.get(AGE);
        if (level().isClientSide) {
            age = smoothAge.tick(age);
            if (age == 1) {
                RiftFx.local(RiftFx.Kind.SIGIL, RiftFx.DARK, position(), (float) RADIUS, ERUPT + 2);
            }
            if (age == ERUPT) {
                ClientFx.quake(getX(), getY(), getZ(), 1.5f, 14);
                RiftFx.local(RiftFx.Kind.BURST, RiftFx.DARK, position().add(0, 0.6, 0), 1.4f, 18);
                RiftFx.local(RiftFx.Kind.SHOCKWAVE, RiftFx.DARK, position(), (float) RADIUS + 1.5f, 14);
            }
            return;
        }
        age++;
        entityData.set(AGE, age);
        if (age == ERUPT) {
            ServerLevel level = (ServerLevel) level();
            Entity source = caster == null ? null : level.getEntity(caster);
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(position(), position()).inflate(RADIUS, 2.5, RADIUS),
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
                if (Math.hypot(player.getX() - getX(), player.getZ() - getZ()) > RADIUS) {
                    continue;
                }
                player.hurt(source == null ? damageSources().magic() : damageSources().indirectMagic(this, source), DAMAGE);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 4));
                player.setDeltaMovement(player.getDeltaMovement().add(0, 0.6, 0));
                player.hurtMarked = true;
            }
            level.playSound(null, blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.2f, 1.4f);
        }
        if (age >= END) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
