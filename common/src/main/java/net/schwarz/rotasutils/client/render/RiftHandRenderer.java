package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.RiftPortalEntity;
import net.schwarz.rotasutils.entity.RiftPortalEntity.RiftPortalShape;
import net.schwarz.rotasutils.sky.PrismHue;

@Environment(EnvType.CLIENT)
final class RiftHandRenderer {
    private static final int FINGERS = 5;
    private static final int ARM_POINTS = 6;
    private static final int FINGER_POINTS = 5;
    private static final float REACH = 2.9f;
    private static final float LUNGE = 2.2f;
    private static final float[] PRISM = new float[3];

    private final Mesh3D mesh = new Mesh3D();

    void render(RiftPortalEntity portal, float age, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float grown = smooth((age - RiftPortalEntity.OPEN_END + 6) / 34f)
                * (1f - smooth((age - portal.closeAt()) / 22f));
        if (grown <= 0.01f) {
            return;
        }
        Vec3 origin = portal.getPosition(partialTick);
        Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        mesh.begin(buffers, pose.last().pose(), pose.last().normal(), 0, 0, 0, cam.subtract(origin));

        float time = (portal.tickCount + partialTick) / 20f;
        float phase = PrismHue.phase();

        Entity target = portal.level().getEntity(portal.strikeTarget());
        float d = age - portal.strikeTick();
        float lash = target == null || d < -9f || d > 12f ? 0f : d <= 0f ? smooth((d + 9f) / 9f) : 1f - smooth(d / 12f);

        Vec3 fwd = flat(portal.facing());
        Vec3 mouth = new Vec3(0, RiftPortalShape.CENTER_Y, 0);
        Vec3 out = fwd.scale(0.9).add(0, 0.45, 0).normalize();
        if (target != null && lash > 0f) {
            Vec3 at = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0).subtract(origin);
            Vec3 toTarget = at.subtract(mouth);
            if (toTarget.lengthSqr() > 1.0e-4) {
                out = lerp(out, toTarget.normalize(), 0.85f * lash).normalize();
            }
        }
        Vec3 side = new Vec3(-out.z, 0, out.x).normalize();
        out = out.add(side.scale(0.06 * Math.sin(time * 0.7))).add(0, 0.04 * Math.sin(time * 0.9 + 1.3), 0).normalize();

        float reach = (REACH + LUNGE * lash) * grown;
        Vec3 wrist = mouth.add(out.scale(reach));

        Vec3 bend = side.scale(0.28 * Math.sin(time * 0.6)).add(0, 0.22, 0);
        Vec3[] arm = new Vec3[ARM_POINTS];
        float[] radius = new float[ARM_POINTS];
        for (int i = 0; i < ARM_POINTS; i++) {
            float s = i / (ARM_POINTS - 1f);
            float arc = (float) Math.sin(Math.PI * s) * 0.5f;
            arm[i] = mouth.add(out.scale(reach * (s * 1.35f - 0.35f))).add(bend.scale(arc));
            radius[i] = (0.72f - 0.30f * s) * grown;
        }
        mesh.tube(arm, radius, 14, skin(phase, 0f, 1.0f));

        Vec3 across = side;
        Vec3 normal = out.cross(across).normalize();
        Vec3 palm = wrist.add(out.scale(0.42 * grown));
        mesh.sphere(palm, across, normal, out, 0.62f * grown, 0.20f * grown, 0.50f * grown, 8, 14,
                skin(phase, 0.08f, 0.9f));

        float curl = 0.22f + 0.12f * (float) Math.sin(time * 1.1) + 0.85f * lash;
        for (int f = 0; f < FINGERS; f++) {
            finger(f, palm, out, across, normal, grown, curl, time, phase);
        }
    }

    private void finger(int index, Vec3 palm, Vec3 out, Vec3 across, Vec3 normal, float grown, float curl,
                        float time, float phase) {
        boolean thumb = index == FINGERS - 1;
        float lane = thumb ? -1.15f : (index - 1.5f) / 1.5f;
        float length = (thumb ? 0.95f : 1.45f - 0.22f * Math.abs(lane)) * grown;
        Vec3 base = palm.add(across.scale(lane * 0.48 * grown))
                .add(out.scale((thumb ? -0.18 : 0.46) * grown))
                .add(normal.scale((thumb ? -0.20 : 0.04) * grown));
        Vec3 spread = out.add(across.scale(lane * (thumb ? 0.85 : 0.30))).add(normal.scale(thumb ? -0.35 : 0)).normalize();
        float own = curl + 0.10f * (float) Math.sin(time * 1.3 + index * 1.7) + (thumb ? 0.25f : 0f);

        Vec3[] pts = new Vec3[FINGER_POINTS];
        float[] radius = new float[FINGER_POINTS];
        Vec3 at = base;
        for (int i = 0; i < FINGER_POINTS; i++) {
            float s = i / (FINGER_POINTS - 1f);
            pts[i] = at;
            radius[i] = (0.19f - 0.17f * s * s) * grown * (thumb ? 1.1f : 1f);
            Vec3 dir = lerp(spread, normal.scale(-1), Math.min(0.95f, own * (0.35f + s))).normalize();
            at = at.add(dir.scale(length / (FINGER_POINTS - 1f)));
        }
        Mesh3D.Style style = skin(phase, 0.13f * (index + 1), 1.15f);
        mesh.tube(pts, radius, 9, style);
        mesh.sphere(pts[0], across, normal, out, radius[0] * 1.25f, radius[0] * 1.25f, radius[0] * 1.25f, 5, 7, style);
    }

    private static Mesh3D.Style skin(float phase, float offset, float rim) {
        PrismHue.rotate(0.55f, 0.20f, 1f, phase + offset, PRISM);
        return new Mesh3D.Style(0.055f + 0.05f * PRISM[0], 0.045f + 0.05f * PRISM[1], 0.085f + 0.05f * PRISM[2],
                PRISM[0], PRISM[1], PRISM[2], rim, 2.4f);
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return a.add(b.subtract(a).scale(t));
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
