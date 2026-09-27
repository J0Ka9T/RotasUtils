package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.ExoBeamEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The ExoElectric Disintegrator's ray: a crimson column 20 blocks across and up to 320 long.
 * <ul>
 *   <li><b>Body</b>: a textured tube of racing red-and-black plasma streaks, scrolling away from the
 *       muzzle.</li>
 *   <li><b>Rim</b>: additive white-hot shell whose brightness follows the view angle (a Fresnel
 *       term), so the silhouette always burns white while the middle shows the red interior. It
 *       reads correctly from the side and from the wielder's own eyes looking straight down it.</li>
 *   <li><b>Nose</b>: the ray swells from the muzzle through an elliptical, white-flooded dome.</li>
 *   <li><b>Core</b>: a thin blinding white-cyan line down the axis.</li>
 *   <li><b>Muzzle</b>: a towering vertical light pillar and horizontal lens streak, crimson bloom.</li>
 * </ul>
 * The charge phase gathers red light, rings and motes at the muzzle before the ray erupts.
 *
 * <p>Two things colour it. <b>Heat</b>: the ordinary beam runs from crimson through orange to a
 * bleached white as it nears the vent, so the wielder can see the gun cooking before it cuts out.
 * <b>Mode</b>: the Annihilation Lance is twice as wide and burns gold-white with a violet haze, adds
 * a wall of shock racing down its length and a far heavier muzzle. Both are read from the beam each
 * frame; nothing about them is synced.</p>
 */
@Environment(EnvType.CLIENT)
public class ExoBeamRenderer extends EntityRenderer<ExoBeamEntity> {
    public static final ResourceLocation PLASMA = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/exo_beam.png");
    private static final float[][] CRIMSON = {
            {1.00f, 0.10f, 0.14f},
            {1.00f, 0.42f, 0.46f},
            {1.00f, 0.80f, 0.82f},
            {0.80f, 0.04f, 0.08f},
    };
    private static final int SIDES = 28;
    private static final int NOSE_RINGS = 12;
    private static final float BODY_STEP = 10f;
    private static final int MAX_RINGS = NOSE_RINGS + (int) (ExoBeamEntity.RANGE / BODY_STEP) + 3;
    private static final int BOLT_POINTS = 9;

    private final BothSides sides = new BothSides();
    private final Quaternionf camera = new Quaternionf();
    private final Vector3f cam = new Vector3f();
    private final Vector3f muzzle = new Vector3f();
    private final Vector3f end = new Vector3f();
    private final Vector3f impact = new Vector3f();
    private boolean struck;
    private final Vector3f dir = new Vector3f();
    private final Vector3f u = new Vector3f();
    private final Vector3f v = new Vector3f();
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f side = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f bd = new Vector3f();
    private final Vector3f[] bolt = new Vector3f[BOLT_POINTS];
    private final float[] stations = new float[MAX_RINGS];
    private final float[] radii = new float[MAX_RINGS];
    private final float[] slopes = new float[MAX_RINGS];
    private final float[] cosT = new float[SIDES + 1];
    private final float[] sinT = new float[SIDES + 1];
    private final float[] c1 = new float[3];
    private final float[] mixed = new float[3];
    private final float[] white = {1f, 1f, 1f};
    private final float[] ice = {0.85f, 1f, 1f};
    private final float[] rim = {1f, 0.9f, 0.9f};
    private final float[] blood = {1f, 0.12f, 0.16f};
    /** Where a cooking beam's crimson ends up, and the lance's own two colours. */
    private static final float[] COOKING = {1f, 0.62f, 0.18f};
    private static final float[] LANCE_HOT = {1f, 0.86f, 0.42f};
    private static final float[] LANCE_HAZE = {0.72f, 0.42f, 1f};
    /** This frame's body colour, rim colour, beam width and how overcharged the shot is. */
    private final float[] shotBody = {1f, 0.12f, 0.16f};
    private final float[] shotRim = {1f, 0.9f, 0.9f};
    private float widthScale = 1f;
    private boolean lance;

    public ExoBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
        for (int i = 0; i < BOLT_POINTS; i++) {
            bolt[i] = new Vector3f();
        }
        for (int k = 0; k <= SIDES; k++) {
            float t = Mth.TWO_PI * k / SIDES;
            cosT[k] = Mth.cos(t);
            sinT[k] = Mth.sin(t);
        }
    }

    /**
     * The ray is far longer than its entity box, so it is culled against its real reach: a muzzle the
     * camera can see, or a far tip still in frame, keeps it; a beam entirely behind the view is dropped.
     */
    @Override
    public boolean shouldRender(ExoBeamEntity beam, Frustum frustum, double x, double y, double z) {
        LivingEntity owner = beam.channeler();
        if (owner == null) {
            return false;
        }
        Vec3 eye = owner.getEyePosition();
        Vec3 far = eye.add(owner.getViewVector(1f).scale(ExoBeamEntity.RANGE));
        // Inflate by what is actually drawn: the widest haze shell around the tube, and the tall
        // muzzle pillar above and below it, so no layer pops when the axis crosses the frame edge.
        float tube = ExoBeamEntity.RADIUS * beam.mode().radius * 1.6f + 4f;
        float pillar = 70f * beam.mode().radius + 4f;
        return frustum.isVisible(new AABB(eye, far).inflate(tube, pillar, tube));
    }

    @Override
    public void render(ExoBeamEntity beam, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        LivingEntity owner = beam.channeler();
        if (owner == null) {
            return;
        }
        float age = beam.age(partialTick);
        float time = age / 20f;
        double ox = Mth.lerp(partialTick, beam.xo, beam.getX());
        double oy = Mth.lerp(partialTick, beam.yo, beam.getY());
        double oz = Mth.lerp(partialTick, beam.zo, beam.getZ());

        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        Vec3 hit = ExoBeamEntity.beamEnd(owner, eye, look);
        float yawRad = owner.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
        Vector3f right = tmp.set(-Mth.cos(yawRad), 0f, -Mth.sin(yawRad));
        Vector3f lookF = dir.set((float) look.x, (float) look.y, (float) look.z);
        Vector3f up = side.set(right).cross(lookF).normalize();
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = owner == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        float fwd = firstPerson ? 0.75f : 1.1f, rt = firstPerson ? 0.30f : 0.38f, dn = firstPerson ? -0.24f : -0.35f;
        muzzle.set((float) (eye.x - ox), (float) (eye.y - oy), (float) (eye.z - oz))
                .add(lookF.x * fwd + right.x * rt + up.x * dn, lookF.y * fwd + right.y * rt + up.y * dn,
                        lookF.z * fwd + right.z * rt + up.z * dn);
        // The ray burns through terrain for its full range; the first surface only gets a scorch flare.
        Vec3 far = eye.add(look.scale(ExoBeamEntity.RANGE));
        struck = hit.distanceToSqr(far) > 1.0;
        impact.set((float) (hit.x - ox), (float) (hit.y - oy), (float) (hit.z - oz));
        end.set((float) (far.x - ox), (float) (far.y - oy), (float) (far.z - oz));
        dir.set(end).sub(muzzle);
        float length = dir.length();
        if (length < 0.1f) {
            return;
        }
        dir.div(length);
        basis(dir, u, v);
        Vec3 camPos = entityRenderDispatcher.camera.getPosition();
        cam.set((float) (camPos.x - ox), (float) (camPos.y - oy), (float) (camPos.z - oz));
        camera.set(entityRenderDispatcher.cameraOrientation());

        lance = beam.mode() == ExoBeamEntity.Mode.LANCE;
        widthScale = beam.mode().radius;
        palette(beam.heat(partialTick), time);
        float firing = age - beam.chargeTicks();
        if (firing < 0f) {
            charge(buffers.getBuffer(VfxRenderTypes.ADDITIVE), pose.last().pose(), age / beam.chargeTicks(), time,
                    beam.tickCount);
        } else {
            fire(pose, buffers, firing, length, time, beam.tickCount);
        }
        super.render(beam, yaw, partialTick, pose, buffers, light);
    }

    /**
     * This frame's colours. A cooking beam slides crimson to orange and then bleaches toward white, so
     * the heat is visible on the ray itself; the lance ignores heat and burns gold-white throughout,
     * with a flicker so it never sits still.
     */
    private void palette(float heat, float time) {
        if (lance) {
            float flicker = 0.92f + 0.08f * Mth.sin(time * 41f);
            for (int i = 0; i < 3; i++) {
                shotBody[i] = LANCE_HOT[i] * flicker;
                shotRim[i] = 1f - (1f - LANCE_HOT[i]) * 0.25f;
            }
            return;
        }
        float bleach = Math.max(0f, heat - 0.55f) / 0.45f;
        for (int i = 0; i < 3; i++) {
            float cooked = blood[i] + (COOKING[i] - blood[i]) * heat;
            shotBody[i] = cooked + (1f - cooked) * bleach * 0.7f;
            shotRim[i] = rim[i];
        }
    }

    // Charge ------------------------------------------------------------------------------------

    private void charge(VertexConsumer vc, Matrix4f m, float c, float time, int tick) {
        float cc = c * c;
        halo(vc, m, muzzle, (0.4f + 2.6f * cc) * widthScale, shotBody, 0.25f + 0.45f * c);
        halo(vc, m, muzzle, 0.1f + 0.6f * cc, white, 0.4f + 0.6f * c);
        star(vc, m, muzzle, (0.4f + 2.2f * cc) * widthScale, whiteMix(shotBody, 0.5f), 0.6f * c, time * 3f);
        // Three dashed rings closing in around the aim, counter-rotating.
        for (int k = 0; k < 3; k++) {
            float r = ((3.2f - k * 0.6f) * (1f - cc) + 0.3f) * widthScale;
            tmp.set(dir).mul(0.1f + k * 0.3f).add(muzzle);
            if (lance) {
                System.arraycopy(k % 2 == 0 ? LANCE_HOT : LANCE_HAZE, 0, c1, 0, 3);
            } else {
                crimson(k / 3f + time * 0.4f, c1);
            }
            ring(vc, m, tmp, u, v, r, 0.14f, c1, 0.8f * c, 32, true, time * ((k & 1) == 0 ? 3.2f : -2.6f));
        }
        // Motes spiralling inward.
        for (int i = 0; i < 20; i++) {
            float ph = frac(time * 1.3f + i / 20f);
            float r = 4f * (1f - ph) * (1f - ph);
            float ang = i * 2.39996f + ph * 5f;
            a.set(u).mul(Mth.cos(ang) * r).add(v.x * Mth.sin(ang) * r, v.y * Mth.sin(ang) * r, v.z * Mth.sin(ang) * r)
                    .add(dir.x * -(1f - ph), dir.y * -(1f - ph), dir.z * -(1f - ph)).add(muzzle);
            crimson(i / 20f, c1);
            star(vc, m, a, 0.14f + 0.18f * c, whiteMix(c1, 0.3f), ph * (0.3f + 0.7f * c), ang);
        }
        // Crackling arcs off the gathering core.
        for (int i = 0; i < 4; i++) {
            float h1 = hash(tick, i * 3 + 1), h2 = hash(tick, i * 3 + 2);
            float ang = h1 * Mth.TWO_PI;
            float r = (0.5f + 1.2f * h2) * (0.3f + 0.7f * c);
            b.set(u).mul(Mth.cos(ang) * r).add(v.x * Mth.sin(ang) * r, v.y * Mth.sin(ang) * r, v.z * Mth.sin(ang) * r)
                    .add(muzzle);
            bolt(vc, m, muzzle, b, 0.15f * r, tick, i + 40, 0.03f, 0.16f, shotBody, 0.85f * c);
        }
    }

    // Beam --------------------------------------------------------------------------------------

    private void fire(PoseStack pose, MultiBufferSource buffers, float f, float length, float time, int tick) {
        Matrix4f m = pose.last().pose();
        float grow = backOut(Math.min(1f, f / 6f)) * (0.97f + 0.03f * Mth.sin(time * 70f));
        // The ray punches out over a few ticks rather than appearing at full length.
        float reach = Math.min(length, 8f + f * 90f);
        int rings = stations(reach, grow, time);

        // Body: racing plasma streaks.
        VertexConsumer body = buffers.getBuffer(VfxRenderTypes.glowTextured(PLASMA));
        Matrix3f n = pose.last().normal();
        float scroll = time * 2.2f;
        for (int j = 0; j < rings - 1; j++) {
            for (int k = 0; k < SIDES; k++) {
                plasma(body, m, n, j, k, scroll, 0.96f, 0.92f, 0f);
            }
        }
        // A looser outer sheath flowing slower and twisted, so the streaks shear past each other.
        for (int j = 0; j < rings - 1; j++) {
            for (int k = 0; k < SIDES; k++) {
                plasma(body, m, n, j, k, scroll * 0.55f + 0.37f, 1.1f, 0.42f, time * 0.15f);
            }
        }

        VertexConsumer glow = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        // White-hot silhouette, then a wider haze in the shot's own colour.
        shell(glow, m, rings, 1.0f, shotRim, 1.0f, 1.6f, 0f);
        shell(glow, m, rings, 1.25f, shotBody, 0.45f, 1.0f, 0f);
        shell(glow, m, rings, 1.6f, lance ? LANCE_HAZE : shotBody, lance ? 0.3f : 0.18f, 0.7f, 0f);
        // The nose is flooded white, fading into the body behind it.
        shell(glow, m, rings, 0.97f, white, 0.85f, 0f, ExoBeamEntity.NOSE * widthScale + 10f);

        // Shock rings racing down the ray.
        float spacing = 28f, lead = frac(time * 150f / spacing) * spacing;
        for (float d = ExoBeamEntity.NOSE * widthScale + lead; d < reach; d += spacing) {
            float r = ExoBeamEntity.RADIUS * widthScale * grow * 1.04f;
            float fade = 1f - d / (float) ExoBeamEntity.RANGE;
            tmp.set(dir).mul(d).add(muzzle);
            ring(glow, m, tmp, u, v, r, 1.6f, shotRim, 0.55f * fade, 48, false, 0f);
            ring(glow, m, tmp, u, v, r * 1.12f, 2.6f, shotBody, 0.35f * fade, 48, false, 0f);
        }
        // The lance throws one wall of shock down its whole length, once per discharge.
        if (lance) {
            float front = Mth.clamp(f / 10f, 0f, 1f);
            float at = front * Math.min(reach, 220f);
            float fade = 1f - front;
            tmp.set(dir).mul(at).add(muzzle);
            float wall = ExoBeamEntity.RADIUS * widthScale * grow;
            ring(glow, m, tmp, u, v, wall * 1.15f, 6f, white, 0.85f * fade, 64, false, 0f);
            ring(glow, m, tmp, u, v, wall * 1.6f, 12f, LANCE_HAZE, 0.5f * fade, 64, false, 0f);
            ring(glow, m, tmp, u, v, wall * 2.3f, 18f, shotBody, 0.25f * fade, 64, false, 0f);
        }
        // Rotating sigil around the muzzle: an outer ticked ring, a counter-spinning dashed ring, an inner band.
        tmp.set(dir).mul(-0.4f).add(muzzle);
        float sigil = grow * widthScale;
        ring(glow, m, tmp, u, v, 5.5f * sigil, 0.25f, shotBody, 0.85f, 64, false, 0f);
        ring(glow, m, tmp, u, v, 6.1f * sigil, 0.35f, shotRim, 0.6f, 36, true, time * 1.2f);
        ring(glow, m, tmp, u, v, 4.2f * sigil, 0.2f, whiteMix(shotBody, 0.4f), 0.8f, 24, true, -time * 2.1f);
        ring(glow, m, tmp, u, v, 2.6f * sigil, 0.45f, shotBody, 0.6f, 48, false, 0f);
        // Axis core.
        a.set(muzzle);
        b.set(dir).mul(reach).add(muzzle);
        strip(glow, m, a, b, 0.9f * grow * widthScale, 0.9f * grow * widthScale, lance ? LANCE_HAZE : ice,
                lance ? LANCE_HAZE : ice, 0.55f, 0.35f);
        strip(glow, m, a, b, 0.25f * grow * widthScale, 0.25f * grow * widthScale, white, white, 1f, 0.8f);

        // Eruption flash.
        if (f < 6f) {
            float k = f / 6f;
            halo(glow, m, muzzle, (4f + 14f * k) * widthScale, white, 0.95f * (1f - k));
            ring(glow, m, muzzle, u, v, (2f + 14f * k) * widthScale, 1.2f, shotBody, 0.8f * (1f - k), 48, false, 0f);
        }

        // Muzzle: crimson bloom, a towering vertical pillar and a horizontal lens streak.
        halo(glow, m, muzzle, 9f * grow * widthScale, shotBody, 0.55f);
        halo(glow, m, muzzle, 3.5f * grow * widthScale, whiteMix(shotBody, 0.6f), 0.8f);
        halo(glow, m, muzzle, 1.4f * grow * widthScale, white, 1f);
        float pillar = 70f * grow * widthScale;
        a.set(muzzle).add(0f, -pillar, 0f);
        b.set(muzzle).add(0f, pillar, 0f);
        pillar(glow, m, a, b, 3.2f * grow, shotBody, 0.3f);
        pillar(glow, m, a, b, 0.9f * grow, lance ? LANCE_HAZE : ice, 0.8f);
        pillar(glow, m, a, b, 0.25f * grow, white, 1f);
        tmp.set(1f, 0f, 0f).rotate(camera);
        a.set(tmp).mul(-26f * grow * widthScale).add(muzzle);
        b.set(tmp).mul(26f * grow * widthScale).add(muzzle);
        pillar(glow, m, a, b, 0.6f * grow, whiteMix(shotBody, 0.5f), 0.55f);
        star(glow, m, muzzle, 5f * grow * widthScale, white, 0.8f, time * 1.5f);

        // Impact where the ray meets a surface.
        float impactAt = impact.distance(muzzle);
        if (struck && reach >= impactAt) {
            end.set(impact);
            halo(glow, m, end, 16f * grow * widthScale, shotBody, 0.5f);
            halo(glow, m, end, 7f * grow * widthScale, whiteMix(shotBody, 0.6f), 0.8f);
            halo(glow, m, end, 3f * grow * widthScale, white, 1f);
            for (int k = 0; k < 3; k++) {
                float ph = frac(time * 2.0f + k / 3f);
                billboardRing(glow, m, end, (2f + 16f * ph) * widthScale, 1.2f * (1f - ph) + 0.2f, shotBody,
                        0.8f * (1f - ph));
            }
            int strike = tick / 2;
            for (int i = 0; i < 8; i++) {
                float h1 = hash(strike, 100 + i), h2 = hash(strike, 200 + i), h3 = hash(strike, 300 + i);
                float ang = h1 * Mth.TWO_PI, spread = 0.6f + 1.2f * h2;
                b.set(u).mul(Mth.cos(ang) * spread).add(v.x * Mth.sin(ang) * spread, v.y * Mth.sin(ang) * spread,
                        v.z * Mth.sin(ang) * spread).sub(dir).normalize().mul((6f + 8f * h3) * grow * widthScale)
                        .add(end);
                bolt(glow, m, end, b, 1.6f, strike, 60 + i, 0.12f, 0.6f, shotBody, 0.9f);
            }
        }
    }

    /** Fills the tube's stations: dense through the nose, sparse after. Returns how many. */
    private int stations(float reach, float grow, float time) {
        int count = 0;
        float nose = Math.min(ExoBeamEntity.NOSE * widthScale, reach);
        for (int i = 0; i <= NOSE_RINGS; i++) {
            float t = i / (float) NOSE_RINGS;
            // Cluster stations near the muzzle, where the dome curves hardest.
            stations[count++] = nose * (1f - (1f - t) * (1f - t));
        }
        for (float s = nose + BODY_STEP; s < reach && count < MAX_RINGS - 1; s += BODY_STEP) {
            stations[count++] = s;
        }
        if (stations[count - 1] < reach - 0.01f) {
            stations[count++] = reach;
        }
        for (int j = 0; j < count; j++) {
            float s = stations[j];
            float ripple = 1f + 0.035f * Mth.sin(s * 0.25f - time * 22f);
            radii[j] = Math.max(0.05f, ExoBeamEntity.radiusAt(s, widthScale) * grow * ripple);
            float ds = 0.05f;
            slopes[j] = (ExoBeamEntity.radiusAt(s + ds, widthScale)
                    - ExoBeamEntity.radiusAt(Math.max(0f, s - ds), widthScale)) / (2f * ds) * grow;
        }
        return count;
    }

    private void ringPoint(Vector3f out, int j, int k, float scale) {
        float r = radii[j] * scale;
        float s = stations[j];
        out.set(dir).mul(s).add(muzzle).add(
                (u.x * cosT[k] + v.x * sinT[k]) * r,
                (u.y * cosT[k] + v.y * sinT[k]) * r,
                (u.z * cosT[k] + v.z * sinT[k]) * r);
    }

    /** Outward surface normal at station j, side k (tilted back through the nose). */
    private void ringNormal(Vector3f out, int j, int k) {
        out.set(u.x * cosT[k] + v.x * sinT[k], u.y * cosT[k] + v.y * sinT[k], u.z * cosT[k] + v.z * sinT[k])
                .sub(dir.x * slopes[j], dir.y * slopes[j], dir.z * slopes[j]).normalize();
    }

    /** One textured plasma quad, both windings. */
    private void plasma(VertexConsumer vc, Matrix4f m, Matrix3f n, int j, int k, float scroll, float scale,
                        float alpha, float twist) {
        float u0 = stations[j] / 48f - scroll, u1 = stations[j + 1] / 48f - scroll;
        float v0 = k * 2f / SIDES + twist + stations[j] * 0.004f, v1 = (k + 1) * 2f / SIDES + twist + stations[j] * 0.004f;
        float v0b = v0 + (stations[j + 1] - stations[j]) * 0.004f, v1b = v1 + (stations[j + 1] - stations[j]) * 0.004f;
        // Fade the body in from the muzzle so the dome's tip is pure light.
        float a0 = Math.min(1f, stations[j] / 6f) * alpha, a1 = Math.min(1f, stations[j + 1] / 6f) * alpha;
        ringPoint(a, j, k, scale);
        ringPoint(b, j + 1, k, scale);
        ringPoint(bu, j + 1, k + 1, scale);
        ringPoint(bv, j, k + 1, scale);
        texVertex(vc, m, n, a, u0, v0, a0);
        texVertex(vc, m, n, b, u1, v0b, a1);
        texVertex(vc, m, n, bu, u1, v1b, a1);
        texVertex(vc, m, n, bv, u0, v1, a0);
    }

    private static void texVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float tu, float tv, float alpha) {
        vc.vertex(m, p.x, p.y, p.z).color(1f, 1f, 1f, alpha).uv(tu, tv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 1f, 0f).endVertex();
    }

    /**
     * An additive shell over the tube at {@code scale} x radius. Brightness is {@code fresnel}-weighted
     * toward the silhouette ({@code power} 0 = uniform); with {@code fadeAt} > 0 it covers only the
     * first {@code fadeAt} blocks, fading out along them.
     */
    private void shell(VertexConsumer vc, Matrix4f m, int rings, float scale, float[] rgb, float alpha, float power,
                       float fadeAt) {
        for (int j = 0; j < rings - 1; j++) {
            if (fadeAt > 0f && stations[j] > fadeAt) {
                break;
            }
            for (int k = 0; k < SIDES; k++) {
                shellVertex(vc, m, j, k, scale, rgb, alpha, power, fadeAt);
                shellVertex(vc, m, j + 1, k, scale, rgb, alpha, power, fadeAt);
                shellVertex(vc, m, j + 1, k + 1, scale, rgb, alpha, power, fadeAt);
                shellVertex(vc, m, j, k + 1, scale, rgb, alpha, power, fadeAt);
            }
        }
    }

    private void shellVertex(VertexConsumer vc, Matrix4f m, int j, int k, float scale, float[] rgb, float alpha,
                             float power, float fadeAt) {
        ringPoint(side, j, k, scale);
        float weight = 1f;
        if (power > 0f) {
            ringNormal(bd, j, k);
            tmp.set(side).sub(cam);
            float len = tmp.length();
            float facing = len < 1.0e-4f ? 0f : Math.abs(bd.dot(tmp) / len);
            weight = (float) Math.pow(1f - facing, power);
        }
        if (fadeAt > 0f) {
            float t = Mth.clamp(stations[j] / fadeAt, 0f, 1f);
            weight *= 1f - t * t;
        }
        sides.vertex(vc, m, side.x, side.y, side.z, rgb[0], rgb[1], rgb[2], alpha * weight);
    }

    // Primitives ---------------------------------------------------------------------------------

    /** A jagged bolt from {@code from} to {@code to}: pinned ends, hashed kinks, glow plus hot core. */
    private void bolt(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float jag, int key, int salt,
                      float core, float glow, float[] rgb, float alpha) {
        bd.set(to).sub(from);
        if (bd.lengthSquared() < 1.0e-6f) {
            return;
        }
        bd.normalize();
        basis(bd, bu, bv);
        for (int j = 0; j < BOLT_POINTS; j++) {
            float t = j / (float) (BOLT_POINTS - 1);
            float env = Mth.sin(Mth.PI * t);
            float ang = hash(key, salt * 31 + j) * Mth.TWO_PI;
            float amt = jag * env * (0.4f + 0.6f * hash(key, salt * 57 + j));
            bolt[j].set(from).lerp(to, t)
                    .add(bu.x * Mth.cos(ang) * amt + bv.x * Mth.sin(ang) * amt,
                            bu.y * Mth.cos(ang) * amt + bv.y * Mth.sin(ang) * amt,
                            bu.z * Mth.cos(ang) * amt + bv.z * Mth.sin(ang) * amt);
        }
        for (int j = 0; j < BOLT_POINTS - 1; j++) {
            strip(vc, m, bolt[j], bolt[j + 1], glow, glow, rgb, rgb, alpha * 0.55f, alpha * 0.55f);
            strip(vc, m, bolt[j], bolt[j + 1], core, core, white, white, alpha, alpha);
        }
    }

    /** A light streak that fades toward both ends, like a lens flare. */
    private void pillar(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float width, float[] rgb, float alpha) {
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
            float e0 = 1f - Math.abs(t0 * 2f - 1f), e1 = 1f - Math.abs(t1 * 2f - 1f);
            e0 = e0 * e0;
            e1 = e1 * e1;
            bu.set(from).lerp(to, t0);
            bv.set(from).lerp(to, t1);
            strip(vc, m, bu, bv, width * (0.3f + 0.7f * e0), width * (0.3f + 0.7f * e1), rgb, rgb, alpha * e0, alpha * e1);
        }
    }

    /** Camera-facing soft line: bright centre, transparent edges. */
    private void strip(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, float w0, float w1,
                       float[] col0, float[] col1, float a0, float a1) {
        side.set(p1).sub(p0);
        float mx = (p0.x + p1.x) * 0.5f - cam.x, my = (p0.y + p1.y) * 0.5f - cam.y, mz = (p0.z + p1.z) * 0.5f - cam.z;
        side.cross(mx, my, mz);
        if (side.lengthSquared() < 1.0e-9f) {
            return;
        }
        side.normalize();
        float hw0 = w0 * 0.5f, hw1 = w1 * 0.5f;
        for (int e = -1; e <= 1; e += 2) {
            vertex(vc, m, p0.x, p0.y, p0.z, col0, a0);
            vertex(vc, m, p0.x + side.x * hw0 * e, p0.y + side.y * hw0 * e, p0.z + side.z * hw0 * e, col0, 0f);
            vertex(vc, m, p1.x + side.x * hw1 * e, p1.y + side.y * hw1 * e, p1.z + side.z * hw1 * e, col1, 0f);
            vertex(vc, m, p1.x, p1.y, p1.z, col1, a1);
        }
    }

    /** A soft annulus in the plane of {@code ax}/{@code ay}; {@code dashed} skips every other segment. */
    private void ring(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f ay, float r, float w,
                      float[] rgb, float alpha, int segs, boolean dashed, float spin) {
        for (int i = 0; i < segs; i++) {
            if (dashed && (i & 1) == 1) {
                continue;
            }
            float t0 = spin + Mth.TWO_PI * i / segs, t1 = spin + Mth.TWO_PI * (i + 1) / segs;
            annulus(vc, m, c, ax, ay, Mth.cos(t0), Mth.sin(t0), Mth.cos(t1), Mth.sin(t1), r, w, rgb, alpha);
        }
    }

    private void billboardRing(VertexConsumer vc, Matrix4f m, Vector3f c, float r, float w, float[] rgb, float alpha) {
        bu.set(1f, 0f, 0f).rotate(camera);
        bv.set(0f, 1f, 0f).rotate(camera);
        ring(vc, m, c, bu, bv, r, w, rgb, alpha, 40, false, 0f);
    }

    private void annulus(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f ay, float x0, float y0,
                         float x1, float y1, float r, float w, float[] rgb, float alpha) {
        if (r <= 0.001f || w <= 0f || alpha <= 0f) {
            return;
        }
        float inner = safeInnerRadius(r, w);
        for (int band = 0; band < 2; band++) {
            float ra = band == 0 ? inner : r, rb = band == 0 ? r : r + w;
            float aa = band == 0 ? 0f : alpha, ab = band == 0 ? alpha : 0f;
            planeVertex(vc, m, c, ax, ay, x0 * ra, y0 * ra, rgb, aa);
            planeVertex(vc, m, c, ax, ay, x0 * rb, y0 * rb, rgb, ab);
            planeVertex(vc, m, c, ax, ay, x1 * rb, y1 * rb, rgb, ab);
            planeVertex(vc, m, c, ax, ay, x1 * ra, y1 * ra, rgb, aa);
        }
    }

    static float safeInnerRadius(float radius, float width) {
        return Math.max(0f, radius - width);
    }

    private void planeVertex(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f ay, float x, float y,
                             float[] rgb, float alpha) {
        vertex(vc, m, c.x + ax.x * x + ay.x * y, c.y + ax.y * x + ay.y * y, c.z + ax.z * x + ay.z * y, rgb, alpha);
    }

    private void halo(VertexConsumer vc, Matrix4f m, Vector3f c, float radius, float[] rgb, float alpha) {
        float safeAlpha = alpha * haloCameraGain(radius, c.distance(cam));
        if (safeAlpha <= 0.001f) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            float t0 = Mth.TWO_PI * i / 24, t1 = Mth.TWO_PI * (i + 1) / 24;
            vertex(vc, m, c.x, c.y, c.z, rgb, safeAlpha);
            billboard(vc, m, c, Mth.cos(t0) * radius, Mth.sin(t0) * radius, rgb, 0f);
            billboard(vc, m, c, Mth.cos(t1) * radius, Mth.sin(t1) * radius, rgb, 0f);
            vertex(vc, m, c.x, c.y, c.z, rgb, safeAlpha);
        }
    }

    public static float haloCameraGain(float radius, float cameraDistance) {
        if (radius <= 0f) {
            return 1f;
        }
        return Mth.clamp(cameraDistance / Math.max(1f, radius * 0.65f), 0f, 1f);
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

    // Helpers ------------------------------------------------------------------------------------

    private static void basis(Vector3f d, Vector3f outU, Vector3f outV) {
        boolean steep = Math.abs(d.y) >= 0.95f;
        outU.set(d).cross(steep ? 1f : 0f, steep ? 0f : 1f, 0f).normalize();
        outV.set(d).cross(outU).normalize();
    }

    private static void crimson(float t, float[] out) {
        float x = frac(t) * CRIMSON.length;
        int i = (int) x;
        float f = x - i;
        f = f * f * (3f - 2f * f);
        float[] p = CRIMSON[i % CRIMSON.length], q = CRIMSON[(i + 1) % CRIMSON.length];
        out[0] = p[0] + (q[0] - p[0]) * f;
        out[1] = p[1] + (q[1] - p[1]) * f;
        out[2] = p[2] + (q[2] - p[2]) * f;
    }

    private float[] whiteMix(float[] rgb, float amount) {
        mixed[0] = rgb[0] + (1f - rgb[0]) * amount;
        mixed[1] = rgb[1] + (1f - rgb[1]) * amount;
        mixed[2] = rgb[2] + (1f - rgb[2]) * amount;
        return mixed;
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    private static float backOut(float t) {
        float s = 1.7f;
        t -= 1f;
        return t * t * ((s + 1f) * t + s) + 1f;
    }

    private static float hash(int a, int b) {
        int x = a * 73856093 ^ b * 19349663;
        x ^= x >>> 13;
        x *= 0x5bd1e995;
        x ^= x >>> 15;
        return (x & 0xFFFFFF) / 16777216f;
    }

    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] rgb, float alpha) {
        sides.vertex(vc, m, x, y, z, rgb[0], rgb[1], rgb[2], alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(ExoBeamEntity beam) {
        return PLASMA;
    }
}
