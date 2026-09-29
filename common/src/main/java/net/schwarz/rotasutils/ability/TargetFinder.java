package net.schwarz.rotasutils.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Locks an ability onto something before its cutscene begins. A small cone round the crosshair picks
 * the living thing the player is plainly aiming at, so the player does not need pixel-perfect aim;
 * with nothing in the cone it falls back to the point the crosshair rests on, or a point straight
 * ahead. Everything is decided here, on the server.
 */
public final class TargetFinder {
    private TargetFinder() {
    }

    public static Target acquire(ServerPlayer player, double range, double coneDegrees) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                x -> x != player && x.isAlive() && !x.isSpectator() && !(x instanceof ArmorStand))) {
            Vec3 centre = e.getBoundingBox().getCenter();
            Vec3 to = centre.subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 1.0) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look)))));
            // Something close is easy to aim at and something far is hard: widen the cone with proximity.
            if (angle > coneDegrees + 8.0 / Math.max(1.0, distance / 4.0) || !player.hasLineOfSight(e)) {
                continue;
            }
            double score = angle + distance * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best != null) {
            Vec3 at = best.position().add(0, best.getBbHeight() * 0.55, 0);
            return new Target(best.getId(), at, eye);
        }
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 point = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(24)) : hit.getLocation();
        return new Target(-1, point, eye);
    }
}
