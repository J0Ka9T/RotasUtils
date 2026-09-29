package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.ZenithBladeEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A Zenith blade: the loosed sword itself, drawn spectral and see-through, with two fading
 * afterimages, a long additive smear in the blade's own colour whose white-hot core runs along
 * the curve, a soft halo, and four-point sparkles at its tip and scattered down its wake.
 * <p>
 * Everything is sampled from the blade's analytic loop ({@link ZenithBladeEntity.Loop}), so the
 * trail is perfectly smooth at any frame rate and needs no stored history. Nothing here allocates
 * per frame beyond the scratch vectors held by the renderer.
 */
@Environment(EnvType.CLIENT)
public class ZenithBladeRenderer extends EntityRenderer<ZenithBladeEntity> {
    /** Samples along the wake; more is smoother, each costs four quads. */
    private static final int TRAIL_SAMPLES = 40;
    /** How much of the loop (as a life fraction) the wake covers behind the blade. */
    private static final float TRAIL_SPAN = 0.5f;
    private static final float BLADE_SCALE = 2.4f;
    private static final float GLOW_WIDTH = 2.0f;
    private static final float CORE_WIDTH = 0.26f;
    private static final int HALO_SEGMENTS = 18;

    private final ItemRenderer items;
    private final ZenithBladeEntity.Loop loop = new ZenithBladeEntity.Loop();
    private final Vector3f[] points = new Vector3f[TRAIL_SAMPLES + 1];
    private final Vector3f[] tangents = new Vector3f[TRAIL_SAMPLES + 1];
    private final Vector3f[] across = new Vector3f[TRAIL_SAMPLES + 1];
    private final float[] color = new float[3];
    private final float[] hue = new float[3];
    private final Vector3f scratch = new Vector3f();
    private final Vector3f tangent = new Vector3f();
    private final Vector3f bladePos = new Vector3f();
    private final Quaternionf camera = new Quaternionf();
    private final Vector3f corner = new Vector3f();
    private final BothSides sides = new BothSides();

    public ZenithBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
        shadowRadius = 0f;
        for (int i = 0; i <= TRAIL_SAMPLES; i++) {
            points[i] = new Vector3f();
            tangents[i] = new Vector3f();
            across[i] = new Vector3f();
        }
    }

    /** The trail reaches blocks behind a half-block carrier: frustum-culling the carrier cut the trail off at screen edges. */
    @Override
    public boolean shouldRender(ZenithBladeEntity blade, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(ZenithBladeEntity blade, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float age = blade.age(partialTick);
        int loopLife = blade.life();
        float u = age / loopLife;
        if (u >= 1f) {
            return;
        }
        float fade = smooth(age / 2.5f) * (1f - smooth((age - (loopLife - 5)) / 5f));
        if (fade <= 0.005f) {
            return;
        }
        int seed = blade.seed();
        loop.set(blade.loopOrigin(partialTick), blade.target(), seed);
        // Render origin is the entity's interpolated position; everything is placed relative to it.
        Vec3 at = new Vec3(Mth.lerp(partialTick, blade.xo, blade.getX()), Mth.lerp(partialTick, blade.yo, blade.getY()),
                Mth.lerp(partialTick, blade.zo, blade.getZ()));
        float ox = (float) at.x, oy = (float) at.y, oz = (float) at.z;
        bladeColor(blade.blade(), color);
        // One in three blades burns rainbow, so a volley reads as the Zenith's prism, not a flock.
        boolean prismatic = Math.floorMod(seed >>> 3, 3) == 0;
        camera.set(entityRenderDispatcher.cameraOrientation());

        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(VfxRenderTypes.ADDITIVE);

        // The wake, sampled back along the true loop.
        for (int i = 0; i <= TRAIL_SAMPLES; i++) {
            double su = Math.max(0.0, u - TRAIL_SPAN * i / TRAIL_SAMPLES);
            loop.at(su, points[i]).sub(ox, oy, oz);
            loop.tangent(su, tangents[i]);
            across[i].set(loop.normal()).cross(tangents[i]).normalize();
        }
        float time = age / 20f;
        wake(glow, m, u, fade, prismatic, time);
        flatBand(glow, m, u, fade, prismatic, time);
        rims(glow, m, u, fade, prismatic, time);

        // Halo around the blade itself.
        loop.at(u, bladePos).sub(ox, oy, oz);
        loop.tangent(u, tangent);
        halo(glow, m, bladePos, 2.2f, color, 0.35f * fade);
        halo(glow, m, bladePos, 0.8f, whiteHot(color, 0.8f), 0.55f * fade);

        // Sparkles: one riding the tip, a few shed into the wake and twinkling out.
        scratch.set(tangent).mul(0.95f).add(bladePos);
        float twinkle = 0.75f + 0.25f * Mth.sin(age * 1.7f + seed);
        star(glow, m, scratch, 1.2f * twinkle, whiteHot(color, 0.6f), 0.95f * fade, age * 0.12f);
        star(glow, m, scratch, 1.8f * twinkle, whiteHot(color, 0.3f), 0.5f * fade, -age * 0.08f);
        for (int s = 0; s < 6; s++) {
            int k = 4 + s * 6;
            float life = 1f - (float) k / TRAIL_SAMPLES;
            float flick = 0.5f + 0.5f * Mth.sin(age * (2.1f + s) + seed * 0.37f + s * 1.9f);
            scratch.set(across[k]).mul(0.55f * ((s & 1) == 0 ? 1f : -1f)).add(points[k]);
            star(glow, m, scratch, 0.38f * life * (0.6f + 0.4f * flick), whiteHot(color, 0.4f), 0.8f * fade * life * flick,
                    s * 0.7f);
        }

        // The sword: four afterimages fanning back along the loop, then the blade.
        ghost(blade.blade(), pose, buffers, blade, u - 0.09f, 0.10f * fade, ox, oy, oz);
        ghost(blade.blade(), pose, buffers, blade, u - 0.066f, 0.17f * fade, ox, oy, oz);
        ghost(blade.blade(), pose, buffers, blade, u - 0.044f, 0.27f * fade, ox, oy, oz);
        ghost(blade.blade(), pose, buffers, blade, u - 0.022f, 0.45f * fade, ox, oy, oz);
        ghost(blade.blade(), pose, buffers, blade, u, 0.92f * fade, ox, oy, oz);
        super.render(blade, yaw, partialTick, pose, buffers, light);
    }

    /**
     * The smear: two crossed ribbons (one in the loop's plane, one across it) so it reads from any
     * angle, each a wide soft glow in the blade's colour with a thin white-hot core, tapering and
     * fading toward the tail.
     */
    private void wake(VertexConsumer vc, Matrix4f m, float u, float fade, boolean prismatic, float time) {
        Vector3f normal = loop.normal();
        for (int i = 0; i < TRAIL_SAMPLES; i++) {
            if (u - TRAIL_SPAN * i / TRAIL_SAMPLES <= 0f) {
                break;
            }
            float t0 = (float) i / TRAIL_SAMPLES;
            float t1 = (float) (i + 1) / TRAIL_SAMPLES;
            float f0 = taper(t0), f1 = taper(t1);
            float[] c0 = segmentColor(prismatic, t0, time, hue);
            float r0 = c0[0], g0 = c0[1], b0 = c0[2];
            float[] c1 = segmentColor(prismatic, t1, time, hue);
            float r1 = c1[0], g1 = c1[1], b1 = c1[2];
            // Outer glow, in-plane and cross-plane.
            ribbon(vc, m, i, across[i], across[i + 1], GLOW_WIDTH * f0, GLOW_WIDTH * f1,
                    r0, g0, b0, 0.3f * fade * f0, r1, g1, b1, 0.3f * fade * f1);
            ribbon(vc, m, i, normal, normal, GLOW_WIDTH * 0.55f * f0, GLOW_WIDTH * 0.55f * f1,
                    r0, g0, b0, 0.35f * fade * f0, r1, g1, b1, 0.35f * fade * f1);
            // White-hot core.
            float w0 = 0.75f, w1 = w0;
            ribbon(vc, m, i, across[i], across[i + 1], CORE_WIDTH * f0 + 0.03f, CORE_WIDTH * f1 + 0.03f,
                    lerpWhite(r0, w0), lerpWhite(g0, w0), lerpWhite(b0, w0), 0.9f * fade * f0 * f0,
                    lerpWhite(r1, w1), lerpWhite(g1, w1), lerpWhite(b1, w1), 0.9f * fade * f1 * f1);
            ribbon(vc, m, i, normal, normal, CORE_WIDTH * 0.6f * f0, CORE_WIDTH * 0.6f * f1,
                    lerpWhite(r0, w0), lerpWhite(g0, w0), lerpWhite(b0, w0), 0.7f * fade * f0 * f0,
                    lerpWhite(r1, w1), lerpWhite(g1, w1), lerpWhite(b1, w1), 0.7f * fade * f1 * f1);
        }
    }

    /**
     * The anime layer: a flat, hard-edged band of the blade's colour with a flat white core, plus
     * tapered speed-line streaks trailing at either side, all crisp rather than soft.
     */
    private void flatBand(VertexConsumer vc, Matrix4f m, float u, float fade, boolean prismatic, float time) {
        for (int i = 0; i < TRAIL_SAMPLES; i++) {
            if (u - TRAIL_SPAN * i / TRAIL_SAMPLES <= 0f) {
                break;
            }
            float t0 = (float) i / TRAIL_SAMPLES, t1 = (float) (i + 1) / TRAIL_SAMPLES;
            float f0 = taper(t0), f1 = taper(t1);
            float[] c = segmentColor(prismatic, t0, time, hue);
            float r = c[0], g = c[1], b = c[2];
            hard(vc, m, i, GLOW_WIDTH * 0.32f * f0, GLOW_WIDTH * 0.32f * f1, r, g, b, 0.8f * fade * f0, 0.8f * fade * f1);
            hard(vc, m, i, GLOW_WIDTH * 0.12f * f0, GLOW_WIDTH * 0.12f * f1, 1f, 1f, 1f, fade * f0, fade * f1);
            // Speed lines: three streaks off the band, each ending earlier the further out it sits.
            for (int line = 0; line < 3; line++) {
                float reach = 0.85f - 0.2f * line;
                if (t0 > reach) {
                    continue;
                }
                float off = GLOW_WIDTH * (0.5f + 0.22f * line) * ((line & 1) == 0 ? 1f : -1f);
                float k0 = 1f - t0 / reach, k1 = Math.max(0f, 1f - t1 / reach);
                Vector3f p0 = points[i], p1 = points[i + 1], s0 = across[i], s1 = across[i + 1];
                float w0 = 0.05f * k0, w1 = 0.05f * k1;
                vertex(vc, m, p0.x + s0.x * (off - w0), p0.y + s0.y * (off - w0), p0.z + s0.z * (off - w0), r, g, b, 0.9f * fade * k0);
                vertex(vc, m, p0.x + s0.x * (off + w0), p0.y + s0.y * (off + w0), p0.z + s0.z * (off + w0), r, g, b, 0.9f * fade * k0);
                vertex(vc, m, p1.x + s1.x * (off + w1), p1.y + s1.y * (off + w1), p1.z + s1.z * (off + w1), r, g, b, 0.9f * fade * k1);
                vertex(vc, m, p1.x + s1.x * (off - w1), p1.y + s1.y * (off - w1), p1.z + s1.z * (off - w1), r, g, b, 0.9f * fade * k1);
            }
        }
    }

    /** One segment of a hard-edged ribbon: constant alpha across its width. */
    private void hard(VertexConsumer vc, Matrix4f m, int i, float w0, float w1, float r, float g, float b, float a0,
                      float a1) {
        Vector3f p0 = points[i], p1 = points[i + 1], s0 = across[i], s1 = across[i + 1];
        vertex(vc, m, p0.x - s0.x * w0, p0.y - s0.y * w0, p0.z - s0.z * w0, r, g, b, a0);
        vertex(vc, m, p0.x + s0.x * w0, p0.y + s0.y * w0, p0.z + s0.z * w0, r, g, b, a0);
        vertex(vc, m, p1.x + s1.x * w1, p1.y + s1.y * w1, p1.z + s1.z * w1, r, g, b, a1);
        vertex(vc, m, p1.x - s1.x * w1, p1.y - s1.y * w1, p1.z - s1.z * w1, r, g, b, a1);
    }

    /**
     * Bright hairlines along both outer edges of the smear, the crisp rim Terraria's sword trails
     * have, so the wake reads as a cut through the air rather than a blur.
     */
    private void rims(VertexConsumer vc, Matrix4f m, float u, float fade, boolean prismatic, float time) {
        for (int i = 0; i < TRAIL_SAMPLES; i++) {
            if (u - TRAIL_SPAN * i / TRAIL_SAMPLES <= 0f) {
                break;
            }
            float t0 = (float) i / TRAIL_SAMPLES, t1 = (float) (i + 1) / TRAIL_SAMPLES;
            float f0 = taper(t0), f1 = taper(t1);
            float[] c = segmentColor(prismatic, t0, time, hue);
            float r = lerpWhite(c[0], 0.55f), g = lerpWhite(c[1], 0.55f), b = lerpWhite(c[2], 0.55f);
            for (int edge = -1; edge <= 1; edge += 2) {
                float o0 = GLOW_WIDTH * 0.62f * f0 * edge, o1 = GLOW_WIDTH * 0.62f * f1 * edge;
                Vector3f p0 = points[i], p1 = points[i + 1], s0 = across[i], s1 = across[i + 1];
                float w0 = 0.06f * f0 + 0.015f, w1 = 0.06f * f1 + 0.015f;
                for (int side = -1; side <= 1; side += 2) {
                    vertex(vc, m, p0.x + s0.x * o0, p0.y + s0.y * o0, p0.z + s0.z * o0, r, g, b, 0.85f * fade * f0);
                    vertex(vc, m, p0.x + s0.x * (o0 + w0 * side), p0.y + s0.y * (o0 + w0 * side), p0.z + s0.z * (o0 + w0 * side), r, g, b, 0f);
                    vertex(vc, m, p1.x + s1.x * (o1 + w1 * side), p1.y + s1.y * (o1 + w1 * side), p1.z + s1.z * (o1 + w1 * side), r, g, b, 0f);
                    vertex(vc, m, p1.x + s1.x * o1, p1.y + s1.y * o1, p1.z + s1.z * o1, r, g, b, 0.85f * fade * f1);
                }
            }
        }
    }

    /** Wake colour at trail position {@code t}: the blade's own, or a cycling rainbow for prismatic blades. */
    private float[] segmentColor(boolean prismatic, float t, float time, float[] out) {
        if (!prismatic) {
            out[0] = color[0];
            out[1] = color[1];
            out[2] = color[2];
            return out;
        }
        hsv((t * 0.9f + time * 0.8f) % 1f, 0.75f, 1f, out);
        return out;
    }

    /** One segment of a soft ribbon: bright on the centre line, fading to nothing at both edges. */
    private void ribbon(VertexConsumer vc, Matrix4f m, int i, Vector3f side0, Vector3f side1, float w0, float w1,
                        float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1) {
        Vector3f p0 = points[i], p1 = points[i + 1];
        for (int edge = -1; edge <= 1; edge += 2) {
            vertex(vc, m, p0.x, p0.y, p0.z, r0, g0, b0, a0);
            vertex(vc, m, p0.x + side0.x * w0 * edge, p0.y + side0.y * w0 * edge, p0.z + side0.z * w0 * edge, r0, g0, b0, 0f);
            vertex(vc, m, p1.x + side1.x * w1 * edge, p1.y + side1.y * w1 * edge, p1.z + side1.z * w1 * edge, r1, g1, b1, 0f);
            vertex(vc, m, p1.x, p1.y, p1.z, r1, g1, b1, a1);
        }
    }

    /** A camera-facing soft disc, bright in the middle. */
    private void halo(VertexConsumer vc, Matrix4f m, Vector3f c, float radius, float[] rgb, float alpha) {
        for (int i = 0; i < HALO_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / HALO_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / HALO_SEGMENTS;
            vertex(vc, m, c.x, c.y, c.z, rgb[0], rgb[1], rgb[2], alpha);
            billboard(vc, m, c, Mth.cos(a0) * radius, Mth.sin(a0) * radius, rgb, 0f);
            billboard(vc, m, c, Mth.cos(a1) * radius, Mth.sin(a1) * radius, rgb, 0f);
            vertex(vc, m, c.x, c.y, c.z, rgb[0], rgb[1], rgb[2], alpha);
        }
    }

    /** A Terraria-style four-point sparkle, long thin arms plus a smaller diagonal cross, facing the camera. */
    private void star(VertexConsumer vc, Matrix4f m, Vector3f c, float size, float[] rgb, float alpha, float spin) {
        if (alpha <= 0.01f || size <= 0.01f) {
            return;
        }
        arms(vc, m, c, size, size * 0.14f, spin, rgb, alpha);
        arms(vc, m, c, size * 0.5f, size * 0.1f, spin + Mth.PI / 4f, rgb, alpha * 0.6f);
    }

    private void arms(VertexConsumer vc, Matrix4f m, Vector3f c, float length, float width, float spin, float[] rgb,
                      float alpha) {
        for (int arm = 0; arm < 4; arm++) {
            float a = spin + Mth.HALF_PI * arm;
            float dx = Mth.cos(a), dy = Mth.sin(a);
            vertex(vc, m, c.x, c.y, c.z, rgb[0], rgb[1], rgb[2], alpha);
            billboard(vc, m, c, -dy * width, dx * width, rgb, alpha * 0.35f);
            billboard(vc, m, c, dx * length, dy * length, rgb, 0f);
            billboard(vc, m, c, dy * width, -dx * width, rgb, alpha * 0.35f);
        }
    }

    private void billboard(VertexConsumer vc, Matrix4f m, Vector3f c, float x, float y, float[] rgb, float alpha) {
        corner.set(x, y, 0f).rotate(camera).add(c);
        vertex(vc, m, corner.x, corner.y, corner.z, rgb[0], rgb[1], rgb[2], alpha);
    }

    /**
     * The sword model at life fraction {@code su}, see-through by {@code alpha}. The sprite's
     * diagonal (hilt bottom-left to tip top-right) is turned onto the direction of travel, and its
     * face onto the loop's plane, so the blade always leads with its point.
     */
    private void ghost(ItemStack stack, PoseStack pose, MultiBufferSource buffers, ZenithBladeEntity blade, float su,
                       float alpha, float ox, float oy, float oz) {
        if (su < 0f || alpha <= 0.01f) {
            return;
        }
        loop.at(su, scratch).sub(ox, oy, oz);
        loop.tangent(su, tangent);
        Vector3f n = loop.normal();
        Vector3f b = corner.set(n).cross(tangent);
        float k = Mth.SQRT_OF_TWO / 2f;
        Matrix3f basis = new Matrix3f(
                (tangent.x - b.x) * k, (tangent.y - b.y) * k, (tangent.z - b.z) * k,
                (tangent.x + b.x) * k, (tangent.y + b.y) * k, (tangent.z + b.z) * k,
                n.x, n.y, n.z);
        pose.pushPose();
        pose.translate(scratch.x, scratch.y, scratch.z);
        pose.mulPoseMatrix(new Matrix4f(basis));
        pose.last().normal().mul(basis);
        pose.scale(BLADE_SCALE, BLADE_SCALE, BLADE_SCALE);
        MultiBufferSource spectral = type -> new FadingConsumer(
                buffers.getBuffer(RenderType.itemEntityTranslucentCull(InventoryMenu.BLOCK_ATLAS)), alpha);
        items.renderStatic(stack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                spectral, blade.level(), blade.getId());
        pose.popPose();
    }

    /** The blade's signature colour: vanilla tiers get their material's, anything else a stable hue of its own. */
    static void bladeColor(ItemStack stack, float[] out) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        if (path.contains("wood")) {
            set(out, 1.0f, 0.72f, 0.35f);
        } else if (path.contains("stone")) {
            set(out, 0.62f, 0.78f, 1.0f);
        } else if (path.contains("iron")) {
            set(out, 0.85f, 0.95f, 1.0f);
        } else if (path.contains("gold")) {
            set(out, 1.0f, 0.82f, 0.2f);
        } else if (path.contains("diamond")) {
            set(out, 0.25f, 1.0f, 0.92f);
        } else if (path.contains("netherite")) {
            set(out, 0.78f, 0.38f, 1.0f);
        } else {
            hsv(Math.floorMod(id.hashCode(), 360) / 360f, 0.7f, 1f, out);
        }
    }

    private float[] whiteHot(float[] rgb, float amount) {
        hue[0] = rgb[0] + (1f - rgb[0]) * amount;
        hue[1] = rgb[1] + (1f - rgb[1]) * amount;
        hue[2] = rgb[2] + (1f - rgb[2]) * amount;
        return hue;
    }

    private static float lerpWhite(float c, float amount) {
        return c + (1f - c) * amount;
    }

    /** Wake width/brightness along its length: full just behind the blade, easing to nothing at the tail. */
    private static float taper(float t) {
        float s = 1f - t;
        return s * s * (3f - 2f * s);
    }

    private static void set(float[] out, float r, float g, float b) {
        out[0] = r;
        out[1] = g;
        out[2] = b;
    }

    private static void hsv(float h, float s, float v, float[] out) {
        int rgb = Mth.hsvToRgb(h, s, v);
        out[0] = ((rgb >> 16) & 0xFF) / 255f;
        out[1] = ((rgb >> 8) & 0xFF) / 255f;
        out[2] = (rgb & 0xFF) / 255f;
    }

    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        sides.vertex(vc, m, x, y, z, r, g, b, a);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public ResourceLocation getTextureLocation(ZenithBladeEntity blade) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    /** Passes vertices through with their alpha scaled, turning any item model into a spectral one. */
    private static final class FadingConsumer implements VertexConsumer {
        private final VertexConsumer inner;
        private final float alpha;

        FadingConsumer(VertexConsumer inner, float alpha) {
            this.inner = inner;
            this.alpha = alpha;
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                           int light, float nx, float ny, float nz) {
            inner.vertex(x, y, z, r, g, b, a * alpha, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            inner.color(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            inner.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            inner.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            inner.unsetDefaultColor();
        }
    }
}
