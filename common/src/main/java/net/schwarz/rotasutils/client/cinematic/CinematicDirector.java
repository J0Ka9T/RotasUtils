package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class CinematicDirector {
    private CinematicDirector() {
    }

    private static ClientCast active(float partialTick) {
        ClientCast cast = ClientCasts.local();
        return cast != null && cast.time(partialTick) < cast.endSeconds() ? cast : null;
    }

    public static CameraRig.Shot camera(Camera camera, float partialTick) {
        ClientCast cast = active(partialTick);
        if (cast == null) {
            return null;
        }
        Vec3 normal = camera.getPosition();
        Vec3 look = normal.add(Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).scale(10));
        double base = Minecraft.getInstance().options.fov().get();
        double t = cast.time(partialTick);
        if (cast.projection()) {
            return Projection.shot(cast, camera, partialTick, base);
        }
        if (cast.stargun()) {
            Stargun.Scene scene = Stargun.scene(cast);
            return scene == null ? null : StargunCamera.shot(t, cast.realTime(partialTick), scene, Projection::clip, normal, look, base);
        }
        CameraRig.Frame frame = cast.frame(partialTick);
        if (cast.purple()) {
            if (cast.dissolveFocus != null && cast.sinceImpact(t) >= 0) {
                frame = CameraRig.Frame.of(frame.feet(), cast.target, cast.dissolveFocus);
            }
            return PurpleCamera.shot(t, frame, PurpleCamera.Timing.of(cast), cast::followPoint, normal, look, base, (int) cast.seed);
        }
        CameraRig.kickAt = cast.max() && cast.released() ? cast.releaseAt + cast.travelSeconds : -1;
        return CameraRig.shot(t, frame, cast.socketsForCamera(t, frame), cast.followPoint(t), normal, look, base, (int) cast.seed);
    }

    public static double fov(float partialTick, double base) {
        ClientCast cast = active(partialTick);
        if (cast == null) {
            return base;
        }
        if (cast.projection()) {
            return Projection.fov(cast, partialTick, base);
        }
        if (cast.stargun()) {
            return StargunCamera.fovAt(cast.time(partialTick), base);
        }
        return cast.purple() ? PurpleCamera.fovAt(cast.time(partialTick), base, PurpleCamera.Timing.of(cast))
                : CameraRig.fovAt(cast.time(partialTick), base);
    }

    public static void applyRoll(PoseStack pose, float partialTick) {
        ClientCast cast = active(partialTick);
        if (cast != null && cast.projection()) {
            pose.mulPose(Axis.ZP.rotationDegrees((float) Projection.roll(cast, partialTick)));
        } else if (cast != null && cast.stargun()) {
            pose.mulPose(Axis.ZP.rotationDegrees((float) StargunCamera.rollAt(cast.time(partialTick), cast.realTime(partialTick))));
        } else if (cast != null) {
            double roll = cast.purple() ? PurpleCamera.rollAt(cast.time(partialTick), (int) cast.seed, PurpleCamera.Timing.of(cast))
                    : CameraRig.rollAt(cast.time(partialTick), (int) cast.seed);
            pose.mulPose(Axis.ZP.rotationDegrees((float) roll));
        }
    }

    public static boolean hidesHand(float partialTick) {
        return active(partialTick) != null;
    }

    public static boolean locksInput() {
        return active(0) != null || Projection.holdsLocalPlayer();
    }
}
