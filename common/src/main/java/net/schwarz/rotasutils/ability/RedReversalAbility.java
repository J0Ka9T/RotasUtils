package net.schwarz.rotasutils.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.item.RedReversalItem;

public final class RedReversalAbility implements AbilityDefinition {
    public static final RedReversalAbility INSTANCE = new RedReversalAbility("red_reversal", 1.0, 1.0, false, false,
            RedTimings.RELEASE, RedTimings.END_TICKS, RedTimings.RANGE, RedTimings.PROJECTILE_SPEED);
    public static final RedReversalAbility MAX = new RedReversalAbility("red_reversal_max", 2.0, 2.6, true, true,
            RedTimings.RELEASE, RedTimings.END_TICKS, RedTimings.RANGE, RedTimings.PROJECTILE_SPEED);
    public static final RedReversalAbility PURPLE = new RedReversalAbility("hollow_purple", PurpleTimings.BLAST_SCALE, 4.0, true, false,
            PurpleTimings.RELEASE, PurpleTimings.END_TICKS, PurpleTimings.RANGE, PurpleTimings.PROJECTILE_SPEED);
    public static final ResourceLocation ID = INSTANCE.id;

    private final ResourceLocation id;
    private final double size;
    private final double power;
    private final boolean carve;
    private final boolean aftershock;
    private final int endTicks;
    private final double range;
    private final double speed;
    private final Timeline<AbilityContext> timeline;

    private RedReversalAbility(String name, double size, double power, boolean carve, boolean aftershock,
                               double releaseSeconds, int endTicks, double range, double speed) {
        this.id = Rotasutils.id(name);
        this.size = size;
        this.power = power;
        this.carve = carve;
        this.aftershock = aftershock;
        this.endTicks = endTicks;
        this.range = range;
        this.speed = speed;
        this.timeline = new Timeline<AbilityContext>()
                .at(RedTimings.ANIM_START, "begin", RedReversalAbility::begin)
                .at(releaseSeconds, "release", this::release);
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public int cooldownTicks() {
        return RedTimings.COOLDOWN_TICKS;
    }

    @Override
    public int durationTicks() {
        return endTicks;
    }

    @Override
    public boolean canStart(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof RedReversalItem item && item.ability() == this;
    }

    @Override
    public Timeline<AbilityContext> timeline() {
        return timeline;
    }

@Override
    public void end(AbilityContext context, boolean completed) {
        context.player.setInvulnerable(false);
    }

    private static void begin(AbilityContext c) {
        ServerPlayer player = c.player;
        player.setInvulnerable(true);
        Vec3 to = c.target.position().subtract(player.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * 180.0 / Math.PI) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * 180.0 / Math.PI);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        player.yBodyRot = yaw;
        player.yHeadRot = yaw;
    }

    private void release(AbilityContext c) {
        ServerPlayer player = c.player;
        ServerLevel level = c.level;
        Vec3 aim = c.target.position();
        if (c.target.isEntity()) {
            Entity locked = level.getEntity(c.target.entityId());
            if (locked != null && locked.isAlive()) {
                aim = locked.position().add(0, locked.getBbHeight() * 0.55, 0);
            }
        }
        Vec3 eye = player.getEyePosition();
        Vec3 dir = aim.subtract(eye);
        dir = dir.lengthSqr() < 1.0e-4 ? player.getLookAngle() : dir.normalize();
        Vec3 origin = eye.add(dir.scale(0.7)).add(0, -0.2, 0);
        Vec3 end = origin.add(dir.scale(range));
        var block = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(origin);
        Vec3 limit = origin.add(dir.scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, origin, limit,
                new AABB(origin, limit).inflate(1.0), e -> canHit(e, player));
        Vec3 impact = hit != null ? hit.getLocation() : limit;
        int travel = Math.max(1, (int) Math.ceil(impact.distanceTo(origin) / speed));
        int hitId = hit != null ? hit.getEntity().getId() : -1;
        AbilityNet.sendRelease(c, origin, impact, hitId, travel);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 0.7f);
        final Vec3 travelDir = dir;
        if (aftershock) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
        if (carve) {
            if (this == PURPLE) {
                carve(c, origin, impact, travelDir, travel);
            } else {
                c.after(Math.max(1, travel / 2), () -> carve(c, origin, impact, travelDir, 0));
            }
        }
        c.after(travel, () -> blast(c, impact, travelDir, 1.0));
        if (aftershock) {
            c.after(travel + 6, () -> blast(c, impact, travelDir, 0.45));
        }
    }

    private static boolean canHit(Entity e, ServerPlayer caster) {
        return e != caster && e.isAlive() && !e.isSpectator() && e.isPickable() && !(e instanceof ArmorStand);
    }

    private void carve(AbilityContext c, Vec3 from, Vec3 to, Vec3 travelDir, int travelTicks) {
        ServerPlayer caster = c.player;
        double reach = 3.0 * Math.sqrt(size);
        Vec3 seg = to.subtract(from);
        for (LivingEntity victim : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(reach),
                e -> e.isAlive() && !e.isSpectator() && e != caster && !(e instanceof ArmorStand))) {
            Vec3 centre = victim.getBoundingBox().getCenter();
            double u = Mth.clamp(centre.subtract(from).dot(seg) / Math.max(1.0e-4, seg.lengthSqr()), 0, 1);
            double f = Falloff.of(centre.distanceTo(from.add(seg.scale(u))), reach);
            if (f < 0.02 || spared(victim, caster)) {
                continue;
            }
            if (travelTicks > 0) {
                double flight = travelTicks / 20.0;
                double launch = Math.min(0.10, flight * 0.4);
                int delay = Math.max(1, (int) Math.ceil(20 * (launch + (flight - launch) * Math.pow(u, 2.0 / 3.0))));
                c.after(delay, () -> {
                    if (!victim.isAlive() || victim.level() != c.level || spared(victim, caster)) {
                        return;
                    }
                    Vec3 now = victim.getBoundingBox().getCenter();
                    double along = Mth.clamp(now.subtract(from).dot(seg) / Math.max(1.0e-4, seg.lengthSqr()), 0, 1);
                    double falloff = Falloff.of(now.distanceTo(from.add(seg.scale(along))), reach);
                    if (falloff >= 0.02) {
                        strikeCarve(c, victim, travelDir, falloff);
                    }
                });
            } else {
                strikeCarve(c, victim, travelDir, f);
            }
        }
    }

    private void strikeCarve(AbilityContext c, LivingEntity victim, Vec3 direction, double falloff) {
        victim.invulnerableTime = 0;
        boolean hurt = victim.hurt(c.level.damageSources().playerAttack(c.player),
                (float) (RedTimings.DAMAGE * 0.5 * power * falloff));
        if (!erase(c, victim, hurt)) {
            victim.setDeltaMovement(victim.getDeltaMovement().add(direction.scale(1.5 * falloff)).add(0, 0.4 * falloff, 0));
            victim.hurtMarked = true;
        }
    }

    private void blast(AbilityContext c, Vec3 impact, Vec3 travelDir, double strength) {
        ServerPlayer caster = c.player;
        ServerLevel level = c.level;
        double radius = RedTimings.BLAST_RADIUS * size * (strength < 1 ? 1.3 : 1.0);
        if (strength < 1) {
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.5f);
        }
        AABB box = new AABB(impact, impact).inflate(radius);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e != caster && !(e instanceof ArmorStand))) {
            if (spared(victim, caster)) {
                continue;
            }
            Vec3 centre = victim.getBoundingBox().getCenter();
            double distance = centre.distanceTo(impact);
            double f = Falloff.of(Math.max(0, distance - victim.getBbWidth() * 0.5), radius);
            if (f < 0.02) {
                continue;
            }
            DamageSource source = level.damageSources().playerAttack(caster);
            victim.invulnerableTime = 0;
            boolean hurt = victim.hurt(source, (float) (RedTimings.DAMAGE * power * strength * f));
            if (erase(c, victim, hurt)) {
                continue;
            }
            Vec3 away = centre.subtract(impact);
            away = away.lengthSqr() < 0.04 ? travelDir : away.normalize();
            double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double push = RedTimings.KNOCKBACK * Math.sqrt(power) * strength * f * Math.max(0, resist);
            double lift = (0.12 + RedTimings.LIFT * f) * Math.max(0, resist);
            victim.setDeltaMovement(victim.getDeltaMovement().add(away.x * push, lift, away.z * push));
            victim.hurtMarked = true;
        }
    }

    private static boolean spared(LivingEntity victim, ServerPlayer caster) {
        if (victim instanceof TamableAnimal pet && pet.isOwnedBy(caster)) {
            return true;
        }
        return victim.isAlliedTo(caster);
    }

    private boolean erase(AbilityContext context, LivingEntity victim, boolean hurt) {
        if (this != PURPLE || !hurt || victim.isAlive() || victim instanceof net.minecraft.world.entity.player.Player) {
            return false;
        }
        AbilityNet.sendErase(context, victim);
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
        return true;
    }
}
