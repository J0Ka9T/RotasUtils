package net.schwarz.rotasutils.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.MobAffix;
import net.schwarz.rotasutils.core.MonsterRank;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.ThaiText;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TetrarchEntity extends Monster implements OwnsItsLevel {
    public static final int LEVEL = 99999;
    public static final int ARRIVE_TICKS = 70;
    public static final int DEATH_TICKS = 80;
    private static final float HIT_CAP = 28f;
    private static final float ECHO_HIT_CAP = 20f;
    private static final double ARENA_LEASH = 40.0;
    private static final int MELEE_COOLDOWN = 22;
    private static final int GLOBAL_COOLDOWN = 36;
    public static final double ULT_RADIUS = 11.0;

    private static final EntityDataAccessor<Integer> ARRIVE =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST_START =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> CAST_AIM =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> SHIELD =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ECHO =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SAFE_QUARTER =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(TetrarchEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_12);
    private final int[] cooldowns = new int[TetrarchPower.values().length];
    private int globalCooldown = 60;
    private int meleeCooldown;
    private int castLeft;
    private int shieldTicks;
    private int roarTicks;
    private int idleTicks;
    private Vec3 home;
    private UUID owner;
    private final List<Vec3> judgementMarks = new ArrayList<>();
    private final List<UUID> novaHit = new ArrayList<>();
    private UUID brandTarget;
    private Vec3 brandPos;
    private int brandFuse;
    private final List<Vec3> starMarks = new ArrayList<>();
    private final List<Integer> starDelays = new ArrayList<>();

    public TetrarchEntity(EntityType<? extends TetrarchEntity> type, Level level) {
        super(type, level);
        xpReward = 5000;
        setPersistenceRequired();
        setMaxUpStep(1.5f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.ARMOR, 24.0)
                .add(Attributes.ARMOR_TOUGHNESS, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public static TetrarchEntity arrive(ServerLevel level, Vec3 pos, float yaw) {
        TetrarchEntity boss = new TetrarchEntity(RotasRegistry.TETRARCH.get(), level);
        boss.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
        boss.home = pos;
        boss.entityData.set(ARRIVE, 0);
        boss.name(level);
        level.addFreshEntity(boss);
        return boss;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ARRIVE, ARRIVE_TICKS);
        entityData.define(CAST, -1);
        entityData.define(CAST_START, 0);
        entityData.define(CAST_AIM, new Vector3f());
        entityData.define(SHIELD, 0f);
        entityData.define(ECHO, false);
        entityData.define(SAFE_QUARTER, 0);
        entityData.define(PHASE, 1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

public boolean arriving() {
        return entityData.get(ARRIVE) < ARRIVE_TICKS;
    }

    public float arrival(float partialTick) {
        int t = entityData.get(ARRIVE);
        return t >= ARRIVE_TICKS ? 1f : Math.min(1f, (t + partialTick) / ARRIVE_TICKS);
    }

    public TetrarchPower casting() {
        return TetrarchPower.byOrdinal(entityData.get(CAST));
    }

    public float castTime(float partialTick) {
        return tickCount - entityData.get(CAST_START) + partialTick;
    }

    public Vec3 castAim() {
        Vector3f aim = entityData.get(CAST_AIM);
        return new Vec3(aim.x, aim.y, aim.z);
    }

    public float shield() {
        return entityData.get(SHIELD);
    }

    public boolean echo() {
        return entityData.get(ECHO);
    }

    public int safeQuarter() {
        return entityData.get(SAFE_QUARTER);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public static Vec3 quarterDirection(int quarter) {
        double angle = Math.PI / 2 * quarter;
        return new Vec3(Math.cos(angle), 0, Math.sin(angle));
    }

@Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!hasCustomName()) {
            name((ServerLevel) level());
        }
        if (arriving()) {
            int t = entityData.get(ARRIVE) + 1;
            entityData.set(ARRIVE, t);
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (t == ARRIVE_TICKS && !echo()) {
                say("arrive");
                announce();
                riftSound("rift.split", 3f, 0.7f);
                level().playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 0.5f);
                RiftFx.send((ServerLevel) level(), RiftFx.Kind.BURST, RiftFx.PRISM, position().add(0, 1.8, 0), 3f, 30);
                RiftFx.send((ServerLevel) level(), RiftFx.Kind.SHOCKWAVE, RiftFx.PRISM, position(), 22f, 34);
            }
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (arriving() || isDeadOrDying()) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (home == null) {
            home = position();
        }
        if (echo() && (owner == null || !(level.getEntity(owner) instanceof TetrarchEntity master) || !master.isAlive())) {
            RiftFx.send(level, RiftFx.Kind.TEAR, RiftFx.VIOLET, position(), 0.9f, 14);
            discard();
            return;
        }
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) {
                cooldowns[i]--;
            }
        }
        globalCooldown = Math.max(0, globalCooldown - 1);
        meleeCooldown = Math.max(0, meleeCooldown - 1);
        if (roarTicks > 0) {
            roarTicks--;
        }
        tickShield();
        tickBrand(level);
        updatePhase(level);
        regenerateWhenAlone(level);

        Player target = getTarget() instanceof Player player && player.isAlive() && !player.isSpectator() ? player : null;
        TetrarchPower power = casting();
        if (power != TetrarchPower.ASCENSION && isNoGravity()) {
            setNoGravity(false);
        }
        if (power != null) {
            getNavigation().stop();
            if (target != null && power != TetrarchPower.CONVERGENCE) {
                getLookControl().setLookAt(target, 30f, 30f);
            }
            tickCast(level, power, target);
            return;
        }
        if (target == null) {
            return;
        }
        if (distanceToSqr(home) > ARENA_LEASH * ARENA_LEASH && cooldowns[TetrarchPower.RIFT_STEP.ordinal()] == 0) {
            blink(level, home);
            return;
        }
        getLookControl().setLookAt(target, 30f, 30f);
        double distance = distanceTo(target);
        if (globalCooldown == 0 && roarTicks == 0) {
            TetrarchPower chosen = choose(distance);
            if (chosen != null) {
                begin(chosen, target);
                return;
            }
        }
        if (distance > 3.2) {
            if (tickCount % 8 == 0) {
                getNavigation().moveTo(target, 1.0);
            }
        } else {
            getNavigation().stop();
            if (meleeCooldown == 0) {
                meleeCooldown = MELEE_COOLDOWN;
                swing(InteractionHand.MAIN_HAND);
                doHurtTarget(target);
            }
        }
    }

    private TetrarchPower choose(double distance) {
        int total = 0;
        TetrarchPower[] all = TetrarchPower.values();
        int[] weights = new int[all.length];
        for (TetrarchPower power : all) {
            if (echo() && power != TetrarchPower.RIFT_STEP) {
                continue;
            }
            if (power.weight <= 0 || cooldowns[power.ordinal()] > 0 || phase() < power.minPhase || distance > power.range) {
                continue;
            }
            if (power == TetrarchPower.RIFT_STEP && distance < 6) {
                continue;
            }
            if (power == TetrarchPower.GOLD_AEGIS && shield() > 0) {
                continue;
            }
            weights[power.ordinal()] = power.weight;
            total += power.weight;
        }
        if (total == 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (TetrarchPower power : all) {
            roll -= weights[power.ordinal()];
            if (roll < 0) {
                return power;
            }
        }
        return null;
    }

    private void begin(TetrarchPower power, Player target) {
        ServerLevel level = (ServerLevel) level();
        entityData.set(CAST, power.ordinal());
        entityData.set(CAST_START, tickCount);
        castLeft = power.length();
        cooldowns[power.ordinal()] = scaled(power.cooldown);
        globalCooldown = scaled(GLOBAL_COOLDOWN) + power.length();
        Vec3 aim = target == null ? position() : target.getEyePosition();
        switch (power) {
            case VIOLET_LANCE -> {
                Vec3 from = lanceOrigin();
                Vec3 dir = aim.subtract(from).normalize();
                aim = from.add(dir.scale(power.range));
                level.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.6f);
            }
            case JUDGEMENT -> {
                judgementMarks.clear();
                for (Player player : players(power.range)) {
                    judgementMarks.add(player.position());
                    RiftFx.send(level, RiftFx.Kind.SIGIL, RiftFx.GOLD, player.position(), 2.8f, power.windup + 2);
                }
                level.playSound(null, blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2f, 0.7f);
            }
            case CRIMSON_NOVA -> {
                novaHit.clear();
                aim = position();
                RiftFx.send(level, RiftFx.Kind.GATHER, RiftFx.CRIMSON, position().add(0, 1.2, 0), 5f, power.windup);
                level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
            }
            case CONVERGENCE -> {
                int safe = random.nextInt(4);
                entityData.set(SAFE_QUARTER, safe);
                aim = position();
                openConvergence(level, safe);
                RiftFx.send(level, RiftFx.Kind.GATHER, RiftFx.PRISM, position().add(0, 3.5, 0), 7f, power.windup);
                riftSound("rift.strain", 2.5f, 0.6f);
                say("convergence");
            }
            case STARFALL -> {
                planStarfall(level, power);
                riftSound("rift.omen", 2f, 0.9f);
            }
            case SUMMON_ECHOES -> {
                RiftFx.send(level, RiftFx.Kind.GATHER, RiftFx.VIOLET, position().add(0, 1.6, 0), 4f, power.windup);
                level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2f, 0.6f);
            }
            case GRAVITY_WELL -> {
                RiftFx.send(level, RiftFx.Kind.SIGIL, RiftFx.DARK, position(), (float) power.range, power.length());
                level.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2f, 0.5f);
            }
            case HEAVENS_WHEEL -> {
                wheelSpin = random.nextBoolean() ? 1 : -1;
                wheelAngle = random.nextDouble() * 90.0;
                wheelHit.clear();
                aim = position();
                RiftFx.send(level, RiftFx.Kind.GATHER, RiftFx.PRISM, position().add(0, 1.6, 0), 9f, power.windup);
                RiftFx.send(level, RiftFx.Kind.SIGIL, RiftFx.PRISM, position(), (float) WHEEL_REACH, power.length());
                riftSound("rift.strain", 2.5f, 0.8f);
                say("wheel");
            }
            case ASCENSION -> {
                aim = position();
                ascendFrom = position();
                spiralTurn = random.nextDouble() * 90.0;
                pillars.clear();
                setNoGravity(true);
                RiftFx.send(level, RiftFx.Kind.PILLAR, RiftFx.PRISM, position(), 2.2f, power.windup);
                RiftFx.send(level, RiftFx.Kind.GATHER, RiftFx.PRISM, position().add(0, 2, 0), 12f, power.windup);
                level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 0.5f);
                riftSound("rift.omen", 3f, 0.6f);
                say("ascend");
            }
            case GOLD_AEGIS -> RiftFx.send(level, RiftFx.Kind.GATHER, RiftFx.GOLD, position().add(0, 1.4, 0), 3f, power.windup);
            case RIFT_STEP -> RiftFx.send(level, RiftFx.Kind.GATHER, RiftFx.VIOLET, position().add(0, 1.4, 0), 2f, power.windup);
            default -> {
            }
        }
        entityData.set(CAST_AIM, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
    }

    private void tickCast(ServerLevel level, TetrarchPower power, Player target) {
        int elapsed = power.length() - castLeft;
        if (elapsed < power.windup) {
            telegraph(level, power, elapsed);
        } else if (elapsed == power.windup) {
            execute(level, power, target);
        } else {
            sustain(level, power, elapsed - power.windup);
        }
        if (--castLeft <= 0) {
            entityData.set(CAST, -1);
        }
    }

private void telegraph(ServerLevel level, TetrarchPower power, int elapsed) {
        if (power == TetrarchPower.VIOLET_LANCE && elapsed == power.windup - 8) {
            riftSound("rift.strain", 1.5f, 1.4f);
        }
        if (power == TetrarchPower.HEAVENS_WHEEL && elapsed % 6 == 0) {
            drawWheel(level, 0.35f, 7);
        }
        if (power == TetrarchPower.ASCENSION) {
            Vec3 at = ascendFrom.add(0, ASCEND_HEIGHT * smooth(elapsed / (float) power.windup), 0);
            teleportTo(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            if (elapsed % 8 == 0) {
                RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.PRISM, position().add(0, 1.4, 0), 1.2f, 12);
            }
        }
    }

    private void execute(ServerLevel level, TetrarchPower power, Player target) {
        float mult = enraged() ? 1.3f : 1f;
        switch (power) {
            case RIFT_STEP -> {
                if (target != null) {
                    Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
                    blink(level, behind);
                    if (distanceToSqr(target) < 16) {
                        swing(InteractionHand.MAIN_HAND);
                        RiftFx.send(level, RiftFx.Kind.SLASH, RiftFx.VIOLET, position().add(0, 1.5, 0),
                                target.position().add(0, 1, 0), 1.3f, 9);
                        target.hurt(damageSources().mobAttack(this), 14f * mult);
                    }
                }
            }
            case VIOLET_LANCE -> {
                Vec3 from = lanceOrigin();
                Vec3 to = castAim();
                int fan = phase() >= 2 && !echo() ? 1 : 0;
                java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
                for (int side = -fan; side <= fan; side++) {
                    Vec3 end = side == 0 ? to : from.add(to.subtract(from).yRot((float) Math.toRadians(20 * side)));
                    for (Player player : players(power.range + 2)) {
                        if (!struck.contains(player.getUUID())
                                && distanceToSegment(player.position().add(0, player.getBbHeight() * 0.5, 0), from, end) < 1.3) {
                            struck.add(player.getUUID());
                            player.hurt(damageSources().indirectMagic(this, this), 20f * mult);
                        }
                    }
                    RiftFx.send(level, RiftFx.Kind.LANCE, side == 0 ? RiftFx.VIOLET : RiftFx.PRISM, from, end,
                            side == 0 ? 1.2f : 0.9f, 18);
                }
                riftSound("rift.zap", 2.5f, 0.6f);
                level.playSound(null, blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.5f, 0.5f);
            }
            case JUDGEMENT -> {
                for (Vec3 mark : judgementMarks) {
                    RiftFx.send(level, RiftFx.Kind.PILLAR, RiftFx.GOLD, mark, 2.8f, 30);
                    level.playSound(null, BlockPos.containing(mark), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.4f, 1.3f);
                    for (Player player : level.getEntitiesOfClass(Player.class, new AABB(mark, mark).inflate(2.8, 3, 2.8),
                            this::fair)) {
                        if (Math.hypot(player.getX() - mark.x, player.getZ() - mark.z) > 2.8) {
                            continue;
                        }
                        player.hurt(damageSources().indirectMagic(this, this), 22f * mult);
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
                    }
                }
            }
            case GOLD_AEGIS -> {
                entityData.set(SHIELD, echo() ? 0f : 80f);
                shieldTicks = 200;
                RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.GOLD, position().add(0, 1.6, 0), 1.6f, 20);
                RiftFx.send(level, RiftFx.Kind.SHOCKWAVE, RiftFx.GOLD, position(), 5f, 16);
                level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2f, 1.4f);
            }
            case CRIMSON_NOVA -> {
                ClientFxCall.quake(level, position(), 3.5f, 30);
                RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.CRIMSON, position().add(0, 1, 0), 2.2f, 20);
                riftSound("rift.crack", 2f, 0.6f);
                level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.5f);
            }
            case CRIMSON_BRAND -> {
                if (target != null) {
                    brandTarget = target.getUUID();
                    brandPos = target.position();
                    brandFuse = 80;
                    RiftFx.send(level, RiftFx.Kind.SIGIL, RiftFx.CRIMSON, brandPos, 8f, brandFuse + 1);
                    RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.CRIMSON, target.position().add(0, 1, 0), 0.8f, 14);
                    target.displayClientMessage(ThaiText.c("rotasutils.tetrarch.branded").withStyle(ChatFormatting.RED), true);
                    level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5f, 0.5f);
                }
            }
            case VOID_GRASP -> {
                for (Player player : players(power.range)) {
                    VoidGraspEntity.erupt(level, player.position(), this);
                }
            }
            case GRAVITY_WELL -> {
            }
            case STARFALL -> {
                for (int i = 0; i < starMarks.size(); i++) {
                    Vec3 mark = starMarks.get(i);
                    Vec3 from = mark.add((random.nextDouble() - 0.5) * 24, 42, (random.nextDouble() - 0.5) * 24);
                    RiftFx.send(level, RiftFx.Kind.METEOR, i % 4, mark, from, 1f, starDelays.get(i));
                }
            }
            case SUMMON_ECHOES -> summonEchoes(level);
            case HEAVENS_WHEEL -> {
                riftSound("rift.zap", 3f, 0.5f);
                level.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5f, 0.5f);
                ClientFxCall.quake(level, position(), 2f, 16);
            }
            case ASCENSION -> {
                RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.PRISM, position().add(0, 1.4, 0), 3.5f, 30);
                riftSound("rift.split", 3f, 0.6f);
            }
            case CONVERGENCE -> {
                int safe = safeQuarter();
                Vec3 safeDir = quarterDirection(safe);
                for (Player player : players(ULT_RADIUS + 14)) {
                    Vec3 offset = player.position().subtract(position()).multiply(1, 0, 1);
                    boolean inSafe = offset.length() > 3 && offset.normalize().dot(safeDir) > Math.cos(Math.toRadians(45));
                    if (!inSafe) {
                        player.hurt(damageSources().indirectMagic(this, this), 34f * mult);
                    }
                }
                RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.PRISM, position().add(0, 2.5, 0), 4f, 30);
                RiftFx.send(level, RiftFx.Kind.SHOCKWAVE, RiftFx.PRISM, position(), (float) ULT_RADIUS + 14f, 30);
                riftSound("rift.split", 3f, 0.8f);
                level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2f, 0.4f);
                ClientFxCall.quake(level, position(), 5f, 48);
            }
        }
    }

    private void sustain(ServerLevel level, TetrarchPower power, int t) {
        float mult = enraged() ? 1.3f : 1f;
        switch (power) {
            case CRIMSON_NOVA -> {
                double radius = novaRadius(t);
                for (Player player : players(radius + 1)) {
                    double d = Math.hypot(player.getX() - getX(), player.getZ() - getZ());
                    boolean grounded = player.getY() - getY() < 1.1;
                    if (grounded && Math.abs(d - radius) < 0.9 && !novaHit.contains(player.getUUID())) {
                        novaHit.add(player.getUUID());
                        player.hurt(damageSources().mobAttack(this), 16f * mult);
                        player.knockback(1.6, getX() - player.getX(), getZ() - player.getZ());
                        player.setSecondsOnFire(4);
                    }
                }
            }
            case GRAVITY_WELL -> {
                for (Player player : players(power.range)) {
                    Vec3 pull = position().subtract(player.position()).normalize().scale(0.11);
                    player.setDeltaMovement(player.getDeltaMovement().add(pull.x, 0.01, pull.z));
                    player.hurtMarked = true;
                }
                if (t == power.active - 1) {
                    swing(InteractionHand.MAIN_HAND);
                    RiftFx.send(level, RiftFx.Kind.SHOCKWAVE, RiftFx.DARK, position(), 6f, 14);
                    RiftFx.send(level, RiftFx.Kind.SLASH, RiftFx.DARK, position().add(0, 1.2, 0),
                            position().add(getLookAngle()), 2.2f, 10);
                    for (Player player : players(4.5)) {
                        player.hurt(damageSources().mobAttack(this), 15f * mult);
                        player.knockback(2.2, getX() - player.getX(), getZ() - player.getZ());
                    }
                    level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2f, 0.5f);
                    ClientFxCall.quake(level, position(), 2.5f, 20);
                }
            }
            case HEAVENS_WHEEL -> tickWheel(level, power, t, mult);
            case ASCENSION -> tickAscension(level, power, t, mult);
            case STARFALL -> {
                for (int i = 0; i < starMarks.size(); i++) {
                    if (starDelays.get(i) != t) {
                        continue;
                    }
                    Vec3 mark = starMarks.get(i);
                    for (Player player : level.getEntitiesOfClass(Player.class, new AABB(mark, mark).inflate(2.6, 3, 2.6), this::fair)) {
                        if (Math.hypot(player.getX() - mark.x, player.getZ() - mark.z) > 2.6) {
                            continue;
                        }
                        player.hurt(damageSources().indirectMagic(this, this), 13f * mult);
                        player.setDeltaMovement(player.getDeltaMovement().add(0, 0.55, 0));
                        player.hurtMarked = true;
                    }
                    level.playSound(null, BlockPos.containing(mark), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9f, 1.2f);
                }
            }
            default -> {
            }
        }
    }

private static final double WHEEL_REACH = 20.0;
    private int wheelSpin = 1;
    private double wheelAngle;
    private final java.util.Map<java.util.UUID, Integer> wheelHit = new java.util.HashMap<>();

    public static double wheelSpeed(int t) {
        double ramp = Math.min(1.0, t / 90.0);
        return 1.2 + 3.3 * ramp * ramp;
    }

    private void tickWheel(ServerLevel level, TetrarchPower power, int t, float mult) {
        wheelAngle += wheelSpin * wheelSpeed(t) * (enraged() ? 1.2 : 1.0);
        if (t % 2 == 0) {
            drawWheel(level, 1f, 4);
        }
        Vec3 centre = position();
        for (Player player : players(WHEEL_REACH + 1)) {
            Vec3 offset = player.position().subtract(centre).multiply(1, 0, 1);
            double d = offset.length();
            if (d < 1.5 || d > WHEEL_REACH || player.getY() - getY() > 3) {
                continue;
            }
            double bearing = Math.toDegrees(Math.atan2(offset.z, offset.x));
            for (int spoke = 0; spoke < 4; spoke++) {
                double gap = Math.abs(Mth.wrapDegrees(bearing - (wheelAngle + spoke * 90.0)));
                if (gap < Math.toDegrees(1.4 / d)) {
                    Integer last = wheelHit.get(player.getUUID());
                    if (last == null || t - last >= 12) {
                        wheelHit.put(player.getUUID(), t);
                        player.hurt(damageSources().indirectMagic(this, this), 12f * mult);
                        player.knockback(0.9, getX() - player.getX(), getZ() - player.getZ());
                    }
                    break;
                }
            }
        }
        if (t == power.active - 1) {
            RiftFx.send(level, RiftFx.Kind.SHOCKWAVE, RiftFx.PRISM, position(), (float) WHEEL_REACH, 20);
            riftSound("rift.implode", 2f, 1.2f);
        }
    }

    private void drawWheel(ServerLevel level, float size, int life) {
        Vec3 from = position().add(0, 1.1, 0);
        for (int spoke = 0; spoke < 4; spoke++) {
            double a = Math.toRadians(wheelAngle + spoke * 90.0);
            Vec3 to = from.add(Math.cos(a) * WHEEL_REACH, 0, Math.sin(a) * WHEEL_REACH);
            RiftFx.send(level, RiftFx.Kind.LANCE, spoke, from, to, size, life);
        }
    }

private static final double ASCEND_HEIGHT = 7.0;
    private Vec3 ascendFrom = Vec3.ZERO;
    private double spiralTurn;
    private final java.util.List<double[]> pillars = new java.util.ArrayList<>();

    private void tickAscension(ServerLevel level, TetrarchPower power, int t, float mult) {
        int fall = power.active - 14;
        if (t < fall) {
            teleportTo(ascendFrom.x, ascendFrom.y + ASCEND_HEIGHT + 0.3 * Math.sin(t * 0.2), ascendFrom.z);
            setDeltaMovement(Vec3.ZERO);
            if (t % 8 == 0) {
                double progress = t / (double) fall;
                double radius = 22.0 - 18.0 * progress;
                spiralTurn += 26.0;
                for (int arm = 0; arm < 4; arm++) {
                    double a = Math.toRadians(spiralTurn + arm * 90.0);
                    Vec3 at = ascendFrom.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                    Vec3 floor = ground(level, at);
                    Vec3 mark = floor == null ? at : floor;
                    RiftFx.send(level, RiftFx.Kind.SIGIL, arm, mark, 3f, 10);
                    pillars.add(new double[]{mark.x, mark.y, mark.z, t + 8, arm});
                }
            }
            for (java.util.Iterator<double[]> it = pillars.iterator(); it.hasNext(); ) {
                double[] p = it.next();
                if (p[3] != t) {
                    continue;
                }
                it.remove();
                Vec3 mark = new Vec3(p[0], p[1], p[2]);
                RiftFx.send(level, RiftFx.Kind.PILLAR, (int) p[4], mark, 3f, 18);
                level.playSound(null, BlockPos.containing(mark), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1f, 1.4f);
                for (Player player : level.getEntitiesOfClass(Player.class, new AABB(mark, mark).inflate(3, 4, 3), this::fair)) {
                    if (Math.hypot(player.getX() - mark.x, player.getZ() - mark.z) <= 3) {
                        player.hurt(damageSources().indirectMagic(this, this), 16f * mult);
                    }
                }
            }
            return;
        }
        if (t == fall) {
            pillars.clear();
            RiftFx.send(level, RiftFx.Kind.SIGIL, RiftFx.PRISM, ascendFrom, 12f, 14);
            riftSound("rift.strain", 3f, 0.5f);
        }
        double k = Math.min(1.0, (t - fall) / 13.0);
        teleportTo(ascendFrom.x, ascendFrom.y + ASCEND_HEIGHT * (1 - k * k), ascendFrom.z);
        if (t == power.active - 1) {
            setNoGravity(false);
            teleportTo(ascendFrom.x, ascendFrom.y, ascendFrom.z);
            RiftFx.send(level, RiftFx.Kind.PILLAR, RiftFx.PRISM, ascendFrom, 5f, 26);
            RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.PRISM, ascendFrom.add(0, 1, 0), 4.5f, 30);
            RiftFx.send(level, RiftFx.Kind.SHOCKWAVE, RiftFx.PRISM, ascendFrom, 24f, 34);
            for (Player player : players(12)) {
                double d = Math.hypot(player.getX() - ascendFrom.x, player.getZ() - ascendFrom.z);
                float hit = (float) (30.0 * (1.0 - d / 12.0));
                if (hit > 2f) {
                    player.hurt(damageSources().mobAttack(this), hit * mult);
                    player.knockback(2.4, ascendFrom.x - player.getX(), ascendFrom.z - player.getZ());
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.6, 0));
                    player.hurtMarked = true;
                }
            }
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3f, 0.4f);
            riftSound("rift.split", 3f, 0.5f);
            ClientFxCall.quake(level, ascendFrom, 6f, 50);
        }
    }

    private static float smooth(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        return c * c * (3f - 2f * c);
    }

    private void planStarfall(ServerLevel level, TetrarchPower power) {
        starMarks.clear();
        starDelays.clear();
        for (Player player : players(power.range)) {
            for (int n = 0; n < 3 && starMarks.size() < 18; n++) {
                Vec3 at = n == 0 ? player.position() : player.position().add((random.nextDouble() - 0.5) * 7, 0,
                        (random.nextDouble() - 0.5) * 7);
                Vec3 floor = ground(level, at);
                Vec3 mark = floor == null ? at : floor;
                int delay = 4 + (starMarks.size() * 5) % (power.active - 6);
                starMarks.add(mark);
                starDelays.add(delay);
                RiftFx.send(level, RiftFx.Kind.SIGIL, starMarks.size() % 4, mark, 2.6f, power.windup + delay);
            }
        }
    }

    public static double novaRadius(float t) {
        return Math.min(16.0, t * 0.55);
    }

    private void openConvergence(ServerLevel level, int safe) {
        RiftPortalEntity.Palette[] colours = {RiftPortalEntity.Palette.VIOLET, RiftPortalEntity.Palette.GOLD,
                RiftPortalEntity.Palette.CRIMSON, RiftPortalEntity.Palette.VOID};
        for (int quarter = 0; quarter < 4; quarter++) {
            Vec3 dir = quarterDirection(quarter);
            Vec3 spot = ground(level, position().add(dir.scale(ULT_RADIUS)));
            if (spot == null) {
                continue;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(dir.x, -dir.z));
            RiftPortalEntity.open(level, spot, yaw, RiftPortalEntity.Kind.CONVERGE, colours[quarter],
                    TetrarchPower.CONVERGENCE.windup + 12);
        }
    }

    private void summonEchoes(ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            Vec3 dir = quarterDirection(random.nextInt(4)).scale(4 + random.nextDouble() * 2);
            Vec3 spot = ground(level, position().add(dir));
            if (spot == null) {
                continue;
            }
            TetrarchEntity echo = new TetrarchEntity(RotasRegistry.TETRARCH.get(), level);
            echo.entityData.set(ECHO, true);
            echo.owner = getUUID();
            echo.home = spot;
            echo.moveTo(spot.x, spot.y, spot.z, getYRot(), 0);
            echo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(90.0);
            echo.setHealth(90f);
            echo.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0);
            echo.entityData.set(ARRIVE, ARRIVE_TICKS - 20);
            echo.name(level);
            echo.setTarget(getTarget());
            level.addFreshEntity(echo);
            RiftFx.send(level, RiftFx.Kind.TEAR, RiftFx.VIOLET, spot, 1.1f, 18);
        }
    }

    private void blink(ServerLevel level, Vec3 to) {
        Vec3 spot = ground(level, to);
        if (spot == null) {
            return;
        }
        RiftFx.send(level, RiftFx.Kind.TEAR, RiftFx.VIOLET, position(), 1.1f, 14);
        riftSound("rift.vanish", 1.6f, 0.8f);
        teleportTo(spot.x, spot.y, spot.z);
        RiftFx.send(level, RiftFx.Kind.TEAR, RiftFx.VIOLET, spot, 1.1f, 14);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 0.4f);
    }

private void tickShield() {
        if (shieldTicks > 0 && --shieldTicks == 0) {
            entityData.set(SHIELD, 0f);
        }
    }

    private void tickBrand(ServerLevel level) {
        if (brandTarget == null) {
            return;
        }
        Player player = level.getPlayerByUUID(brandTarget);
        if (player == null || !player.isAlive()) {
            brandTarget = null;
            return;
        }
        if (brandFuse % 10 == 0) {
            RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.CRIMSON, player.position().add(0, player.getBbHeight() + 0.4, 0),
                    0.3f, 8);
        }
        if (--brandFuse > 0) {
            return;
        }
        if (player.position().distanceTo(brandPos) < 8) {
            player.hurt(damageSources().indirectMagic(this, this), 18f * (enraged() ? 1.3f : 1f));
            RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.CRIMSON, brandPos.add(0, 1, 0), 2.6f, 24);
            RiftFx.send(level, RiftFx.Kind.SHOCKWAVE, RiftFx.CRIMSON, brandPos, 8f, 18);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.8f);
        } else {
            RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.DARK, brandPos.add(0, 0.5, 0), 0.6f, 14);
        }
        brandTarget = null;
    }

    private void updatePhase(ServerLevel level) {
        if (echo()) {
            return;
        }
        float fraction = getHealth() / getMaxHealth();
        int next = fraction <= 1f / 3 ? 3 : fraction <= 2f / 3 ? 2 : 1;
        if (next <= phase()) {
            if (enraged() && bossBar.getColor() != BossEvent.BossBarColor.RED) {
                bossBar.setColor(BossEvent.BossBarColor.RED);
                say("enrage");
            }
            return;
        }
        setNoGravity(false);
        pillars.clear();
        entityData.set(PHASE, next);
        roarTicks = 50;
        bossBar.setColor(next == 2 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.WHITE);
        say(next == 2 ? "phase2" : "phase3");
        level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3f, 0.5f);
        riftSound("rift.implode", 2.5f, 0.7f);
        RiftFx.send(level, RiftFx.Kind.BURST, RiftFx.PRISM, position().add(0, 2, 0), 2.5f, 26);
        RiftFx.send(level, RiftFx.Kind.SHOCKWAVE, RiftFx.PRISM, position(), 18f, 28);
        RiftFx.send(level, RiftFx.Kind.PILLAR, RiftFx.PRISM, position(), 3.5f, 40);
        for (int quarter = 0; quarter < 4; quarter++) {
            Vec3 spot = ground(level, position().add(quarterDirection(quarter).scale(12)));
            if (spot != null) {
                RiftFx.send(level, RiftFx.Kind.PILLAR, quarter, spot, 2.4f, 36);
            }
        }
        ClientFxCall.quake(level, position(), 4f, 40);
        entityData.set(CAST, -1);
        begin(TetrarchPower.SUMMON_ECHOES, getTarget() instanceof Player player ? player : null);
    }

    private void regenerateWhenAlone(ServerLevel level) {
        if (players(32).isEmpty()) {
            if (++idleTicks > 100 && tickCount % 20 == 0) {
                heal(10f);
            }
        } else {
            idleTicks = 0;
        }
    }

    public boolean enraged() {
        return !echo() && getHealth() / getMaxHealth() <= 0.25f;
    }

    private int scaled(int ticks) {
        return enraged() ? (int) (ticks * 0.65) : ticks;
    }

@Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (arriving() || roarTicks > 30 || isDeadOrDying()) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        if (source.getEntity() instanceof TetrarchEntity) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.5f;
        }
        amount = Math.min(amount, echo() ? ECHO_HIT_CAP : HIT_CAP);
        float shield = shield();
        if (shield > 0) {
            float absorbed = Math.min(shield, amount);
            entityData.set(SHIELD, shield - absorbed);
            amount -= absorbed;
            if (!level().isClientSide) {
                Vec3 from = source.getSourcePosition() == null ? position().add(getLookAngle()) : source.getSourcePosition();
                Vec3 toward = from.subtract(position()).multiply(1, 0, 1);
                Vec3 hit = position().add(0, 1.5, 0).add(toward.lengthSqr() < 1.0e-4 ? Vec3.ZERO : toward.normalize().scale(2.0));
                RiftFx.send((ServerLevel) level(), RiftFx.Kind.SHIELD_SPARK, RiftFx.GOLD, hit, 1f, 10);
            }
            if (amount <= 0) {
                level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof Player player) {
            player.knockback(0.9, getX() - player.getX(), getZ() - player.getZ());
        }
        if (level() instanceof ServerLevel level) {
            int colour = enraged() ? RiftFx.CRIMSON : phase() == 3 ? RiftFx.DARK : phase() == 2 ? RiftFx.GOLD : RiftFx.VIOLET;
            RiftFx.send(level, RiftFx.Kind.SLASH, colour, position().add(0, 1.6, 0), target.position().add(0, 1, 0),
                    echo() ? 0.8f : 1.2f, 8);
        }
        return hit;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

@Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !echo()) {
            say("death");
            riftSound("rift.collapse", 3f, 0.6f);
            level().playSound(null, blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.5f, 0.6f);
            ClientFxCall.quake((ServerLevel) level(), position(), 4f, 48);
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (!level().isClientSide) {
            ServerLevel level = (ServerLevel) level();
            int end = echo() ? 20 : DEATH_TICKS;
            if (deathTime >= end && !isRemoved()) {
                if (echo()) {
                    RiftFx.send(level, RiftFx.Kind.TEAR, RiftFx.VIOLET, position(), 0.9f, 14);
                } else {
                    RiftFx.send(level, RiftFx.Kind.APOTHEOSIS, RiftFx.PRISM, position().add(0, deathTime * 0.035, 0), 1.5f, 120);
                    riftSound("rift.split", 3f, 0.5f);
                    ClientFxCall.quake(level, position(), 5f, 64);
                }
                level.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (echo()) {
            return;
        }
        spawnAtLocation(new ItemStack(Items.NETHER_STAR, 2));
        spawnAtLocation(new ItemStack(Items.DIAMOND, 16));
        spawnAtLocation(new ItemStack(Items.NETHERITE_INGOT, 2));
        spawnAtLocation(new ItemStack(RotasRegistry.ENRICHED_ORIDECON.get(), 4));
        spawnAtLocation(new ItemStack(RotasRegistry.ENRICHED_ELUNIUM.get(), 4));
        spawnAtLocation(new ItemStack(RotasRegistry.CERTIFICATE_SCROLL.get(), 1));
        for (var rune : RotasRegistry.RUNES.values()) {
            spawnAtLocation(new ItemStack(rune.get(), 1));
        }
    }

@Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!echo()) {
            bossBar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    private void name(ServerLevel level) {
        MonsterRank rank = echo() ? MonsterRank.ELITE : MonsterRank.WORLD_BOSS;
        String base = ThaiText.t(echo() ? "entity.rotasutils.tetrarch.echo" : "entity.rotasutils.tetrarch");
        String text = RotasData.get(level.getServer()).levelConfig().mobLevel().formatName(base, LEVEL);
        Component plate = Component.literal(text).withStyle(style -> style.withColor(TextColor.fromRgb(rank.rgb()))
                .withInsertion(MobAffix.encode(rank, List.of())));
        setCustomName(plate);
        bossBar.setName(plate);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ECHO.equals(key)) {
            refreshDimensions();
        }
        if (PHASE.equals(key) && level().isClientSide && tickCount > 5) {
            phaseShiftTick = tickCount;
        }
    }

    private int phaseShiftTick = -100000;

    public float sincePhaseShift(float partialTick) {
        return tickCount - phaseShiftTick + partialTick;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return echo() ? super.getDimensions(pose).scale(0.65f) : super.getDimensions(pose);
    }

    private void say(String key) {
        if (echo() || !(level() instanceof ServerLevel level)) {
            return;
        }
        Component line = ThaiText.c("rotasutils.tetrarch.say." + key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        Component speaker = ThaiText.c("entity.rotasutils.tetrarch").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 64 * 64) {
                player.sendSystemMessage(Component.empty().append(speaker).append(Component.literal(": ")).append(line));
            }
        }
    }

private boolean seenForming;
    private int formedTick = -100000;

    public float sinceFormed(float partialTick) {
        return tickCount - formedTick + partialTick;
    }

    private void clientTick() {
        if (arriving()) {
            seenForming = true;
        } else if (seenForming) {
            seenForming = false;
            formedTick = tickCount;
        }
        Vec3 chest = position().add(0, getBbHeight() * 0.6, 0);
        if (arriving() && tickCount % 8 == 0) {
            RiftFx.local(RiftFx.Kind.GATHER, RiftFx.PRISM, chest, 3f, 8);
        }
        TetrarchPower power = casting();
        if (power == TetrarchPower.GRAVITY_WELL && castTime(0) > power.windup && tickCount % 2 == 0) {
            RiftFx.local(RiftFx.Kind.GATHER, RiftFx.DARK, position().add(0, 0.4, 0), (float) power.range, 2);
        }
        if (deathTime > 0 && tickCount % 3 == 0) {
            int colour = (deathTime / 3) % 4;
            Vec3 at = position().add((random.nextDouble() - 0.5) * 1.2, 0.6 + random.nextDouble() * 2.2 + deathTime * 0.035,
                    (random.nextDouble() - 0.5) * 1.2);
            RiftFx.local(RiftFx.Kind.BURST, colour, at, echo() ? 0.25f : 0.6f, 12);
        }
        if (enraged() && deathTime == 0 && tickCount % 5 == 0) {
            Vec3 at = position().add((random.nextDouble() - 0.5) * 1.4, random.nextDouble() * 2.8, (random.nextDouble() - 0.5) * 1.4);
            RiftFx.local(RiftFx.Kind.BURST, RiftFx.CRIMSON, at, 0.2f, 10);
        }
    }

private List<Player> players(double range) {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(range), this::fair);
    }

    private boolean fair(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    public Vec3 lanceOrigin() {
        return position().add(0, getBbHeight() * 0.8, 0);
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(1.0e-6, ab.lengthSqr()), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private void riftSound(String id, float volume, float pitch) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(new ResourceLocation(net.schwarz.rotasutils.Rotasutils.MOD_ID, id));
        level().playSound(null, blockPosition(), event, SoundSource.HOSTILE, volume, pitch);
    }

    private void announce() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Component title = ThaiText.c("entity.rotasutils.tetrarch").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component subtitle = ThaiText.c("rotasutils.tetrarch.epithet").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 64 * 64) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(12, 70, 24));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
            }
        }
    }

    private Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 3; dy >= -5; dy--) {
            BlockPos pos = base.offset(0, dy, 0);
            if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                    && level.noCollision(this, getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(position())))) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return null;
    }

@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("RotasEcho", echo());
        tag.putInt("RotasPhase", phase());
        if (home != null) {
            tag.putDouble("RotasHomeX", home.x);
            tag.putDouble("RotasHomeY", home.y);
            tag.putDouble("RotasHomeZ", home.z);
        }
        if (owner != null) {
            tag.putUUID("RotasOwner", owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ECHO, tag.getBoolean("RotasEcho"));
        entityData.set(PHASE, Math.max(1, tag.getInt("RotasPhase")));
        entityData.set(ARRIVE, ARRIVE_TICKS);
        if (tag.contains("RotasHomeX")) {
            home = new Vec3(tag.getDouble("RotasHomeX"), tag.getDouble("RotasHomeY"), tag.getDouble("RotasHomeZ"));
        }
        if (tag.hasUUID("RotasOwner")) {
            owner = tag.getUUID("RotasOwner");
        }
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }

    static final class ClientFxCall {
        private ClientFxCall() {
        }

        static void quake(ServerLevel level, Vec3 at, float degrees, double falloff) {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeDouble(at.x);
            buf.writeDouble(at.y);
            buf.writeDouble(at.z);
            buf.writeFloat(degrees);
            buf.writeDouble(falloff);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(at) < falloff * falloff) {
                    dev.architectury.networking.NetworkManager.sendToPlayer(player, QUAKE, new net.minecraft.network.FriendlyByteBuf(buf.copy()));
                }
            }
        }
    }

    public static final ResourceLocation QUAKE = new ResourceLocation(net.schwarz.rotasutils.Rotasutils.MOD_ID, "quake");
}
