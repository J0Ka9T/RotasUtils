package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.ExcaliburSlashEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Excalibur. Gather: a golden pillar of light climbs from the wielder, a triple sigil turns on the
 * ground and the blade tip swells into a star. Release: the swing carves a crescent around the
 * wielder, then a colossal diagonal crescent of light (needle tips, gold body, white razor edge on
 * the cutting side) flies forward trailing afterimages, speed streaks and a burning cut in the ground.
 * All additive on {@link VfxRenderTypes#ADDITIVE}.
 */
@Environment(EnvType.CLIENT)
public class ExcaliburSlashRenderer extends EntityRenderer<ExcaliburSlashEntity> {
    private static final int ARC = 28;
    private static final float[] GOLD = {0.66f, 0.56f, 1f};
    private static final float[] PALE = {0.86f, 0.94f, 1f};
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] SKY = {0.37f, 0.93f, 0.84f};

    private final BothSides sides = new BothSides();
    private final Quaternionf camera = new Quaternionf();
    private final Vector3f cam = new Vector3f();
    private final Vector3f o = new Vector3f();
    private final Vector3f d = new Vector3f();
    private final Vector3f r = new Vector3f();
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f side = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    private final Vector3f[] arc = new Vector3f[ARC + 1];
    private final Vector3f[] out = new Vector3f[ARC + 1];

    public ExcaliburSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
        for (int i = 0; i <= ARC; i++) {
            arc[i] = new Vector3f();
            out[i] = new Vector3f();
        }
    }

    @Override
    public boolean shouldRender(ExcaliburSlashEntity slash, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ExcaliburSlashEntity slash, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float age = slash.age(partialTick);
        float time = age / 20f;
        double ex = Mth.lerp(partialTick, slash.xo, slash.getX());
        double ey = Mth.lerp(partialTick, slash.yo, slash.getY());
        double ez = Mth.lerp(partialTick, slash.zo, slash.getZ());
        Vector3f origin = slash.origin();
        o.set((float) (origin.x - ex), (float) (origin.y - ey), (float) (origin.z - ez));
        d.set(slash.direction()).normalize();
        r.set(-d.z, 0f, d.x);
        Vec3 c = entityRenderDispatcher.camera.getPosition();
        cam.set((float) (c.x - ex), (float) (c.y - ey), (float) (c.z - ez));
        camera.set(entityRenderDispatcher.cameraOrientation());
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.ADDITIVE);

        float release = age - ExcaliburSlashEntity.GATHER;
        if (release < 0f) {
            gather(vc, m, age / ExcaliburSlashEntity.GATHER, time);
        } else {
            unleash(vc, m, release, age, time);
        }
        super.render(slash, yaw, partialTick, pose, buffers, light);
    }

    // Gather ------------------------------------------------------------------------------------

    private void gather(VertexConsumer vc, Matrix4f m, float g, float time) {
        float gg = g * g;
        pillar(vc, m, 1f, gg);
        // Ground sigil: three rings, the middle one dashed and turning.
        tmp.set(o).add(0f, 0.06f, 0f);
        flatRing(vc, m, tmp, 7f, 0.35f, GOLD, 0.8f * g, 64, false, 0f);
        flatRing(vc, m, tmp, 5.5f, 0.3f, PALE, 0.85f * g, 40, true, time * 1.6f);
        flatRing(vc, m, tmp, 3.6f, 0.25f, GOLD, 0.7f * g, 24, true, -time * 2.4f);
        flatRing(vc, m, tmp, 8.5f * (1f - gg) + 1f, 0.5f, SKY, 0.5f * g, 48, false, 0f);
        // The raised blade tip.
        tmp.set(o).add(0f, 3.4f, 0f);
        halo(vc, m, tmp, 1f + 5f * gg, GOLD, 0.5f * g);
        halo(vc, m, tmp, 0.4f + 1.6f * gg, WHITE, 0.9f * g);
        star(vc, m, tmp, 2f + 9f * gg, PALE, 0.9f * g, time * 1.5f);
    }

    /** The column of light over the wielder; {@code k} scales width, {@code grow} its height. */
    private void pillar(VertexConsumer vc, Matrix4f m, float k, float grow) {
        if (k <= 0.01f) {
            return;
        }
        a.set(o).add(0f, 0.5f, 0f);
        b.set(o).add(0f, 6f + 110f * grow, 0f);
        strip(vc, m, a, b, 7f * k, 3f * k, GOLD, GOLD, 0.35f * k, 0f);
        strip(vc, m, a, b, 2.2f * k, 1f * k, PALE, PALE, 0.8f * k, 0.1f * k);
        strip(vc, m, a, b, 0.6f * k, 0.3f * k, WHITE, WHITE, 1f * k, 0.3f * k);
    }

    // Release -----------------------------------------------------------------------------------

    private void unleash(VertexConsumer vc, Matrix4f m, float t, float age, float time) {
        float front = ExcaliburSlashEntity.front(age);
        float spent = age - (ExcaliburSlashEntity.GATHER + ExcaliburSlashEntity.TRAVEL);
        float fade = 1f - smooth(spent / ExcaliburSlashEntity.FADE);
        float erupt = backOut(Math.min(1f, t / 3f));
        boolean moving = spent < 0f;
        float live = moving ? 1f : fade;

        // The pillar collapses into the cut.
        pillar(vc, m, 1f - smooth(t / 6f), 1f);
        if (t < 5f) {
            tmp.set(o).add(0f, 2f, 0f);
            halo(vc, m, tmp, 3f + 10f * (t / 5f), PALE, 0.45f * (1f - t / 5f));
        }

        // The swing itself: a crescent carved around the wielder, sweeping tip to tip in 3 ticks
        // and lingering as it fades.
        float sweep = smooth(t / 3f);
        float swingFade = 1f - smooth((t - 3f) / 7f);
        if (swingFade > 0.01f) {
            crescent(vc, m, 0.6f, 0.12f, sweep, 0.6f, GOLD, PALE, swingFade, time);
        }

        // Afterimages strung out behind the flying cut, fastest-fading first.
        for (int k = 3; k >= 1; k--) {
            float at = front - k * 2.2f;
            if (at <= 1f) {
                continue;
            }
            float ghost = (1f - k / 4f) * 0.3f * live;
            crescent(vc, m, at, erupt * (1f - k * 0.03f), 1f, 1f, GOLD, GOLD, ghost, time);
        }
        // The flying slash.
        if (front > 0.5f) {
            speedLines(vc, m, front, erupt, live);
            crescent(vc, m, front, erupt, 1f, 1f, GOLD, PALE, live, time);
        }

        // Scorched cut along the ground under the slash's path.
        a.set(o).add(0f, 0.08f, 0f);
        b.set(d).mul(front).add(o).add(0f, 0.08f, 0f);
        groundBand(vc, m, a, b, 0.8f, SKY, 0.35f * fade);
        groundBand(vc, m, a, b, 0.15f, WHITE, 0.7f * fade);
    }

    private final Vector3f radial = new Vector3f();
    private final float[] thick = new float[ARC + 1];

    /**
     * One crescent of light at distance {@code at}: a glow halo, a gold body, a pale inner body and
     * a white razor edge on the leading (outer) side. {@code reveal} draws only the first part of
     * the sweep (tip to tip) for the swing; {@code glow} scales the soft outer layer.
     */
    private void crescent(VertexConsumer vc, Matrix4f m, float at, float scale, float reveal, float glow,
                          float[] body, float[] inner, float alpha, float time) {
        if (alpha <= 0.01f || scale <= 0.01f) {
            return;
        }
        Vector3f origin = o;
        float span = ExcaliburSlashEntity.ARC_SPAN;
        int n = Math.max(2, Math.round(ARC * reveal));
        for (int i = 0; i <= n; i++) {
            float theta = -span + 2f * span * reveal * i / n;
            ExcaliburSlashEntity.crescent(origin, d, at, theta, scale, arc[i], out[i]);
            float th = ExcaliburSlashEntity.thickness(theta, scale);
            // While sweeping, the newest part (the blade) is thickest, tapering back toward the start.
            if (reveal < 1f) {
                th = ExcaliburSlashEntity.THICK * scale * (float) Math.pow(i / (float) n, 1.5);
            }
            thick[i] = th * (1f + 0.06f * Mth.sin(time * 40f + i * 0.7f));
        }
        // Layers: wide glow, body, inner body, razor edge.
        crescentBand(vc, m, n, 7f * glow, 0f, SKY, 0.12f * alpha);
        crescentBand(vc, m, n, 3f * glow, 0f, body, 0.3f * alpha);
        crescentBand(vc, m, n, 1.2f, 0f, inner, 0.7f * alpha);
        edgeLine(vc, m, n, 0f, 0.07f + 0.05f * scale, WHITE, alpha);
    }

    /**
     * A crescent-shaped band: at each sample the band spans {@code width} x thickness radially,
     * centred {@code lean} x thickness toward the outer (cutting) edge; alpha peaks on that centre.
     */
    private void crescentBand(VertexConsumer vc, Matrix4f m, int n, float width, float lean, float[] c, float alpha) {
        for (int i = 0; i < n; i++) {
            for (int s = -1; s <= 1; s += 2) {
                bandVertex(vc, m, i, 0f, lean, c, alpha);
                bandVertex(vc, m, i, s * width * 0.5f, lean, c, 0f);
                bandVertex(vc, m, i + 1, s * width * 0.5f, lean, c, 0f);
                bandVertex(vc, m, i + 1, 0f, lean, c, alpha);
            }
        }
    }

    private void bandVertex(VertexConsumer vc, Matrix4f m, int i, float offset, float lean, float[] c, float alpha) {
        float k = thick[i] * (lean + offset);
        Vector3f p = arc[i], nrm = out[i];
        vertex(vc, m, p.x + nrm.x * k, p.y + nrm.y * k, p.z + nrm.z * k, c, alpha);
    }

    /** The razor: a thin white line along the outer edge, also spread along the travel axis so it reads edge-on. */
    private void edgeLine(VertexConsumer vc, Matrix4f m, int n, float at, float width, float[] c, float alpha) {
        for (int i = 0; i < n; i++) {
            float k0 = thick[i] * at, k1 = thick[i + 1] * at;
            Vector3f p0 = arc[i], p1 = arc[i + 1], n0 = out[i], n1 = out[i + 1];
            float x0 = p0.x + n0.x * k0, y0 = p0.y + n0.y * k0, z0 = p0.z + n0.z * k0;
            float x1 = p1.x + n1.x * k1, y1 = p1.y + n1.y * k1, z1 = p1.z + n1.z * k1;
            float t0 = thick[i] > 0.05f ? 1f : 0.3f, t1 = thick[i + 1] > 0.05f ? 1f : 0.3f;
            for (int s = -1; s <= 1; s += 2) {
                vertex(vc, m, x0, y0, z0, c, alpha * t0);
                vertex(vc, m, x0 + n0.x * width * s, y0 + n0.y * width * s, z0 + n0.z * width * s, c, 0f);
                vertex(vc, m, x1 + n1.x * width * s, y1 + n1.y * width * s, z1 + n1.z * width * s, c, 0f);
                vertex(vc, m, x1, y1, z1, c, alpha * t1);
                vertex(vc, m, x0, y0, z0, c, alpha * t0 * 0.8f);
                vertex(vc, m, x0 - d.x * width * 2f * s, y0, z0 - d.z * width * 2f * s, c, 0f);
                vertex(vc, m, x1 - d.x * width * 2f * s, y1, z1 - d.z * width * 2f * s, c, 0f);
                vertex(vc, m, x1, y1, z1, c, alpha * t1 * 0.8f);
            }
        }
    }

    /** Thin streaks trailing back from the crescent's edge: the air it has just cut. */
    private void speedLines(VertexConsumer vc, Matrix4f m, float front, float scale, float alpha) {
        float span = ExcaliburSlashEntity.ARC_SPAN;
        for (int i = 0; i < 14; i++) {
            float theta = -span * 0.9f + 1.8f * span * i / 13f;
            ExcaliburSlashEntity.crescent(o, d, front, theta, scale, a, radial);
            float len = 10f + 8f * (float) Math.abs(Math.sin(i * 12.9898f));
            b.set(d).mul(-len).add(a);
            strip(vc, m, a, b, 0.1f, 0.02f, PALE, SKY, 0.45f * alpha, 0f);
        }
    }

    private void groundBand(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float hw, float[] c, float alpha) {
        if (alpha <= 0.01f || from.distanceSquared(to) < 0.01f) {
            return;
        }
        for (int s = -1; s <= 1; s += 2) {
            vertex(vc, m, from.x, from.y, from.z, c, alpha * 0.4f);
            vertex(vc, m, from.x + r.x * hw * s, from.y, from.z + r.z * hw * s, c, 0f);
            vertex(vc, m, to.x + r.x * hw * s, to.y, to.z + r.z * hw * s, c, 0f);
            vertex(vc, m, to.x, to.y, to.z, c, alpha);
        }
    }

    // Primitives ---------------------------------------------------------------------------------

    private void strip(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, float w0, float w1,
                       float[] c0, float[] c1, float a0, float a1) {
        side.set(p1).sub(p0);
        side.cross((p0.x + p1.x) * 0.5f - cam.x, (p0.y + p1.y) * 0.5f - cam.y, (p0.z + p1.z) * 0.5f - cam.z);
        if (side.lengthSquared() < 1.0e-9f) {
            return;
        }
        side.normalize();
        float h0 = w0 * 0.5f, h1 = w1 * 0.5f;
        for (int e = -1; e <= 1; e += 2) {
            vertex(vc, m, p0.x, p0.y, p0.z, c0, a0);
            vertex(vc, m, p0.x + side.x * h0 * e, p0.y + side.y * h0 * e, p0.z + side.z * h0 * e, c0, 0f);
            vertex(vc, m, p1.x + side.x * h1 * e, p1.y + side.y * h1 * e, p1.z + side.z * h1 * e, c1, 0f);
            vertex(vc, m, p1.x, p1.y, p1.z, c1, a1);
        }
    }

    private void flatRing(VertexConsumer vc, Matrix4f m, Vector3f c, float rad, float w, float[] col, float alpha, int segs,
                          boolean dashed, float spin) {
        for (int i = 0; i < segs; i++) {
            if (dashed && (i & 1) == 1) {
                continue;
            }
            float t0 = spin + Mth.TWO_PI * i / segs, t1 = spin + Mth.TWO_PI * (i + 1) / segs;
            float x0 = Mth.cos(t0), z0 = Mth.sin(t0), x1 = Mth.cos(t1), z1 = Mth.sin(t1);
            for (int band = 0; band < 2; band++) {
                float ra = band == 0 ? rad - w : rad, rb = band == 0 ? rad : rad + w;
                float aa = band == 0 ? 0f : alpha, ab = band == 0 ? alpha : 0f;
                vertex(vc, m, c.x + x0 * ra, c.y, c.z + z0 * ra, col, aa);
                vertex(vc, m, c.x + x0 * rb, c.y, c.z + z0 * rb, col, ab);
                vertex(vc, m, c.x + x1 * rb, c.y, c.z + z1 * rb, col, ab);
                vertex(vc, m, c.x + x1 * ra, c.y, c.z + z1 * ra, col, aa);
            }
        }
    }

    private void halo(VertexConsumer vc, Matrix4f m, Vector3f c, float radius, float[] col, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            float t0 = Mth.TWO_PI * i / 24, t1 = Mth.TWO_PI * (i + 1) / 24;
            vertex(vc, m, c.x, c.y, c.z, col, alpha);
            billboard(vc, m, c, Mth.cos(t0) * radius, Mth.sin(t0) * radius, col, 0f);
            billboard(vc, m, c, Mth.cos(t1) * radius, Mth.sin(t1) * radius, col, 0f);
            vertex(vc, m, c.x, c.y, c.z, col, alpha);
        }
    }

    private void star(VertexConsumer vc, Matrix4f m, Vector3f c, float size, float[] col, float alpha, float spin) {
        if (alpha <= 0.01f || size <= 0.01f) {
            return;
        }
        arms(vc, m, c, size, size * 0.08f, spin, col, alpha);
        arms(vc, m, c, size * 0.5f, size * 0.06f, spin + Mth.PI / 4f, col, alpha * 0.6f);
    }

    private void arms(VertexConsumer vc, Matrix4f m, Vector3f c, float len, float w, float spin, float[] col, float alpha) {
        for (int arm = 0; arm < 4; arm++) {
            float ang = spin + Mth.HALF_PI * arm;
            float dx = Mth.cos(ang), dy = Mth.sin(ang);
            vertex(vc, m, c.x, c.y, c.z, col, alpha);
            billboard(vc, m, c, -dy * w, dx * w, col, alpha * 0.35f);
            billboard(vc, m, c, dx * len, dy * len, col, 0f);
            billboard(vc, m, c, dy * w, -dx * w, col, alpha * 0.35f);
        }
    }

    private void billboard(VertexConsumer vc, Matrix4f m, Vector3f c, float x, float y, float[] col, float alpha) {
        side.set(x, y, 0f).rotate(camera).add(c);
        vertex(vc, m, side.x, side.y, side.z, col, alpha);
    }

    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float alpha) {
        sides.vertex(vc, m, x, y, z, c[0], c[1], c[2], alpha);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float backOut(float t) {
        float s = 1.7f;
        t -= 1f;
        return t * t * ((s + 1f) * t + s) + 1f;
    }

    @Override
    public ResourceLocation getTextureLocation(ExcaliburSlashEntity slash) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
