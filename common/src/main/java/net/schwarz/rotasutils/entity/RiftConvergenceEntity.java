package net.schwarz.rotasutils.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.registry.RotasRegistry;

/**
 * Four rifts clash: violet, gold, crimson and void open around a point one after another, each
 * throws its light into the centre, the beams meet in a turning four-coloured singularity that grows
 * until the ground shakes, and it collapses in a flash and a shockwave - leaving the Tetrarch
 * standing where it was.
 *
 * <p>The server places the rifts, keeps the clock ({@link #age}) and brings the Tetrarch; the beams,
 * the singularity, the shockwave, the particles, the sounds and the quake are drawn on each client
 * from the same clock. Rift heights are synced so beams start exactly at each rift.</p>
 */
public class RiftConvergenceEntity extends Entity {
    public static final double RADIUS = 7.0;
    public static final float CORE_HEIGHT = 3.4f;
    /** A rift opens every this many ticks, in order. */
    public static final int STAGGER = 12;
    public static final int BEAMS_START = 56;
    public static final int COLLAPSE = 160;
    public static final int RIFTS_CLOSE = 205;
    public static final int END = 250;

    /** Order the four rifts open in, and their colours. */
    public static final RiftPortalEntity.Palette[] RIFTS = {RiftPortalEntity.Palette.VIOLET, RiftPortalEntity.Palette.GOLD,
            RiftPortalEntity.Palette.CRIMSON, RiftPortalEntity.Palette.VOID};

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(RiftConvergenceEntity.class, EntityDataSerializers.INT);
    /** Height offset of each rift from this entity, packed as four signed bytes. */
    private static final EntityDataAccessor<Integer> HEIGHTS =
            SynchedEntityData.defineId(RiftConvergenceEntity.class, EntityDataSerializers.INT);

    public RiftConvergenceEntity(EntityType<? extends RiftConvergenceEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    /** Starts a convergence centred at {@code centre}; the Tetrarch will face {@code yaw}. */
    public static RiftConvergenceEntity begin(ServerLevel level, Vec3 centre, float yaw, int[] heights) {
        RiftConvergenceEntity convergence = new RiftConvergenceEntity(RotasRegistry.RIFT_CONVERGENCE.get(), level);
        convergence.moveTo(centre.x, centre.y, centre.z, yaw, 0f);
        int packed = 0;
        for (int i = 0; i < 4; i++) {
            packed |= (heights[i] & 0xFF) << (i * 8);
        }
        convergence.entityData.set(HEIGHTS, packed);
        level.addFreshEntity(convergence);
        return convergence;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AGE, 0);
        entityData.define(HEIGHTS, 0);
    }

    private final SmoothAge smoothAge = new SmoothAge();

    public float age(float partialTick) {
        int age = level().isClientSide ? smoothAge.value(entityData.get(AGE)) : entityData.get(AGE);
        return age + partialTick;
    }

    /** Ground under rift {@code i}, relative to this entity. */
    public int height(int i) {
        return (byte) (entityData.get(HEIGHTS) >> (i * 8));
    }

    /** Where rift {@code i} stands, relative to this entity. */
    public static Vec3 riftOffset(int i) {
        return TetrarchEntity.quarterDirection(i).scale(RADIUS);
    }

    public Vec3 core() {
        return position().add(0, CORE_HEIGHT, 0);
    }

    @Override
    public void tick() {
        super.tick();
        int age = entityData.get(AGE);
        if (level().isClientSide) {
            age = smoothAge.tick(age);
            clientTick(age);
            return;
        }
        age++;
        entityData.set(AGE, age);
        ServerLevel level = (ServerLevel) level();
        if (age % STAGGER == 0 && age / STAGGER <= 4) {
            int i = age / STAGGER - 1;
            Vec3 dir = TetrarchEntity.quarterDirection(i);
            Vec3 spot = position().add(dir.scale(RADIUS)).add(0, height(i), 0);
            // Each rift faces the centre; all four close together.
            float riftYaw = (float) Math.toDegrees(Math.atan2(dir.x, -dir.z));
            RiftPortalEntity.open(level, spot, riftYaw, RiftPortalEntity.Kind.CONVERGE, RIFTS[i], RIFTS_CLOSE - age);
        }
        if (age == COLLAPSE) {
            TetrarchEntity.arrive(level, position(), getYRot());
            TetrarchEntity.ClientFxCall.quake(level, position(), 5f, 60);
        }
        if (age >= END) {
            discard();
        }
    }

    private void clientTick(int age) {
        Vec3 core = core();
        if (age >= BEAMS_START && age < COLLAPSE) {
            float charge = (age - BEAMS_START) / (float) (COLLAPSE - BEAMS_START);
            // Light spiralling into the singularity, thicker as it charges.
            if (age % Math.max(1, 4 - (int) (charge * 3)) == 0) {
                RiftFx.local(RiftFx.Kind.GATHER, RiftFx.PRISM, core, 3f + 3f * charge, 1);
            }
            if (age % 10 == 0) {
                ClientFx.quake(getX(), getY(), getZ(), 0.35f + 1.4f * charge, 40);
            }
        }
        if (age == BEAMS_START) {
            sound("rift.strain", 2f, 0.7f);
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 2f, 0.5f, false);
        } else if (age == BEAMS_START + 40) {
            sound("rift.hum", 2f, 0.6f);
        } else if (age == COLLAPSE - 20) {
            sound("rift.collapse", 2.5f, 0.8f);
        } else if (age == COLLAPSE) {
            sound("rift.split", 3f, 0.6f);
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 3f, 0.5f, false);
            RiftFx.local(RiftFx.Kind.BURST, RiftFx.PRISM, core, 5f, 30);
            RiftFx.local(RiftFx.Kind.SHOCKWAVE, RiftFx.PRISM, position(), 24f, 36);
        }
    }

    private void sound(String id, float volume, float pitch) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(new ResourceLocation(Rotasutils.MOD_ID, id));
        Vec3 core = core();
        level().playLocalSound(core.x, core.y, core.z, event, SoundSource.AMBIENT, volume, pitch, false);
    }

    /** Never written to disk (the type is {@code noSave}); summoned ones start fresh. */
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

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }
}
