package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One tentacle as a tapered tube along a living curve: it pushes out along {@code forward}, splays
 * along {@code spread}, writhes in two crossing waves that travel toward the tip, and curls at the
 * end. With a {@code target} it lashes toward that point instead, bending hardest near the tip.
 *
 * <p>Texture layout: {@code u} goes once around the tube (the underside, with the suckers, is the
 * second half), {@code v} runs from base (0) to tip (1), so one texture carries the whole length.
 * Used for the void sky (hundred-block tentacles) and the tentacle rift (a few blocks).</p>
 */
@Environment(EnvType.CLIENT)
public final class TentacleMesh {
    private static final int SEGMENTS = 40;
    private static final int SIDES = 12;

    private final Vector3f[] points = new Vector3f[SEGMENTS + 1];
    private final Vector3f[] normals = new Vector3f[SEGMENTS + 1];
    private final Vector3f[] binormals = new Vector3f[SEGMENTS + 1];
    private final float[] radii = new float[SEGMENTS + 1];

    public TentacleMesh() {
        for (int i = 0; i <= SEGMENTS; i++) {
            points[i] = new Vector3f();
            normals[i] = new Vector3f();
            binormals[i] = new Vector3f();
        }
    }

    /** The shape of one tentacle for one frame. All vectors are in the caller's space. */
    public static final class Shape {
        public final Vector3f base = new Vector3f();
        public final Vector3f forward = new Vector3f();
        public final Vector3f spread = new Vector3f();
        /** Any unit vector not parallel to {@link #forward}; sets which way the suckers face. */
        public final Vector3f up = new Vector3f(0, 1, 0);
        /** Full length and base radius. */
        public float length;
        public float radius;
        /** 0..1 how much of it is out. */
        public float grown = 1f;
        /** How far it splays sideways relative to how far it reaches out. */
        public float splay = 0.5f;
        /** Writhing strength as a share of the length. */
        public float writhe = 0.16f;
        public float time;
        public float phase;
        /** Where it lashes to, and how far into the lash it is (0 none, 1 struck). */
        public final Vector3f target = new Vector3f();
        public float lash;
    }

    /** Builds the curve for {@code shape}; call before {@link #emit}. */
    public void build(Shape shape) {
        Vector3f side1 = new Vector3f();
        shape.forward.cross(shape.up, side1);
        if (side1.lengthSquared() < 1.0e-6f) {
            shape.forward.cross(new Vector3f(1, 0, 0), side1);
        }
        side1.normalize();
        Vector3f side2 = new Vector3f();
        shape.forward.cross(side1, side2).normalize();

        float length = shape.length * Math.max(0.02f, shape.grown);
        float t = shape.time;
        float amplitude = shape.length * shape.writhe;
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = i / (float) SEGMENTS;
            Vector3f p = points[i].set(shape.base);
            // Reach and splay.
            p.add(new Vector3f(shape.forward).mul(length * s));
            p.add(new Vector3f(shape.spread).mul(length * s * shape.splay));
            // Two crossing waves travelling toward the tip, strongest near it.
            float falloff = (float) Math.pow(s, 1.4);
            float w1 = (float) Math.sin(s * 7.0 - t * 1.3 + shape.phase) * amplitude * falloff;
            float w2 = (float) Math.cos(s * 5.2 - t * 1.05 + shape.phase * 1.7) * amplitude * falloff * 0.8f;
            p.add(new Vector3f(side1).mul(w1)).add(new Vector3f(side2).mul(w2));
            // The tip curls round on itself, slowly turning.
            float curl = (float) Math.pow(s, 3.0) * shape.length * 0.14f;
            double angle = t * 0.7 + shape.phase * 2.3;
            p.add(new Vector3f(side1).mul((float) Math.cos(angle) * curl))
                    .add(new Vector3f(side2).mul((float) Math.sin(angle) * curl));
            // Lashing: the far part of the tentacle is pulled onto a line to the target.
            if (shape.lash > 0f) {
                float pull = shape.lash * (float) Math.pow(s, 1.6);
                Vector3f onLine = new Vector3f(shape.target).sub(shape.base).mul(s).add(shape.base);
                p.lerp(onLine, pull);
            }
            // Taper to a point, with a slow swallowing ripple along the body.
            float ripple = 1f + 0.07f * (float) Math.sin(s * 30.0 - t * 2.4 + shape.phase);
            radii[i] = shape.radius * (float) Math.pow(1f - 0.93f * s, 0.85) * ripple;
        }
        // Frames: tangent from neighbours, normals kept facing "up" so the suckers stay underneath.
        Vector3f tangent = new Vector3f();
        for (int i = 0; i <= SEGMENTS; i++) {
            Vector3f ahead = points[Math.min(SEGMENTS, i + 1)];
            Vector3f behind = points[Math.max(0, i - 1)];
            ahead.sub(behind, tangent);
            if (tangent.lengthSquared() < 1.0e-8f) {
                tangent.set(shape.forward);
            }
            tangent.normalize();
            tangent.cross(shape.up, normals[i]);
            if (normals[i].lengthSquared() < 1.0e-6f) {
                tangent.cross(side1, normals[i]);
            }
            normals[i].normalize();
            normals[i].cross(tangent, binormals[i]).normalize();
        }
    }

    /**
     * Writes the tube as quads in an entity vertex format (position, colour, uv, overlay, light,
     * normal). Colours multiply the texture.
     */
    public void emit(VertexConsumer vc, Matrix4f pose, Matrix3f normalPose, int light,
                     float r, float g, float b, float a) {
        Vector3f n0 = new Vector3f();
        Vector3f n1 = new Vector3f();
        for (int i = 0; i < SEGMENTS; i++) {
            float v0 = i / (float) SEGMENTS;
            float v1 = (i + 1) / (float) SEGMENTS;
            for (int k = 0; k < SIDES; k++) {
                float u0 = k / (float) SIDES;
                float u1 = (k + 1) / (float) SIDES;
                double a0 = Math.PI * 2 * u0;
                double a1 = Math.PI * 2 * u1;
                vertex(vc, pose, normalPose, i, a0, u0, v0, light, r, g, b, a, n0);
                vertex(vc, pose, normalPose, i + 1, a0, u0, v1, light, r, g, b, a, n1);
                vertex(vc, pose, normalPose, i + 1, a1, u1, v1, light, r, g, b, a, n1);
                vertex(vc, pose, normalPose, i, a1, u1, v0, light, r, g, b, a, n0);
            }
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normalPose, int i, double angle, float u, float v,
                        int light, float r, float g, float b, float a, Vector3f scratch) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        scratch.set(normals[i]).mul(c).add(new Vector3f(binormals[i]).mul(s));
        Vector3f p = points[i];
        float radius = radii[i];
        vc.vertex(pose, p.x + scratch.x * radius, p.y + scratch.y * radius, p.z + scratch.z * radius)
                .color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normalPose, scratch.x, scratch.y, scratch.z).endVertex();
    }

    /** The tip of the last built curve, e.g. to spawn particles where it strikes. */
    public Vector3f tip() {
        return points[SEGMENTS];
    }
}
