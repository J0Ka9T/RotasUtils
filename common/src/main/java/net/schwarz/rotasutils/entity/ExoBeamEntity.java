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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.item.ExoDisintegratorItem;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * The ExoElectric Disintegrator's ray. It exists exactly as long as its wielder keeps channelling:
 * {@link #CHARGE} ticks of gathering light at the muzzle, then a continuous beam that pierces every
 * creature along the wielder's aim for its full range, burning straight through terrain, ticking
 * heavy damage (a flat bite plus a share of max health), igniting and shoving victims, and unmaking what it
 * kills (no corpse is left, only a burst of light). After {@link #OVERHEAT} ticks of firing the gun
 * vents and must cool down.
 * <p>
 * The beam's shape is not synced: every client re-derives it from the wielder's own interpolated
 * aim each frame, so it tracks the crosshair perfectly smoothly.
 */
public class ExoBeamEntity extends Entity {
    /**
     * What the gun is doing. The ordinary beam is channelled for as long as the trigger is held; the
     * lance is a single overcharged discharge, fired by sneaking, that runs its own length whether the
     * trigger is held or not and leaves the gun cooling.
     */
    public enum Mode {
        BEAM(14, 0, 1f, 1f, COOLDOWN),
        LANCE(22, 16, 2.05f, 3.4f, 160);

        /** Ticks of gathering before it fires. */
        public final int charge;
        /** Ticks of firing before it ends on its own; 0 = until the trigger is released. */
        public final int fire;
        /** Beam radius and damage relative to the ordinary beam. */
        public final float radius;
        public final float damage;
        /** Cooldown put on the gun when it ends. */
        public final int cooldown;

        Mode(int charge, int fire, float radius, float damage, int cooldown) {
            this.charge = charge;
            this.fire = fire;
            this.radius = radius;
            this.damage = damage;
            this.cooldown = cooldown;
        }

        static Mode of(int ordinal) {
            Mode[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : BEAM;
        }
    }

    /** Ticks of charge before the ray fires. */
    public static final int CHARGE = 14;
    /** Ticks of continuous fire before the gun overheats and vents. Ten seconds, as the tooltip says. */
    public static final int OVERHEAT = 200;
    /** Cool-down after venting, in ticks. */
    private static final int COOLDOWN = 40;
    public static final double RANGE = 320.0;
    /** Full beam radius (the ray is 20 blocks across). */
    public static final float RADIUS = 10f;
    /** Length of the rounded, white-hot nose over which the ray swells to full width. */
    public static final float NOSE = 14f;
    private static final int DAMAGE_INTERVAL = 2;
    /** Per-strike bite: a strike every other tick over the ten-second vent is 340 + 60% max HP per second. */
    private static final float DAMAGE = 34f;
    /** Extra damage per strike as a fraction of the victim's max health, so bosses melt too. */
    private static final float MAX_HEALTH_BITE = 0.06f;
    /** The sizzle under a connect is throttled to one every this many ticks, however many victims. */
    private static final int HIT_SOUND_INTERVAL = 6;
    /** Embers shed from the ray's skin; the hot pair takes over as the gun cooks. */
    private static final net.minecraft.core.particles.DustParticleOptions EMBER =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.12f, 0.12f), 3.5f);
    private static final net.minecraft.core.particles.DustParticleOptions EMBER_HOT =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.62f, 0.2f), 3.5f);

    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(ExoBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(ExoBeamEntity.class, EntityDataSerializers.INT);

    public ExoBeamEntity(EntityType<? extends ExoBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Starts an ordinary beam for the wielder, unless one is already running. */
    public static void channel(ServerLevel level, LivingEntity owner) {
        channel(level, owner, Mode.BEAM);
    }

    /** Starts a ray for the wielder in {@code mode}, unless one is already running. */
    static boolean canStartRay(boolean beamActive, boolean ceroActive, boolean coolingDown) {
        return !beamActive && !ceroActive && !coolingDown;
    }

    public static void channel(ServerLevel level, LivingEntity owner, Mode mode) {
        boolean coolingDown = owner instanceof Player player
                && player.getCooldowns().isOnCooldown(RotasRegistry.EXO_DISINTEGRATOR.get());
        if (!canStartRay(activeFor(owner), ExoCeroMuzzleEntity.activeFor(owner), coolingDown)) {
            return;
        }
        ExoBeamEntity beam = new ExoBeamEntity(RotasRegistry.EXO_BEAM.get(), level);
        beam.entityData.set(OWNER, owner.getId());
        beam.entityData.set(MODE, mode.ordinal());
        beam.moveTo(owner.getX(), owner.getY(), owner.getZ(), 0f, 0f);
        level.addFreshEntity(beam);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0f, 1.7f);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 0.8f, mode == Mode.LANCE ? 0.7f : 1.5f);
    }

    /**
     * The Annihilation Overdrive (Epic Fight innate skill): starts a Lance even while the gun is cooling,
     * since the skill has its own cost, but never on top of a ray or barrage already running.
     */
    public static void overdrive(ServerLevel level, LivingEntity owner) {
        if (activeFor(owner) || ExoCeroMuzzleEntity.activeFor(owner)) {
            return;
        }
        if (owner instanceof Player player) {
            player.getCooldowns().removeCooldown(RotasRegistry.EXO_DISINTEGRATOR.get());
        }
        channel(level, owner, Mode.LANCE);
    }

    /** True while {@code owner} already channels a ray, so the barrage can stand aside. */
    public static boolean activeFor(LivingEntity owner) {
        return !owner.level().getEntitiesOfClass(ExoBeamEntity.class, owner.getBoundingBox().inflate(4),
                b -> b.entityData.get(OWNER) == owner.getId()).isEmpty();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(MODE, Mode.BEAM.ordinal());
    }

    public Mode mode() {
        return Mode.of(entityData.get(MODE));
    }

    /** Ticks of charge this shot gathers before it fires. */
    public int chargeTicks() {
        return mode().charge;
    }

    /**
     * The wielder. The beam needs them to still be holding the trigger; the lance is already loosed,
     * so it only needs them alive - letting go does not swallow the discharge.
     */
    @Nullable
    public LivingEntity channeler() {
        Entity owner = level().getEntity(entityData.get(OWNER));
        if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
            return null;
        }
        if (mode() == Mode.LANCE) {
            return living;
        }
        return living.isUsingItem() && living.getUseItem().getItem() instanceof ExoDisintegratorItem ? living : null;
    }

    /**
     * How hot the gun is, 0 to 1: the ordinary beam heats over its run to the vent, the lance is at
     * full heat the moment it fires. Both sides derive it from the age, so nothing is synced for it.
     */
    public float heat(float partialTick) {
        return heatAt(mode(), age(partialTick) - chargeTicks());
    }

    /** Heat for a given number of ticks since the shot erupted; pure so the curve can be tested. */
    public static float heatAt(Mode mode, float firing) {
        if (firing <= 0f) {
            return 0f;
        }
        return mode == Mode.LANCE ? 1f : Mth.clamp(firing / OVERHEAT, 0f, 1f);
    }

    public float age(float partialTick) {
        return tickCount + partialTick;
    }

    /** Where the ray stops: the first solid block along the aim, else full range. */
    public static Vec3 beamEnd(LivingEntity owner, Vec3 eye, Vec3 look) {
        Vec3 far = eye.add(look.scale(RANGE));
        HitResult hit = owner.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = channeler();
        if (level().isClientSide) {
            if (owner != null) {
                clientTick(owner);
            }
            return;
        }
        if (owner == null) {
            if (tickCount > 3) {
                if (tickCount < mode().charge) {
                    // The charge is dropped before it erupts: a soft fizzle, so letting go has a tell.
                    playAt(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.6f);
                } else {
                    playAt(SoundEvents.BEACON_DEACTIVATE, 0.9f, 1.6f);
                }
            }
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        Mode mode = mode();
        int firing = tickCount - mode.charge;
        if (firing < 0) {
            if (tickCount % 5 == 0) {
                playAt(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.8f + tickCount / (float) mode.charge);
            }
            if (mode == Mode.LANCE && tickCount % 3 == 0) {
                playAt(SoundEvents.CONDUIT_AMBIENT_SHORT, 0.6f, 0.5f + 1.2f * tickCount / mode.charge);
            }
            if (tickCount == mode.charge - 3) {
                // The muzzle locks: one clean note right before the discharge, so the shot is called.
                playAt(SoundEvents.BEACON_POWER_SELECT, 0.8f, mode == Mode.LANCE ? 0.6f : 1.9f);
                playAt(SoundEvents.AMETHYST_BLOCK_CHIME, mode == Mode.LANCE ? 1.0f : 0.7f, 0.8f);
            }
            return;
        }
        if (firing == 0 && mode == Mode.LANCE) {
            playAt(SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.7f);
            playAt(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.6f);
        }
        if (firing == 0) {
            // The discharge itself: the thunder carries the crack, the explosion carries the weight.
            playAt(SoundEvents.GENERIC_EXPLODE, mode == Mode.LANCE ? 1.3f : 0.55f,
                    mode == Mode.LANCE ? 0.7f : 1.5f);
            playAt(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.55f, 1.9f);
            playAt(SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
            playAt(SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
        } else if (firing % 12 == 0) {
            playAt(SoundEvents.BEACON_AMBIENT, 1.0f, 1.9f);
        } else if (firing % 20 == 6) {
            playAt(SoundEvents.FIRE_AMBIENT, 0.35f, 1.9f);
        }
        if (firing % 7 == 3) {
            playAt(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.25f, 1.6f + random.nextFloat() * 0.4f);
        }
        // The lance bites every tick; the beam every other one.
        if (mode == Mode.LANCE || firing % DAMAGE_INTERVAL == 0) {
            burn((ServerLevel) level(), owner);
        }
        if (mode.fire > 0 ? firing >= mode.fire : firing >= OVERHEAT) {
            vent(owner, mode);
        }
    }

    /** The shot ends: the gun vents, goes on cooldown, and the ray is gone. */
    private void vent(LivingEntity owner, Mode mode) {
        owner.stopUsingItem();
        if (owner instanceof Player player) {
            player.getCooldowns().addCooldown(RotasRegistry.EXO_DISINTEGRATOR.get(), mode.cooldown);
        }
        playAt(SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.7f);
        if (mode == Mode.LANCE) {
            playAt(SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.5f);
        } else {
            // The vent after a full burn has to read as a failure, not just the shot ending.
            playAt(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.0f, 1.1f);
        }
        ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getEyeY() - 0.3, owner.getZ(),
                mode == Mode.LANCE ? 30 : 14, 0.35, 0.25, 0.35, 0.05);
        discard();
    }

    private void burn(ServerLevel level, LivingEntity owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getViewVector(1f);
        Vec3 end = eye.add(look.scale(RANGE));
        Mode mode = mode();
        AABB sweep = new AABB(eye, end).inflate(RADIUS * mode.radius + 2.0);
        DamageSource source = damageSources().indirectMagic(this, owner);
        Vec3 axis = end.subtract(eye);
        double length = axis.length();
        Vec3 unit = length < 1.0e-6 ? look : axis.scale(1.0 / length);
        int hits = 0;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && !e.isSpectator())) {
            if (victim == owner || victim instanceof ArmorStand || spared(victim, owner)) {
                continue;
            }
            Vec3 center = victim.getBoundingBox().getCenter();
            double along = center.subtract(eye).dot(unit);
            if (along < 0 || along > length + victim.getBbWidth()) {
                continue;
            }
            Vec3 onAxis = eye.add(unit.scale(along));
            double reach = radiusAt((float) along, mode.radius) + victim.getBbWidth() * 0.5;
            if (center.distanceToSqr(onAxis) > reach * reach) {
                continue;
            }
            victim.invulnerableTime = 0;
            float strike = (DAMAGE + victim.getMaxHealth() * MAX_HEALTH_BITE) * mode.damage;
            if (!victim.hurt(source, strike)) {
                continue;
            }
            hits++;
            victim.setSecondsOnFire(6);
            // The ray's pressure shoves whatever it holds along its length.
            victim.setDeltaMovement(victim.getDeltaMovement().add(unit.scale(0.35 * mode.radius)));
            victim.hurtMarked = true;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 6, 0.2, 0.2, 0.2, 0.35);
            if (!victim.isAlive()) {
                disintegrate(level, victim);
            }
        }
        // One sizzle under a connect carry, throttled so a crowd cannot stack it into noise.
        if (hits > 0 && tickCount % HIT_SOUND_INTERVAL == 0) {
            playAt(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.8f + random.nextFloat() * 0.3f);
        }
    }

    /** The victim comes apart into light. Mobs leave no corpse; players keep vanilla death. */
    private void disintegrate(ServerLevel level, LivingEntity victim) {
        AABB box = victim.getBoundingBox();
        Vec3 c = box.getCenter();
        double sx = box.getXsize() * 0.4, sy = box.getYsize() * 0.4, sz = box.getZsize() * 0.4;
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, sx, sy, sz, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 24, sx, sy, sz, 0.12);
        level.sendParticles(ParticleTypes.GLOW, c.x, c.y, c.z, 16, sx, sy, sz, 0.3);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 12, sx, sy, sz, 0.05);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.55f, 1.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.8f, 1.7f);
        if (!(victim instanceof Player)) {
            victim.remove(RemovalReason.KILLED);
        }
    }

    /** Shared by the ray and its Cero bolts: the wielder's pets and allies are never hit. */
    static boolean spared(LivingEntity victim, @Nullable LivingEntity owner) {
        if (owner == null) {
            return false;
        }
        if (victim instanceof TamableAnimal pet && pet.isOwnedBy(owner)) {
            return true;
        }
        return victim.isAlliedTo(owner);
    }

    private void playAt(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Client: loose sparks off the impact and along the ray, and kick the camera when it first fires. */
    private void clientTick(LivingEntity owner) {
        Mode mode = mode();
        int firing = tickCount - mode.charge;
        // The local player's own readout (crosshair gauge and lens punch) is fed every tick, charge
        // included. The lance never heats - it is its own short discharge - so it reports the
        // fraction of the discharge spent instead, which drains the same gauge over its run.
        float gauge = mode == Mode.LANCE && firing >= 0
                ? Mth.clamp(firing / (float) mode.fire, 0f, 1f) : heat(0f);
        ClientFx.beamState(entityData.get(OWNER), mode.ordinal(),
                Mth.clamp(age(0f) / mode.charge, 0f, 1f), gauge, firing >= 0);
        if (firing < 0) {
            return;
        }
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getViewVector(1f);
        Vec3 end = beamEnd(owner, eye, look);
        if (firing == 0) {
            ClientFx.quake(owner.getX(), owner.getY(), owner.getZ(), mode == Mode.LANCE ? 13f : 5.5f, 32);
        } else if (firing % 8 == 0 || mode == Mode.LANCE) {
            // The rumble grows with the heat, so a nearly-venting gun feels like it is coming apart.
            float rumble = (mode == Mode.LANCE ? 2.2f : 0.8f) * (1f + heat(0f));
            ClientFx.quake(owner.getX(), owner.getY(), owner.getZ(), rumble, 32);
        }
        Level level = level();
        net.minecraft.core.particles.DustParticleOptions ember = heat(0f) > 0.6f ? EMBER_HOT : EMBER;
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                    (random.nextDouble() - 0.5) * 2.0, random.nextDouble() * 1.2, (random.nextDouble() - 0.5) * 2.0);
            level.addParticle(ember, end.x + (random.nextDouble() - 0.5) * RADIUS, end.y + (random.nextDouble() - 0.5) * RADIUS,
                    end.z + (random.nextDouble() - 0.5) * RADIUS, 0, 0, 0);
        }
        // The hotter the gun runs, the more it vents at the muzzle: the tell before it cuts out.
        float heat = heat(0f);
        if (heat > 0.35f && random.nextFloat() < heat) {
            Vec3 vent = eye.add(look.scale(0.9)).add(0, -0.25, 0);
            level.addParticle(ParticleTypes.SMOKE, vent.x, vent.y, vent.z,
                    (random.nextDouble() - 0.5) * 0.1, 0.05 + 0.1 * heat, (random.nextDouble() - 0.5) * 0.1);
            if (heat > 0.7f) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, vent.x, vent.y, vent.z,
                        (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
                level.addParticle(EMBER_HOT, vent.x, vent.y, vent.z,
                        (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
            }
        }
        // Embers shed from the ray's skin along its first stretch, where the wielder can see them.
        double span = Math.min(eye.distanceTo(end), 60.0);
        if (span < 1.0e-3) {
            // The eye is inside a collider and the ray has no length; normalising would seed NaNs.
            return;
        }
        Vec3 unit = end.subtract(eye).normalize();
        Vec3 side = unit.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(unit);
        for (int i = 0; i < 5; i++) {
            double along = random.nextDouble() * span;
            double ang = random.nextDouble() * Math.PI * 2;
            double r = radiusAt((float) along, mode.radius) * (0.85 + random.nextDouble() * 0.3);
            Vec3 p = eye.add(unit.scale(along)).add(side.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
            level.addParticle(ember, p.x, p.y, p.z, unit.x * 0.6, unit.y * 0.6, unit.z * 0.6);
        }
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

    @Override
    public boolean isPickable() {
        return false;
    }

    /** Ray radius at distance {@code s} from the muzzle: an elliptical nose, then full width. */
    public static float radiusAt(float s) {
        return radiusAt(s, 1f);
    }

    /** The same profile at {@code scale} times the ordinary beam's width, for the lance. */
    public static float radiusAt(float s, float scale) {
        float nose = NOSE * scale;
        if (s >= nose) {
            return RADIUS * scale;
        }
        float k = 1f - Math.max(0f, s) / nose;
        return RADIUS * scale * (float) Math.sqrt(1f - k * k);
    }

    /** Smooth 0..1 ramp. */
    public static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
