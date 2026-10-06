package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.ability.StargunTimings;

@Environment(EnvType.CLIENT)
public final class CastPoseApplier {
    private CastPoseApplier() {
    }

    private static ClientCast castOf(LivingEntity entity) {
        ClientCast cast = ClientCasts.forCaster(entity.getId());
        return cast == null || cast.cancelled ? null : cast;
    }

    public static boolean hidesHeldItem(LivingEntity entity) {
        ClientCast cast = castOf(entity);
        return cast != null && !cast.stargun() && cast.time(0) < cast.endSeconds();
    }

    public static void apply(HumanoidModel<?> m, LivingEntity entity, float partialTick) {
        brace(m, Projection.braced(entity, partialTick));
        ClientCast cast = castOf(entity);
        if (cast == null) {
            return;
        }
        double t = cast.time(partialTick);
        if (cast.projection()) {
            project(m, cast, t);
            return;
        }
        if (cast.stargun()) {
            aim(m, t);
            return;
        }
        double w = cast.purple() ? PurplePose.weight(t) : RedPose.weight(t);
        if (t < 0 || w <= 0) {
            return;
        }
        RedPose.Pose p = cast.purple() ? PurplePose.sample(t) : RedPose.sample(t);
        float dy = (float) p.dy();
        blend(m.body, 0, dy, 0, p.bodyPitch(), p.bodyYaw(), 0, w);
        blend(m.head, 0, dy, 0, m.head.xRot * 0.6 + p.headPitch(), p.headYaw(), 0, w);
        blend(m.rightArm, -Math.cos(p.bodyYaw()) * 5.0, 2.0 + dy, Math.sin(p.bodyYaw()) * 5.0, p.rArmX(), p.rArmY(), p.rArmZ(), w);
        blend(m.leftArm, Math.cos(p.bodyYaw()) * 5.0, 2.0 + dy, -Math.sin(p.bodyYaw()) * 5.0, p.lArmX(), p.lArmY(), p.lArmZ(), w);
        blend(m.rightLeg, -1.9, 12.0 + dy, 0.1, p.rLegX(), p.bodyYaw() * 0.25, p.rLegZ(), w);
        blend(m.leftLeg, 1.9, 12.0 + dy, 0.1, p.lLegX(), p.bodyYaw() * 0.25, p.lLegZ(), w);
        m.hat.copyFrom(m.head);
    }

    private static void aim(HumanoidModel<?> m, double t) {
        double w = Curves.smoothstep(Curves.window(t, 0.1, 1.6))
                * (1 - Curves.smoothstep(Curves.window(t, StargunTimings.CAMERA_RETURN - 0.4, StargunTimings.END - 0.3)));
        if (t < 0 || w <= 0) {
            return;
        }
        double raise = Curves.smootherstep(Curves.window(t, 0.2, 2.6));
        double charge = StargunTimings.charge(t);
        double shot = t >= StargunTimings.FIRE ? Math.exp(-(t - StargunTimings.FIRE) / 0.35) : 0;
        double kick = shot * Math.min(1, (t - StargunTimings.FIRE) / 0.06 + 0.0);
        double breath = Math.sin(t * 5.3) * 0.012, tremble = charge * 0.035 * Math.sin(t * 43.0) + charge * 0.02 * Math.sin(t * 29.0);
        double lean = -0.10 - 0.16 * charge - 0.28 * kick;
        double dy = 0.4 * charge * Math.sin(t * 31.0) * 0.2;
        blend(m.body, 0, dy, 0, lean, 0, 0, w);
        blend(m.head, 0, dy, 0, -0.50 * raise - 0.18 * kick + breath, 0, 0, w);
        blend(m.rightArm, -5.0, 2.0 + dy, 0, -(1.35 + 0.95 * raise) - 0.55 * kick + tremble + breath, -0.10, 0.05 + tremble * 0.5, w);
        blend(m.leftArm, 5.0, 2.0 + dy, 0, -(1.20 + 0.85 * raise) - 0.45 * kick - tremble, 0.55 * raise, -0.05, w);
        blend(m.rightLeg, -1.9, 12.0 + dy, 0.1, 0.30 + 0.12 * charge + 0.20 * kick, 0.0, 0.06, w);
        blend(m.leftLeg, 1.9, 12.0 + dy, 0.1, -0.34 - 0.10 * charge - 0.12 * kick, 0.0, -0.06, w);
        m.hat.copyFrom(m.head);
    }

    private static void project(HumanoidModel<?> m, ClientCast cast, double t) {
        double w = ProjectionPose.weight(t);
        Projection.Scene scene = Projection.scene(cast);
        if (t < 0 || w <= 0 || scene == null) {
            return;
        }
        RedPose.Pose p = ProjectionPose.sample(t, scene.stage);
        double dy = p.dy(), fz = ProjectionPose.leanForward(p.bodyPitch()), fy = ProjectionPose.leanDown(p.bodyPitch());
        double c = Math.cos(p.bodyYaw()) * 5.0, s = Math.sin(p.bodyYaw()) * 5.0;
        blend(m.body, 0, dy + fy, fz, p.bodyPitch(), p.bodyYaw(), 0, w);
        blend(m.head, 0, dy + fy, fz, p.headPitch(), p.headYaw(), 0, w);
        blend(m.rightArm, -c, 2.0 + dy + fy, s + fz, p.rArmX(), p.rArmY(), p.rArmZ(), w);
        blend(m.leftArm, c, 2.0 + dy + fy, -s + fz, p.lArmX(), p.lArmY(), p.lArmZ(), w);
        blend(m.rightLeg, -1.9, 12.0 + dy, 0.1, p.rLegX(), 0, p.rLegZ(), w);
        blend(m.leftLeg, 1.9, 12.0 + dy, 0.1, p.lLegX(), 0, p.lLegZ(), w);
        m.hat.copyFrom(m.head);
    }

    private static void brace(HumanoidModel<?> m, double w) {
        if (w <= 0) {
            return;
        }
        blend(m.rightArm, m.rightArm.x, m.rightArm.y, m.rightArm.z, -1.85, -0.60, 0, w);
        blend(m.leftArm, m.leftArm.x, m.leftArm.y, m.leftArm.z, -1.85, 0.60, 0, w);
        blend(m.rightLeg, m.rightLeg.x, m.rightLeg.y, m.rightLeg.z, 0.25, 0, 0.14, w);
        blend(m.leftLeg, m.leftLeg.x, m.leftLeg.y, m.leftLeg.z, -0.25, 0, -0.14, w);
        blend(m.body, m.body.x, m.body.y, m.body.z, 0.14, 0, 0, w);
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
