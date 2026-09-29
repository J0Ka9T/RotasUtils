package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.ability.RedTimings;

/**
 * Lays the cast pose over a humanoid model after the game has posed it. Every part is blended between
 * what the game did and what the pose wants by {@link RedPose#weight}, so the pose fades in and out
 * instead of popping. Arm positions follow the body's twist the way the game's own attack animation
 * moves them, and the whole model is lowered so the spread legs still stand on the ground.
 */
@Environment(EnvType.CLIENT)
public final class CastPoseApplier {
    private CastPoseApplier() {
    }

    /** The active cast for this entity, or null. */
    private static ClientCast castOf(LivingEntity entity) {
        ClientCast cast = ClientCasts.forCaster(entity.getId());
        return cast == null || cast.cancelled ? null : cast;
    }

    public static boolean hidesHeldItem(LivingEntity entity) {
        ClientCast cast = castOf(entity);
        return cast != null && cast.time(0) < RedTimings.END;
    }

    public static void apply(HumanoidModel<?> m, LivingEntity entity, float partialTick) {
        ClientCast cast = castOf(entity);
        if (cast == null) {
            return;
        }
        double t = cast.time(partialTick);
        double w = RedPose.weight(t);
        if (t < 0 || w <= 0) {
            return;
        }
        RedPose.Pose p = RedPose.sample(t);
        float dy = (float) p.dy();
        blend(m.body, 0, dy, 0, p.bodyPitch(), p.bodyYaw(), 0, w);
        blend(m.head, 0, dy, 0, m.head.xRot * 0.6 + p.headPitch(), p.headYaw(), 0, w);
        blend(m.rightArm, -Math.cos(p.bodyYaw()) * 5.0, 2.0 + dy, Math.sin(p.bodyYaw()) * 5.0, p.rArmX(), p.rArmY(), p.rArmZ(), w);
        blend(m.leftArm, Math.cos(p.bodyYaw()) * 5.0, 2.0 + dy, -Math.sin(p.bodyYaw()) * 5.0, p.lArmX(), p.lArmY(), p.lArmZ(), w);
        blend(m.rightLeg, -1.9, 12.0 + dy, 0.1, p.rLegX(), p.bodyYaw() * 0.25, p.rLegZ(), w);
        blend(m.leftLeg, 1.9, 12.0 + dy, 0.1, p.lLegX(), p.bodyYaw() * 0.25, p.lLegZ(), w);
        m.hat.copyFrom(m.head);
    }

    private static void blend(ModelPart part, double x, double y, double z, double xr, double yr, double zr, double w) {
        part.x = (float) (part.x + (x - part.x) * w);
        part.y = (float) (part.y + (y - part.y) * w);
        part.z = (float) (part.z + (z - part.z) * w);
        part.xRot = (float) (part.xRot + (xr - part.xRot) * w);
        part.yRot = (float) (part.yRot + (yr - part.yRot) * w);
        part.zRot = (float) (part.zRot + (zr - part.zRot) * w);
    }
}
