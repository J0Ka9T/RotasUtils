package net.schwarz.rotasutils.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * One spectral sword loosed by the Zenith. It flies a closed loop: out from its wielder to the
 * aimed point along one side, and back along the other, following the wielder as they move. The
 * loop is a pure function of (origin, target, seed, age), so the client draws it at sub-tick
 * precision and long smooth trails without keeping any history, and the server cuts along exactly
 * the same curve. Each blade cuts a given creature once.
 */
public class ZenithBladeEntity extends Entity {
    /** Longest a loop can take, out and back (a blade aimed at full reach). */
    public static final int LIFE = 34;
    /** Shortest loop, for a target right in front of the wielder. */
    public static final int MIN_LIFE = 12;
    /**
     * Blocks the loop covers per tick of its life. As in Terraria the blades keep a steady pace, so a
     * close target is cut in a snap and a far one takes longer, instead of every loop taking the
     * same time and near blades crawling.
     */
    private static final double PACE = 2.4;
    /** How close to the blade's path, beyond a creature's own half-width, still cuts it. */
    private static final double CUT_RADIUS = 2.2;

    private static final EntityDataAccessor<ItemStack> BLADE =
            SynchedEntityData.defineId(ZenithBladeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(ZenithBladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> ORIGIN =
            SynchedEntityData.defineId(ZenithBladeEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> TARGET =
            SynchedEntityData.defineId(ZenithBladeEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SEED =
            SynchedEntityData.defineId(ZenithBladeEntity.class, EntityDataSerializers.INT);

    private final Set<Integer> cut = new HashSet<>();
    private float damage;

    public ZenithBladeEntity(EntityType<? extends ZenithBladeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void loose(ServerLevel level, LivingEntity owner, Vec3 target, ItemStack blade, float damage) {
        ZenithBladeEntity entity = new ZenithBladeEntity(RotasRegistry.ZENITH_BLADE.get(), level);
        Vec3 origin = origin(owner, 1f);
        entity.entityData.set(BLADE, blade);
        entity.entityData.set(OWNER, owner.getId());
        entity.entityData.set(ORIGIN, new Vector3f((float) origin.x, (float) origin.y, (float) origin.z));
        entity.entityData.set(TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
        entity.entityData.set(SEED, level.random.nextInt());
        entity.damage = damage;
        entity.moveTo(origin.x, origin.y, origin.z, 0f, 0f);
        level.addFreshEntity(entity);
    }

    /** Where the loop starts and ends: the wielder's sword hand, roughly. */
    public static Vec3 origin(Entity owner, float partialTick) {
        return owner.getPosition(partialTick).add(0, owner.getEyeHeight() * 0.72, 0);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BLADE, new ItemStack(Items.IRON_SWORD));
        entityData.define(OWNER, -1);
        entityData.define(ORIGIN, new Vector3f());
        entityData.define(TARGET, new Vector3f());
        entityData.define(SEED, 0);
    }

    public ItemStack blade() {
        return entityData.get(BLADE);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public float age(float partialTick) {
        return tickCount + partialTick;
    }

    /** Ticks this blade's loop takes, from how far it was thrown. Same on both sides: both are synced. */
    public int life() {
        Vector3f o = entityData.get(ORIGIN), t = entityData.get(TARGET);
        return lifeFor(new Vector3f(t).sub(o).length());
    }

    /** Loop time for a throw of {@code distance} blocks: steady pace, clamped to a snap and a sweep. */
    public static int lifeFor(double distance) {
        return (int) Math.round(Mth.clamp(MIN_LIFE + distance / PACE, MIN_LIFE, LIFE));
    }

    @Nullable
    public Entity owner() {
        Entity owner = level().getEntity(entityData.get(OWNER));
        return owner != null && owner.isAlive() ? owner : null;
    }

    /** The loop's start point this frame: the live wielder if there is one, else where it was loosed. */
    public Vec3 loopOrigin(float partialTick) {
        Entity owner = owner();
        if (owner != null) {
            return origin(owner, partialTick);
        }
        Vector3f o = entityData.get(ORIGIN);
        return new Vec3(o.x, o.y, o.z);
    }

    public Vec3 target() {
        Vector3f t = entityData.get(TARGET);
        return new Vec3(t.x, t.y, t.z);
    }

    /**
     * The loop, fixed for one frame. {@link #at(double, Vector3f)} places the blade at any
     * fraction of its life, so trails are sampled from the true curve rather than from history.
     */
    public static final class Loop {
        final Vector3f origin = new Vector3f();
        final Vector3f forward = new Vector3f();
        final Vector3f side = new Vector3f();
        final Vector3f normal = new Vector3f();
        float length;
        float width;

        public Loop set(Vec3 from, Vec3 to, int seed) {
            origin.set((float) from.x, (float) from.y, (float) from.z);
            forward.set((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
            length = Math.max(0.5f, forward.length());
            forward.div(length);
            Vector3f right = new Vector3f(forward).cross(0f, 1f, 0f);
            if (right.lengthSquared() < 1.0e-4f) {
                right.set(1f, 0f, 0f);
            }
            right.normalize();
            Vector3f up = new Vector3f(right).cross(forward).normalize();
            // Each blade loops in its own tilted plane and to a random side, so a volley blooms
            // into a rose of arcs around the aim line instead of stacking on one curve.
            float roll = ((seed & 0xFFFF) / 65535f * 2f - 1f) * 1.25f;
            float sign = (seed & 0x10000) != 0 ? 1f : -1f;
            side.set(right).mul(Mth.cos(roll) * sign).add(up.x * Mth.sin(roll), up.y * Mth.sin(roll), up.z * Mth.sin(roll));
            side.normalize();
            normal.set(forward).cross(side).normalize();
            float spread = 0.32f + 0.3f * (((seed >>> 17) & 0xFF) / 255f);
            width = Mth.clamp(length * spread, 2.2f, 18f);
            return this;
        }

        /** Position at life fraction {@code u} (0 = hand, 0.5 = target, 1 = hand again). */
        public Vector3f at(double u, Vector3f out) {
            double theta = Math.PI * 2.0 * Mth.clamp(u, 0.0, 1.0);
            float reach = (float) ((1.0 - Math.cos(theta)) * 0.5) * length;
            float swing = (float) Math.sin(theta) * width;
            return out.set(origin).add(forward.x * reach + side.x * swing, forward.y * reach + side.y * swing,
                    forward.z * reach + side.z * swing);
        }

        /** Direction of travel at {@code u}, unit length. */
        public Vector3f tangent(double u, Vector3f out) {
            double theta = Math.PI * 2.0 * Mth.clamp(u, 0.0, 1.0);
            float dReach = (float) (Math.PI * Math.sin(theta)) * length;
            float dSwing = (float) (Math.PI * 2.0 * Math.cos(theta)) * width;
            out.set(forward.x * dReach + side.x * dSwing, forward.y * dReach + side.y * dSwing,
                    forward.z * dReach + side.z * dSwing);
            return out.lengthSquared() < 1.0e-8f ? out.set(forward) : out.normalize();
        }

        public Vector3f normal() {
            return normal;
        }
    }

    private final Loop loop = new Loop();
    private final Vector3f from = new Vector3f();
    private final Vector3f to = new Vector3f();

    @Override
    public void tick() {
        super.tick();
        int life = life();
        if (level().isClientSide) {
            clientTick(life);
            return;
        }
        if (tickCount > life) {
            discard();
            return;
        }
        Entity owner = owner();
        loop.set(loopOrigin(1f), target(), seed());
        loop.at((tickCount - 1) / (double) life, from);
        loop.at(tickCount / (double) life, to);
        setPos(to.x, to.y, to.z);
        cutAlong(owner);
    }

    private void cutAlong(@Nullable Entity owner) {
        ServerLevel level = (ServerLevel) level();
        AABB box = new AABB(from.x, from.y, from.z, to.x, to.y, to.z).inflate(CUT_RADIUS + 1.0);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (victim == owner || victim instanceof ArmorStand || cut.contains(victim.getId()) || spared(victim, owner)) {
                continue;
            }
            Vec3 center = victim.getBoundingBox().getCenter();
            double reach = CUT_RADIUS + victim.getBbWidth() * 0.5;
            if (segmentDistanceSq(center, from, to) > reach * reach) {
                continue;
            }
            cut.add(victim.getId());
            DamageSource source = owner instanceof Player player ? damageSources().playerAttack(player)
                    : owner instanceof LivingEntity living ? damageSources().mobAttack(living)
                    : damageSources().magic();
            // Every blade is its own strike, as in Terraria: a volley is not swallowed by the
            // half-second of invulnerability one hit leaves behind.
            victim.invulnerableTime = 0;
            if (victim.hurt(source, damage)) {
                // A bright slash mark across the victim along the blade's own path: the cut itself.
                Vec3 dir = new Vec3(to.x - from.x, to.y - from.y, to.z - from.z);
                Vec3 half = dir.lengthSqr() < 1.0e-6 ? new Vec3(1.2, 0, 0) : dir.normalize().scale(1.4);
                RiftFx.send(level, RiftFx.Kind.SLASH, RiftFx.PRISM, center.subtract(half), center.add(half), 0.9f, 9);
                // Anime hit: flash, starburst, speed lines, ring.
                RiftFx.send(level, RiftFx.Kind.IMPACT, RiftFx.PRISM, center, 0.9f + victim.getBbWidth() * 0.5f, 12);
                level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 3, 0.1, 0.1, 0.1, 0.12);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                        0.35f, 1.4f + random.nextFloat() * 0.4f);
            }
        }
    }

    /**
     * Each blade bites with a small kick as it swings through its target. Positional, so whoever is
     * close feels it; common code must not touch client classes to single out the wielder.
     */
    private void clientTick(int life) {
        if (tickCount == life / 2) {
            Vec3 t = target();
            ClientFx.quake(t.x, t.y, t.z, 0.35f, 40);
        }
    }

    private static boolean spared(LivingEntity victim, @Nullable Entity owner) {
        if (owner == null) {
            return false;
        }
        if (victim instanceof TamableAnimal pet && owner instanceof LivingEntity living && pet.isOwnedBy(living)) {
            return true;
        }
        return victim.isAlliedTo(owner);
    }

    private static double segmentDistanceSq(Vec3 p, Vector3f a, Vector3f b) {
        double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        double apx = p.x - a.x, apy = p.y - a.y, apz = p.z - a.z;
        double len = abx * abx + aby * aby + abz * abz;
        double t = len < 1.0e-9 ? 0 : Mth.clamp((apx * abx + apy * aby + apz * abz) / len, 0, 1);
        double dx = apx - abx * t, dy = apy - aby * t, dz = apz - abz * t;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    /** Never written to disk (the type is {@code noSave}). */
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
