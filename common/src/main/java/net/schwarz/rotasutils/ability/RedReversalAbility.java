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

/**
 * Red Reversal: a slow, deliberate charge and one violent release. The server owns the gameplay half -
 * it faces the caster at the target when the sequence begins, fires the real attack at
 * {@link RedTimings#RELEASE} (not when the item was clicked), finds what it hits, and later applies
 * damage and knockback with a smooth falloff. The cutscene, the red mass and the camera are the client's;
 * it only learns where the attack starts, where it lands and when.
 */
public final class RedReversalAbility implements AbilityDefinition {
    public static final RedReversalAbility INSTANCE = new RedReversalAbility("red_reversal", 1.0, 1.0, false);
    /**
     * Red Reversal MAX: the same sequence, but everything it throws is bigger - twice the blast, a far heavier hit,
     * a line of force that tears through everything along its path, and an aftershock where it lands.
     */
    public static final RedReversalAbility MAX = new RedReversalAbility("red_reversal_max", 2.0, 2.6, true);
    public static final ResourceLocation ID = INSTANCE.id;

    private final ResourceLocation id;
    /** Blast radius and damage multipliers over the base values in {@link RedTimings}. */
    private final double size;
    private final double power;
    /** Whether the shot also hurts everything it passes, and the landing sends a second, smaller blast. */
    private final boolean max;

    private final Timeline<AbilityContext> timeline = new Timeline<AbilityContext>()
            .at(RedTimings.ANIM_START, "begin", RedReversalAbility::begin)
            .at(RedTimings.RELEASE, "release", this::release);

    private RedReversalAbility(String name, double size, double power, boolean max) {
        this.id = Rotasutils.id(name);
        this.size = size;
        this.power = power;
        this.max = max;
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
        return RedTimings.END_TICKS;
    }

    @Override
    public boolean canStart(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof RedReversalItem item && item.ability() == this;
    }

    @Override
    public Timeline<AbilityContext> timeline() {
        return timeline;
    }

    // Events -----------------------------------------------------------------------------------------

    /** Nothing may interrupt the cutscene: the caster cannot be hurt until it ends. */
    @Override
    public void end(AbilityContext context, boolean completed) {
        context.player.setInvulnerable(false);
    }

    /** Turns the caster to face what they locked onto; the client turns the rest of the body itself. */
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

    /** The real attack. Everything the client shows of it is decided here. */
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
        Vec3 end = origin.add(dir.scale(RedTimings.RANGE));
        var block = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? RedTimings.RANGE : block.getLocation().distanceTo(origin);
        Vec3 limit = origin.add(dir.scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, origin, limit,
                new AABB(origin, limit).inflate(1.0), e -> canHit(e, player));
        Vec3 impact = hit != null ? hit.getLocation() : limit;
        int travel = Math.max(1, (int) Math.ceil(impact.distanceTo(origin) / RedTimings.PROJECTILE_SPEED));
        int hitId = hit != null ? hit.getEntity().getId() : -1;
        AbilityNet.sendRelease(c, origin, impact, hitId, travel);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 0.7f);
        final Vec3 travelDir = dir;
        if (max) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 0.5f);
            c.after(Math.max(1, travel / 2), () -> carve(c, origin, impact, travelDir));
        }
        c.after(travel, () -> blast(c, impact, travelDir, 1.0));
        if (max) {
            c.after(travel + 12, () -> blast(c, impact, travelDir, 0.45));
        }
    }

    private static boolean canHit(Entity e, ServerPlayer caster) {
        return e != caster && e.isAlive() && !e.isSpectator() && e.isPickable() && !(e instanceof ArmorStand);
    }

    /** MAX only: everything within a few blocks of the line of fire is struck on the way past. */
    private void carve(AbilityContext c, Vec3 from, Vec3 to, Vec3 travelDir) {
        ServerPlayer caster = c.player;
        double reach = 3.0;
        Vec3 seg = to.subtract(from);
        for (LivingEntity victim : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(reach),
                e -> e.isAlive() && !e.isSpectator() && e != caster && !(e instanceof ArmorStand))) {
            Vec3 centre = victim.getBoundingBox().getCenter();
            double u = Mth.clamp(centre.subtract(from).dot(seg) / Math.max(1.0e-4, seg.lengthSqr()), 0, 1);
            double f = Falloff.of(centre.distanceTo(from.add(seg.scale(u))), reach);
            if (f < 0.02 || spared(victim, caster)) {
                continue;
            }
            victim.invulnerableTime = 0;
            victim.hurt(c.level.damageSources().playerAttack(caster), (float) (RedTimings.DAMAGE * 0.5 * power * f));
            victim.setDeltaMovement(victim.getDeltaMovement().add(travelDir.scale(1.5 * f)).add(0, 0.4 * f, 0));
            victim.hurtMarked = true;
        }
    }

    /** Damage and knockback round the impact, both fading smoothly with distance from its centre. */
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
            victim.hurt(source, (float) (RedTimings.DAMAGE * power * strength * f));
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
}
