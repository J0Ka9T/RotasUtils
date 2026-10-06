package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.CelestialFxEntity;

import java.util.List;

@Environment(EnvType.CLIENT)
public class AbyssFxRenderer extends CelestialFxRenderer {
    private static final ResourceLocation A_RUNES = abyss("sigil_runes");
    private static final ResourceLocation A_SEAL = abyss("sigil_arcane");
    private static final ResourceLocation SWIRL = abyss("void_swirl");
    private static final ResourceLocation CRACKS = abyss("cracks");
    private static final ResourceLocation SMOKE = abyss("smoke");

    private static final float[] BLACK = {0.01f, 0.0f, 0.03f};
    private static final float[] SHADOW = {0.07f, 0.01f, 0.12f};
    private static final float[] VIOLET = {0.55f, 0.12f, 0.95f};
    private static final float[] CRIMSON = {0.95f, 0.1f, 0.4f};
    private static final float[] LILAC = {0.85f, 0.65f, 1.0f};

    private static final Mesh3D.Style SHADE_FLESH = new Mesh3D.Style(0.06f, 0.02f, 0.09f, 0.55f, 0.18f, 0.95f, 0.9f, 2.6f);
    private static final Mesh3D.Style MAW_FLESH = new Mesh3D.Style(0.09f, 0.015f, 0.04f, 0.85f, 0.1f, 0.3f, 0.6f, 3.0f);
    private static final Mesh3D.Style BONE = new Mesh3D.Style(0.62f, 0.56f, 0.66f, 0.9f, 0.25f, 0.5f, 0.45f, 3.0f);
    private static final Mesh3D.Style TONGUE = new Mesh3D.Style(0.14f, 0.015f, 0.05f, 0.9f, 0.12f, 0.35f, 0.6f, 3.0f);
    private static final Mesh3D.Style INK = new Mesh3D.Style(0.03f, 0.01f, 0.05f, 0.65f, 0.22f, 1.0f, 1.0f, 2.4f);

    public AbyssFxRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation abyss(String name) {
        return new ResourceLocation("rotasutils", "textures/vfx/abyss/" + name + ".png");
    }

    @Override
    public void render(CelestialFxEntity fx, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!CelestialFxEntity.isAbyss(fx.kind())) {
            super.render(fx, yaw, pt, pose, buffers, light);
            return;
        }
        if (!begin(fx, pt, pose, buffers)) return;
        float age = fx.tickCount + pt;
        switch (fx.kind()) {
            case CelestialFxEntity.SHADOW_BOLT -> shadowBolt(fx, age);
            case CelestialFxEntity.VOID_BURST -> voidBurst(fx, age);
            case CelestialFxEntity.GRASP -> grasp(fx, age);
            case CelestialFxEntity.VOID_RAY -> voidRay(fx, age, pt);
            case CelestialFxEntity.ECLIPSE -> eclipse(fx, age);
            case CelestialFxEntity.SHADE_STEP -> shadeStep(fx, age);
            case CelestialFxEntity.PIT -> pit(fx, age);
            case CelestialFxEntity.VEIL -> veil(fx, age, pt);
            case CelestialFxEntity.OBLIVION_CHARGE -> oblivionCharge(fx, age, pt);
            case CelestialFxEntity.OBLIVION -> oblivion(fx, age);
            case CelestialFxEntity.INK_SPLASH -> inkSplash(fx, age);
            default -> { }
        }
    }

private void shadowBolt(CelestialFxEntity fx, float age) {
        Vec3 dir = fx.targetPos();
        Vec3 head = fx.boltPos(age);
        float in = smooth(0, 2, age);
        Vec3 side = perpendicular(dir), up = dir.cross(side);
        float len = Math.min(3.6f, 0.8f + age * 1.2f);
        int n = 20;
        Vec3[] body = new Vec3[n];
        float[] radius = new float[n];
        for (int i = 0; i < n; i++) {
            float s = i / (n - 1f);
            float amp = 0.42f * Mth.sin(Mth.PI * Math.min(1, s * 1.1f)) * s;
            float wave = Mth.sin(s * 9f - age * 1.1f);
            body[i] = head.subtract(dir.scale(len * s)).add(side.scale(amp * wave)).add(up.scale(amp * 0.3f * Mth.cos(s * 9f - age * 1.1f)));
            float profile = s < 0.12f ? Mth.sqrt(s / 0.12f) : (float) Math.pow(1 - (s - 0.12f) / 0.88f, 1.3);
            radius[i] = (0.26f * profile + 0.01f) * in;
        }
        glow(body[3], 2.6f, VIOLET, 0.35f * in);
        mesh.tube(body, radius, 12, INK);
        for (int k = 0; k < 2; k++) {
            int at = k == 0 ? 4 : 10;
            float flap = Mth.sin(age * 1.4f + k * 1.7f);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3 finDir = side.scale(sgn).add(up.scale(0.4 + 0.5 * flap)).normalize();
                Vec3 c = body[at].add(finDir.scale(0.28 * (k == 0 ? 1 : 0.7)));
                Vec3 fwd = dir;
                Vec3 thin = finDir.cross(fwd).normalize();
                mesh.sphere(c, finDir, thin, fwd, 0.28f * (k == 0 ? 1 : 0.7f) * in, 0.03f, 0.18f * in, 6, 10, INK);
            }
        }
        for (int i = 3; i < n - 4; i += 3) {
            flare(body[i].add(up.scale(radius[i] * 0.9)), 0.25f + 0.05f * Mth.sin(age * 2 + i), i, CRIMSON, 0.9f * in);
        }
        Vec3 eye = body[1].add(up.scale(radius[2] * 0.7)).add(side.scale(radius[2] * 0.7));
        mesh.sphere(eye, side, up, dir, 0.07f, 0.07f, 0.07f, 6, 8, new Mesh3D.Style(0.9f, 0.75f, 1f, 1f, 1f, 1f, 0.8f, 1.5f));
        glow(eye, 0.7f, LILAC, 0.8f * in);
        int seed = fx.seedValue();
        for (int i = 0; i < 5; i++) {
            float ph = frac(age * 0.25f + i / 5f);
            Vec3 drop = body[n - 1].add(sphereDir(seed + (int) (age * 0.25f + i / 5f), i).scale(0.2 * ph)).add(0, -0.9 * ph * ph, 0);
            mesh.sphere(drop, X, new Vec3(0, 1, 0), Z, 0.05f * (1 - ph) + 0.02f, 0.07f * (1 - ph) + 0.02f, 0.05f * (1 - ph) + 0.02f, 4, 6, INK);
        }
    }

    private void voidBurst(CelestialFxEntity fx, float age) {
        Vec3 c = here();
        float t = Mth.clamp(age / fx.life(), 0, 1);
        float fade = 1 - smooth(0.35f, 1, t);
        float r = fx.radius();
        int seed = fx.seedValue();
        for (int i = 0; i < 8; i++) {
            Vec3 p = c.add(sphereDir(seed, i).scale(r * 1.4 * easeOut(t)));
            darkTex(SMOKE, p, camRight, camUp, r * (0.6f + 0.9f * t), SHADOW, 0.7f * fade, i);
        }
        darkTex(GLOW, c, camRight, camUp, r * 1.2f * (1 - t), BLACK, 0.95f, 0);
        sprite(A_RUNES, c, camRight, camUp, r * (0.7f + 1.8f * easeOut(t)), CRIMSON, 0.9f * fade, -age * 0.15f);
        glow(c, r * 3f, VIOLET, 0.4f * fade);
        for (int i = 0; i < 10; i++) {
            Vec3 d = sphereDir(seed, i + 30);
            float reach = r * (0.8f + 1.8f * CelestialFxRenderer.hash(seed, i)) * easeOut(t);
            strip(c.add(d.scale(reach * 0.4)), c.add(d.scale(reach)), 0.1f * r, 0, CRIMSON, CRIMSON, 0.9f * fade, 0);
            flare(c.add(d.scale(reach)), 0.35f * r, i, LILAC, fade);
        }
    }

    private void grasp(CelestialFxEntity fx, float age) {
        Vec3 c = here().add(0, 0.03, 0);
        float life = fx.life();
        float R = fx.radius();
        float open = backOut(Math.min(1, age / 7f)) * (1 - smooth(life - 6, life, age));
        int seed = fx.seedValue();
        darkTex(SMOKE, c, X, Z, R * 1.3f * open, BLACK, 0.7f, seed);
        darkTex(GLOW, c, X, Z, R * 1.05f * open, BLACK, 0.95f, 0);
        darkTex(GLOW, c, X, Z, R * 0.95f * open, BLACK, 0.95f, 0);
        if (open > 0.05f) {
            int m = 40;
            Vec3[] rim = new Vec3[m + 1];
            float[] rr = new float[m + 1];
            for (int i = 0; i <= m; i++) {
                float a = i * TAU / m;
                float wob = 1 + 0.05f * Mth.sin(a * 5 + age * 0.2f);
                rim[i] = c.add(Mth.cos(a) * R * 0.98 * open * wob, 0.02, Mth.sin(a) * R * 0.98 * open * wob);
                rr[i] = 0.12f;
            }
            mesh.tube(rim, rr, 6, SHADE_FLESH);
        }
        for (int k = 0; k < 3; k++) {
            float ph = frac(age * 0.03f + k / 3f);
            ring(c.add(0, 0.03, 0), X, Z, R * open * ph, 0.05f, VIOLET, 0.45f * (1 - ph), 48, false, 0);
        }
        float rise = easeOut(Math.min(1, Math.max(0, age - 2) / 9f)) * (1 - smooth(life - 12, life - 2, age));
        float grip = smooth(9, 13, age);
        List<LivingEntity> held = inside(fx, c, R);
        int hands = 0;
        for (LivingEntity e : held) {
            if (hands >= 6) break;
            Vec3 base = new Vec3(e.getX(), c.y, e.getZ()).add(0.35, 0, 0.35);
            float size = e.getBbWidth() * 0.8f + 0.9f;
            float reach = Math.min(e.getBbHeight() * 0.45f, 2.2f);
            hand(base, base.add(-0.2, reach * rise, -0.2), size, grip, age + hands * 11, rise, hands);
            ripple(base, age, hands);
            hands++;
        }
        for (int k = hands; k < 4; k++) {
            float a = k * 2.39996f + seed;
            float rr = R * (0.3f + 0.45f * CelestialFxRenderer.hash(seed, k));
            Vec3 base = c.add(Mth.cos(a) * rr, 0, Mth.sin(a) * rr);
            float idleGrip = 0.3f + 0.35f * Mth.sin(age * 0.12f + k);
            hand(base, base.add(Mth.sin(age * 0.05f + k) * 0.25, (1.4 + 0.3 * Mth.sin(age * 0.07f + k)) * rise, 0), 1.1f, idleGrip, age + k * 13, rise, k);
            ripple(base, age, k);
        }
    }

    private void hand(Vec3 base, Vec3 palm, float size, float grip, float time, float a, int id) {
        if (a <= 0.02f) return;
        Vec3[] arm = new Vec3[7];
        float[] armR = new float[7];
        for (int i = 0; i < 7; i++) {
            float t = i / 6f;
            arm[i] = base.lerp(palm, t).add(Mth.sin(time * 0.1f + t * 3) * 0.15 * t * (1 - t) * 4, 0, Mth.cos(time * 0.08f + t * 2) * 0.12 * t * (1 - t) * 4);
            armR[i] = size * (0.2f - 0.07f * t) * a;
        }
        arm[0] = arm[0].add(0, -0.3, 0);
        mesh.tube(arm, armR, 10, SHADE_FLESH);
        float yaw = id * 1.3f + time * 0.01f;
        Vec3 fwd = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        mesh.sphere(palm, fwd, new Vec3(0, 1, 0), side, size * 0.2f * a, size * 0.1f * a, size * 0.18f * a, 8, 12, SHADE_FLESH);
        for (int f = 0; f < 5; f++) {
            float ang = yaw + (f - 2) * 0.55f + (f == 0 ? -0.5f : 0);
            Vec3 radial = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
            Vec3 joint = palm.add(radial.scale(0.16 * size)).add(0, 0.05 * size, 0);
            float lean = 0.7f - 1.9f * grip;
            float[] lengths = {0.3f, 0.24f, 0.18f};
            Vec3[] pts = new Vec3[4];
            float[] rad = new float[4];
            pts[0] = joint;
            rad[0] = size * 0.065f * a;
            for (int j = 0; j < 3; j++) {
                float bend = lean - j * 0.6f * grip;
                Vec3 dirv = radial.scale(Mth.sin(bend)).add(0, Mth.cos(bend), 0);
                pts[j + 1] = pts[j].add(dirv.scale(lengths[j] * size * (f == 0 ? 0.8 : 1)));
                rad[j + 1] = size * (0.055f - j * 0.013f) * a;
            }
            mesh.tube(pts, rad, 7, SHADE_FLESH);
            Vec3 tipDir = pts[3].subtract(pts[2]).normalize();
            Vec3 nail = pts[3].add(tipDir.scale(0.12 * size));
            mesh.tube(new Vec3[]{pts[3], nail}, new float[]{rad[3] * 0.9f, 0f}, 6, new Mesh3D.Style(0.5f, 0.05f, 0.15f, 1f, 0.2f, 0.4f, 1.2f, 1.5f));
            flare(nail, 0.25f, f, CRIMSON, 0.7f * a);
        }
    }

    private void ripple(Vec3 base, float age, int id) {
        float ph = frac(age * 0.05f + id * 0.37f);
        ring(base.add(0, 0.04, 0), X, Z, 0.3f + 1.2f * ph, 0.03f, LILAC, 0.5f * (1 - ph), 24, false, 0);
    }

    private List<LivingEntity> inside(CelestialFxEntity fx, Vec3 c, float r) {
        Entity owner = fx.owner();
        List<LivingEntity> out = fx.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 3, r),
                e -> e != owner && e.isAlive() && !e.isSpectator() && e.position().distanceToSqr(c.x, e.getY(), c.z) <= r * r);
        out.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        return out;
    }

    private void voidRay(CelestialFxEntity fx, float age, float pt) {
        Entity owner = fx.owner();
        if (owner == null) return;
        Vec3 look = owner.getViewVector(pt);
        Vec3 eye = owner.getEyePosition(pt);
        Vec3 muzzle = eye.add(look.scale(1.0)).add(0, -0.25, 0);
        Vec3 far = eye.add(look.scale(fx.radius()));
        HitResult hit = owner.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        Vec3 dir = end.subtract(muzzle);
        float length = (float) dir.length();
        if (length < 0.1f) return;
        dir = dir.scale(1 / length);
        float grow = backOut(Math.min(1, age / 5f));
        float reach = Math.min(length, 2 + age * 12);
        Vec3 tip = muzzle.add(dir.scale(reach));
        Vec3 u = perpendicular(dir), v = dir.cross(u);
        float w = 0.85f * grow * (0.96f + 0.04f * Mth.sin(age * 60));
        glowTube(muzzle, tip, w * 0.8f, w * 2.4f, VIOLET, 0.25f, 0.8f);
        glowTube(muzzle, tip, w * 0.7f, w * 1.15f, CRIMSON, 0.7f, 2.6f);
        strip(muzzle, tip, w * 2.6f, w * 2.6f, VIOLET, VIOLET, 0.28f, 0.28f);
        darkBeam(STREAK, muzzle, tip, w, w, BLACK, 0.95f, -age * 0.3f, -age * 0.3f + reach / 4f);
        darkBeam(SMOKE, muzzle, tip, w * 1.5f, w * 1.5f, SHADOW, 0.5f, -age * 0.1f, -age * 0.1f + reach / 6f);
        Vec3 side = dir.cross(muzzle.add(tip).scale(0.5).subtract(cam)).normalize();
        for (int s = -1; s <= 1; s += 2) {
            Vec3 off = side.scale(s * w * 0.95);
            streakBeam(muzzle.add(off), tip.add(off), 0.12f, 0.12f, CRIMSON, 0.95f, -age * 0.4f);
            strip(muzzle.add(off), tip.add(off), 0.03f, 0.03f, LILAC, LILAC, 0.9f, 0.9f);
        }
        float spacing = 2.6f;
        for (float d = (age * 0.8f) % spacing; d < reach; d += spacing) {
            ring(muzzle.add(dir.scale(d)), u, v, w * 1.1f, 0.08f, CRIMSON, 0.6f, 24, true, age * 0.2f);
        }
        abyssSigil(muzzle, u, v, 1.3f * grow, 1, age * 0.15f, -age * 0.25f);
        darkTex(SWIRL, muzzle, u, v, 0.9f * grow, BLACK, 0.9f, -age * 0.3f);
        if (reach >= length - 0.01f) {
            darkTex(SWIRL, end, camRight, camUp, 1.6f, BLACK, 0.9f, age * 0.3f);
            sprite(SWIRL, end, camRight, camUp, 1.6f, CRIMSON, 0.6f, age * 0.3f);
            glow(end, 3.5f, VIOLET, 0.5f);
            int key = (int) (age / 2);
            for (int k = 0; k < 4; k++) {
                Vec3 far2 = end.add(sphereDir(key * 5 + fx.seedValue(), k).scale(1.0 + CelestialFxRenderer.hash(key, k) * 1.2));
                zigzag(end, far2, 0.3f, key * 17 + k, CRIMSON, 0.8f);
            }
        }
    }

    private void eclipse(CelestialFxEntity fx, float age) {
        Vec3 c = here();
        float R = fx.radius();
        float life = fx.life();
        float grow = backOut(Math.min(1, age / 8f)) * (1 - smooth(life - 6, life, age));
        if (grow <= 0.01f) return;
        float re = (1.15f + R * 0.05f) * grow;
        Entity caster = fx.owner();
        Vec3 ahead = caster == null ? Vec3.ZERO : caster.getLookAngle().multiply(1, 0, 1).normalize().scale(3.0);
        Vec3 eye = c.add(ahead).add(0, 4.4 + 0.2 * Mth.sin(age * 0.12f), 0);
        Vec3 look = cam;
        double best = R * R * 4;
        for (LivingEntity e : inside(fx, c, R * 2)) {
            double d = e.position().distanceToSqr(c);
            if (d < best) { best = d; look = e.getEyePosition(); }
        }
        Vec3 f = look.subtract(eye).normalize().add(cam.subtract(eye).normalize().scale(0.8)).normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 ay = worldUp.subtract(f.scale(worldUp.dot(f)));
        ay = ay.lengthSqr() < 1e-4 ? X : ay.normalize();
        Vec3 ax = ay.cross(f).normalize();
        float open;
        if (age < 10) open = smooth(2, 10, age);
        else if (age < 22) open = 1;
        else if (age < 26) open = 1 - smooth(22, 25, age);
        else open = 0.75f * smooth(27, 31, age);

        glow(eye, re * 4.5f, CRIMSON, 0.18f * grow);
        int seed = fx.seedValue();
        Mesh3D.Style flesh = new Mesh3D.Style(0.16f, 0.05f, 0.2f, 0.75f, 0.2f, 1.0f, 0.9f, 2.2f);
        for (int k = 0; k < 8; k++) {
            float a = k * TAU / 8 + CelestialFxRenderer.hash(seed, k) * 0.4f;
            Vec3 root = eye.add(ax.scale(Mth.cos(a) * re * 0.6)).add(ay.scale(Mth.sin(a) * re * 0.6)).subtract(f.scale(re * 0.7));
            Vec3 out = ax.scale(Mth.cos(a)).add(ay.scale(Mth.sin(a))).scale(0.8).subtract(f.scale(0.6)).normalize();
            int n = 12;
            Vec3[] pts = new Vec3[n];
            float[] rad = new float[n];
            float len = (3.2f + 1.4f * CelestialFxRenderer.hash(seed, k + 9)) * grow;
            for (int i = 0; i < n; i++) {
                float t = i / (n - 1f);
                float sway = Mth.sin(age * 0.15f + k * 1.3f + t * 4f) * t * 0.9f;
                Vec3 side = out.cross(f).normalize();
                pts[i] = root.add(out.scale(len * t)).add(side.scale(sway)).add(0, -t * t * 1.2, 0);
                rad[i] = re * 0.2f * (1 - t) + 0.02f;
            }
            mesh.tube(pts, rad, 8, flesh);
            flare(pts[n - 1], 0.35f, k, CRIMSON, 0.8f * grow);
        }
        mesh.sphere(eye, ax, ay, f, re, re, re, 16, 22, new Mesh3D.Style(0.06f, 0.02f, 0.08f, 1.0f, 0.15f, 0.4f, 1.1f, 2.4f));
        if (open > 0.05f) {
            float dilate = age < 22 ? 0.09f + 0.03f * Mth.sin(age * 0.5f) : 0.16f;
            Vec3 front = eye.add(f.scale(re * 1.005));
            sprite(abyss("iris"), front, ax, ay, re * 0.62f, CRIMSON, 0.95f * open, age * 0.02f);
            sprite(abyss("iris"), front, ax, ay, re * 0.6f, LILAC, 0.25f * open, -age * 0.015f);
            mesh.sphere(eye.add(f.scale(re * 0.97)), ax, ay, f, re * dilate, re * 0.45f, re * 0.08f, 6, 12,
                    new Mesh3D.Style(0.0f, 0.0f, 0.0f, 0.9f, 0.1f, 0.3f, 0.6f, 3f));
        }
        float upper = Mth.lerp(open, Mth.HALF_PI + 0.03f, 0.55f);
        float lower = Mth.lerp(open, Mth.HALF_PI - 0.03f, Mth.PI - 0.6f);
        float lidR = re * 1.07f;
        Mesh3D.Style lid = new Mesh3D.Style(0.2f, 0.06f, 0.24f, 0.8f, 0.25f, 1.0f, 0.8f, 2.5f);
        mesh.sphereSection(eye, ax, ay, f, lidR, lidR, lidR, 0, upper, 10, 24, lid);
        mesh.sphereSection(eye, ax, ay, f, lidR, lidR, lidR, lower, Mth.PI, 10, 24, lid);
        ring(eye.add(ay.scale(Mth.cos(upper) * lidR)), ax, f, Mth.sin(upper) * lidR, 0.08f, CRIMSON, 0.9f * grow, 40, false, 0);
        ring(eye.add(ay.scale(Mth.cos(lower) * lidR)), ax, f, Mth.sin(lower) * lidR, 0.08f, CRIMSON, 0.9f * grow, 40, false, 0);
        if (age > 10 && age < 22 && best < R * R * 4) {
            strip(eye.add(f.scale(re)), look, 0.06f, 0.01f, CRIMSON, CRIMSON, 0.5f, 0.15f);
        }
        if (age >= 26) {
            float t = Mth.clamp((age - 26) / 16f, 0, 1);
            float front = R * (0.25f + 1.1f * easeOut(t));
            float thornFade = 1 - smooth(0.55f, 1, t);
            ring(c.add(0, 0.1, 0), X, Z, front, 0.2f, CRIMSON, 0.9f * thornFade, 80, false, 0);
            Mesh3D.Style thornStyle = new Mesh3D.Style(0.08f, 0.02f, 0.1f, 1.0f, 0.15f, 0.45f, 1.0f, 2f);
            for (int k = 0; k < 28; k++) {
                float a = k * TAU / 28 + CelestialFxRenderer.hash(seed, k + 40) * 0.2f;
                float h = (1.2f + 1.3f * CelestialFxRenderer.hash(seed, k + 60)) * Mth.sin(Mth.PI * Math.min(1, t * 1.6f));
                if (h <= 0.05f) continue;
                Vec3 base = c.add(Mth.cos(a) * front, 0, Mth.sin(a) * front);
                Vec3 outDir = new Vec3(Mth.cos(a), 0, Mth.sin(a));
                Vec3 tip = base.add(outDir.scale(h * 0.45)).add(0, h, 0);
                mesh.tube(new Vec3[]{base, base.lerp(tip, 0.5).add(0, 0.05, 0), tip}, new float[]{0.28f, 0.14f, 0.0f}, 7, thornStyle);
            }
        }
    }

    private void shadeStep(CelestialFxEntity fx, float age) {
        Vec3 from = fx.originPos().add(0, 1, 0);
        Vec3 to = fx.targetPos().add(0, 1, 0);
        float t = Mth.clamp(age / fx.life(), 0, 1);
        float fade = 1 - smooth(0.3f, 1, t);
        int seed = fx.seedValue();
        darkBeam(SMOKE, from, to, 0.8f, 0.8f, SHADOW, 0.55f * fade, age * 0.05f, age * 0.05f + 2);
        streakBeam(from, to, 0.18f, 0.18f, CRIMSON, 0.7f * fade, -age * 0.2f);
        for (int i = 0; i < 10; i++) {
            float s = i / 9f;
            Vec3 p = from.lerp(to, s).add(sphereDir(seed, i).scale(0.4 + t));
            darkTex(SMOKE, p, camRight, camUp, 0.7f + 0.8f * t, SHADOW, 0.6f * fade, i + age * 0.08f);
        }
        for (Vec3 end : new Vec3[]{from, to}) {
            abyssSigil(end.add(0, -0.95, 0), X, Z, 1.6f * (1 - 0.3f * t), fade, age * 0.1f, -age * 0.15f);
            darkTex(SWIRL, end, camRight, camUp, 1.4f * (1 - t), BLACK, 0.9f * fade, age * 0.4f);
            sprite(SWIRL, end, camRight, camUp, 1.4f * (1 - t), CRIMSON, 0.6f * fade, age * 0.4f);
        }
    }

    private void pit(CelestialFxEntity fx, float age) {
        Vec3 c = here().add(0, 0.04, 0);
        float life = fx.life();
        float R = fx.radius();
        float mouth = R * 0.62f;
        float open = backOut(Math.min(1, age / 12f));
        float bite = smooth(life - 9, life - 3, age);
        float gape = open * (1 - 0.75f * bite);
        int seed = fx.seedValue();
        Vec3 away = new Vec3(c.x - cam.x, 0, c.z - cam.z);
        away = away.lengthSqr() < 1e-4 ? X : away.normalize();
        darkTex(SMOKE, c, X, Z, mouth * 1.5f * open, BLACK, 0.6f, seed);
        for (int k = 0; k < 7; k++) {
            float d = k / 6f;
            Vec3 layer = c.add(away.scale(d * mouth * 0.45 * gape)).add(0, 0.002 * k, 0);
            float rr = mouth * gape * (1 - 0.7f * d);
            darkTex(GLOW, layer, X, Z, rr * 1.15f, BLACK, 0.6f, 0);
            ring(layer, X, Z, rr * 0.92f, 0.06f, CRIMSON, 0.55f * (1 - d) * gape, 36, false, 0);
        }
        Vec3 deep = c.add(away.scale(mouth * 0.45 * gape));
        sprite(GLOW, deep.add(0, 0.02, 0), X, Z, mouth * 0.4f * gape, CRIMSON, 0.7f + 0.25f * Mth.sin(age * 0.3f), 0);
        for (int layer = 0; layer < 2; layer++) {
            int n = 48;
            Vec3[] lip = new Vec3[n + 1];
            float[] rad = new float[n + 1];
            float scale = layer == 0 ? 1.08f : 0.9f;
            for (int i = 0; i <= n; i++) {
                float a = i * TAU / n;
                float wob = 1 + 0.08f * Mth.sin(a * 3 + age * 0.2f) + 0.04f * Mth.sin(a * 7 - age * 0.3f);
                lip[i] = c.add(Mth.cos(a) * mouth * gape * scale * wob, layer == 0 ? 0.25 : 0.12, Mth.sin(a) * mouth * gape * scale * wob);
                rad[i] = (layer == 0 ? 0.55f : 0.32f) * (0.4f + 0.6f * open);
            }
            mesh.tube(lip, rad, 14, MAW_FLESH);
        }
        for (int row = 0; row < 2; row++) {
            int count = row == 0 ? 14 : 10;
            for (int i = 0; i < count; i++) {
                float a = (i + row * 0.5f) * TAU / count;
                float len = (row == 0 ? 1.9f : 1.3f) * (0.75f + 0.5f * CelestialFxRenderer.hash(seed, i + row * 40)) * (0.5f + 0.5f * open);
                float rim = mouth * gape * (row == 0 ? 0.98f : 0.8f);
                Vec3 inward = new Vec3(-Mth.cos(a), 0, -Mth.sin(a));
                Vec3 base = c.add(Mth.cos(a) * rim, 0.2, Mth.sin(a) * rim);
                Vec3 tipOpen = base.add(inward.scale(len * 0.5)).add(0, len, 0);
                Vec3 tipShut = c.add(0, 0.6 + row * 0.2, 0).add(inward.scale(-0.3));
                Vec3 tip = tipOpen.lerp(tipShut, bite);
                Vec3 mid = base.lerp(tip, 0.5).add(0, 0.25, 0).subtract(inward.scale(0.15));
                float br = (row == 0 ? 0.3f : 0.22f) * (0.5f + 0.5f * open);
                mesh.tube(new Vec3[]{base.add(0, -0.2, 0), base, mid, tip}, new float[]{br * 1.1f, br, br * 0.6f, 0f}, 12, BONE);
            }
        }
        int t = 0;
        for (LivingEntity e : inside(fx, c, R)) {
            if (t++ >= 5) break;
            Vec3 end = e.position().add(0, e.getBbHeight() * 0.4, 0);
            Vec3 start = c.add(0, 0.3, 0);
            Vec3 mid = start.lerp(end, 0.5).add(0, 1.4 + 0.3 * Mth.sin(age * 0.2f + t), 0);
            int n = 14;
            Vec3[] pts = new Vec3[n];
            float[] rad = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1f);
                float k = 1 - u;
                pts[i] = start.scale(k * k).add(mid.scale(2 * k * u)).add(end.scale(u * u))
                        .add(0, 0.12 * Mth.sin(u * 12 - age * 0.6f), 0);
                rad[i] = (0.24f * (1 - u) + 0.07f) * gape;
            }
            mesh.tube(pts, rad, 12, TONGUE);
        }
        for (int i = 0; i < 14; i++) {
            float ph = frac(age * 0.03f + CelestialFxRenderer.hash(seed, i + 70));
            Vec3 e = c.add((CelestialFxRenderer.hash(seed, i) - 0.5) * mouth, 0.3 + ph * 3.5, (CelestialFxRenderer.hash(seed, i + 5) - 0.5) * mouth);
            flare(e, 0.35f, i, CRIMSON, gape * Mth.sin(ph * Mth.PI));
        }
        if (bite > 0.9f) {
            float f = smooth(life - 3, life, age);
            glow(c.add(0, 0.8, 0), 8, CRIMSON, 0.9f * (1 - f));
            darkTex(abyss("shock_ring"), c.add(0, 0.05, 0), X, Z, R * (0.6f + 1.6f * f), BLACK, 0.8f * (1 - f), 0);
        }
    }

    private void veil(CelestialFxEntity fx, float age, float pt) {
        Entity owner = fx.owner();
        if (owner == null) return;
        Vec3 feet = owner.getPosition(pt);
        float fade = smooth(0, 8, age) * (1 - smooth(fx.life() - 15, fx.life(), age));
        darkTex(GLOW, feet.add(0, 0.04, 0), X, Z, 2.2f, BLACK, 0.55f * fade, 0);
        sprite(A_RUNES, feet.add(0, 0.06, 0), X, Z, 1.4f, VIOLET, 0.35f * fade, age * 0.03f);
        for (int i = 0; i < 8; i++) {
            float ph = frac(age * 0.015f + i / 8f);
            float a = i * TAU / 8 + age * 0.05f;
            Vec3 p = feet.add(Mth.cos(a) * 0.7, ph * owner.getBbHeight() * 1.2, Mth.sin(a) * 0.7);
            darkTex(SMOKE, p, camRight, camUp, 0.5f + 0.4f * ph, SHADOW, 0.5f * fade * Mth.sin(ph * Mth.PI), i + age * 0.05f);
            if ((i & 1) == 0) flare(p, 0.3f, a, CRIMSON, 0.5f * fade * Mth.sin(ph * Mth.PI));
        }
    }

    private void oblivionCharge(CelestialFxEntity fx, float age, float pt) {
        Entity owner = fx.owner();
        if (owner == null) return;
        float c = Mth.clamp(age / fx.life(), 0, 1);
        Vec3 feet = owner.getPosition(pt).add(0, 0.05, 0);
        Vec3 look = owner.getViewVector(pt);
        Vec3 core = owner.getEyePosition(pt).add(look.scale(1.4)).add(0, 0.3, 0);
        int seed = fx.seedValue();
        abyssSigil(feet, X, Z, 4.5f * easeOut(c), 0.9f, age * 0.03f, -age * 0.05f);
        darkTex(CRACKS, feet.add(0, 0.01, 0), X, Z, 5f * easeOut(c), BLACK, 0.8f, seed);
        sprite(CRACKS, feet.add(0, 0.02, 0), X, Z, 5f * easeOut(c), CRIMSON, 0.6f * c, seed);
        voidCurtain(feet, 4.5f * easeOut(c), 1.5f + 2.5f * c, 0.5f * c, age);
        for (int i = 0; i < 18; i++) {
            float ph = frac(age * 0.04f + i / 18f);
            Vec3 p = core.add(sphereDir(seed, i).scale(5.5 * (1 - ph) * (1 - ph)));
            darkTex(SMOKE, p, camRight, camUp, 0.9f * (1 - ph) + 0.2f, SHADOW, 0.7f * ph, i);
            strip(p, core, 0.05f, 0.01f, VIOLET, VIOLET, 0.5f * ph, 0);
        }
        Vec3 u = perpendicular(look), v = look.cross(u);
        abyssSigil(core, u, v, 0.8f + 1.4f * easeOut(c), 0.5f + 0.5f * c, age * 0.1f, -age * 0.16f);
        float orb = 0.3f + 1.3f * c * c;
        glow(core, orb * 5, CRIMSON, 0.5f * c);
        ring(core, camRight, camUp, orb * 1.05f, 0.15f, CRIMSON, 0.9f, 36, false, 0);
        darkTex(GLOW, core, camRight, camUp, orb * 1.15f, BLACK, 1, 0);
        darkTex(GLOW, core, camRight, camUp, orb, BLACK, 1, 0);
    }

    private void oblivion(CelestialFxEntity fx, float age) {
        Vec3 c = here();
        float life = fx.life();
        float R = fx.radius();
        int seed = fx.seedValue();
        float collapseT = Mth.clamp(age / 25f, 0, 1);
        float burst = Math.max(0, age - 25) / (life - 25);
        float fade = 1 - smooth(0.6f, 1, burst);
        abyssSigil(c.add(0, 0.05, 0), X, Z, R * 1.05f, fade, age * 0.02f, -age * 0.035f);
        darkTex(CRACKS, c.add(0, 0.04, 0), X, Z, R * 1.2f * Math.max(collapseT, 0.3f), BLACK, 0.9f * fade, seed);
        sprite(CRACKS, c.add(0, 0.06, 0), X, Z, R * 1.2f * Math.max(collapseT, 0.3f), CRIMSON, 0.7f * fade, seed);
        voidCurtain(c, R * 1.05f, 3f + 5f * collapseT, 0.6f * fade, age);
        Vec3 heart = c.add(0, 3, 0);
        if (burst <= 0) {
            float s = 0.5f + R * 0.45f * easeOut(collapseT);
            glow(heart, s * 4, CRIMSON, 0.4f);
            for (int i = 0; i < 26; i++) {
                float ph = frac(age * 0.05f + i / 26f);
                Vec3 p = heart.add(sphereDir(seed, i).scale(R * 1.4 * (1 - ph)));
                darkTex(SMOKE, p, camRight, camUp, 1.2f * (1 - ph) + 0.3f, SHADOW, 0.7f * ph, i);
                strip(p, heart, 0.1f, 0.02f, VIOLET, VIOLET, 0.6f * ph, 0);
            }
            sprite(A_RUNES, heart, camRight, camUp, s * 1.5f, CRIMSON, 0.8f, age * 0.1f);
            ring(heart, camRight, camUp, s * 1.03f, 0.3f, CRIMSON, 0.9f, 56, false, 0);
            darkTex(GLOW, heart, camRight, camUp, s * 1.15f, BLACK, 1, 0);
            darkTex(GLOW, heart, camRight, camUp, s, BLACK, 1, 0);
            darkTex(SWIRL, heart, camRight, camUp, s * 1.3f, BLACK, 0.8f, -age * 0.25f);
            return;
        }
        float flash = 1 - smooth(0, 0.15f, burst);
        glow(heart, R * 5 * (0.3f + flash), CRIMSON, 0.6f * flash + 0.2f * fade);
        flare(heart, R * 3 * flash + 1, 0, LILAC, flash);
        float dome = R * 1.4f * easeOut(Math.min(1, burst * 1.8f));
        for (int i = 0; i < 30; i++) {
            Vec3 d = sphereDir(seed, i + 100);
            if (d.y < -0.1) d = new Vec3(d.x, -d.y, d.z);
            darkTex(SMOKE, heart.add(d.scale(dome)), camRight, camUp, R * 0.35f * (0.6f + burst), SHADOW, 0.75f * fade, i + age * 0.03f);
        }
        for (int k = 0; k < 3; k++) {
            float tk = Mth.clamp((burst - k * 0.08f) / 0.6f, 0, 1);
            if (tk <= 0) continue;
            ring(c.add(0, 0.2, 0), X, Z, R * 1.8f * easeOut(tk), 0.8f * (1 - tk) + 0.2f, k == 1 ? VIOLET : CRIMSON, (1 - tk) * fade, 96, false, 0);
        }
        for (int k = 0; k < 8; k++) {
            float a = k * TAU / 8 + CelestialFxRenderer.hash(seed, k) * 0.4f;
            Vec3 base = c.add(Mth.cos(a) * R * 0.85, 0, Mth.sin(a) * R * 0.85);
            float h = (6 + 6 * CelestialFxRenderer.hash(seed, k + 9)) * easeOut(Math.min(1, burst * 3));
            darkBeam(STREAK, base, base.add(0, h, 0), 0.8f, 0.1f, BLACK, 0.9f * fade, -age * 0.2f, -age * 0.2f + h / 4);
            streakBeam(base, base.add(0, h, 0), 0.3f, 0.05f, CRIMSON, 0.8f * fade, -age * 0.3f);
        }
    }

    private void inkSplash(CelestialFxEntity fx, float age) {
        Vec3 c = here();
        float life = fx.life();
        float r = fx.radius();
        int seed = fx.seedValue();
        double ground = groundY(fx, c);
        float wrap = 1 - smooth(4, 9, age);
        for (int k = 0; k < 3; k++) {
            Vec3 prev = null;
            for (int i = 0; i <= 14; i++) {
                float t = i / 14f;
                float a = t * TAU * 1.5f + k * TAU / 3 + age * 0.5f;
                float rad = (0.9f - 0.5f * t) * r * (0.6f + 0.4f * wrap);
                Vec3 p = c.add(Mth.cos(a) * rad, (t - 0.5) * 1.2 * r, Mth.sin(a) * rad);
                if (prev != null) darkBeam(STREAK, prev, p, 0.12f, 0.12f, BLACK, 0.9f * wrap, t, t + 0.1f);
                prev = p;
            }
        }
        if (age < 3) glow(c, 3f * r, CRIMSON, 0.5f * (1 - age / 3));
        float g = 0.045f;
        for (int i = 0; i < 20; i++) {
            Vec3 d = sphereDir(seed, i);
            Vec3 v = new Vec3(d.x, Math.abs(d.y) * 0.8 + 0.3, d.z).scale(0.18 + 0.22 * CelestialFxRenderer.hash(seed, i + 50));
            float t0 = 3 + CelestialFxRenderer.hash(seed, i + 9) * 3;
            float t = age - t0;
            if (t < 0) continue;
            double y0 = c.y - ground;
            double land = (v.y + Math.sqrt(v.y * v.y + 2 * g * y0)) / g;
            if (t < land) {
                Vec3 p = c.add(v.scale(t)).add(0, -0.5 * g * t * t, 0);
                Vec3 vel = new Vec3(v.x, v.y - g * t, v.z);
                darkBeam(STREAK, p, p.subtract(vel.scale(2.2)), 0.07f, 0.01f, BLACK, 0.9f, 0, 1);
                darkTex(SMOKE, p, camRight, camUp, 0.1f, BLACK, 0.9f, i);
            } else {
                Vec3 at = new Vec3(c.x + v.x * land, ground + 0.02 + i * 0.0006, c.z + v.z * land);
                float since = (float) (t - land);
                float grow = 0.18f + 0.22f * easeOut(Math.min(1, since / 6f));
                float fade = 1 - smooth(life - t0 - 25, life - t0, (float) t);
                darkTex(SMOKE, at, X, Z, grow * (0.7f + 0.6f * CelestialFxRenderer.hash(seed, i + 3)), BLACK, 0.85f * fade, i);
                if (since < 8) ring(at, X, Z, grow * 1.3f, 0.03f, CRIMSON, 0.7f * (1 - since / 8), 16, false, 0);
            }
        }
        float fade = 1 - smooth(life - 25, life, age);
        darkTex(SMOKE, new Vec3(c.x, ground + 0.015, c.z), X, Z, r * (0.6f + 0.5f * easeOut(Math.min(1, age / 10f))), BLACK, 0.9f * fade, seed);
    }

    private double groundY(CelestialFxEntity fx, Vec3 c) {
        if (Double.isNaN(fx.clientGroundY)) {
            HitResult down = fx.level().clip(new ClipContext(c, c.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, fx));
            fx.clientGroundY = down.getType() == HitResult.Type.MISS ? c.y - 12 : down.getLocation().y;
        }
        return fx.clientGroundY;
    }

    private void rect(ResourceLocation tex, Vec3 c, Vec3 u, Vec3 v, float hw, float hh, float[] col, float a) {
        if (a <= 0.003f) return;
        var buf = buffers.getBuffer(VfxRenderTypes.lightTextured(tex));
        Vec3 ru = u.scale(hw), rv = v.scale(hh);
        texVertex(buf, c.subtract(ru).subtract(rv), 0, 0, col, a);
        texVertex(buf, c.add(ru).subtract(rv), 1, 0, col, a);
        texVertex(buf, c.add(ru).add(rv), 1, 1, col, a);
        texVertex(buf, c.subtract(ru).add(rv), 0, 1, col, a);
    }

    private void rectDark(ResourceLocation tex, Vec3 c, Vec3 u, Vec3 v, float hw, float hh, float[] col, float a) {
        if (a <= 0.003f) return;
        var buf = buffers.getBuffer(VfxRenderTypes.glowTextured(tex));
        Vec3 ru = u.scale(hw), rv = v.scale(hh);
        entityVertex(buf, c.subtract(ru).subtract(rv), 0, 0, col, a);
        entityVertex(buf, c.add(ru).subtract(rv), 1, 0, col, a);
        entityVertex(buf, c.add(ru).add(rv), 1, 1, col, a);
        entityVertex(buf, c.subtract(ru).add(rv), 0, 1, col, a);
    }

private void abyssSigil(Vec3 c, Vec3 u, Vec3 v, float r, float a, float spinOuter, float spinInner) {
        if (a <= 0.003f || r <= 0.01f) return;
        darkTex(GLOW, c, u, v, r * 1.35f, BLACK, 0.8f * a, 0);
        sprite(A_RUNES, c, u, v, r, VIOLET, a, spinOuter);
        sprite(A_SEAL, c, u, v, r * 0.74f, CRIMSON, a, spinInner);
        sprite(A_RUNES, c, u, v, r, LILAC, 0.25f * a, spinOuter);
    }

    private void voidCurtain(Vec3 c, float r, float height, float a, float time) {
        if (a <= 0.003f || r <= 0.05f || height <= 0.05f) return;
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(Mth.cos(a0) * r, 0, Mth.sin(a0) * r), p1 = c.add(Mth.cos(a1) * r, 0, Mth.sin(a1) * r);
            float[] col = (i & 3) == 0 ? CRIMSON : VIOLET;
            wall(p0, p1, height, col, a, time);
        }
    }

    private void wall(Vec3 p0, Vec3 p1, float height, float[] col, float a, float time) {
        var buf = buffers.getBuffer(VfxRenderTypes.lightTextured(STREAK));
        float scroll = -time * 0.02f;
        texVertex(buf, p0, scroll, 0, col, a);
        texVertex(buf, p1, scroll, 1, col, a);
        texVertex(buf, p1.add(0, height, 0), scroll + 1, 1, col, 0);
        texVertex(buf, p0.add(0, height, 0), scroll + 1, 0, col, 0);
    }

    private void tendril(Vec3 base, float lean, float height, float time, float width, float a) {
        if (height <= 0.05f || a <= 0.003f) return;
        int n = 8;
        Vec3 prev = base;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float sway = 0.35f * Mth.sin(time * 0.15f + t * 3f) * t;
            float curl = t * t * 1.1f;
            Vec3 p = base.add(Mth.cos(lean) * curl + Mth.cos(lean + Mth.HALF_PI) * sway, height * t,
                    Mth.sin(lean) * curl + Mth.sin(lean + Mth.HALF_PI) * sway);
            float w0 = width * (1 - (i - 1) / (float) n), w1 = width * (1 - t) + 0.02f;
            darkBeam(STREAK, prev, p, w0, w1, BLACK, 0.95f * a, time * 0.05f + t, time * 0.05f + t + 0.2f);
            strip(prev, p, w0 * 1.6f, w1 * 1.6f, VIOLET, VIOLET, 0.25f * a, 0.25f * a);
            prev = p;
        }
        flare(prev, 0.8f, time * 0.1f, CRIMSON, a);
    }
}
