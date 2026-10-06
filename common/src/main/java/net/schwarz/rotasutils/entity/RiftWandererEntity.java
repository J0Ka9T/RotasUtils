package net.schwarz.rotasutils.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.registry.RotasRegistry;

public class RiftWandererEntity extends PathfinderMob {
    public static final int WALK_TICKS = 50;
    public static final int FADE_TICKS = 34;
    private static final double STRIDE = 0.085;

    private static final EntityDataAccessor<Integer> EMERGE =
            SynchedEntityData.defineId(RiftWandererEntity.class, EntityDataSerializers.INT);

    private float walkYaw;
    private boolean settled;

    public RiftWandererEntity(EntityType<? extends RiftWandererEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    static void arrive(ServerLevel level, RiftPortalEntity portal) {
        RiftWandererEntity wanderer = new RiftWandererEntity(RotasRegistry.RIFT_WANDERER.get(), level);
        float yaw = portal.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0f, yaw);
        Vec3 start = portal.position().subtract(forward.scale(0.7));
        wanderer.moveTo(start.x, portal.getY(), start.z, yaw, 0f);
        wanderer.setYHeadRot(yaw);
        wanderer.yBodyRot = yaw;
        wanderer.finalizeSpawn(level, level.getCurrentDifficultyAt(wanderer.blockPosition()), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(wanderer);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(EMERGE, 0);
    }

    @Override
    protected void registerGoals() {
    }

    private void settle() {
        if (settled) {
            return;
        }
        settled = true;
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public float emergence(float partialTick) {
        int emerge = entityData.get(EMERGE);
        if (emerge >= WALK_TICKS) {
            return 1f;
        }
        return Math.min(1f, (emerge + partialTick) / FADE_TICKS);
    }

    public boolean arriving() {
        return entityData.get(EMERGE) < WALK_TICKS;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && arriving()) {
            int emerge = entityData.get(EMERGE);
            if (emerge == 0) {
                walkYaw = getYRot();
            }
            double pace = emerge > WALK_TICKS - 10 ? STRIDE * (WALK_TICKS - emerge) / 10.0 : STRIDE;
            Vec3 forward = Vec3.directionFromRotation(0f, walkYaw);
            setDeltaMovement(forward.x * pace, getDeltaMovement().y, forward.z * pace);
            setYRot(walkYaw);
            setYHeadRot(walkYaw);
            yBodyRot = walkYaw;
            emerge++;
            entityData.set(EMERGE, emerge);
            if (emerge >= WALK_TICKS) {
                settle();
            }
        } else if (!level().isClientSide) {
            settle();
        }
        if (level().isClientSide && arriving()) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                        getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            }
        }
        super.tick();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("RotasArrived", !arriving());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(EMERGE, WALK_TICKS);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !arriving();
    }
}
