package net.schwarz.rotasutils.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.registry.RotasRegistry;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A standing rift that tears open, does its work, and seals.
 *
 * <p>What it does depends on its {@link Kind}: a traveller steps out (arrival), anyone who walks in
 * is carried to a bound destination (travel, with a short exit rift there), nearby monsters are
 * dragged in and swallowed (maw), or something reaches out and strikes monsters - tentacles
 * (tentacle) or a colossal prismatic hand (radiant).</p>
 *
 * <p>The server owns the timeline ({@link #age}, synced) and the effects on the world; everything
 * seen and heard - the slit widening into an oval, the swirl, the particles, the sounds, the
 * tentacles' lashes - is done on the client from the same age, so it costs a few numbers per tick.
 * The portal faces along its yaw.</p>
 */
public class RiftPortalEntity extends Entity {
    /** Slit appears and splits open. */
    public static final int OPEN_END = 40;
    /** An arriving traveller starts to come through. */
    public static final int ARRIVE_AT = 55;
    /** Ticks from the close starting to the portal being gone. */
    public static final int CLOSE_TICKS = 35;

    /** What a rift is for, how long it stays open, and its colours. */
    public enum Kind {
        ARRIVAL(125, Palette.VIOLET),
        TRAVEL(640, Palette.GOLD),
        EXIT(72, Palette.GOLD),
        MAW(210, Palette.VOID),
        TENTACLE(270, Palette.VOID),
        /** A colossal prismatic hand reaches out and strikes, on the tentacle rift's timing. */
        RADIANT(270, Palette.PRISM),
        /** One of the four rifts of a convergence; the convergence sets its colour and close time. */
        CONVERGE(200, Palette.VIOLET);

        /** Age at which it starts to close. */
        public final int closeAt;
        public final Palette palette;

        Kind(int closeAt, Palette palette) {
            this.closeAt = closeAt;
            this.palette = palette;
        }

        public int end() {
            return closeAt + CLOSE_TICKS;
        }

        static Kind of(int ordinal) {
            Kind[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ARRIVAL;
        }
    }

    /** Colour families of the rift's swirl, rim and light. */
    public enum Palette { VIOLET, GOLD, VOID, CRIMSON, PRISM }

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(RiftPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(RiftPortalEntity.class, EntityDataSerializers.INT);
    /** Tentacle rift: the tick the next strike lands, and the entity it lands on (-1 none). */
    private static final EntityDataAccessor<Integer> STRIKE_TICK =
            SynchedEntityData.defineId(RiftPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STRIKE_TARGET =
            SynchedEntityData.defineId(RiftPortalEntity.class, EntityDataSerializers.INT);
    /** Colour and close time set by whoever opened it; -1 keeps the kind's own. */
    private static final EntityDataAccessor<Integer> PALETTE =
            SynchedEntityData.defineId(RiftPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLOSE_AT =
            SynchedEntityData.defineId(RiftPortalEntity.class, EntityDataSerializers.INT);

    // Travel.
    private static final double TRAVEL_REACH = 0.55;
    private static final int TRAVEL_COOLDOWN = 60;
    private ResourceLocation destinationLevel;
    private Vec3 destination;
    private float destinationYaw;
    private final Map<UUID, Integer> travelled = new HashMap<>();
    private int lastExit = -1000;

    // Maw and tentacles.
    private static final double MAW_RANGE = 14.0;
    private static final double TENTACLE_RANGE = 10.0;
    private static final int STRIKE_EVERY = 22;
    private static final int STRIKE_WINDUP = 9;
    private static final float STRIKE_DAMAGE = 9f;
    /** Monsters sturdier than this are dragged but never swallowed whole. */
    private static final float SWALLOW_MAX_HEALTH = 100f;

    private boolean arrived;

    public RiftPortalEntity(EntityType<? extends RiftPortalEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    /** An arrival rift at {@code pos} whose opening faces along {@code yaw}. */
    public static RiftPortalEntity open(ServerLevel level, Vec3 pos, float yaw) {
        return open(level, pos, yaw, Kind.ARRIVAL);
    }

    public static RiftPortalEntity open(ServerLevel level, Vec3 pos, float yaw, Kind kind) {
        return open(level, pos, yaw, kind, null, -1);
    }

    /** A rift in a given colour that starts closing at {@code closeAt} (-1 for the kind's defaults). */
    public static RiftPortalEntity open(ServerLevel level, Vec3 pos, float yaw, Kind kind, Palette palette, int closeAt) {
        RiftPortalEntity portal = new RiftPortalEntity(RotasRegistry.RIFT_PORTAL.get(), level);
        portal.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        portal.entityData.set(KIND, kind.ordinal());
        portal.entityData.set(PALETTE, palette == null ? -1 : palette.ordinal());
        portal.entityData.set(CLOSE_AT, closeAt);
        level.addFreshEntity(portal);
        return portal;
    }

    /** A travel rift that carries whoever steps in to {@code destination}, facing {@code arrivalYaw}. */
    public static RiftPortalEntity openTravel(ServerLevel level, Vec3 pos, float yaw, ResourceLocation destinationLevel,
                                              Vec3 destination, float arrivalYaw) {
        RiftPortalEntity portal = new RiftPortalEntity(RotasRegistry.RIFT_PORTAL.get(), level);
        portal.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        portal.entityData.set(KIND, Kind.TRAVEL.ordinal());
        portal.destinationLevel = destinationLevel;
        portal.destination = destination;
        portal.destinationYaw = arrivalYaw;
        level.addFreshEntity(portal);
        return portal;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AGE, 0);
        entityData.define(KIND, Kind.ARRIVAL.ordinal());
        entityData.define(STRIKE_TICK, -1000);
        entityData.define(STRIKE_TARGET, -1);
        entityData.define(PALETTE, -1);
        entityData.define(CLOSE_AT, -1);
    }

    public Palette palette() {
        int ordinal = entityData.get(PALETTE);
        Palette[] all = Palette.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : kind().palette;
    }

    /** Age at which this rift starts to close. */
    public int closeAt() {
        int override = entityData.get(CLOSE_AT);
        return override > 0 ? override : kind().closeAt;
    }

    public int end() {
        return closeAt() + CLOSE_TICKS;
    }

    private final SmoothAge smoothAge = new SmoothAge();

    public int age() {
        return level().isClientSide ? smoothAge.value(entityData.get(AGE)) : entityData.get(AGE);
    }

    /** Age with the frame's fraction, for smooth animation. */
    public float age(float partialTick) {
        return age() + partialTick;
    }

    public Kind kind() {
        return Kind.of(entityData.get(KIND));
    }

    public int strikeTick() {
        return entityData.get(STRIKE_TICK);
    }

    public int strikeTarget() {
        return entityData.get(STRIKE_TARGET);
    }

    /** The unit vector the opening faces. */
    public Vec3 facing() {
        return Vec3.directionFromRotation(0f, getYRot());
    }

    /** Centre of the opening in world space. */
    public Vec3 centre() {
        return position().add(0, RiftPortalShape.CENTER_Y, 0);
    }

    /** 0..1 how open the rift is right now: full between opening and closing. */
    public float openness(float partialTick) {
        float age = age(partialTick);
        return Math.min(RiftPortalShape.height(age, closeAt()), RiftPortalShape.width(age, closeAt()));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            smoothAge.tick(entityData.get(AGE));
            clientEffects();
            return;
        }
        int age = age() + 1;
        entityData.set(AGE, age);
        Kind kind = kind();
        boolean open = age >= OPEN_END && age < closeAt();
        switch (kind) {
            case ARRIVAL -> {
                if (!arrived && age >= ARRIVE_AT) {
                    arrived = true;
                    RiftWandererEntity.arrive((ServerLevel) level(), this);
                }
            }
            case TRAVEL -> {
                if (open) {
                    carry((ServerLevel) level(), age);
                }
            }
            case MAW -> {
                if (open) {
                    devour((ServerLevel) level());
                }
            }
            case TENTACLE, RADIANT -> {
                if (open) {
                    strike((ServerLevel) level(), age);
                }
            }
            default -> {
            }
        }
        if (age >= end()) {
            discard();
        }
    }

    // Travel -------------------------------------------------------------------------------------

    private void carry(ServerLevel level, int age) {
        if (destination == null || destinationLevel == null) {
            return;
        }
        ServerLevel target = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, destinationLevel));
        if (target == null) {
            return;
        }
        Vec3 centre = centre();
        Vec3 forward = facing();
        AABB reach = new AABB(centre, centre).inflate(RiftPortalShape.RADIUS_X + 0.6, RiftPortalShape.RADIUS_Y + 0.6,
                RiftPortalShape.RADIUS_X + 0.6);
        for (Entity entity : level.getEntities(this, reach, e -> e instanceof LivingEntity && e.isAlive())) {
            if (entity instanceof RiftWandererEntity) {
                continue;
            }
            Integer until = travelled.get(entity.getUUID());
            if (until != null && age < until) {
                continue;
            }
            Vec3 offset = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(centre);
            double depth = offset.dot(forward);
            Vec3 across = offset.subtract(forward.scale(depth));
            double lateral = Math.hypot(across.x, across.z);
            if (Math.abs(depth) > TRAVEL_REACH || lateral > RiftPortalShape.RADIUS_X * 0.9
                    || Math.abs(across.y) > RiftPortalShape.RADIUS_Y * 0.9) {
                continue;
            }
            travelled.put(entity.getUUID(), age + TRAVEL_COOLDOWN);
            Vec3 arriveForward = Vec3.directionFromRotation(0f, destinationYaw);
            if (age - lastExit > 60) {
                lastExit = age;
                // A short rift at the far end, so arriving reads as stepping out of one.
                open(target, destination.subtract(arriveForward.scale(0.4)), destinationYaw, Kind.EXIT);
            }
            Vec3 landing = destination.add(arriveForward.scale(1.2));
            level.playSound(null, entity.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.7f);
            entity.teleportTo(target, landing.x, landing.y, landing.z, EnumSet.noneOf(RelativeMovement.class),
                    destinationYaw, entity.getXRot());
            entity.setDeltaMovement(arriveForward.scale(0.2));
            entity.fallDistance = 0f;
            target.playSound(null, landing.x, landing.y, landing.z, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    // Maw ----------------------------------------------------------------------------------------

    private void devour(ServerLevel level) {
        Vec3 centre = centre();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(MAW_RANGE),
                m -> m instanceof Enemy && m.isAlive())) {
            Vec3 toward = centre.subtract(mob.position().add(0, mob.getBbHeight() * 0.5, 0));
            double distance = toward.length();
            if (distance > MAW_RANGE) {
                continue;
            }
            if (distance < 1.5 && mob.getMaxHealth() <= SWALLOW_MAX_HEALTH) {
                RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.DARK, mob.position().add(0, mob.getBbHeight() * 0.5, 0), 0.9f, 16);
                level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 0.6f, 1.6f);
                mob.discard();
                continue;
            }
            // Stronger the closer it gets, so the last few blocks are a plunge.
            double pull = 0.05 + 0.18 * (1.0 - distance / MAW_RANGE);
            Vec3 push = toward.normalize().scale(pull);
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.8).add(push.x, push.y + 0.02, push.z));
            mob.hurtMarked = true;
            mob.fallDistance = 0f;
            if (distance < 1.5) {
                // Too big to swallow: it is chewed on instead.
                mob.hurt(level.damageSources().magic(), 1.5f);
            }
        }
    }

    // Tentacles ----------------------------------------------------------------------------------

    private void strike(ServerLevel level, int age) {
        int phase = (age - OPEN_END) % STRIKE_EVERY;
        if (phase == 0) {
            // Pick the target a little before the blow lands, so the lash can wind up on clients.
            Mob target = nearestMonster(level);
            entityData.set(STRIKE_TARGET, target == null ? -1 : target.getId());
            entityData.set(STRIKE_TICK, target == null ? -1000 : age + STRIKE_WINDUP);
        } else if (age == strikeTick()) {
            Entity target = level.getEntity(strikeTarget());
            if (target instanceof Mob mob && mob.isAlive() && mob.distanceToSqr(centre()) < (TENTACLE_RANGE + 2) * (TENTACLE_RANGE + 2)) {
                Vec3 away = mob.position().subtract(position()).multiply(1, 0, 1).normalize();
                mob.hurt(level.damageSources().magic(), STRIKE_DAMAGE);
                mob.setDeltaMovement(away.x * 0.9, 0.55, away.z * 0.9);
                mob.hurtMarked = true;
                level.playSound(null, mob.blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.2f, 0.5f);
                RiftFx.send(level, RiftFx.Kind.SLASH, RiftFx.DARK, centre(), mob.position().add(0, mob.getBbHeight() * 0.5, 0), 1f, 8);
            }
        }
    }

    private Mob nearestMonster(ServerLevel level) {
        Vec3 centre = centre();
        Mob best = null;
        double bestDistance = TENTACLE_RANGE * TENTACLE_RANGE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(TENTACLE_RANGE),
                m -> m instanceof Enemy && m.isAlive())) {
            double d = mob.distanceToSqr(centre);
            if (d < bestDistance) {
                bestDistance = d;
                best = mob;
            }
        }
        return best;
    }

    // Client -------------------------------------------------------------------------------------

    private void clientEffects() {
        int age = age();
        Kind kind = kind();
        Vec3 forward = facing();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 centre = centre();
        float open = RiftPortalShape.width(age, closeAt());
        float tall = RiftPortalShape.height(age, closeAt());
        int colour = fxColour();

        // A few motes drawn into the rim; sparse, so the swirl stays readable.
        if (tall > 0.05f && open > 0.05f && age % 6 == 0) {
            RiftFx.local(RiftFx.Kind.GATHER, colour, centre, 1.6f * Math.max(open, tall), 1);
        }
        if (age == 1) {
            sound("rift.crack", 1.2f, 0.9f);
        } else if (age == 14) {
            sound("rift.strain", 1.0f, 1.1f);
        } else if (age == 28) {
            sound("rift.split", 1.1f, 1.2f);
            burst(centre, forward, 24);
        } else if (kind == Kind.ARRIVAL && age == ARRIVE_AT) {
            sound("rift.zap", 0.9f, 0.8f);
        } else if (age == closeAt() + 20) {
            sound("rift.implode", 1.0f, 1.3f);
        } else if (age == end() - 2) {
            sound("rift.seal", 1.0f, 1.2f);
            burst(centre, forward, 16);
        }
        if (kind == Kind.MAW && age >= OPEN_END && age < closeAt() && random.nextFloat() < 0.6f) {
            // Air and dust streaming into the maw.
            double a = random.nextDouble() * Math.PI * 2;
            double d = 3 + random.nextDouble() * 6;
            if (age % 3 == 0) {
                RiftFx.local(RiftFx.Kind.GATHER, RiftFx.DARK, centre, (float) d, 1);
            }
        }
    }

    private void burst(Vec3 centre, Vec3 forward, int count) {
        RiftFx.local(RiftFx.Kind.BURST, fxColour(), centre.add(forward.scale(0.3)), count / 24f, 18);
    }

    /** This rift's colour in the rift-FX palette. */
    private int fxColour() {
        return switch (palette()) {
            case GOLD -> RiftFx.GOLD;
            case VOID -> RiftFx.DARK;
            case CRIMSON -> RiftFx.CRIMSON;
            case PRISM -> RiftFx.VIOLET;
            default -> RiftFx.VIOLET;
        };
    }

    private void sound(String id, float volume, float pitch) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(new ResourceLocation(Rotasutils.MOD_ID, id));
        Vec3 centre = centre();
        level().playLocalSound(centre.x, centre.y, centre.z, event, SoundSource.AMBIENT, volume, pitch, false);
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
        return distance < 128 * 128;
    }

    /** The shape of the opening over time, shared by the renderer and the particle effects. */
    public static final class RiftPortalShape {
        public static final float RADIUS_X = 1.05f;
        public static final float RADIUS_Y = 1.55f;
        public static final float CENTER_Y = 1.6f;

        private RiftPortalShape() {
        }

        /** 0..1 vertical extent: a slit grows first. */
        public static float height(float age, int closeAt) {
            if (age < closeAt) {
                return ease(Mth.clamp(age / 16f, 0f, 1f));
            }
            return 1f - ease(Mth.clamp((age - closeAt - 12) / 23f, 0f, 1f));
        }

        /** 0..1 horizontal extent: the slit then splits open with a little overshoot. */
        public static float width(float age, int closeAt) {
            if (age < closeAt) {
                float t = Mth.clamp((age - 14f) / 24f, 0f, 1f);
                return 0.04f + 0.96f * back(t);
            }
            return (1f - ease(Mth.clamp((age - closeAt) / 20f, 0f, 1f))) * 0.96f + 0.04f;
        }

        /** 0..1 white flash at the split and at the seal. */
        public static float flash(float age, int closeAt) {
            float split = (float) Math.exp(-Math.pow((age - 28f) / 3.5f, 2));
            float seal = (float) Math.exp(-Math.pow((age - (closeAt + CLOSE_TICKS - 3f)) / 3f, 2));
            return Math.max(split, seal);
        }

        private static float ease(float t) {
            return t * t * (3f - 2f * t);
        }

        private static float back(float t) {
            float c = 1.9f;
            float u = t - 1f;
            return 1f + (c + 1f) * u * u * u + c * u * u;
        }
    }
}
