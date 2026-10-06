package net.schwarz.rotasutils.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class CelestialFxEntity extends Entity {
    public static final int BOLT = 0;
    public static final int IMPACT = 1;
    public static final int NOVA = 2;
    public static final int RAY = 3;
    public static final int LANCE = 4;
    public static final int COMET = 5;
    public static final int BIND = 6;
    public static final int WARD = 7;
    public static final int CHARGE = 8;
    public static final int SUPERNOVA = 9;

    public static final int SHADOW_BOLT = 100;
    public static final int VOID_BURST = 101;
    public static final int GRASP = 102;
    public static final int VOID_RAY = 103;
    public static final int ECLIPSE = 104;
    public static final int SHADE_STEP = 105;
    public static final int PIT = 106;
    public static final int VEIL = 107;
    public static final int OBLIVION_CHARGE = 108;
    public static final int OBLIVION = 109;
    public static final int INK_SPLASH = 110;

    public static boolean isAbyss(int kind) {
        return kind >= 100;
    }

    public static boolean followsOwner(int kind) {
        return (kind >= 20 && kind <= 29 && kind != 22 && kind != 24) || kind == COMET || kind == WARD || kind == CHARGE || kind == RAY
                || kind == VOID_RAY || kind == VEIL || kind == OBLIVION_CHARGE;
    }

    public static boolean isBolt(int kind) {
        return kind == BOLT || kind == SHADOW_BOLT;
    }

    public static final float BOLT_SPEED = 1.9f;

    public interface Behaviour {
        void impact(CelestialFxEntity fx, Vec3 at, @Nullable Entity hit);

        default void tick(CelestialFxEntity fx) {
        }
    }

    public static volatile Behaviour behaviour;
    public static volatile Behaviour abyssBehaviour;

    private Behaviour hook() {
        return isAbyss(kind()) ? abyssBehaviour : behaviour;
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> TARGET = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> ORIGIN = SynchedEntityData.defineId(CelestialFxEntity.class, EntityDataSerializers.VECTOR3);

    public float damage;
    public int spellLevel;
    private int keepAliveUntil = Integer.MAX_VALUE;
    public final Vec3[] trail = new Vec3[14];
    public int trailCount;
    public double clientGroundY = Double.NaN;

    public CelestialFxEntity(EntityType<? extends CelestialFxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static CelestialFxEntity create(Level level, int kind, @Nullable Entity owner, Vec3 pos, int life, float radius) {
        CelestialFxEntity fx = new CelestialFxEntity(RotasRegistry.CELESTIAL_FX.get(), level);
        fx.setPos(pos);
        fx.entityData.set(KIND, kind);
        fx.entityData.set(OWNER, owner == null ? -1 : owner.getId());
        fx.entityData.set(SEED, level.random.nextInt());
        fx.entityData.set(LIFE, life);
        fx.entityData.set(RADIUS, radius);
        fx.entityData.set(ORIGIN, pos.toVector3f());
        fx.entityData.set(TARGET, pos.toVector3f());
        return fx;
    }

    public CelestialFxEntity target(Vec3 target) {
        entityData.set(TARGET, target.toVector3f());
        return this;
    }

    public CelestialFxEntity delay(int ticks) {
        entityData.set(DELAY, ticks);
        return this;
    }

    public CelestialFxEntity seed(int seed) {
        entityData.set(SEED, seed);
        return this;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
        entityData.define(OWNER, -1);
        entityData.define(SEED, 0);
        entityData.define(LIFE, 20);
        entityData.define(DELAY, 0);
        entityData.define(RADIUS, 1f);
        entityData.define(TARGET, new Vector3f());
        entityData.define(ORIGIN, new Vector3f());
    }

    public int kind() { return entityData.get(KIND); }
    public int seedValue() { return entityData.get(SEED); }
    public int life() { return entityData.get(LIFE); }
    public int delayTicks() { return entityData.get(DELAY); }
    public float radius() { return entityData.get(RADIUS); }
    public Vec3 targetPos() { return new Vec3(entityData.get(TARGET)); }
    public Vec3 originPos() { return new Vec3(entityData.get(ORIGIN)); }

    @Nullable
    public Entity owner() {
        int id = entityData.get(OWNER);
        return id < 0 ? null : level().getEntity(id);
    }

    public void refresh() {
        keepAliveUntil = tickCount + 4;
    }

public Vec3 boltPos(float age) {
        return originPos().add(targetPos().scale(BOLT_SPEED * age));
    }

    public float lanceU(float age) {
        float flight = Math.max(1, life() - delayTicks());
        float u = Mth.clamp((age - delayTicks()) / flight, 0f, 1f);
        return u * u * (1.6f - 0.6f * u);
    }

    public Vec3 lancePos(float u) {
        Vec3 p0 = originPos();
        Vec3 p2 = targetPos();
        Vec3 mid = p0.add(p2).scale(0.5);
        float side = ((seedValue() & 0xFF) / 255f - 0.5f) * 6f;
        Vec3 dir = p2.subtract(p0);
        Vec3 lateral = new Vec3(-dir.z, 0, dir.x).normalize().scale(side);
        Vec3 p1 = mid.add(0, 4 + dir.length() * 0.18, 0).add(lateral);
        float a = 1 - u;
        return p0.scale(a * a).add(p1.scale(2 * a * u)).add(p2.scale(u * u));
    }

@Override
    public void tick() {
        super.tick();
        int kind = kind();
        Entity owner = owner();
        if (followsOwner(kind)) {
            if (owner != null) setPos(owner.getX(), owner.getY(), owner.getZ());
            if (level().isClientSide && kind == COMET && owner != null) {
                System.arraycopy(trail, 0, trail, 1, trail.length - 1);
                trail[0] = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
                trailCount = Math.min(trail.length, trailCount + 1);
            }
        }
        if (level().isClientSide) {
            if (tickCount == 1 && kind == SUPERNOVA) ClientFx.quake(getX(), getY(), getZ(), 4.5f, 48);
            if (tickCount == 1 && (kind == NOVA || kind == ECLIPSE)) ClientFx.quake(getX(), getY(), getZ(), 1.6f, 16);
            if (tickCount == 1 && kind == OBLIVION) ClientFx.quake(getX(), getY(), getZ(), 3f, 48);
            if (tickCount == 26 && kind == OBLIVION) ClientFx.quake(getX(), getY(), getZ(), 5.5f, 56);
            if (tickCount == 1 && kind == IMPACT) ClientFx.quake(getX(), getY(), getZ(), 0.25f + 0.35f * radius(), 20);
            if (tickCount == 1 && (kind == BIND || kind == PIT)) ClientFx.quake(getX(), getY(), getZ(), 1.4f, 24);
            if (tickCount == 13 && kind == GRASP) ClientFx.quake(getX(), getY(), getZ(), 2.2f, 24);
            if (tickCount == 1 && kind == VOID_BURST) ClientFx.quake(getX(), getY(), getZ(), 0.8f, 20);
            if (isBolt(kind)) setPos(boltPos(tickCount));
            return;
        }
        if ((owner == null && followsOwner(kind))
                || tickCount > life() || tickCount > keepAliveUntil) {
            discard();
            return;
        }
        Behaviour hook = hook();
        if (isBolt(kind)) tickBolt(owner);
        else if (kind == LANCE && lanceU(tickCount) >= 1f) {
            if (hook != null) hook.impact(this, targetPos(), null);
            discard();
        } else if (hook != null) {
            hook.tick(this);
        }
    }

    private void tickBolt(@Nullable Entity owner) {
        Vec3 from = boltPos(tickCount - 1);
        Vec3 to = boltPos(tickCount);
        HitResult block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB sweep = new AABB(from, to).inflate(0.6);
        for (Entity candidate : level().getEntities(this, sweep, e -> e instanceof LivingEntity && e != owner && e.isAlive())) {
            var clip = candidate.getBoundingBox().inflate(0.35).clip(from, to);
            if (clip.isPresent() && from.distanceToSqr(clip.get()) < bestDistance) {
                best = candidate;
                bestDistance = from.distanceToSqr(clip.get());
            }
        }
        if (best != null || block.getType() != HitResult.Type.MISS) {
            Vec3 at = best != null ? best.position().add(0, best.getBbHeight() * 0.5, 0) : to;
            Behaviour hook = hook();
            if (hook != null) hook.impact(this, at, best);
            discard();
            return;
        }
        setPos(to);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
