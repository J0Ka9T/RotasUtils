package net.schwarz.rotasutils.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.item.ExoDisintegratorItem;
import net.schwarz.rotasutils.registry.RotasRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ExoCeroMuzzleEntity extends Entity {
    public static final int SHOTS = CeroBallistics.SHOTS;
    public static final int BURST = CeroBallistics.BURST;
    public static final int VOLLEY_TICKS = CeroBallistics.VOLLEY_TICKS;
    public static final int FADE = CeroBallistics.MAX_FLIGHT + 6;
    public static final int LIFE = VOLLEY_TICKS + FADE;
    public static final int COOLDOWN = 150;
    public static final int SHOCK_PERIOD = 6;
    public static final double SPEED = CeroBallistics.SPEED;

    private static final float DAMAGE = 1.8f;
    private static final float MAX_HEALTH_BITE = 0.005f;
    private static final float SPLASH = 4.5f * (float) CeroBallistics.SCALE;
    private static final float SPLASH_SHARE = 0.8f;
    private static final float SPLASH_BITE = 0.004f;
    private static final int FIRE_SECONDS = 5;

    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(ExoCeroMuzzleEntity.class, EntityDataSerializers.INT);

    private final CeroDamageBatch<LivingEntity> damageBatch = new CeroDamageBatch<>();
    private final java.util.List<PendingHit> pending = new java.util.ArrayList<>();

    private record PendingHit(LivingEntity victim, float damage, int dueTick) {
    }

    public ExoCeroMuzzleEntity(EntityType<? extends ExoCeroMuzzleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static int spawnedBy(int tick) {
        return CeroBallistics.spawnedBy(tick);
    }

    public static int spawnTick(int index) {
        return CeroBallistics.spawnTick(index);
    }

    public static float hash01(int a, int b) {
        return (float) CeroBallistics.hash01(a, b);
    }

    static boolean canStart(boolean sneaking, boolean correctItem, boolean coolingDown, boolean usingItem,
                            boolean beamActive, boolean barrageActive) {
        return sneaking && correctItem && !coolingDown && !usingItem && !beamActive && !barrageActive;
    }

    public static void unleash(ServerPlayer player) {
        boolean correctItem = player.getMainHandItem().getItem() instanceof ExoDisintegratorItem;
        boolean coolingDown = player.getCooldowns().isOnCooldown(RotasRegistry.EXO_DISINTEGRATOR.get());
        boolean beamActive = ExoBeamEntity.activeFor(player);
        boolean barrageActive = activeFor(player);
        if (!canStart(player.isShiftKeyDown(), correctItem, coolingDown, player.isUsingItem(), beamActive, barrageActive)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ExoCeroMuzzleEntity mount = new ExoCeroMuzzleEntity(RotasRegistry.EXO_CERO_MUZZLE.get(), level);
        mount.entityData.set(OWNER, player.getId());
        mount.moveTo(player.getX(), player.getY(), player.getZ(), 0f, 0f);
        level.addFreshEntity(mount);
        player.getCooldowns().addCooldown(RotasRegistry.EXO_DISINTEGRATOR.get(), COOLDOWN);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 0.7f, 1.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    public static boolean activeFor(LivingEntity owner) {
        return !owner.level().getEntitiesOfClass(ExoCeroMuzzleEntity.class, owner.getBoundingBox().inflate(4),
                mount -> mount.entityData.get(OWNER) == owner.getId()).isEmpty();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
    }

    @Nullable
    public LivingEntity owner() {
        Entity entity = level().getEntity(entityData.get(OWNER));
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = owner();
        if (level().isClientSide) {
            if (owner != null) {
                rumble();
            }
            return;
        }
        if (owner == null || !owner.isAlive()
                || !(owner.getMainHandItem().getItem() instanceof ExoDisintegratorItem)) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        ServerLevel server = (ServerLevel) level();
        if (tickCount >= 1 && tickCount <= VOLLEY_TICKS) {
            fireTick(server, owner);
        }
        landPending(owner);
        sounds(server);
        if (tickCount > LIFE) {
            discard();
        }
    }

    private void fireTick(ServerLevel level, LivingEntity owner) {
        int count = CeroBallistics.shotsAt(tickCount);
        if (count <= 0) {
            return;
        }
        int firstIndex = CeroBallistics.firstShotIndex(tickCount);
        long seed = getUUID().getMostSignificantBits() ^ getUUID().getLeastSignificantBits();
        java.util.List<CeroFx.Shot> shots = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            shots.add(trace(level, owner, firstIndex + i, seed));
        }
        CeroFx.send(level, owner.getId(), firstIndex, shots);
    }

    private void landPending(LivingEntity owner) {
        if (pending.isEmpty()) {
            return;
        }
        damageBatch.clear();
        boolean any = false;
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingHit hit = pending.get(i);
            if (tickCount < hit.dueTick()) {
                continue;
            }
            pending.remove(i);
            damageBatch.add(hit.victim(), hit.damage());
            any = true;
        }
        if (any) {
            applyDamage(owner);
        }
    }

    private void hold(LivingEntity victim, float damage, double distance) {
        pending.add(new PendingHit(victim, damage, tickCount + Math.round(CeroBallistics.flightTicks(distance))));
    }

    private CeroFx.Shot trace(ServerLevel level, LivingEntity owner, int index, long seed) {
        Vec3 look = owner.getViewVector(1f);
        Vec3 start = CeroBallistics.muzzle(owner.getEyePosition(), look, index);
        Vec3 direction = CeroBallistics.direction(look, index, seed);
        Vec3 far = start.add(direction.scale(CeroBallistics.RANGE));
        HitResult block = level.clip(new ClipContext(start, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        EntityHit entityHit = findEntityHit(level, owner, start, limit);
        if (entityHit != null) {
            LivingEntity victim = entityHit.victim();
            double flown = start.distanceTo(entityHit.at());
            hold(victim, DAMAGE + victim.getMaxHealth() * MAX_HEALTH_BITE, flown);
            splash(level, owner, entityHit.at(), victim, flown);
            return new CeroFx.Shot(start, entityHit.at(), true, CeroBallistics.flightTicks(flown));
        }
        if (block.getType() != HitResult.Type.MISS) {
            double flown = start.distanceTo(limit);
            splash(level, owner, limit, null, flown);
            return new CeroFx.Shot(start, limit, true, CeroBallistics.flightTicks(flown));
        }
        return new CeroFx.Shot(start, far, false, CeroBallistics.flightTicks(start.distanceTo(far)));
    }

    @Nullable
    private EntityHit findEntityHit(ServerLevel level, LivingEntity owner, Vec3 start, Vec3 limit) {
        Vec3 axis = limit.subtract(start);
        double length = axis.length();
        if (length < 1.0e-6) {
            return null;
        }
        Vec3 direction = axis.scale(1.0 / length);
        double traveled = 0.0;
        while (traveled < length) {
            double next = Math.min(length, traveled + CeroBallistics.ENTITY_QUERY_SEGMENT);
            Vec3 segmentStart = start.add(direction.scale(traveled));
            Vec3 segmentEnd = start.add(direction.scale(next));
            LivingEntity nearest = null;
            Vec3 nearestAt = null;
            double best = Double.MAX_VALUE;
            AABB sweep = new AABB(segmentStart, segmentEnd).inflate(0.6 * CeroBallistics.SCALE);
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, sweep,
                    entity -> entity.isAlive() && !entity.isSpectator())) {
                if (candidate == owner || candidate instanceof ArmorStand || ExoBeamEntity.spared(candidate, owner)) {
                    continue;
                }
                Optional<Vec3> clip = candidate.getBoundingBox().inflate(1.3 * CeroBallistics.SCALE).clip(segmentStart, segmentEnd);
                if (clip.isEmpty()) {
                    continue;
                }
                double distance = clip.get().distanceToSqr(segmentStart);
                if (distance < best) {
                    best = distance;
                    nearest = candidate;
                    nearestAt = clip.get();
                }
            }
            if (nearest != null) {
                return new EntityHit(nearest, nearestAt);
            }
            traveled = next;
        }
        return null;
    }

    private record EntityHit(LivingEntity victim, Vec3 at) {
    }

    private void splash(ServerLevel level, LivingEntity owner, Vec3 at, @Nullable LivingEntity direct, double flown) {
        AABB box = new AABB(at, at).inflate(SPLASH);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && !entity.isSpectator())) {
            if (victim == direct || victim == owner || victim instanceof ArmorStand || ExoBeamEntity.spared(victim, owner)) {
                continue;
            }
            hold(victim, DAMAGE * SPLASH_SHARE + victim.getMaxHealth() * SPLASH_BITE, flown);
        }
    }

    private void applyDamage(LivingEntity owner) {
        DamageSource source = damageSources().indirectMagic(this, owner);
        damageBatch.forEach((victim, amount) -> {
            if (!victim.isAlive()) {
                return;
            }
            victim.invulnerableTime = 0;
            if (victim.hurt(source, amount)) {
                victim.setSecondsOnFire(FIRE_SECONDS);
            }
        });
        damageBatch.clear();
    }

    private void sounds(ServerLevel level) {
        if (tickCount <= VOLLEY_TICKS) {
            if (CeroBallistics.shotsAt(tickCount) > 0) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.34f,
                        1.6f + random.nextFloat() * 0.35f);
            }
            if (tickCount % SHOCK_PERIOD == 1) {
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3f,
                        1.85f);
            }
        } else if (tickCount == VOLLEY_TICKS + 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.4f);
        }
    }

    private void rumble() {
        if (tickCount > VOLLEY_TICKS + 1) {
            return;
        }
        if (tickCount == 1) {
            ClientFx.quake(getX(), getY(), getZ(), 7f, 40);
        } else if (CeroBallistics.shotsAt(tickCount) > 0) {
            ClientFx.quake(getX(), getY(), getZ(), 1.2f, 26);
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

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }
}
