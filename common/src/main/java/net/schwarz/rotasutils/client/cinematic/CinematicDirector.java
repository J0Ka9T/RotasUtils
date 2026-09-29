package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.RedTimings;

/**
 * What the game's camera, lens and controls do while this client's own player is casting. Only the
 * caster is ever affected: other players watching keep their normal cameras. The hooks in the camera,
 * the field of view, the view roll, the hand and the input all ask this one class.
 */
@Environment(EnvType.CLIENT)
public final class CinematicDirector {
    private CinematicDirector() {
    }

    private static ClientCast active(float partialTick) {
        ClientCast cast = ClientCasts.local();
        return cast != null && cast.time(partialTick) < RedTimings.END ? cast : null;
    }

    /** The shot to put the camera at, given the player's own camera; null when no cutscene is running. */
    public static CameraRig.Shot camera(Camera camera, float partialTick) {
        ClientCast cast = active(partialTick);
        if (cast == null) {
            return null;
        }
        Vec3 normal = camera.getPosition();
        Vec3 look = normal.add(Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).scale(10));
        double base = Minecraft.getInstance().options.fov().get();
        double t = cast.time(partialTick);
        CameraRig.Frame frame = cast.frame(partialTick);
        return CameraRig.shot(t, frame, cast.socketsForCamera(t, frame), cast.followPoint(t), normal, look, base, (int) cast.seed);
    }

    /** The field of view in degrees, or {@code base} when nothing is playing. */
    public static double fov(float partialTick, double base) {
        ClientCast cast = active(partialTick);
        return cast == null ? base : CameraRig.fovAt(cast.time(partialTick), base);
    }

    /** Applies the shot's roll to the view matrix, right after the game's own hurt tilt. */
    public static void applyRoll(PoseStack pose, float partialTick) {
        ClientCast cast = active(partialTick);
        if (cast != null) {
            pose.mulPose(Axis.ZP.rotationDegrees((float) CameraRig.rollAt(cast.time(partialTick), (int) cast.seed)));
        }
    }

    public static boolean hidesHand(float partialTick) {
        return active(partialTick) != null;
    }

    /** True while movement, jumping, attacking and using items are locked out. */
    public static boolean locksInput() {
        return active(0) != null;
    }
}
