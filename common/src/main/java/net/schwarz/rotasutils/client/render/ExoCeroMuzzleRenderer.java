package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ExoCeroMuzzleRenderer extends EntityRenderer<ExoCeroMuzzleEntity> {
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] CORE = {0.90f, 0.99f, 1f};
    private static final float[] CYAN = {0.25f, 0.85f, 1f};
    private static final float[] DEEP = {0.10f, 0.45f, 1f};

    private final Quaternionf camera = new Quaternionf();
    private final Vector3f cam = new Vector3f();
    private final Vector3f muzzle = new Vector3f();
    private final Vector3f dir = new Vector3f();
    private final Vector3f u = new Vector3f();
    private final Vector3f v = new Vector3f();
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f c = new Vector3f();
    private final Vector3f d = new Vector3f();
    private final Vector3f side = new Vector3f();
    private final Vector3f tmp = new Vector3f();

    public ExoCeroMuzzleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(ExoCeroMuzzleEntity mount, Frustum frustum, double x, double y, double z) {
        LivingEntity owner = mount.owner();
        if (owner == null) {
            return false;
        }
        Vec3 eye = owner.getEyePosition();
        Vec3 far = eye.add(owner.getViewVector(1f).scale(net.schwarz.rotasutils.entity.CeroBallistics.RANGE));
        return frustum.isVisible(new AABB(eye, far).inflate(24.0));
    }

    @Override
    public void render(ExoCeroMuzzleEntity mount, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        LivingEntity owner = mount.owner();
        if (owner == null) {
            return;
        }
        double ox = Mth.lerp(partialTick, mount.xo, mount.getX());
        double oy = Mth.lerp(partialTick, mount.yo, mount.getY());
        double oz = Mth.lerp(partialTick, mount.zo, mount.getZ());
        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        float yawRad = owner.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
        Vector3f right = tmp.set(-Mth.cos(yawRad), 0f, -Mth.sin(yawRad));
        Vector3f lookF = dir.set((float) look.x, (float) look.y, (float) look.z);
        Vector3f up = side.set(right).cross(lookF).normalize();
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = owner == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
        float fwd = firstPerson ? 0.75f : 1.1f, rt = firstPerson ? 0.30f : 0.38f, dn = firstPerson ? -0.24f : -0.35f;
        muzzle.set((float) (eye.x - ox), (float) (eye.y - oy), (float) (eye.z - oz))
                .add(lookF.x * fwd + right.x * rt + up.x * dn, lookF.y * fwd + right.y * rt + up.y * dn,
                        lookF.z * fwd + right.z * rt + up.z * dn);
        basis(dir, u, v);
        Vec3 camPos = entityRenderDispatcher.camera.getPosition();
        cam.set((float) (camPos.x - ox), (float) (camPos.y - oy), (float) (camPos.z - oz));
        camera.set(entityRenderDispatcher.cameraOrientation());

        float age = mount.tickCount + partialTick;
        float time = age / 20f;
        float rise = smooth(age / 5f);
        float fall = age <= ExoCeroMuzzleEntity.VOLLEY_TICKS
                ? 1f : 1f - smooth((age - ExoCeroMuzzleEntity.VOLLEY_TICKS) / ExoCeroMuzzleEntity.FADE);
        float amp = rise * fall;
        if (amp <= 0.01f) {
            return;
        }
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        boolean bursting = net.schwarz.rotasutils.entity.CeroBallistics.shotsAt(mount.tickCount) > 0;
        float strobe = bursting ? 1f - smooth(frac(age) * 1.65f) : 0f;
        core(vc, m, time, amp * 0.6f, strobe);
        emitters(vc, m, age, time, amp, strobe);
        fused(vc, m, age, amp);
        shocks(vc, m, age, amp * strobe);
    }

    private void core(VertexConsumer vc, Matrix4f m, float time, float amp, float strobe) {
        float pulse = amp * (1f + 0.55f * strobe) * (1f + 0.05f * Mth.sin(time * 23f));
        halo(vc, m, muzzle, 2.4f * pulse, DEEP, 0.2f * amp);
        halo(vc, m, muzzle, 1.2f * pulse, CYAN, 0.48f * amp);
        halo(vc, m, muzzle, 0.62f * amp, CORE, 0.85f * amp);
        halo(vc, m, muzzle, 0.26f * amp, WHITE, amp);
        star(vc, m, muzzle, 1.5f * pulse, CORE, 0.6f * amp, time * 2.4f);
        a.set(muzzle);
        b.set(muzzle).add(dir.x * 3.2f, dir.y * 3.2f, dir.z * 3.2f);
        strip(vc, m, a, b, 0.4f * pulse, 0.06f, CYAN, 0.45f * amp, 0f);
        strip(vc, m, a, b, 0.16f * amp, 0.02f, CORE, 0.7f * amp, 0f);
    }

    private void emitters(VertexConsumer vc, Matrix4f m, float age, float time, float amp, float strobe) {
        int charge = net.schwarz.rotasutils.entity.CeroBallistics.CHARGE_TICKS;
        int lanes = net.schwarz.rotasutils.entity.CeroBallistics.EMITTERS;
        float gathered = smooth(age / charge);
        float ring = (float) net.schwarz.rotasutils.entity.CeroBallistics.EMITTER_RING * (1.8f - 0.8f * gathered);
        float fired = Math.max(0f, age - charge) * lanes;
        float turn = time * 0.8f + (fired / lanes) * 0.09f;
        ring(vc, m, muzzle, u, v, ring, 0.08f * amp, CYAN, 0.35f * amp * gathered, 32);
        for (int i = 0; i < lanes; i++) {
            float angle = Mth.TWO_PI * i / lanes + turn;
            a.set(u).mul(Mth.cos(angle) * ring).add(v.x * Mth.sin(angle) * ring, v.y * Mth.sin(angle) * ring,
                    v.z * Mth.sin(angle) * ring).add(muzzle);
            float size = (0.18f + 0.3f * gathered) * (float) net.schwarz.rotasutils.entity.CeroBallistics.SCALE * amp * (1f + 0.6f * strobe);
            halo(vc, m, a, size * 2.4f, DEEP, 0.35f * amp);
            halo(vc, m, a, size * 1.3f, CYAN, 0.7f * amp);
            halo(vc, m, a, size * 0.6f, WHITE, amp);
        }
    }

    private void fused(VertexConsumer vc, Matrix4f m, float age, float amp) {
        float firing = age - net.schwarz.rotasutils.entity.CeroBallistics.CHARGE_TICKS;
        if (firing <= 0f) {
            return;
        }
        float speed = (float) net.schwarz.rotasutils.entity.CeroBallistics.SPEED;
        float range = (float) net.schwarz.rotasutils.entity.CeroBallistics.RANGE;
        float head = Math.min(range, firing * speed);
        float stopped = age - ExoCeroMuzzleEntity.VOLLEY_TICKS;
        float tail = stopped > 0f ? Math.min(head, stopped * speed) : 0f;
        if (head - tail < 0.5f) {
            return;
        }
        float girth = (float) (net.schwarz.rotasutils.entity.CeroBallistics.EMITTER_RING
                + 1.3 * net.schwarz.rotasutils.entity.CeroBallistics.SCALE);
        a.set(dir).mul(tail).add(muzzle);
        b.set(dir).mul(head).add(muzzle);
        strip(vc, m, a, b, girth * 2.6f, girth * 2.6f, DEEP, 0.16f * amp, 0.10f * amp);
        strip(vc, m, a, b, girth * 1.7f, girth * 1.7f, CYAN, 0.20f * amp, 0.12f * amp);
        strip(vc, m, a, b, girth * 0.5f, girth * 0.5f, CORE, 0.22f * amp, 0.12f * amp);
    }

    private void shocks(VertexConsumer vc, Matrix4f m, float age, float amp) {
        for (int k = 0; k < 2; k++) {
            float phase = frac(age / ExoCeroMuzzleEntity.SHOCK_PERIOD + k * 0.5f);
            float radius = 2.5f + 16f * phase;
            float alpha = 0.4f * (1f - phase) * (1f - phase) * amp;
            billboardRing(vc, m, muzzle, radius, 0.6f + 1.8f * phase, CORE, alpha);
            billboardRing(vc, m, muzzle, radius * 1.08f, 1.4f, CYAN, alpha * 0.5f);
        }
    }

private void ring(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f ay, float r, float w,
                      float[] rgb, float alpha, int segs) {
        for (int i = 0; i < segs; i++) {
            float t0 = Mth.TWO_PI * i / segs, t1 = Mth.TWO_PI * (i + 1) / segs;
            annulus(vc, m, c, ax, ay, Mth.cos(t0), Mth.sin(t0), Mth.cos(t1), Mth.sin(t1), r, w, rgb, alpha);
        }
    }

    private void billboardRing(VertexConsumer vc, Matrix4f m, Vector3f c, float r, float w, float[] rgb, float alpha) {
        a.set(1f, 0f, 0f).rotate(camera);
        b.set(0f, 1f, 0f).rotate(camera);
        ring(vc, m, c, a, b, r, w, rgb, alpha, 24);
    }

    private void annulus(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f ay, float x0, float y0,
                         float x1, float y1, float r, float w, float[] rgb, float alpha) {
        if (r <= 0.001f || w <= 0f || alpha <= 0.002f) {
            return;
        }
        float inner = Math.max(0f, r - w);
        for (int band = 0; band < 2; band++) {
            float ra = band == 0 ? inner : r, rb = band == 0 ? r : r + w;
            float aa = band == 0 ? 0f : alpha, ab = band == 0 ? alpha : 0f;
            planeVertex(vc, m, c, ax, ay, x0 * ra, y0 * ra, rgb, aa);
            planeVertex(vc, m, c, ax, ay, x0 * rb, y0 * rb, rgb, ab);
            planeVertex(vc, m, c, ax, ay, x1 * rb, y1 * rb, rgb, ab);
            planeVertex(vc, m, c, ax, ay, x1 * ra, y1 * ra, rgb, aa);
        }
    }

    private void planeVertex(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f ay, float x, float y,
                             float[] rgb, float alpha) {
        vertex(vc, m, c.x + ax.x * x + ay.x * y, c.y + ax.y * x + ay.y * y, c.z + ax.z * x + ay.z * y, rgb, alpha);
    }

    private void strip(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, float w0, float w1,
                       float[] rgb, float a0, float a1) {
        side.set(p1).sub(p0);
        float mx = (p0.x + p1.x) * 0.5f - cam.x, my = (p0.y + p1.y) * 0.5f - cam.y, mz = (p0.z + p1.z) * 0.5f - cam.z;
        side.cross(mx, my, mz);
        if (side.lengthSquared() < 1.0e-9f) {
            return;
        }
        side.normalize();
        float h0 = w0 * 0.5f, h1 = w1 * 0.5f;
        for (int e = -1; e <= 1; e += 2) {
            vertex(vc, m, p0.x, p0.y, p0.z, rgb, a0);
            vertex(vc, m, p0.x + side.x * h0 * e, p0.y + side.y * h0 * e, p0.z + side.z * h0 * e, rgb, 0f);
            vertex(vc, m, p1.x + side.x * h1 * e, p1.y + side.y * h1 * e, p1.z + side.z * h1 * e, rgb, 0f);
            vertex(vc, m, p1.x, p1.y, p1.z, rgb, a1);
        }
    }

    private void halo(VertexConsumer vc, Matrix4f m, Vector3f c, float radius, float[] rgb, float alpha) {
        float safe = alpha * ExoBeamRenderer.haloCameraGain(radius, c.distance(cam));
        if (radius <= 0.01f || safe <= 0.005f) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            float t0 = Mth.TWO_PI * i / 12f, t1 = Mth.TWO_PI * (i + 1) / 12f;
            vertex(vc, m, c.x, c.y, c.z, rgb, safe);
            billboard(vc, m, c, Mth.cos(t0) * radius, Mth.sin(t0) * radius, rgb, 0f);
            billboard(vc, m, c, Mth.cos(t1) * radius, Mth.sin(t1) * radius, rgb, 0f);
            vertex(vc, m, c.x, c.y, c.z, rgb, safe);
        }
    }

    private void star(VertexConsumer vc, Matrix4f m, Vector3f c, float size, float[] rgb, float alpha, float spin) {
        if (alpha <= 0.01f || size <= 0.01f) {
            return;
        }
        arms(vc, m, c, size, size * 0.1f, spin, rgb, alpha);
        arms(vc, m, c, size * 0.5f, size * 0.08f, spin + Mth.PI / 4f, rgb, alpha * 0.6f);
    }

    private void arms(VertexConsumer vc, Matrix4f m, Vector3f c, float len, float w, float spin, float[] rgb, float alpha) {
        for (int arm = 0; arm < 4; arm++) {
            float ang = spin + Mth.HALF_PI * arm;
            float dx = Mth.cos(ang), dy = Mth.sin(ang);
            vertex(vc, m, c.x, c.y, c.z, rgb, alpha);
            billboard(vc, m, c, -dy * w, dx * w, rgb, alpha * 0.35f);
            billboard(vc, m, c, dx * len, dy * len, rgb, 0f);
            billboard(vc, m, c, dy * w, -dx * w, rgb, alpha * 0.35f);
        }
    }

    private void billboard(VertexConsumer vc, Matrix4f m, Vector3f c, float x, float y, float[] rgb, float alpha) {
        tmp.set(x, y, 0f).rotate(camera).add(c);
        vertex(vc, m, tmp.x, tmp.y, tmp.z, rgb, alpha);
    }

    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] rgb, float alpha) {
        vc.vertex(m, x, y, z).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
    }

    private static void basis(Vector3f d, Vector3f outU, Vector3f outV) {
        boolean steep = Math.abs(d.y) >= 0.95f;
        outU.set(d).cross(steep ? 1f : 0f, steep ? 0f : 1f, 0f).normalize();
        outV.set(d).cross(outU).normalize();
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public ResourceLocation getTextureLocation(ExoCeroMuzzleEntity mount) {
        return ExoBeamRenderer.PLASMA;
    }
}
