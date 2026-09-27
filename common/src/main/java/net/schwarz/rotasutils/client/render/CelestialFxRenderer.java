package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.CelestialFxEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CelestialFxRenderer extends EntityRenderer<CelestialFxEntity> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation RUNES = tex("sigil_runes");
    private static final ResourceLocation SEAL = tex("sigil_star");
    protected static final ResourceLocation FLARE = tex("flare");
    protected static final ResourceLocation GLOW = tex("glow");
    protected static final ResourceLocation STREAK = tex("streak");
    /** Tileable soft energy clouds, scrolled up light pillars (scripts/gen_celestial_textures.py). */
    protected static final ResourceLocation NEBULA = tex("nebula");
    /** A shock ring with a crisp leading edge and a soft wake inside it. */
    protected static final ResourceLocation SHOCK = tex("shock");
    /** A free-floating, uneven puff of energy (soft all round, unlike the tiling nebula). */
    protected static final ResourceLocation CLOUD = tex("cloud");
    protected static final float TAU = (float) (Math.PI * 2);
    protected static final Vec3 X = new Vec3(1, 0, 0);
    protected static final Vec3 Z = new Vec3(0, 0, 1);

    protected static final float[] WHITE = {1, 1, 1};
    /*
     * One purple family, hue-shifted: darker runs cooler and more saturated (deep violet), brighter runs
     * warmer (lilac toward white). Rose is the single accent - sigils, crowns, one ring - never alternated
     * element by element. Every celestial texture is white, so these vertex colours are the whole palette.
     */
    private static final float[] PURPLE = {0.58f, 0.22f, 1.00f};
    private static final float[] LILAC = {0.86f, 0.62f, 1.00f};
    private static final float[] ROSE = {1.00f, 0.56f, 0.86f};
    private static final float[] DEEP = {0.20f, 0.04f, 0.42f};
    private static final float[] VOID = {0.06f, 0.02f, 0.16f};

    private final BothSides quads = new BothSides();
    /** Solid, lit 3D geometry (tubes, spheres) with fresnel rims; see Mesh3D. */
    protected final Mesh3D mesh = new Mesh3D();
    protected MultiBufferSource buffers;
    protected VertexConsumer vc;
    protected Matrix4f m;
    protected Matrix3f normal;
    protected double ox, oy, oz;
    protected Vec3 cam;
    protected Vec3 camRight;
    protected Vec3 camUp;

    public CelestialFxRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    protected static ResourceLocation tex(String name) {
        return new ResourceLocation("rotasutils", "textures/vfx/celestial/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(CelestialFxEntity entity) {
        return NONE;
    }

    @Override
    public boolean shouldRender(CelestialFxEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(CelestialFxEntity fx, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!begin(fx, pt, pose, buffers)) return;
        float age = fx.tickCount + pt;
        switch (fx.kind()) {
            case CelestialFxEntity.BOLT -> bolt(fx, age);
            case CelestialFxEntity.IMPACT -> impact(fx, age);
            case CelestialFxEntity.NOVA -> nova(fx, age);
            case CelestialFxEntity.RAY -> ray(fx, age, pt);
            case CelestialFxEntity.LANCE -> lance(fx, age);
            case CelestialFxEntity.COMET -> comet(fx, age, pt);
            case CelestialFxEntity.BIND -> bind(fx, age, pt);
            case CelestialFxEntity.WARD -> ward(fx, age, pt);
            case CelestialFxEntity.CHARGE -> charge(fx, age, pt);
            case CelestialFxEntity.SUPERNOVA -> supernova(fx, age);
            default -> { }
        }
    }

    /** Captures camera basis, matrices and the entity's interpolated position for this frame. */
    protected boolean begin(CelestialFxEntity fx, float pt, PoseStack pose, MultiBufferSource buffers) {
        Camera camera = entityRenderDispatcher.camera;
        if (camera == null) return false;
        cam = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        camRight = new Vec3(-left.x(), -left.y(), -left.z());
        camUp = new Vec3(up.x(), up.y(), up.z());
        this.buffers = buffers;
        m = pose.last().pose();
        normal = pose.last().normal();
        ox = Mth.lerp(pt, fx.xo, fx.getX());
        oy = Mth.lerp(pt, fx.yo, fx.getY());
        oz = Mth.lerp(pt, fx.zo, fx.getZ());
        mesh.begin(buffers, m, normal, ox, oy, oz, cam);
        return true;
    }

    // ---- Celestial spells ----

    /**
     * Astral Bolt: a comet of painted light. The head is the one hot point - a white star in a purple
     * cloud; the body is nebula under flowing wisps, streaming back and fading to nothing, with sparks
     * spiralling off it.
     */
    private void bolt(CelestialFxEntity fx, float age) {
        Vec3 head = fx.boltPos(age);
        Vec3 dir = fx.targetPos();
        Vec3 side = perpendicular(dir);
        Vec3 up = dir.cross(side);
        float appear = smooth(0, 3, age);
        float pulse = 0.85f + 0.15f * Mth.sin(age * 0.8f);
        float length = Math.min(9f, 1f + age * 2.2f);
        Vec3 tail = head.subtract(dir.scale(length));
        lightBeam(NEBULA, head, tail, 0.6f, 0.05f, PURPLE, 0.55f * appear, age * 0.12f, 1.5f, true);
        lightBeam(STREAK, head, tail, 0.4f, 0.03f, LILAC, 0.85f * appear, age * 0.2f, 1.2f, true);
        lightBeam(STREAK, head, tail, 0.13f, 0.01f, WHITE, appear, age * 0.3f, 1f, true);
        for (int i = 0; i < 6; i++) {
            float ph = frac(age * 0.12f + i / 6f);
            float ang = age * 0.35f + i * TAU / 6;
            Vec3 p = head.subtract(dir.scale(ph * length))
                    .add(side.scale(Mth.cos(ang) * 0.6f * (1 - ph))).add(up.scale(Mth.sin(ang) * 0.6f * (1 - ph)));
            flare(p, 0.15f + 0.5f * (1 - ph), ang, i % 3 == 0 ? ROSE : LILAC, appear * (1 - ph));
        }
        sprite(CLOUD, head, camRight, camUp, 1.3f * pulse, PURPLE, 0.6f * appear, age * 0.05f);
        star(head, side, up, 1.5f * pulse, age, appear);
        glow(head, 3.8f, PURPLE, 0.3f * appear);
    }

    /** A hit: a white flash, a shock sphere, clouds of light blooming and dispersing, rays thrown out. */
    private void impact(CelestialFxEntity fx, float age) {
        Vec3 c = here();
        float t = Mth.clamp(age / fx.life(), 0, 1);
        float r = fx.radius();
        float pop = easeOut(Math.min(1, age / 5f));
        float fade = 1 - smooth(0.35f, 1, t);
        float flash = 1 - smooth(0, 4, age);
        sprite(SHOCK, c, camRight, camUp, r * (0.6f + 3.2f * easeOut(t)), LILAC, 0.9f * (1 - t) * (1 - t), 0);
        sprite(CLOUD, c, camRight, camUp, r * (1.2f + 1.4f * pop), PURPLE, 0.55f * fade, fx.seedValue() * 0.1f + age * 0.03f);
        sprite(CLOUD, c, camRight, camUp, r * (0.7f + 0.8f * pop), ROSE, 0.35f * fade, -age * 0.05f);
        glow(c, r * (1.5f + 2f * flash), WHITE, 0.9f * flash);
        star(c, camRight, camUp, r * (1.1f + 1.4f * easeOut(t)), age, fade);
        for (int i = 0; i < 8; i++) {
            Vec3 tip = c.add(sphereDir(fx.seedValue(), i).scale(r * (0.8f + 2.6f * easeOut(t))));
            streakBeam(c, tip, 0.14f * r * (1 - t), 0, LILAC, 0.7f * fade, -age * 0.2f);
        }
    }

    /**
     * Nova Burst: the sigil opens under the caster with an overshoot, shock rings race out over the
     * ground, a shock sphere bursts off a white star above, and columns of light punch up round the
     * rim and thin away while motes drift up.
     */
    private void nova(CelestialFxEntity fx, float age) {
        Vec3 center = here().add(0, 0.07, 0);
        float t = Mth.clamp(age / fx.life(), 0, 1);
        float fade = 1 - smooth(0.55f, 1, t);
        float radius = fx.radius();
        float open = backOut(Math.min(1, age / 7f));
        observatory(center, radius * 0.8f * open, age, fade);
        for (int i = 0; i < 3; i++) {
            float wave = Mth.clamp((age - i * 3f) / 14f, 0, 1);
            if (wave <= 0 || wave >= 1) continue;
            planeTex(SHOCK, center.add(0, 0.05 + i * 0.01, 0), X, Z, radius * (0.2f + 1.1f * easeOut(wave)),
                    i == 1 ? ROSE : LILAC, 0.95f * (1 - wave) * (1 - wave) * fade, i);
        }
        float flash = 1 - smooth(0, 7, age);
        Vec3 crown = center.add(0, 1.4, 0);
        float burst = Math.min(1, age / 8f);
        if (burst < 1) {
            sprite(SHOCK, crown, camRight, camUp, radius * (0.3f + 1.4f * easeOut(burst)), WHITE,
                    (float) Math.pow(1 - burst, 1.5), 0);
        }
        sprite(CLOUD, crown, camRight, camUp, 2f + 2f * flash, PURPLE, 0.5f * fade, age * 0.03f);
        star(crown, camRight, camUp, 2.2f + flash * 3, age, fade * (0.35f + flash * 0.65f));
        float h = (3.5f + 4f * flash) * backOut(Math.min(1, age / 5f)) * fade;
        for (int i = 0; i < 8; i++) {
            float angle = i * TAU / 8 + age * 0.015f;
            Vec3 foot = center.add(Mth.cos(angle) * radius * 0.68f, 0, Mth.sin(angle) * radius * 0.68f);
            lightPillar(STREAK, foot, h, 0.5f * fade, 0.1f, PURPLE, 0.8f * fade, age * 0.12f, 1.5f);
            lightPillar(STREAK, foot, h * 0.9f, 0.13f, 0.02f, WHITE, 0.9f * fade, age * 0.2f, 1.2f);
            flare(foot.add(0, h, 0), 0.7f, angle, LILAC, fade);
        }
        for (int i = 0; i < 14; i++) {
            float ph = frac(age * 0.04f + i / 14f);
            Vec3 d = sphereDir(fx.seedValue(), i);
            Vec3 p = center.add(d.x * radius * 0.6f, ph * 4, d.z * radius * 0.6f);
            flare(p, 0.1f + 0.35f * (1 - ph), i, LILAC, fade * (1 - ph));
        }
    }

    /**
     * Prism Ray. A camera-facing strip collapses to a line from the caster's own eye, which is where
     * it is seen most; so the beam is a real tube: an indigo haze shell, an azure body, a white-hot
     * fresnel rim that makes the silhouette burn from any angle, and a thin white core. Two prismatic
     * rails spiral round it in turning spectrum colours - it is a prism ray - and rings race down it.
     * It punches out with an overshoot and a flash, and lands in a splash of light with a shock ring.
     */
    private void ray(CelestialFxEntity fx, float age, float pt) {
        Entity owner = fx.owner();
        if (owner == null) return;
        Vec3 look = owner.getViewVector(pt);
        Vec3 eye = owner.getEyePosition(pt);
        Vec3 start = eye.add(look.scale(1)).add(0, -0.2, 0);
        Vec3 far = eye.add(look.scale(fx.radius()));
        HitResult hit = owner.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        Vec3 axis = end.subtract(start);
        float total = (float) axis.length();
        if (total < 0.1f) return;
        Vec3 direction = axis.scale(1 / total);
        Vec3 side = perpendicular(direction);
        Vec3 up = direction.cross(side);
        float grow = backOut(Math.min(1, age / 6f)) * (0.97f + 0.03f * Mth.sin(age * 3.5f));
        float length = Math.min(total, 1 + age * 14);
        Vec3 tip = start.add(direction.scale(length));
        float r = 0.55f * grow;

        // A faint shaded tube keeps its body seen down its own length; painted layers carry the look.
        glowTube(start, tip, r * 0.6f, r * 2.3f, DEEP, 0.18f, 0.8f);
        glowTube(start, tip, r * 0.5f, r * 1.03f, WHITE, 0.4f, 3.6f);
        lightBeam(NEBULA, start, tip, r * 1.8f, r * 1.8f, PURPLE, 0.55f, age * 0.08f, length / 6f, false);
        lightBeam(STREAK, start, tip, r * 1.1f, r * 1.1f, LILAC, 0.85f, age * 0.2f, length / 8f, false);
        lightBeam(STREAK, start, tip, r * 0.35f, r * 0.35f, WHITE, 0.95f, age * 0.35f, length / 10f, false);

        // Prismatic rails: two spirals whose colour runs through the spectrum along the beam.
        float[] hue = new float[3];
        for (int rail = 0; rail < 2; rail++) {
            Vec3 previous = null;
            int n = Math.max(2, (int) (length * 2));
            for (int i = 0; i <= n; i++) {
                float d = length * i / n;
                float phase = d * 1.1f - age * 0.35f + rail * Mth.PI;
                Vec3 point = start.add(direction.scale(d))
                        .add(side.scale(Mth.cos(phase) * r * 1.6f))
                        .add(up.scale(Mth.sin(phase) * r * 1.6f));
                if (previous != null) {
                    // Purple flowing into rose along the beam, not a rainbow: one colour family.
                    float k = 0.5f + 0.5f * Mth.sin(d * 0.25f - age * 0.3f + rail * Mth.PI);
                    for (int ch = 0; ch < 3; ch++) hue[ch] = PURPLE[ch] + (ROSE[ch] - PURPLE[ch]) * k;
                    streakSegment(previous, point, 0.13f, hue, 0.8f * grow, d * 0.1f, d * 0.1f + 0.4f);
                }
                previous = point;
            }
        }
        // Shock rings racing away from the caster down the beam.
        for (float d = (age * 1.2f) % 4f; d < length; d += 4f) {
            planeTex(SHOCK, start.add(direction.scale(d)), side, up, r * 2.4f, LILAC,
                    0.6f * grow * (1 - d / Math.max(1, length) * 0.7f), age * 0.1f + d);
        }
        // Muzzle: the lens the ray comes out of, and a flash as it erupts.
        iris(start, side, up, 1.5f * grow, age, Math.min(1, grow));
        float erupt = 1 - smooth(0, 6, age);
        if (erupt > 0) {
            glow(start, 6f * erupt + 1f, WHITE, 0.8f * erupt);
            ring(start, side, up, 1f + 6f * (1 - erupt), 0.4f, LILAC, 0.8f * erupt, 40, false, 0);
        }
        if (length >= total - 0.02f) {
            float throb = 0.85f + 0.15f * Mth.sin(age * 1.7f);
            sprite(CLOUD, end, camRight, camUp, 3.2f * throb, PURPLE, 0.6f, age * 0.06f);
            sprite(CLOUD, end, camRight, camUp, 2f * throb, ROSE, 0.35f, -age * 0.08f);
            glow(end, 5.5f * throb, PURPLE, 0.55f);
            glow(end, 2.2f, WHITE, 0.9f);
            star(end, camRight, camUp, 2.6f * throb, age, 0.9f);
            for (int k = 0; k < 3; k++) {
                float ph = frac(age * 0.09f + k / 3f);
                ring(end, camRight, camUp, 0.8f + 4.5f * ph, 0.25f * (1 - ph) + 0.05f, LILAC, 0.7f * (1 - ph),
                        36, false, 0);
            }
        }
    }

    /**
     * An additive tube from {@code a} to {@code b}, radius {@code r0} at {@code a} widening to {@code r1}
     * at {@code b}, shaded fresnel-style: bright where the surface grazes the view, faint face-on.
     * {@code power} sharpens that - high for a thin burning rim, low for an even haze. Unlike a
     * camera-facing strip it keeps its width looking straight down it, so beams read from the caster.
     */
    protected void glowTube(Vec3 a, Vec3 b, float r0, float r1, float[] col, float alpha, float power) {
        if (alpha <= 0.003f) return;
        Vec3 axis = b.subtract(a);
        double length = axis.length();
        if (length < 1e-4) return;
        Vec3 dir = axis.scale(1 / length);
        Vec3 u = perpendicular(dir), w = dir.cross(u);
        int sides = 28;
        int rings = Math.max(2, Math.min(24, (int) (length / 2) + 2));
        additive();
        for (int j = 0; j < rings; j++) {
            float t0 = j / (float) rings, t1 = (j + 1) / (float) rings;
            Vec3 c0 = a.add(axis.scale(t0)), c1 = a.add(axis.scale(t1));
            float ra = r0 + (r1 - r0) * t0, rb = r0 + (r1 - r0) * t1;
            for (int k = 0; k < sides; k++) {
                float g0 = k * TAU / sides, g1 = (k + 1) * TAU / sides;
                Vec3 n0 = u.scale(Mth.cos(g0)).add(w.scale(Mth.sin(g0)));
                Vec3 n1 = u.scale(Mth.cos(g1)).add(w.scale(Mth.sin(g1)));
                tubeVertex(c0.add(n0.scale(ra)), n0, col, alpha, power);
                tubeVertex(c1.add(n0.scale(rb)), n0, col, alpha, power);
                tubeVertex(c1.add(n1.scale(rb)), n1, col, alpha, power);
                tubeVertex(c0.add(n1.scale(ra)), n1, col, alpha, power);
            }
        }
    }

    private void tubeVertex(Vec3 p, Vec3 n, float[] col, float alpha, float power) {
        Vec3 view = cam.subtract(p);
        double len = view.length();
        float facing = len < 1e-6 ? 1f : (float) Math.abs(n.dot(view) / len);
        v(p, col, alpha * (float) Math.pow(1f - facing, power));
    }

    private void lance(CelestialFxEntity fx, float age) {
        float progress = fx.lanceU(age);
        Vec3 head = fx.lancePos(progress);
        Vec3 ahead = fx.lancePos(Math.min(1, progress + 0.02f));
        Vec3 direction = progress < 0.99f ? ahead.subtract(head)
                : fx.targetPos().subtract(fx.lancePos(0.97f));
        if (direction.lengthSqr() < 1e-6) direction = fx.targetPos().subtract(fx.originPos());
        if (direction.lengthSqr() < 1e-6) return;
        direction = direction.normalize();
        Vec3 side = perpendicular(direction);
        Vec3 up = direction.cross(side);
        float appear = smooth(0, 5, age);
        Vec3 pommel = head.subtract(direction.scale(2.4));
        Vec3 tip = head.add(direction.scale(1.2));
        lightBeam(NEBULA, tip, pommel, 0.7f, 0.08f, PURPLE, 0.55f * appear, age * 0.1f, 1f, true);
        lightBeam(STREAK, tip, pommel, 0.5f, 0.06f, ROSE, 0.9f * appear, age * 0.17f, 1f, true);
        lightBeam(STREAK, tip, pommel, 0.16f, 0.02f, WHITE, appear, age * 0.25f, 1f, true);
        sprite(CLOUD, tip, camRight, camUp, 1.1f, PURPLE, 0.5f * appear, age * 0.05f);
        for (int i = -1; i <= 1; i += 2) {
            Vec3 wing = head.subtract(direction.scale(0.6)).add(side.scale(i * 0.66f));
            strip(tip, wing, 0.18f, 0, PURPLE, WHITE, appear * 0.8f, 0);
        }
        star(tip, side, up, 0.9f, age, appear);
        if (age < fx.delayTicks()) {
            iris(pommel, side, up, 1.1f * appear, age, appear);
            return;
        }
        for (int i = 1; i <= 8; i++) {
            float old = Math.max(0, progress - i * 0.035f);
            Vec3 mote = fx.lancePos(old).subtract(direction.scale(1.2));
            flare(mote, 0.56f * (1 - i / 10f), i + age * 0.1f,
                    LILAC, appear * (1 - i / 10f));
        }
    }

    private void comet(CelestialFxEntity fx, float age, float pt) {
        Entity owner = fx.owner();
        if (owner == null) return;
        float fade = 1 - smooth(fx.life() - 5, fx.life(), age);
        Vec3 head = owner.getPosition(pt).add(0, owner.getBbHeight() * 0.55, 0);
        Vec3 previous = head;
        for (int i = 0; i < fx.trailCount; i++) {
            Vec3 point = fx.trail[i];
            if (point == null) break;
            float width = 1 - i / (float) fx.trail.length;
            strip(previous, point, 1.8f * width, 1.5f * width,
                    DEEP, PURPLE, 0.22f * width * fade, 0.2f * width * fade);
            streakSegment(previous, point, 0.68f * width, LILAC, 0.7f * width * fade,
                    i * 0.24f - age * 0.23f, (i + 1) * 0.24f - age * 0.23f);
            streakSegment(previous, point, 0.15f * width, WHITE, width * fade,
                    i * 0.24f - age * 0.34f, (i + 1) * 0.24f - age * 0.34f);
            if (i % 3 == 0) {
                sprite(CLOUD, point, camRight, camUp, 1.6f * width + 0.4f, PURPLE, 0.45f * width * fade, age * 0.04f + i);
            }
            if ((i & 1) == 0) {
                Vec3 shard = point.add(sphereDir(fx.seedValue(), i).scale(0.4 + 0.6 * (1 - width)));
                flare(shard, 0.62f * width, age * 0.1f + i, ROSE, width * fade);
            }
            previous = point;
        }
        Vec3 forward = owner.getViewVector(pt);
        Vec3 side = perpendicular(forward);
        sprite(CLOUD, head, camRight, camUp, 2.4f, PURPLE, 0.55f * fade, age * 0.05f);
        star(head, side, forward.cross(side), 2f, age, fade);
        glow(head, 4f, LILAC, 0.42f * fade);
    }

    private void bind(CelestialFxEntity fx, float age, float pt) {
        Vec3 center = here().add(0, 0.06, 0);
        float fade = smooth(0, 8, age) * (1 - smooth(fx.life() - 10, fx.life(), age));
        float radius = fx.radius() * backOut(Math.min(1, age / 10f));
        observatory(center, radius, age, fade);
        for (int i = 0; i < 6; i++) {
            float angle = age * 0.012f + i * TAU / 6;
            Vec3 base = center.add(Mth.cos(angle) * radius * 0.75f, 0,
                    Mth.sin(angle) * radius * 0.75f);
            Vec3 crown = base.add(0, 2.5 + 0.4 * Mth.sin(age * 0.13f + i), 0);
            float columnHeight = (float) (crown.y - base.y) * backOut(Math.min(1, age / 8f));
            lightPillar(STREAK, base, columnHeight, 0.35f, 0.06f, PURPLE, 0.75f * fade, age * 0.1f, 1.2f);
            lightPillar(STREAK, base, columnHeight, 0.1f, 0.02f, WHITE, 0.85f * fade, age * 0.18f, 1f);
            flare(crown, 0.75f, age * 0.1f + i, WHITE, fade);
        }
        Entity owner = fx.owner();
        List<Vec3> targets = new ArrayList<>();
        for (LivingEntity entity : fx.level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(fx.radius(), 3, fx.radius()),
                entity -> entity != owner && entity.isAlive())) {
            if (targets.size() >= 24) break;
            if (entity.position().distanceToSqr(center) > fx.radius() * fx.radius() * 1.2) continue;
            targets.add(entity.getPosition(pt).add(0, entity.getBbHeight() + 0.4, 0));
        }
        targets.sort((a, b) -> Double.compare(Math.atan2(a.z - center.z, a.x - center.x),
                Math.atan2(b.z - center.z, b.x - center.x)));
        for (int i = 0; i < targets.size(); i++) {
            Vec3 target = targets.get(i);
            star(target, camRight, camUp, 0.8f, age + i, fade);
            streakBeam(target, new Vec3(target.x, center.y, target.z), 0.09f, 0.09f,
                    PURPLE, 0.65f * fade, -age * 0.12f);
            if (targets.size() > 1) {
                Vec3 next = targets.get((i + 1) % targets.size());
                streakBeam(target, next, 0.07f, 0.07f, ROSE, 0.5f * fade,
                        -age * 0.08f);
            }
        }
    }

    private void ward(CelestialFxEntity fx, float age, float pt) {
        Entity owner = fx.owner();
        if (owner == null) return;
        Vec3 center = owner.getPosition(pt).add(0, owner.getBbHeight() * 0.55, 0);
        float fade = smooth(0, 7, age) * (1 - smooth(fx.life() - 12, fx.life(), age));
        float radius = 1.4f * backOut(Math.min(1, age / 8f));
        for (int layer = 0; layer < 3; layer++) {
            float angle = age * (layer % 2 == 0 ? 0.025f : -0.035f) + layer * 2.1f;
            float tilt = 0.55f + layer * 0.3f;
            Vec3 u = new Vec3(Mth.cos(angle), 0, Mth.sin(angle));
            Vec3 v = new Vec3(-Mth.sin(angle) * Mth.cos(tilt), Mth.sin(tilt),
                    Mth.cos(angle) * Mth.cos(tilt));
            ring(center, u, v, radius + layer * 0.18f, 0.07f,
                    layer == 1 ? ROSE : LILAC, 0.6f * fade, 48, true, age * 0.035f);
            for (int i = 0; i < 4; i++) {
                float phase = age * 0.045f * (layer + 1) + i * TAU / 4;
                Vec3 point = center.add(u.scale(Mth.cos(phase) * radius))
                        .add(v.scale(Mth.sin(phase) * radius));
                flare(point, 0.55f, phase, i == 0 ? ROSE : LILAC, fade);
            }
        }
        sprite(CLOUD, center, camRight, camUp, 2.6f, PURPLE, 0.28f * fade, age * 0.02f);
        sprite(CLOUD, center, camRight, camUp, 1.8f, ROSE, 0.16f * fade, -age * 0.03f);
        for (int i = 0; i < 8; i++) {
            float ph = frac(age * 0.03f + i / 8f);
            float ang = i * TAU / 8 + age * 0.02f;
            Vec3 p = center.add(Mth.cos(ang) * radius * 1.1f, -1 + ph * 2.6f, Mth.sin(ang) * radius * 1.1f);
            flare(p, 0.1f + 0.3f * (1 - ph), ang, LILAC, fade * (1 - ph) * smooth(0, 0.15f, ph));
        }
        star(center, camRight, camUp, 0.85f, age, 0.42f * fade);
        glow(center, 3.5f, DEEP, 0.15f * fade);
    }

    private void charge(CelestialFxEntity fx, float age, float pt) {
        Entity owner = fx.owner();
        if (owner == null) return;
        float phase = Mth.clamp(age / fx.life(), 0, 1);
        Vec3 feet = owner.getPosition(pt).add(0, 0.07, 0);
        Vec3 look = owner.getViewVector(pt);
        Vec3 focus = owner.getEyePosition(pt).add(look.scale(1.3)).add(0, 0.25, 0);
        Vec3 side = perpendicular(look);
        Vec3 up = look.cross(side);
        observatory(feet, 1.2f + 3.1f * easeOut(phase), age, 0.3f + 0.7f * phase);
        iris(focus, side, up, 0.5f + 1.8f * phase, age, 0.4f + 0.6f * phase);
        for (int i = 0; i < 18; i++) {
            float travel = frac(age * 0.035f + i / 18f);
            Vec3 from = focus.add(sphereDir(fx.seedValue(), i).scale(5 * (1 - travel) * (1 - travel)));
            streakBeam(from, focus, 0.07f, 0, PURPLE,
                    0.38f * travel, -age * 0.15f);
            flare(from, 0.5f, i, LILAC, travel * 0.75f);
        }
        sprite(CLOUD, focus, camRight, camUp, 0.6f + 2.4f * phase * phase, PURPLE, 0.3f + 0.4f * phase, age * 0.08f);
        float pulse = frac(age / 10f);
        planeTex(SHOCK, feet.add(0, 0.03, 0), X, Z, 1.5f + 3.5f * easeOut(pulse), LILAC,
                0.7f * (1 - pulse) * (1 - pulse) * phase, 0);
        star(focus, side, up, 0.6f + 2.5f * phase * phase, age, 0.3f + 0.7f * phase);
        glow(focus, 1.5f + 5f * phase * phase, LILAC, 0.35f + 0.5f * phase);
    }

    /**
     * Supernova. Impact: a white flash and a shock sphere bursting off the crown, painted shock rings
     * racing out over the ground on an ease-out, and a column of light that punches up with an
     * overshoot. Sustain: the column is painted energy - nebula clouds and flowing wisps scrolling
     * upward - fading to nothing at its top, with two spirals of stars climbing it. Dissipation: the
     * column thins, the stars rise away and everything desaturates out.
     */
    private void supernova(CelestialFxEntity fx, float age) {
        Vec3 center = here().add(0, 0.08, 0);
        float t = Mth.clamp(age / fx.life(), 0, 1);
        float fade = 1 - smooth(0.62f, 1, t);
        float flash = smooth(0, 3, age) * (1 - smooth(3, 10, age));
        float radius = fx.radius();
        observatory(center, radius * 0.88f, age, fade);

        // Shock rings racing out over the ground, staggered, eased, dying as they go.
        for (int i = 0; i < 3; i++) {
            float wave = Mth.clamp((age - 2 - i * 5f) / 18f, 0, 1);
            if (wave <= 0 || wave >= 1) continue;
            float left = (1 - wave) * (1 - wave);
            planeTex(SHOCK, center.add(0, 0.05 + i * 0.01, 0), X, Z, radius * (0.3f + 1.9f * easeOut(wave)),
                    i == 1 ? ROSE : LILAC, 0.95f * left * fade, i * 0.7f);
        }
        Vec3 crown = center.add(0, 2.2, 0);
        float burst = Mth.clamp(age / 8f, 0, 1);
        if (burst < 1) {
            sprite(SHOCK, crown, camRight, camUp, radius * (0.4f + 2.6f * easeOut(burst)), WHITE,
                    (float) Math.pow(1 - burst, 1.5), 0);
        }
        star(crown, camRight, camUp, radius * (0.42f + flash * 0.9f), age, fade * (0.4f + flash * 0.6f));
        glow(crown, radius * (2f + flash), LILAC, 0.45f * fade);

        // The column: punches up with an overshoot, breathes, thins as it dies.
        float height = 42 * backOut(Math.min(1, age / 6f));
        float thin = fade * (1 + 0.5f * flash) * (0.94f + 0.06f * Mth.sin(age * 0.6f));
        lightPillar(NEBULA, center, height, radius * 0.36f * thin, radius * 0.12f * thin, PURPLE, 0.6f * fade,
                age * 0.05f, 3f);
        lightPillar(STREAK, center, height, radius * 0.24f * thin, radius * 0.06f * thin, LILAC, 0.85f * fade,
                age * 0.09f, 2.5f);
        lightPillar(STREAK, center, height, radius * 0.07f * thin, radius * 0.02f, WHITE, 0.95f * fade,
                age * 0.14f, 2f);

        // Two spirals of stars climbing the column.
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < 10; i++) {
                float ph = frac(age * 0.025f + i / 10f);
                float ang = arm * Mth.PI + ph * TAU * 2 + age * 0.05f;
                float r = radius * 0.45f * thin * (1 - ph * 0.6f);
                Vec3 p = center.add(Mth.cos(ang) * r, ph * height, Mth.sin(ang) * r);
                float life = (1 - ph) * smooth(0, 0.08f, ph);
                flare(p, 0.5f + 0.9f * (1 - ph), ang, arm == 0 ? LILAC : ROSE, fade * life);
            }
        }
        // Motes drifting up and away from the blast.
        for (int i = 0; i < 16; i++) {
            Vec3 direction = sphereDir(fx.seedValue(), i);
            if (direction.y < 0) direction = new Vec3(direction.x, -direction.y, direction.z);
            Vec3 point = center.add(direction.scale(radius * (0.4f + 0.9f * easeOut(t)))).add(0, t * 4, 0);
            flare(point, 0.5f + flash, i + age * 0.08f, LILAC, fade * (1 - t * 0.5f));
        }
    }

    /**
     * A column of painted light from {@code base} up {@code height}: three crossed vertical planes and one
     * facing the camera, so it has body from every side without the faceted look of a shaded tube. The
     * texture runs up the column (U along the height) and scrolls with {@code scroll}; alpha fades in at
     * the foot and out to nothing at the top, and the width tapers from {@code r0} to {@code r1}.
     */
    protected void lightPillar(ResourceLocation tex, Vec3 base, float height, float r0, float r1, float[] col, float a,
                               float scroll, float tile) {
        if (a <= 0.003f || height <= 0.05f) return;
        Vec3 toCam = cam.subtract(base);
        Vec3 face = new Vec3(-toCam.z, 0, toCam.x);
        face = face.lengthSqr() < 1e-6 ? X : face.normalize();
        Vec3[] planes = {face, X, new Vec3(0.5, 0, 0.866), new Vec3(-0.5, 0, 0.866)};
        VertexConsumer buf = buffers.getBuffer(VfxRenderTypes.lightTextured(tex));
        int n = 10;
        for (int pi = 0; pi < planes.length; pi++) {
            Vec3 side = planes[pi];
            float layer = pi == 0 ? 1f : 0.55f;  // the camera-facing plane carries the most light
            for (int i = 0; i < n; i++) {
                float t0 = i / (float) n, t1 = (i + 1) / (float) n;
                float w0 = r0 + (r1 - r0) * t0, w1 = r0 + (r1 - r0) * t1;
                float a0 = a * layer * envelope(t0), a1 = a * layer * envelope(t1);
                Vec3 c0 = base.add(0, height * t0, 0), c1 = base.add(0, height * t1, 0);
                float u0 = t0 * tile - scroll, u1 = t1 * tile - scroll;
                texVertex(buf, c0.subtract(side.scale(w0)), u0, 0, col, a0);
                texVertex(buf, c1.subtract(side.scale(w1)), u1, 0, col, a1);
                texVertex(buf, c1.add(side.scale(w1)), u1, 1, col, a1);
                texVertex(buf, c0.add(side.scale(w0)), u0, 1, col, a0);
            }
        }
    }

    /**
     * Painted light along any axis from {@code a} to {@code b}: a camera-facing plane plus two fixed ones
     * crossed round the axis, textured with U along the length, scrolling. {@code trail} fades from
     * full at {@code a} to nothing at {@code b} (bolts, lances); otherwise it is even with soft ends (rays).
     */
    protected void lightBeam(ResourceLocation tex, Vec3 a, Vec3 b, float r0, float r1, float[] col, float alpha,
                             float scroll, float tile, boolean trail) {
        if (alpha <= 0.003f) return;
        Vec3 axis = b.subtract(a);
        double len = axis.length();
        if (len < 1e-4) return;
        Vec3 dir = axis.scale(1 / len);
        Vec3 face = dir.cross(a.add(b).scale(0.5).subtract(cam));
        face = face.lengthSqr() < 1e-8 ? perpendicular(dir) : face.normalize();
        Vec3 u = perpendicular(dir), w = dir.cross(u).normalize();
        Vec3[] planes = {face, u, w};
        VertexConsumer buf = buffers.getBuffer(VfxRenderTypes.lightTextured(tex));
        int n = Math.max(4, Math.min(16, (int) (len / 1.5)));
        for (int pi = 0; pi < planes.length; pi++) {
            Vec3 side = planes[pi];
            float layer = pi == 0 ? 1f : 0.5f;
            for (int i = 0; i < n; i++) {
                float t0 = i / (float) n, t1 = (i + 1) / (float) n;
                float w0 = r0 + (r1 - r0) * t0, w1 = r0 + (r1 - r0) * t1;
                float e0 = trail ? (float) Math.pow(1 - t0, 1.2) : smooth(0, 0.05f, t0) * smooth(0, 0.05f, 1 - t0);
                float e1 = trail ? (float) Math.pow(1 - t1, 1.2) : smooth(0, 0.05f, t1) * smooth(0, 0.05f, 1 - t1);
                Vec3 c0 = a.add(axis.scale(t0)), c1 = a.add(axis.scale(t1));
                float u0 = t0 * tile - scroll, u1 = t1 * tile - scroll;
                texVertex(buf, c0.subtract(side.scale(w0)), u0, 0, col, alpha * layer * e0);
                texVertex(buf, c1.subtract(side.scale(w1)), u1, 0, col, alpha * layer * e1);
                texVertex(buf, c1.add(side.scale(w1)), u1, 1, col, alpha * layer * e1);
                texVertex(buf, c0.add(side.scale(w0)), u0, 1, col, alpha * layer * e0);
            }
        }
    }

    /** Fades in over the first 8% of a column and out along the rest, to nothing at the top. */
    private static float envelope(float t) {
        return smooth(0, 0.08f, t) * (float) Math.pow(1 - t, 1.3);
    }

    private void iris(Vec3 center, Vec3 u, Vec3 v, float radius, float age, float alpha) {
        if (alpha <= 0.003f || radius <= 0.01f) return;
        sprite(SEAL, center, u, v, radius, ROSE, 0.7f * alpha, age * 0.08f);
        ring(center, u, v, radius * 0.75f, radius * 0.05f, LILAC, 0.6f * alpha,
                40, true, -age * 0.05f);
        for (int i = 0; i < 6; i++) {
            float angle = age * 0.07f + i * TAU / 6;
            Vec3 point = center.add(u.scale(Mth.cos(angle) * radius * 0.8f))
                    .add(v.scale(Mth.sin(angle) * radius * 0.8f));
            flare(point, radius * 0.16f, angle, WHITE, 0.65f * alpha);
        }
    }

    private void observatory(Vec3 center, float radius, float age, float alpha) {
        if (alpha <= 0.003f || radius <= 0.01f) return;
        underlay(center, X, Z, radius * 1.3f, 0.48f * alpha);
        planeTex(GLOW, center, X, Z, radius * 1.4f, DEEP, 0.4f * alpha, 0);
        sprite(RUNES, center, X, Z, radius, LILAC, 0.7f * alpha, age * 0.02f);
        sprite(SEAL, center.add(0, 0.018, 0), X, Z, radius * 0.68f, ROSE,
                0.9f * alpha, -age * 0.035f);
        ring(center.add(0, 0.026, 0), X, Z, radius * 0.84f, radius * 0.035f,
                WHITE, 0.45f * alpha, 48, true, age * 0.015f);
        for (int i = 0; i < 8; i++) {
            float angle = i * TAU / 8 + age * 0.012f;
            Vec3 marker = center.add(Mth.cos(angle) * radius * 0.83f, 0.04,
                    Mth.sin(angle) * radius * 0.83f);
            flare(marker, Math.max(0.24f, radius * 0.12f), angle,
                    i % 4 == 0 ? ROSE : LILAC, 0.62f * alpha);
        }
    }

    private void star(Vec3 center, Vec3 u, Vec3 v, float radius, float age, float alpha) {
        if (alpha <= 0.003f || radius <= 0.01f) return;
        float spin = age * 0.035f;
        // A wide deep-violet nebula behind every star: the purple mass the white core burns out of.
        glow(center, radius * 4.5f, DEEP, 0.3f * alpha);
        glow(center, radius * 2.4f, PURPLE, 0.34f * alpha);
        for (int i = 0; i < 8; i++) {
            float angle = spin + i * TAU / 8;
            float length = radius * ((i & 1) == 0 ? 1f : 0.48f);
            Vec3 direction = u.scale(Mth.cos(angle)).add(v.scale(Mth.sin(angle)));
            Vec3 tip = center.add(direction.scale(length));
            streakBeam(center, tip, radius * 0.15f, 0,
                    PURPLE, 0.52f * alpha, -age * 0.1f);
            strip(center, tip, radius * 0.045f, 0, WHITE, WHITE, 0.72f * alpha, 0);
        }
        flare(center, radius * 1.2f, -spin, WHITE, 0.9f * alpha);
    }

    // ---- Shared drawing primitives ----

    /** Camera-facing beam from {@code p0} to {@code p1}, textured with the scrolling energy streak. */
    protected void streakBeam(Vec3 p0, Vec3 p1, float w0, float w1, float[] col, float a, float scroll) {
        streakQuad(p0, p1, w0, w1, col, a, scroll, scroll + (float) p0.distanceTo(p1) / 4f);
    }

    protected void streakSegment(Vec3 p0, Vec3 p1, float w, float[] col, float a, float u0, float u1) {
        streakQuad(p0, p1, w, w, col, a, u0, u1);
    }

    protected void streakQuad(Vec3 p0, Vec3 p1, float w0, float w1, float[] col, float a, float u0, float u1) {
        if (a <= 0.003f) return;
        Vec3 axis = p1.subtract(p0);
        Vec3 side = axis.cross(p0.add(p1).scale(0.5).subtract(cam));
        double len = side.length();
        if (len < 1e-6) return;
        side = side.scale(1 / len);
        VertexConsumer buf = buffers.getBuffer(VfxRenderTypes.lightTextured(STREAK));
        texVertex(buf, p0.add(side.scale(-w0)), u0, 0, col, a);
        texVertex(buf, p1.add(side.scale(-w1)), u1, 0, col, a);
        texVertex(buf, p1.add(side.scale(w1)), u1, 1, col, a);
        texVertex(buf, p0.add(side.scale(w0)), u0, 1, col, a);
    }

    // ---- Textured primitives ---------------------------------------------------------------------

    /** Painted four-ray flare with bloom, facing the camera. */
    protected void flare(Vec3 c, float size, float spin, float[] col, float a) {
        sprite(FLARE, c, camRight, camUp, size * 0.5f, col, a, spin);
    }

    /** Soft round glow facing the camera. */
    protected void glow(Vec3 c, float size, float[] col, float a) {
        planeTex(GLOW, c, camRight, camUp, size * 0.5f, col, a, 0);
    }

    /** A textured square of half-size {@code r} in the plane of {@code u, v}, rotated by {@code rot}. */
    protected void sprite(ResourceLocation tex, Vec3 c, Vec3 u, Vec3 v, float r, float[] col, float a, float rot) {
        planeTex(tex, c, u, v, r, col, a, rot);
    }

    protected void planeTex(ResourceLocation tex, Vec3 c, Vec3 u, Vec3 v, float r, float[] col, float a, float rot) {
        if (a <= 0.003f || r <= 0) return;
        float cs = Mth.cos(rot), sn = Mth.sin(rot);
        Vec3 ru = u.scale(cs).add(v.scale(sn)).scale(r);
        Vec3 rv = v.scale(cs).subtract(u.scale(sn)).scale(r);
        VertexConsumer buf = buffers.getBuffer(VfxRenderTypes.lightTextured(tex));
        texVertex(buf, c.subtract(ru).subtract(rv), 0, 0, col, a);
        texVertex(buf, c.add(ru).subtract(rv), 1, 0, col, a);
        texVertex(buf, c.add(ru).add(rv), 1, 1, col, a);
        texVertex(buf, c.subtract(ru).add(rv), 0, 1, col, a);
    }

    /** Alpha-blended dark disc under a sigil, so it keeps contrast on bright ground and in daylight. */
    protected void underlay(Vec3 c, Vec3 u, Vec3 v, float r, float a) {
        VertexConsumer buf = buffers.getBuffer(VfxRenderTypes.glowTextured(GLOW));
        Vec3 ru = u.scale(r), rv = v.scale(r);
        entityVertex(buf, c.subtract(ru).subtract(rv), 0, 0, a);
        entityVertex(buf, c.add(ru).subtract(rv), 1, 0, a);
        entityVertex(buf, c.add(ru).add(rv), 1, 1, a);
        entityVertex(buf, c.subtract(ru).add(rv), 0, 1, a);
    }

    protected void texVertex(VertexConsumer buf, Vec3 p, float u, float v, float[] col, float a) {
        texVertex(buf, p.x, p.y, p.z, u, v, col, a);
    }

    protected void texVertex(VertexConsumer buf, double x, double y, double z, float u, float v, float[] col, float a) {
        buf.vertex(m, (float) (x - ox), (float) (y - oy), (float) (z - oz)).uv(u, v)
                .color(col[0], col[1], col[2], Mth.clamp(a, 0, 1)).endVertex();
    }

    protected void entityVertex(VertexConsumer buf, Vec3 p, float u, float v, float a) {
        entityVertex(buf, p, u, v, VOID, a);
    }

    /**
     * Alpha-blended (darkening) textured square in the plane of {@code u, v}: the only way to draw
     * black - smoke, void cores, shadows. A colour near black darkens; alpha sets the strength.
     */
    protected void darkTex(ResourceLocation tex, Vec3 c, Vec3 u, Vec3 v, float r, float[] col, float a, float rot) {
        if (a <= 0.003f || r <= 0) return;
        float cs = Mth.cos(rot), sn = Mth.sin(rot);
        Vec3 ru = u.scale(cs).add(v.scale(sn)).scale(r);
        Vec3 rv = v.scale(cs).subtract(u.scale(sn)).scale(r);
        VertexConsumer buf = buffers.getBuffer(VfxRenderTypes.glowTextured(tex));
        entityVertex(buf, c.subtract(ru).subtract(rv), 0, 0, col, a);
        entityVertex(buf, c.add(ru).subtract(rv), 1, 0, col, a);
        entityVertex(buf, c.add(ru).add(rv), 1, 1, col, a);
        entityVertex(buf, c.subtract(ru).add(rv), 0, 1, col, a);
    }

    /** Alpha-blended camera-facing beam with a scrolling texture (dark cores, shadow tendrils). */
    protected void darkBeam(ResourceLocation tex, Vec3 p0, Vec3 p1, float w0, float w1, float[] col, float a, float u0, float u1) {
        if (a <= 0.003f) return;
        Vec3 axis = p1.subtract(p0);
        Vec3 side = axis.cross(p0.add(p1).scale(0.5).subtract(cam));
        double len = side.length();
        if (len < 1e-6) return;
        side = side.scale(1 / len);
        VertexConsumer buf = buffers.getBuffer(VfxRenderTypes.glowTextured(tex));
        entityVertex(buf, p0.add(side.scale(-w0)), u0, 0, col, a);
        entityVertex(buf, p1.add(side.scale(-w1)), u1, 0, col, a);
        entityVertex(buf, p1.add(side.scale(w1)), u1, 1, col, a);
        entityVertex(buf, p0.add(side.scale(w0)), u0, 1, col, a);
    }

    protected void entityVertex(VertexConsumer buf, Vec3 p, float u, float v, float[] col, float a) {
        buf.vertex(m, (float) (p.x - ox), (float) (p.y - oy), (float) (p.z - oz))
                .color(col[0], col[1], col[2], Mth.clamp(a, 0, 1)).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0, 1, 0).endVertex();
    }

    // ---- Vector primitives (additive) -----------------------------------------------------------

    protected VertexConsumer additive() {
        vc = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        return vc;
    }

    protected Vec3 here() {
        return new Vec3(ox, oy, oz);
    }

    protected void v(Vec3 p, float[] c, float a) {
        quads.vertex(vc, m, (float) (p.x - ox), (float) (p.y - oy), (float) (p.z - oz), c[0], c[1], c[2], a);
    }

    /** Camera-facing soft line: bright along its axis, transparent at both edges. */
    protected void strip(Vec3 p0, Vec3 p1, float w0, float w1, float[] c0, float[] c1, float a0, float a1) {
        if (a0 <= 0.003f && a1 <= 0.003f) return;
        Vec3 axis = p1.subtract(p0);
        Vec3 mid = p0.add(p1).scale(0.5);
        Vec3 side = axis.cross(mid.subtract(cam));
        double len = side.length();
        if (len < 1e-6) return;
        side = side.scale(1 / len);
        additive();
        for (int s = -1; s <= 1; s += 2) {
            v(p0, c0, a0);
            v(p1, c1, a1);
            v(p1.add(side.scale(s * w1)), c1, 0);
            v(p0.add(side.scale(s * w0)), c0, 0);
        }
    }

    /** Soft annulus in the plane of {@code u, v}. */
    protected void ring(Vec3 c, Vec3 u, Vec3 vAxis, float r, float w, float[] col, float a, int seg, boolean dashed, float phase) {
        if (a <= 0.003f || r <= 0) return;
        additive();
        float ri = Math.max(0, r - w), ro = r + w;
        for (int i = 0; i < seg; i++) {
            if (dashed && (i & 1) == 1) continue;
            float a0 = phase + i * TAU / seg, a1 = phase + (i + 1) * TAU / seg;
            Vec3 d0 = u.scale(Mth.cos(a0)).add(vAxis.scale(Mth.sin(a0)));
            Vec3 d1 = u.scale(Mth.cos(a1)).add(vAxis.scale(Mth.sin(a1)));
            v(c.add(d0.scale(ri)), col, 0);
            v(c.add(d1.scale(ri)), col, 0);
            v(c.add(d1.scale(r)), col, a);
            v(c.add(d0.scale(r)), col, a);
            v(c.add(d0.scale(r)), col, a);
            v(c.add(d1.scale(r)), col, a);
            v(c.add(d1.scale(ro)), col, 0);
            v(c.add(d0.scale(ro)), col, 0);
        }
    }

    /** Jagged lightning, re-struck on a tick key so it crackles without jitter. */
    protected void zigzag(Vec3 from, Vec3 to, float jag, int key, float[] col, float a) {
        Vec3 axis = to.subtract(from);
        Vec3 dir = axis.normalize();
        Vec3 u = perpendicular(dir), w = dir.cross(u);
        Vec3 prev = from;
        int n = 7;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float amp = i == n ? 0 : jag * Mth.sin(Mth.PI * t);
            Vec3 p = from.add(axis.scale(t)).add(u.scale((hash(key, i) - 0.5f) * 2 * amp)).add(w.scale((hash(key, i + 20) - 0.5f) * 2 * amp));
            strip(prev, p, 0.16f, 0.16f, col, col, a * 0.6f, a * 0.6f);
            strip(prev, p, 0.04f, 0.04f, WHITE, WHITE, a, a);
            prev = p;
        }
    }

    // ---- Maths ----------------------------------------------------------------------------------

    protected static Vec3 perpendicular(Vec3 dir) {
        Vec3 u = dir.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = dir.cross(new Vec3(1, 0, 0));
        return u.normalize();
    }

    protected static Vec3 sphereDir(int seed, int i) {
        float y = 1 - 2 * hash(seed, i * 3 + 1);
        float a = TAU * hash(seed, i * 3 + 2);
        float r = Mth.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Mth.cos(a) * r, y, Mth.sin(a) * r);
    }

    protected static float frac(float x) {
        return x - Mth.floor(x);
    }

    protected static float smooth(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    protected static float easeOut(float t) {
        float k = 1 - t;
        return 1 - k * k * k;
    }

    protected static float backOut(float t) {
        float s = 1.70158f;
        float k = t - 1;
        return k * k * ((s + 1) * k + s) + 1;
    }

    static float hash(int a, int b) {
        int x = a * 73856093 ^ b * 19349663;
        x ^= x >>> 13;
        x *= 0x5bd1e995;
        x ^= x >>> 15;
        return (x & 0xFFFFFF) / 16777216f;
    }
}
