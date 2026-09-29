package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the caster's hand really is. The pose maths gives a close guess, but the model draws the
 * arm with its own matrices, so the Red core is anchored to the matrix the game just used: while the held-item
 * layer draws, the arm's pivot and fist are recorded in view space, and the effect renderer, drawing later
 * in the same frame, turns them into world positions. The core therefore sits exactly at the vanilla hand
 * whatever the animation, the skin model or the player's height. With no fresh sample it falls back to the guess.
 */
@Environment(EnvType.CLIENT)
public final class HandCapture {
    private HandCapture() {
    }

    private record Sample(Vector3f pivot, Vector3f fist, long nanos) {
    }

    private static final Map<Integer, Sample> SAMPLES = new HashMap<>();
    /** Length of the vanilla arm, from shoulder pivot to the end of the fist, in blocks. */
    private static final float ARM = 0.75f;
    private static final long FRESH_NANOS = 150_000_000L;

    /** Called by the held-item layer with the pose stack matrix already at the arm's pivot. */
    public static void record(int entityId, Matrix4f atArm) {
        Vector3f pivot = atArm.transformPosition(new Vector3f(0f, 0f, 0f), new Vector3f());
        Vector3f fist = atArm.transformPosition(new Vector3f(0f, ARM, 0f), new Vector3f());
        SAMPLES.put(entityId, new Sample(pivot, fist, System.nanoTime()));
    }

    public static void clear() {
        SAMPLES.clear();
    }

    /**
     * The sockets with the hand replaced by the recorded one, if there is a fresh record. {@code view} is the
     * view matrix the effects are drawn with and {@code camera} the camera's world position; {@code coreRadius}
     * decides how far past the fist the core's centre sits, so a bigger core clears the hand.
     */
    public static RedPose.Sockets resolve(int entityId, Matrix4f view, Vec3 camera, RedPose.Sockets guess, double coreRadius) {
        Sample s = SAMPLES.get(entityId);
        if (s == null || System.nanoTime() - s.nanos() > FRESH_NANOS) {
            return guess;
        }
        Matrix4f inverse = new Matrix4f(view).invert();
        Vector3f p = inverse.transformPosition(new Vector3f(s.pivot()), new Vector3f());
        Vector3f f = inverse.transformPosition(new Vector3f(s.fist()), new Vector3f());
        Vec3 pivot = new Vec3(camera.x + p.x, camera.y + p.y, camera.z + p.z);
        Vec3 fist = new Vec3(camera.x + f.x, camera.y + f.y, camera.z + f.z);
        Vec3 dir = fist.subtract(pivot);
        dir = dir.lengthSqr() < 1.0e-9 ? guess.armDir() : dir.normalize();
        Vec3 core = fist.add(dir.scale(0.16 + 0.9 * coreRadius)).add(0, 0.03, 0);
        return new RedPose.Sockets(fist, pivot.add(dir.scale(0.55)), guess.chest(), core, guess.eye(), dir);
    }
}
