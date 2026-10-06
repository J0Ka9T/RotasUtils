package net.schwarz.rotasutils.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

public class ExcaliburSlashEntity extends Entity {
    public static final int GATHER = 22;
    public static final float SPEED = 3.2f;
    public static final float RANGE = 64f;
    public static final float HALF_WIDTH = 10f;
    public static final float HEIGHT = 24f;
    public static final int TRAVEL = (int) Math.ceil(RANGE / SPEED);
    public static final int FADE = 24;
    public static final int LIFE = GATHER + TRAVEL + FADE;
    public static final float ARC_RADIUS = 60f;
    public static final float ARC_SPAN = (float) Math.toRadians(17);
    public static final float ROLL = (float) Math.toRadians(80);
    public static final float MID_HEIGHT = 15f;
    public static final float THICK = 1.1f;
    private static final float DAMAGE = 160f;
    private static final float MAX_HEALTH_BITE = 0.15f;

    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(ExcaliburSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> ORIGIN =
            SynchedEntityData.defineId(ExcaliburSlashEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DIRECTION =
            SynchedEntityData.defineId(ExcaliburSlashEntity.class, EntityDataSerializers.VECTOR3);

    private final Set<Integer> struck = new HashSet<>();

    public ExcaliburSlashEntity(EntityType<? extends ExcaliburSlashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void unleash(ServerLevel level, LivingEntity owner) {
        ExcaliburSlashEntity slash = new ExcaliburSlashEntity(RotasRegistry.EXCALIBUR_SLASH.get(), level);
        Vec3 look = owner.getViewVector(1f);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        slash.entityData.set(OWNER, owner.getId());
        slash.entityData.set(ORIGIN, new Vector3f((float) owner.getX(), (float) owner.getY(), (float) owner.getZ()));
        slash.entityData.set(DIRECTION, new Vector3f((float) flat.x, 0f, (float) flat.z));
        slash.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0f, 0f);
        level.addFreshEntity(slash);
        slash.playAt(SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.7f);
        slash.playAt(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2f, 0.8f);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(ORIGIN, new Vector3f());
        entityData.define(DIRECTION, new Vector3f(0f, 0f, 1f));
    }

    public Vector3f origin() {
        return entityData.get(ORIGIN);
    }

    public Vector3f direction() {
        return entityData.get(DIRECTION);
    }

    @Nullable
    public Entity owner() {
        return level().getEntity(entityData.get(OWNER));
    }

    public float age(float partialTick) {
        return tickCount + partialTick;
    }

    public static float front(float age) {
        return Math.max(0f, Math.min(RANGE, (age - GATHER) * SPEED));
    }

    public static Vector3f crescent(Vector3f origin, Vector3f dir, float front, float theta, float scale, Vector3f out,
                                    Vector3f radial) {
        float rx = -dir.z, rz = dir.x;
        float cr = (float) Math.cos(ROLL), sr = (float) Math.sin(ROLL);
        float e1x = rx * cr, e1y = sr, e1z = rz * cr;
        float e2x = -rx * sr, e2y = cr, e2z = -rz * sr;
        float st = (float) Math.sin(theta), ct = (float) Math.cos(theta);
        radial.set(e1x * st + e2x * ct, e1y * st + e2y * ct, e1z * st + e2z * ct);
        float radius = ARC_RADIUS * scale;
        float lead = front + 40f * scale * (ct - (float) Math.cos(ARC_SPAN));
        return out.set(origin).add(dir.x * lead, MID_HEIGHT * scale, dir.z * lead)
                .sub(e2x * radius, e2y * radius, e2z * radius)
                .add(radial.x * radius, radial.y * radius, radial.z * radius);
    }

    public static float thickness(float theta, float scale) {
        float t = (theta + ARC_SPAN) / (2f * ARC_SPAN);
        return THICK * scale * (float) Math.pow(Math.max(0f, (float) Math.sin(Math.PI * t)), 0.9);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (tickCount > LIFE) {
            discard();
            return;
        }
        Entity owner = owner();
        if (tickCount < GATHER) {
            if (tickCount % 4 == 0) {
                playAt(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f + tickCount / (float) GATHER);
            }
            return;
        }
        if (tickCount == GATHER) {
            playAt(SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 0.7f);
            playAt(SoundEvents.TRIDENT_THUNDER, 2.0f, 0.9f);
            playAt(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0f, 0.5f);
            playAt(SoundEvents.TOTEM_USE, 0.8f, 1.4f);
            if (owner instanceof LivingEntity living) {
                living.swing(InteractionHand.MAIN_HAND, true);
            }
        }
        if (tickCount <= GATHER + TRAVEL) {
            sweep((ServerLevel) level(), owner);
            if (tickCount % 3 == 0) {
                Vector3f o = origin(), d = direction();
                float f = front(tickCount);
                level().playSound(null, o.x + d.x * f, o.y, o.z + d.z * f, SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 1.2f, 1.3f);
            }
        }
    }

    private void sweep(ServerLevel level, @Nullable Entity owner) {
        Vector3f o = origin(), d = direction();
        float near = front(tickCount - 1) - 2f, far = front(tickCount) + 2f;
        Vec3 a = new Vec3(o.x + d.x * near, o.y, o.z + d.z * near);
        Vec3 b = new Vec3(o.x + d.x * far, o.y, o.z + d.z * far);
        AABB box = new AABB(a, b).inflate(HALF_WIDTH + 1, 0, HALF_WIDTH + 1).expandTowards(0, HEIGHT, 0).move(0, -3, 0);
        DamageSource source = owner instanceof Player player ? damageSources().playerAttack(player)
                : owner instanceof LivingEntity living ? damageSources().mobAttack(living) : damageSources().magic();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (victim == owner || victim instanceof ArmorStand || struck.contains(victim.getId()) || spared(victim, owner)) {
                continue;
            }
            double rx = victim.getX() - o.x, rz = victim.getZ() - o.z;
            double along = rx * d.x + rz * d.z;
            if (along < near - 6 || along > far + 6) {
                continue;
            }
            float cr = (float) Math.cos(ROLL), sr = (float) Math.sin(ROLL);
            double lat = -rx * d.z + rz * d.x;
            double up = victim.getY() + victim.getBbHeight() * 0.5 - o.y - MID_HEIGHT;
            double q1 = lat * cr + up * sr;
            double q2 = -lat * sr + up * cr + ARC_RADIUS;
            double rho = Math.sqrt(q1 * q1 + q2 * q2);
            double theta = Math.atan2(q1, q2);
            double reach = thickness((float) Math.max(-ARC_SPAN, Math.min(ARC_SPAN, theta)), 1f) * 0.5
                    + victim.getBbWidth() * 0.5 + 2.5;
            if (Math.abs(theta) > ARC_SPAN + 0.12 || Math.abs(rho - ARC_RADIUS) > reach) {
                continue;
            }
            struck.add(victim.getId());
            victim.invulnerableTime = 0;
            if (victim.hurt(source, DAMAGE + victim.getMaxHealth() * MAX_HEALTH_BITE)) {
                victim.setDeltaMovement(victim.getDeltaMovement().add(d.x * 1.6, 0.9, d.z * 1.6));
                victim.hurtMarked = true;
                Vec3 c = victim.getBoundingBox().getCenter();
                Vec3 tilt = new Vec3(-d.z * Math.cos(ROLL), Math.sin(ROLL), d.x * Math.cos(ROLL))
                        .scale(Math.max(1.5, victim.getBbHeight()));
                RiftFx.send(level, RiftFx.Kind.SLASH, RiftFx.WHITE, c.subtract(tilt), c.add(tilt), 1.6f, 10);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f,
                        1.2f + random.nextFloat() * 0.3f);
                level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 12, 0.3, 0.5, 0.3, 0.4);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0.6);
            }
        }
    }

    private void clientTick() {
        Vector3f o = origin(), d = direction();
        if (tickCount == GATHER) {
            ClientFx.quake(o.x, o.y, o.z, 6f, 48);
        }
        if (tickCount < GATHER) {
            for (int i = 0; i < 1; i++) {
                double ang = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 5;
                level().addParticle(ParticleTypes.END_ROD, o.x + Math.cos(ang) * r, o.y + 0.2, o.z + Math.sin(ang) * r,
                        -Math.cos(ang) * r * 0.05, 0.25 + random.nextDouble() * 0.3, -Math.sin(ang) * r * 0.05);
            }
            return;
        }
        if (tickCount <= GATHER + TRAVEL) {
            float f = front(tickCount);
            if (tickCount % 2 == 0) {
                ClientFx.quake(o.x + d.x * f, o.y, o.z + d.z * f, 1.5f, 24);
            }
            Vector3f at = new Vector3f(), radial = new Vector3f();
            for (int i = 0; i < 3; i++) {
                float theta = (random.nextFloat() * 2f - 1f) * ARC_SPAN;
                crescent(o, d, f, theta, 1f, at, radial);
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                        -d.x * 0.4 + radial.x * 0.2, radial.y * 0.2, -d.z * 0.4 + radial.z * 0.2);
            }
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

    private void playAt(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 200 * 200;
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
