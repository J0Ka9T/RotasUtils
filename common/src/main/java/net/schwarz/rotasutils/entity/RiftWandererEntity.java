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

/**
 * The robed traveller who steps out of a {@link RiftPortalEntity}.
 *
 * <p>For the arrival it walks on rails: no goals yet, a steady stride straight out of the portal while its
 * body fades in from the swirl (see {@code RiftWandererRenderer}). Once clear it turns into an
 * ordinary, patient figure that watches whoever comes near. It never despawns and cannot be hurt,
 * so an administrator can make it an NPC with the NPC Wand like any other entity.</p>
 */
public class RiftWandererEntity extends PathfinderMob {
    /** Ticks from first appearing inside the portal to standing clear of it. */
    public static final int WALK_TICKS = 50;
    /** Ticks over which the body fades in from nothing. */
    public static final int FADE_TICKS = 34;
    private static final double STRIDE = 0.085;

    /** Ticks since the arrival began; {@link #WALK_TICKS} and above means arrived. */
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

    /** Places a traveller just inside the portal, facing out, and starts the walk. */
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
        // New travellers start inside the swirl; loaded ones are set to arrived in readAdditionalSaveData.
        entityData.define(EMERGE, 0);
    }

    /** No goals during the arrival, so nothing steers or turns it; see {@link #settle()}. */
    @Override
    protected void registerGoals() {
    }

    /** Once clear of the portal: watch whoever comes near. */
    private void settle() {
        if (settled) {
            return;
        }
        settled = true;
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /** 0 while still inside the swirl, 1 once fully solid. */
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
                // Walk straight out along the way it was placed facing.
                walkYaw = getYRot();
            }
            // Measured stride out of the portal, slowing for the last few steps.
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
            // Wisps of the rift still clinging to the body as it forms.
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
        // Saved mid-walk: skip straight to standing, never replay the arrival.
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
