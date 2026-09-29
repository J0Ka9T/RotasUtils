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
    public static final RedReversalAbility INSTANCE = new RedReversalAbility();
    public static final ResourceLocation ID = Rotasutils.id("red_reversal");
    /** Whether the blast may break blocks. Off: this is a duel move, not a terrain wrecker. */
    private static final boolean BREAKS_BLOCKS = false;

    private final Timeline<AbilityContext> timeline = new Timeline<AbilityContext>()
            .at(RedTimings.ANIM_START, "begin", RedReversalAbility::begin)
            .at(RedTimings.RELEASE, "release", RedReversalAbility::release);

    private RedReversalAbility() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
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
        return player.getMainHandItem().getItem() instanceof RedReversalItem;
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
    private static void release(AbilityContext c) {
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
        c.after(travel, () -> blast(c, impact, travelDir));
    }

    private static boolean canHit(Entity e, ServerPlayer caster) {
        return e != caster && e.isAlive() && !e.isSpectator() && e.isPickable() && !(e instanceof ArmorStand);
    }

    /** Damage and knockback round the impact, both fading smoothly with distance from its centre. */
    private static void blast(AbilityContext c, Vec3 impact, Vec3 travelDir) {
        ServerPlayer caster = c.player;
        ServerLevel level = c.level;
        double radius = RedTimings.BLAST_RADIUS;
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
            victim.hurt(source, (float) (RedTimings.DAMAGE * f));
            Vec3 away = centre.subtract(impact);
            away = away.lengthSqr() < 0.04 ? travelDir : away.normalize();
            double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double push = RedTimings.KNOCKBACK * f * Math.max(0, resist);
            double lift = (0.12 + RedTimings.LIFT * f) * Math.max(0, resist);
            victim.setDeltaMovement(victim.getDeltaMovement().add(away.x * push, lift, away.z * push));
            victim.hurtMarked = true;
        }
        if (BREAKS_BLOCKS) {
            Rotasutils.LOG.debug("Red Reversal block breaking is switched off in code");
        }
    }

    private static boolean spared(LivingEntity victim, ServerPlayer caster) {
        if (victim instanceof TamableAnimal pet && pet.isOwnedBy(caster)) {
            return true;
        }
        return victim.isAlliedTo(caster);
    }
}
