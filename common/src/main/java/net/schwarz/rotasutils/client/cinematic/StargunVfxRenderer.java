package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static net.schwarz.rotasutils.ability.StargunTimings.*;

@Environment(EnvType.CLIENT)
public final class StargunVfxRenderer {
    private StargunVfxRenderer() {
    }

    private static final float[] CY = {0.30f, 0.88f, 1.0f, 1f}, WHITE = {1f, 0.97f, 0.92f, 1f}, MG = {1.0f, 0.32f, 0.85f, 1f},
            VI = {0.48f, 0.28f, 1.0f, 1f}, GOLD = {1.0f, 0.80f, 0.38f, 1f};
    private static final Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);
    private static final double RUNE_RIM = 0.955, SHOCK_RIM = 0.93;

    private static float[] c(float[] base, double a) {
        return new float[]{base[0], base[1], base[2], (float) Math.max(0, Math.min(1, a))};
    }

    private static float[] c(double r, double g, double b, double a) {
        return new float[]{(float) r, (float) g, (float) b, (float) Math.max(0, Math.min(1, a))};
    }

    private static double rnd(long seed, int i, int k) {
        return RedVfxRenderer.rnd(seed, i, k);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Vec3 plane(Vec3 centre, Vec3 u, Vec3 w, double a, double r) {
        return centre.add(u.scale(Math.cos(a) * r)).add(w.scale(Math.sin(a) * r));
    }

    private static double backOut(double x) {
        x = Curves.clamp01(x) - 1;
        return 1 + 2.7 * x * x * x + 1.7 * x * x;
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    static final class Ctx {
        final Mesh m;
        final Paint add, under, solid;
        final Stargun.Scene s;
        final ClientCast cast;
        final double t;
        final Vec3 cam, forward;
        final boolean alien;
        final double portalRadius, far;
        final long seed;
        final float[] lightColor = {0.45f, 0.95f, 1.0f};

        Ctx(Mesh m, Paint add, Paint under, Paint solid, Stargun.Scene s, ClientCast cast, double t, Vec3 cam, Vec3 forward, boolean alien, double far) {
            this.m = m;
            this.add = add;
            this.under = under;
            this.solid = solid;
            this.s = s;
            this.cast = cast;
            this.t = t;
            this.cam = cam;
            this.forward = forward;
            this.alien = alien;
            this.far = far;
            this.portalRadius = PORTAL_RADIUS * portal(t);
            this.seed = cast.seed;
        }

        double mask(Vec3 p) {
            if (alien) {
                return 1;
            }
            double sp = p.subtract(s.portal).dot(s.axis);
            if (sp >= 0) {
                return 1;
            }
            if (portalRadius < 0.5) {
                return 0;
            }
            double sc = cam.subtract(s.portal).dot(s.axis);
            if (sc <= 0) {
                return 1;
            }
            double k = sc / (sc - sp);
            double r = cam.add(p.subtract(cam).scale(k)).distanceTo(s.portal);
            return Curves.smoothstep((portalRadius - r) / (portalRadius * 0.04 + 0.4));
        }

        boolean otherWorldVisible() {
            return alien || portalRadius >= 0.5;
        }
    }

static double dusk(double t) {
        return Curves.smoothstep(Curves.window(t, 0.3, 4.5)) * (1 - Curves.smoothstep(Curves.window(t, RAY_FADE, CLOSE_END)));
    }

    private static double aim(double t) {
        return backOut(Curves.window(t, CHARGE + 2.0, CHARGE + 3.6)) * (1 - 0.7 * Curves.smoothstep(Curves.window(t, IMPACT, IMPACT + 5)))
                * (1 - Curves.smoothstep(Curves.window(t, FRONT_END - 3, FRONT_END)));
    }

private static Vec3 vec(org.joml.Vector3f v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    public static void render(PoseStack poseStack, Camera camera, float partialTick, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientCasts.all().isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Matrix4f view = poseStack.last().pose();
        Vec3 left = vec(camera.getLeftVector()), up = vec(camera.getUpVector());
        Vec3 forward = left.cross(up).normalize();
        Mesh mesh = new Mesh(view, cam, camera.getLeftVector(), camera.getUpVector());
        Paint add = new Paint(view, cam, left, up), under = new Paint(view, cam, left, up), solid = new Paint(view, cam, left, up);
        double far = mc.gameRenderer.getDepthFar();
        List<Ctx> draws = new ArrayList<>();
        for (ClientCast cast : ClientCasts.all()) {
            if (!cast.stargun() || cast.cancelled) {
                continue;
            }
            Stargun.Scene scene = Stargun.scene(cast);
            double t = cast.time(partialTick);
            if (scene == null || t < 0 || t > cast.finishedAt()) {
                continue;
            }
            boolean alien = cast.local && StargunCamera.weight(t) > 0.5 && cam.subtract(scene.portal).dot(scene.axis) < 0;
            Ctx context = new Ctx(mesh, add, under, solid, scene, cast, t, cam, forward, alien, far);
            if (t >= END) {
                postFx(context, cam, view, projection);
            } else {
                draws.add(context);
            }
        }
        if (draws.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            for (Ctx x : draws) {
                if (x.alien) {
                    RenderSystem.disableDepthTest();
                    RenderSystem.disableBlend();
                    RenderSystem.depthMask(false);
                    mesh.begin();
                    alienSky(x, Math.min(200, far * 0.55));
                    mesh.draw();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                    RenderSystem.clear(256, Minecraft.ON_OSX);
                    RenderSystem.enableBlend();
                }
            }
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            mesh.begin();
            for (Ctx x : draws) {
                if (x.alien) {
                    window(x);
                } else {
                    duskDome(x, Math.min(300, far * 0.92));
                    disc(x);
                }
            }
            mesh.draw();
            for (Ctx x : draws) {
                shade(x);
            }
            under.flush();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.depthMask(true);
            mesh.begin();
            for (Ctx x : draws) {
                if (x.otherWorldVisible()) {
                    StargunModel.planet(x);
                    StargunModel.asteroids(x);
                    for (int i = 0; i < x.s.islands.length; i++) {
                        StargunModel.island(x, i);
                    }
                    StargunModel.gun(x);
                }
                pieces(x);
            }
            mesh.draw();
            solid.flush();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.depthMask(false);
            mesh.begin();
            for (Ctx x : draws) {
                signal(x);
                portalLight(x);
                otherWorldLight(x);
                aimLight(x);
                ray(x);
                spread(x);
                impact(x);
                crossing(x);
            }
            mesh.draw();
            add.flush();
        } finally {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        for (Ctx x : draws) {
            if (x.cast.local || StargunAtmosphere.red(x.t) > 0) {
                postFx(x, cam, view, projection);
            }
        }
    }

private static void duskDome(Ctx x, double radius) {
        double dusk = dusk(x.t);
        if (dusk < 0.02) {
            return;
        }
        double flare = x.t >= IMPACT ? Math.exp(-(x.t - IMPACT) / 1.2) : 0;
        if (x.t >= FIRE) {
            flare = Math.max(flare, 0.55 * Math.exp(-(x.t - FIRE) / 0.5));
        }
        final double glare = flare;
        x.m.sphere(x.cam.x, x.cam.y, x.cam.z, radius, 14, 26, (nx, ny, nz, fres, out) -> {
            double horizon = Math.exp(-Math.abs(ny) * 4.5);
            out[0] = (float) (0.04 + 0.36 * horizon + 0.5 * glare);
            out[1] = (float) (0.03 + 0.07 * horizon + 0.5 * glare);
            out[2] = (float) (0.14 + 0.26 * horizon + 0.5 * glare);
            out[3] = (float) (dusk * 0.84);
        });
    }

    private static void alienSky(Ctx x, double radius) {
        double t = x.t;
        x.m.sphere(x.cam.x, x.cam.y, x.cam.z, radius, 22, 40, (nx, ny, nz, fres, out) -> {
            double n = Noise3.fbm(nx * 2.4 + 4.0, ny * 2.4 + t * 0.01, nz * 2.4);
            double band = Math.exp(-Math.pow((ny * 0.9 + nx * 0.35 + 0.05) * 3.0, 2));
            double up = Math.max(0, ny);
            out[0] = (float) (0.02 + 0.05 * up + 0.30 * band + 0.20 * n * n);
            out[1] = (float) (0.02 + 0.03 * up + 0.07 * band + 0.06 * n);
            out[2] = (float) (0.10 + 0.10 * up + 0.30 * band + 0.40 * n * n);
            out[3] = 1f;
        });
    }

private static void discColor(double f, double ang, double t, float[] out) {
        double sw = (0.5 + 0.5 * Math.sin(ang * 3 + f * 7 - t * 1.4)) * (0.5 + 0.5 * Math.sin(ang * 5 - f * 11 + t * 2.1));
        out[0] = (float) (Curves.lerp(0.03, 0.30, f) + 0.10 * sw);
        out[1] = (float) (Curves.lerp(0.02, 0.09, f) + 0.26 * sw * f);
        out[2] = (float) (Curves.lerp(0.11, 0.56, f) + 0.28 * sw);
        out[3] = (float) (f > 0.85 ? 0.96 - 0.5 * (f - 0.85) / 0.15 : 0.96);
    }

    private static void disc(Ctx x) {
        double r = x.portalRadius;
        if (r < 0.2) {
            return;
        }
        Stargun.Scene s = x.s;
        double open = portal(x.t);
        double jag = 0.32 * Math.pow(1 - open, 2);
        int n = 72;
        double[] edge = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            edge[i] = r * (1 + jag * (2 * rnd(x.seed, 3000 + i % n, 1) - 1));
        }
        double[] f = {0, 0.4, 0.75, 1.0};
        float[] a = new float[4], b = new float[4], cc = new float[4], d = new float[4];
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            for (int k = 0; k < 3; k++) {
                discColor(f[k], a0, x.t, a);
                discColor(f[k + 1], a0, x.t, b);
                discColor(f[k + 1], a1, x.t, cc);
                discColor(f[k], a1, x.t, d);
                Vec3 pa = plane(s.portal, s.pu, s.pw, a0, edge[i] * f[k]), pb = plane(s.portal, s.pu, s.pw, a0, edge[i] * f[k + 1]);
                Vec3 pc = plane(s.portal, s.pu, s.pw, a1, edge[i + 1] * f[k + 1]), pd = plane(s.portal, s.pu, s.pw, a1, edge[i + 1] * f[k]);
                x.m.v(pa.x, pa.y, pa.z, a[0], a[1], a[2], a[3]);
                x.m.v(pb.x, pb.y, pb.z, b[0], b[1], b[2], b[3]);
                x.m.v(pc.x, pc.y, pc.z, cc[0], cc[1], cc[2], cc[3]);
                x.m.v(pa.x, pa.y, pa.z, a[0], a[1], a[2], a[3]);
                x.m.v(pc.x, pc.y, pc.z, cc[0], cc[1], cc[2], cc[3]);
                x.m.v(pd.x, pd.y, pd.z, d[0], d[1], d[2], d[3]);
            }
        }
    }

    private static void window(Ctx x) {
        double r = x.portalRadius;
        if (r < 0.2) {
            return;
        }
        Stargun.Scene s = x.s;
        int n = 64;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 p0 = plane(s.portal, s.pu, s.pw, a0, r), p1 = plane(s.portal, s.pu, s.pw, a1, r);
            x.m.v(s.portal.x, s.portal.y, s.portal.z, 0.50f, 0.58f, 0.82f, 0.97f);
            x.m.v(p0.x, p0.y, p0.z, 0.16f, 0.24f, 0.50f, 0.97f);
            x.m.v(p1.x, p1.y, p1.z, 0.16f, 0.24f, 0.50f, 0.97f);
        }
    }

    private static void shade(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t, open = portal(t);
        if (!x.alien && open > 0.02) {
            double fade = open * open * (1 - Curves.smoothstep(Curves.window(t, RAY_GONE, CLOSE_END)));
            x.under.on(Paint.CLOUD);
            for (int arm = 0; arm < 5; arm++) {
                for (int k = 0; k < 9; k++) {
                    double u = (k + rnd(x.seed, 5000 + arm * 16 + k, 1)) / 9.0;
                    double a = arm * Math.PI * 2 / 5 + u * 2.4 - t * 0.09 * (1.6 - u);
                    double rad = PORTAL_RADIUS * open * (1.12 + 1.55 * u);
                    Vec3 p = plane(s.portal, s.pu, s.pw, a, rad).subtract(s.axis.scale(2 + 3 * rnd(x.seed, 5000 + arm * 16 + k, 2)));
                    double size = PORTAL_RADIUS * (0.42 + 0.40 * u);
                    x.under.plane(p, s.pu, s.pw, size, a + k, c(0.10 + 0.10 * (1 - u), 0.06, 0.22 + 0.10 * (1 - u), 0.62 * fade * (1 - 0.55 * u)));
                }
            }
        }
        double aim = aim(t);
        if (aim > 0.01) {
            x.under.on(Paint.GLOW).plane(s.centre.add(0, 0.12, 0), X, Z, RADIUS * 1.35, 0, c(0.03, 0.02, 0.12, 0.62 * Math.min(1, aim)));
        }
        double feet = casterSeal(t);
        if (feet > 0.01) {
            x.under.on(Paint.GLOW).plane(s.caster.add(0, 0.05, 0), X, Z, 4.2, 0, c(0.03, 0.02, 0.12, 0.6 * Math.min(1, feet)));
        }
    }

    private static void portalLight(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t, open = portal(t);
        if (open <= 0.001) {
            return;
        }
        double r = x.portalRadius;
        double fade = 1 - Curves.smoothstep(Curves.window(t, RAY_GONE, CLOSE_END));
        double power = StargunModel.power(t);
        double cracks = 1 - Curves.smoothstep(Curves.window(open, 0.45, 0.9));
        if (cracks > 0.01) {
            for (int i = 0; i < 13; i++) {
                double a = Math.PI * 2 * i / 13 + rnd(x.seed, 3100 + i, 1) * 0.5;
                double len = PORTAL_RADIUS * (0.25 + 1.1 * Math.min(1, open * 2.6)) * (0.6 + 0.5 * rnd(x.seed, 3100 + i, 2));
                Vec3 prev = s.portal;
                for (int k = 1; k <= 8; k++) {
                    double u = k / 8.0, wob = (rnd(x.seed, 3100 + i, 10 + k) - 0.5) * 0.35;
                    Vec3 p = plane(s.portal, s.pu, s.pw, a + wob, len * u);
                    x.m.ribbon(prev, p, 0.9 * (1 - u * 0.7), 0.9 * (1 - u * 0.7 - 0.1), c(WHITE, 0.95 * cracks), c(CY, 0.7 * cracks));
                    prev = p;
                }
            }
            x.add.on(Paint.GLOW).billboard(s.portal, r * 1.6 + 6, 0, c(CY, 0.45 * cracks));
            x.add.on(Paint.FLARE).billboard(s.portal, (r + 12) * 2.2 * cracks, t * 0.3, c(WHITE, 0.9 * cracks));
        }
        double kick = t >= FIRE ? Math.exp(-(t - FIRE) / 0.7) : 0;
        if (kick > 0.02) {
            x.add.on(Paint.FLARE).billboard(s.portal, r * 3.4 * (0.4 + kick), t, c(WHITE, 0.9 * kick));
            x.add.on(Paint.SHOCK).plane(s.portal, s.pu, s.pw, r * (1.0 + 1.6 * (1 - kick)) / SHOCK_RIM, 0, c(WHITE, 0.8 * kick));
        }
        double w = 0.7 + 1.5 * open + 3.0 * kick, spin = t * 0.25;
        x.add.on(Paint.SHOCK).plane(s.portal, s.pu, s.pw, r / SHOCK_RIM * 1.02, spin, c(WHITE, 0.9 * fade));
        x.add.plane(s.portal, s.pu, s.pw, r / SHOCK_RIM * 1.10, -spin, c(CY, 0.6 * fade));
        RedVfxRenderer.ring(x.m, s.portal, s.pu, s.pw, r, w, c(WHITE, 0.95 * fade), 96, spin, 0, -2);
        RedVfxRenderer.ring(x.m, s.portal, s.pu, s.pw, r * 0.90, w * 0.8, c(MG, 0.5 * fade), 96, spin * 2, 1.0, -0.2);
        double seal = Curves.smoothstep(Curves.window(open, 0.5, 1.0)) * fade;
        double turn = t * (0.10 + 0.35 * power);
        x.add.on(Paint.RUNES).plane(s.portal, s.pu, s.pw, r * 1.30 / RUNE_RIM, turn, c(CY, 0.95 * seal));
        x.add.plane(s.portal, s.pu, s.pw, r * 1.30 / RUNE_RIM, turn, c(WHITE, 0.40 * seal));
        x.add.plane(s.portal, s.pu, s.pw, r * 1.85 / RUNE_RIM, -turn * 0.6, c(VI, 0.65 * seal));
        x.add.on(Paint.SEAL).plane(s.portal, s.pu, s.pw, r * 1.02, -turn * 0.4, c(MG, (0.16 + 0.22 * power) * seal));
        for (int arm = 0; arm < 5; arm++) {
            Vec3 prev = null;
            for (int k = 0; k <= 26; k++) {
                double u = k / 26.0, a = arm * Math.PI * 2 / 5 + u * 3.4 - t * 0.8;
                Vec3 p = plane(s.portal, s.pu, s.pw, a, r * (0.08 + 0.86 * u));
                if (prev != null) {
                    x.m.ribbon(prev, p, 0.9 * open, 1.2 * open, c(CY, 0.14 * fade * u), c(MG, 0.20 * fade * u));
                }
                prev = p;
            }
        }
        double through = Curves.window(t, EMERGE - 0.5, EMERGE_END + 1.5) * (1 - Curves.smoothstep(Curves.window(t, EMERGE_END + 1.5, EMERGE_END + 3.5)));
        if (through > 0.01) {
            x.add.on(Paint.SHOCK);
            for (int i = 0; i < 4; i++) {
                double u = frac((t - EMERGE) * 0.55 + i * 0.25);
                x.add.plane(s.portal, s.pu, s.pw, r * (0.55 + 0.7 * u) / SHOCK_RIM, 0, c(i % 2 == 0 ? CY : WHITE, 0.75 * through * (1 - u)));
            }
            x.add.on(Paint.GLOW).billboard(s.portal, r * 2.1, 0, c(WHITE, 0.30 * through));
            x.add.on(Paint.FLARE).billboard(s.portal, r * 3.0, t * 0.5, c(CY, 0.25 * through));
        }
        if (x.alien) {
            x.add.on(Paint.GLOW).plane(s.portal, s.pu, s.pw, r * 1.7, 0, c(0.55, 0.78, 1.0, 0.5 * fade));
            return;
        }
        x.add.on(Paint.CLOUD);
        for (int arm = 0; arm < 5; arm++) {
            for (int k = 0; k < 5; k++) {
                double u = (k + 0.5) / 9.0;
                double a = arm * Math.PI * 2 / 5 + u * 2.4 - t * 0.09 * (1.6 - u) + 0.2;
                Vec3 p = plane(s.portal, s.pu, s.pw, a, PORTAL_RADIUS * open * (1.10 + 1.5 * u)).subtract(s.axis.scale(1));
                x.add.plane(p, s.pu, s.pw, PORTAL_RADIUS * (0.34 + 0.3 * u), a, c(arm % 2 == 0 ? VI : MG, (0.16 + 0.14 * power) * fade * open * (1 - u)));
            }
        }
        x.add.on(Paint.STREAK);
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18 + rnd(x.seed, 5200 + i, 1);
            Vec3 from = plane(s.portal, s.pu, s.pw, a, r * (0.35 + 0.6 * rnd(x.seed, 5200 + i, 2)));
            Vec3 dir = s.axis.add(plane(Vec3.ZERO, s.pu, s.pw, a, 0.22)).normalize();
            double len = 180 + 140 * rnd(x.seed, 5200 + i, 3);
            double al = (0.10 + 0.22 * power) * fade * open * (0.6 + 0.4 * Math.sin(t * 0.9 + i * 1.7));
            x.add.strip(from, from.add(dir.scale(len)), 3.5, 11, t * 0.05 + i * 0.13, t * 0.05 + i * 0.13 + 0.6, c(CY, al), c(VI, 0));
        }
        long key = (long) Math.floor(t * 10);
        for (int i = 0; i < 7; i++) {
            if (rnd(x.seed ^ key, 5300 + i, 1) > 0.30 + 0.45 * power) {
                continue;
            }
            double a = rnd(x.seed ^ key, 5300 + i, 2) * Math.PI * 2;
            Vec3 from = plane(s.portal, s.pu, s.pw, a, r);
            Vec3 to = plane(s.portal, s.pu, s.pw, a + (rnd(x.seed ^ key, 5300 + i, 3) - 0.5) * 0.9, r * (1.35 + 0.7 * rnd(x.seed ^ key, 5300 + i, 4)))
                    .add(s.axis.scale(20 * rnd(x.seed ^ key, 5300 + i, 5)));
            bolt(x, from, to, r * 0.10, x.seed ^ key ^ i, 0.9 * fade * open);
        }
        x.add.on(Paint.GLOW).billboard(s.portal, r * 2.4, 0, c(VI, 0.22 * fade * open));
    }

    private static void bolt(Ctx x, Vec3 from, Vec3 to, double jag, long seed, double alpha) {
        Vec3[] bs = RedVfxRenderer.basis(to.subtract(from).normalize());
        Vec3 prev = from;
        int n = 9;
        for (int k = 1; k <= n; k++) {
            double u = (double) k / n, pin = Math.sin(Math.PI * u);
            Vec3 p = lerp(from, to, u).add(bs[0].scale((rnd(seed, k, 1) - 0.5) * 2 * jag * pin)).add(bs[1].scale((rnd(seed, k, 2) - 0.5) * 2 * jag * pin));
            x.m.ribbon(prev, p, 0.9, 0.9, c(CY, 0.5 * alpha), c(CY, 0.5 * alpha));
            x.m.ribbon(prev, p, 0.28, 0.28, c(WHITE, alpha), c(WHITE, alpha));
            prev = p;
        }
    }

    private static void crossing(Ctx x) {
        if (!x.cast.local || StargunCamera.weight(x.t) < 0.5 || x.portalRadius < 1) {
            return;
        }
        Vec3 rel = x.cam.subtract(x.s.portal);
        double through = rel.dot(x.s.axis);
        if (Math.abs(through) > 14 || rel.subtract(x.s.axis.scale(through)).length() > x.portalRadius) {
            return;
        }
        x.add.on(Paint.GLOW).billboard(x.cam.add(x.forward.scale(2)), 9, 0, c(0.85, 0.95, 1.0, 0.95 * Math.exp(-Math.abs(through) / 3.5)));
    }

private static double casterSeal(double t) {
        return backOut(Curves.window(t, 0.5, 1.3)) * (1 - Curves.smoothstep(Curves.window(t, CAMERA_RETURN, END - 0.3)));
    }

    private static void signal(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t;
        double seal = casterSeal(t);
        if (seal > 0.01) {
            Vec3 feet = s.caster.add(0, 0.08, 0);
            double pulse = 0.85 + 0.15 * Math.sin(t * 5);
            x.add.on(Paint.RUNES).plane(feet, X, Z, 2.9 * seal, t * 0.5, c(CY, 0.9 * pulse));
            x.add.plane(feet, X, Z, 2.9 * seal, t * 0.5, c(WHITE, 0.35));
            x.add.on(Paint.SEAL).plane(feet, X, Z, 2.0 * seal, -t * 0.8, c(MG, 0.7 * pulse));
            x.add.on(Paint.STREAK);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12 + t * 0.5;
                Vec3 p = feet.add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
                double h = 1.2 + 0.8 * Math.sin(t * 3 + i * 1.3);
                x.add.strip(p, p.add(0, h, 0), 0.22, 0.04, t * 0.4 + i * 0.2, t * 0.4 + i * 0.2 + 0.5, c(CY, 0.5 * Math.min(1, seal)), c(CY, 0));
            }
        }
        Vec3 hand = s.hand();
        double gather = Curves.smoothstep(Curves.window(t, 0.8, SIGNAL)) * (t < SIGNAL ? 1 : Math.exp(-(t - SIGNAL) / 0.15));
        if (gather > 0.01) {
            x.add.on(Paint.GLOW).billboard(hand, 0.9 * gather, 0, c(CY, 0.8));
            x.add.on(Paint.FLARE).billboard(hand, 1.6 * gather + (t >= SIGNAL ? 3.5 : 0), t * 2, c(WHITE, 0.9 * gather));
        }
        if (t >= SIGNAL && t < RIFT + 0.05) {
            Vec3 p = s.signal(t), tail = s.signal(Math.max(SIGNAL, t - 0.45));
            double size = Math.max(1.4, 0.06 * p.distanceTo(x.cam));
            x.add.on(Paint.STREAK).strip(tail, p, size * 0.15, size * 0.55, 0, 1, c(CY, 0), c(WHITE, 0.95));
            x.add.on(Paint.GLOW).billboard(p, size * 1.6, 0, c(CY, 0.8));
            x.add.on(Paint.FLARE).billboard(p, size * 3.2, t * 3, c(WHITE, 1));
        }
        if (t >= RIFT && t < RIFT + 1.6) {
            double age = t - RIFT, k = Math.exp(-age / 0.4);
            x.add.on(Paint.FLARE).billboard(s.portal, 70 * backOut(age / 0.18) * (0.35 + k), age, c(WHITE, k));
            x.add.on(Paint.SHOCK).plane(s.portal, s.pu, s.pw, 12 + 90 * Math.sqrt(age), 0, c(CY, 0.8 * Math.exp(-age / 0.6)));
        }
    }

private static void otherWorldLight(Ctx x) {
        Stargun.Scene s = x.s;
        if (!x.otherWorldVisible()) {
            return;
        }
        double t = x.t;
        Vec3 sun = s.sun();
        double sm = x.mask(sun);
        if (sm > 0.02) {
            x.add.on(Paint.GLOW).billboard(sun, 130, 0, c(1.0, 0.82, 0.62, 0.55 * sm));
            x.add.billboard(sun, 46, 0, c(WHITE, 0.95 * sm));
            x.add.on(Paint.FLARE).billboard(sun, 210, 0.3, c(1.0, 0.92, 0.80, 0.9 * sm));
        }
        x.add.on(Paint.CLOUD);
        for (int i = 0; i < 14; i++) {
            Vec3 p = s.portal.add(s.view.scale(150 + 190 * rnd(s.seed, 3500 + i, 1)))
                    .add(s.vu.scale((rnd(s.seed, 3500 + i, 2) - 0.5) * 340)).add(s.vw.scale((rnd(s.seed, 3500 + i, 3) - 0.5) * 240));
            double mask = x.mask(p);
            if (mask > 0.02) {
                x.add.billboard(p, 70 + 90 * rnd(s.seed, 3500 + i, 4), i * 1.3, c(i % 3 == 0 ? MG : i % 3 == 1 ? VI : CY, 0.20 * mask));
            }
        }
        for (int i = 0; i < s.stars.length; i++) {
            Vec3 p = s.stars[i];
            double mask = x.mask(p);
            if (mask < 0.02) {
                continue;
            }
            double size = s.starSize[i] * Math.max(1.0, x.cam.distanceTo(p) / 95.0);
            double tw = 0.6 + 0.4 * Math.sin(t * (1.5 + rnd(s.seed, i, 7) * 3) + i);
            float[] col = i % 7 == 0 ? c(MG, tw * mask) : i % 3 == 0 ? c(CY, tw * mask) : c(WHITE, tw * mask);
            if (i % 9 == 0) {
                x.add.on(Paint.FLARE).billboard(p, size * 5, i, col);
            } else {
                x.add.on(Paint.GLOW).billboard(p, size * 1.5, 0, col);
            }
        }
        double pm = x.mask(s.planet);
        if (pm > 0.02) {
            x.add.on(Paint.GLOW).billboard(s.planet.add(StargunModel.SUN.scale(s.planetRadius * 0.35)), s.planetRadius * 2.3, 0, c(1.0, 0.55, 0.45, 0.30 * pm));
        }
        x.add.on(Paint.FLARE);
        for (int i = 0; i < s.islands.length; i++) {
            for (int k = 0; k < 3; k++) {
                Vec3 tip = StargunModel.crystalTip(s, t, i, k);
                double mask = x.mask(tip);
                if (mask > 0.02) {
                    x.add.billboard(tip, s.islandSize[i] * (0.5 + 0.2 * Math.sin(t * 2 + i + k)), t * 0.5 + k, c(CY, 0.85 * mask));
                }
            }
        }
        aurora(x);
        comets(x);
        if (x.alien) {
            dust(x);
        }
        gunLight(x);
    }

    private static void aurora(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t;
        x.add.on(Paint.STREAK);
        for (int k = 0; k < 6; k++) {
            double base = rnd(s.seed, 8800 + k, 1) * Math.PI * 2;
            Vec3 prev = null, prevTop = null;
            double height = 150 + 90 * rnd(s.seed, 8800 + k, 2), dist = 520 + 80 * rnd(s.seed, 8800 + k, 3);
            float[] col = k % 3 == 0 ? new float[]{0.35f, 1.0f, 0.65f, 1f} : k % 3 == 1 ? c(CY, 1) : c(VI, 1);
            for (int i = 0; i <= 18; i++) {
                double u = i / 18.0, a = base + (u - 0.5) * 1.5;
                double wave = Math.sin(u * 7 + t * 0.35 + k) * 35 + Math.sin(u * 3 - t * 0.2) * 55;
                Vec3 foot = s.portal.add(Math.cos(a) * dist, 90 + wave, Math.sin(a) * dist);
                Vec3 top = foot.add(0, height * (0.7 + 0.3 * Math.sin(u * 5 + t * 0.4 + k)), 0);
                if (prev != null) {
                    double m = x.mask(foot) * Math.sin(Math.PI * Math.min(0.999, u));
                    if (m > 0.02) {
                        float[] bot = c(col, 0.0), up = c(col, 0.0);
                        float[] mid = c(col, 0.30 * m);
                        x.add.corner(prev, u * 3 - t * 0.02, 0, bot[0], bot[1], bot[2], 0.0f);
                        x.add.corner(foot, (u + 0.055) * 3 - t * 0.02, 0, bot[0], bot[1], bot[2], 0.0f);
                        x.add.corner(foot.add(0, height * 0.35, 0), (u + 0.055) * 3 - t * 0.02, 0.5, mid[0], mid[1], mid[2], mid[3]);
                        x.add.corner(prev.add(0, height * 0.35, 0), u * 3 - t * 0.02, 0.5, mid[0], mid[1], mid[2], mid[3]);
                        x.add.corner(prev.add(0, height * 0.35, 0), u * 3 - t * 0.02, 0.5, mid[0], mid[1], mid[2], mid[3]);
                        x.add.corner(foot.add(0, height * 0.35, 0), (u + 0.055) * 3 - t * 0.02, 0.5, mid[0], mid[1], mid[2], mid[3]);
                        x.add.corner(top, (u + 0.055) * 3 - t * 0.02, 1, up[0], up[1], up[2], 0.0f);
                        x.add.corner(prevTop, u * 3 - t * 0.02, 1, up[0], up[1], up[2], 0.0f);
                    }
                }
                prev = foot;
                prevTop = top;
            }
        }
    }

    private static void comets(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t;
        x.add.on(Paint.STREAK);
        for (int i = 0; i < 9; i++) {
            double period = 7.5 + 2.0 * rnd(s.seed, 8000 + i, 1), phase = frac(t / period + rnd(s.seed, 8000 + i, 2));
            if (phase > 0.55) {
                continue;
            }
            double u = phase / 0.55;
            Vec3 start = s.portal.add(s.view.scale(260 + 240 * rnd(s.seed, 8000 + i, 3))).add(s.vu.scale(-520)).add(s.vw.scale((rnd(s.seed, 8000 + i, 4) - 0.5) * 360));
            Vec3 dir = s.vu.add(s.vw.scale((rnd(s.seed, 8000 + i, 5) - 0.5) * 0.3)).normalize();
            Vec3 head = start.add(dir.scale(u * 1040));
            Vec3 tail = head.subtract(dir.scale(170 + 120 * rnd(s.seed, 8000 + i, 6)));
            double mask = x.mask(head) * Math.sin(u * Math.PI);
            if (mask < 0.02) {
                continue;
            }
            float[] col = i % 3 == 0 ? c(GOLD, mask) : i % 3 == 1 ? c(CY, mask) : c(MG, mask);
            x.add.on(Paint.STREAK).strip(tail, head, 1.0, 7.5, 0, 1, c(col, 0), c(WHITE, 0.85 * mask));
            x.add.strip(tail, head, 0.4, 3.5, 0.3, 1.3, c(col, 0), col);
            x.add.on(Paint.GLOW).billboard(head, 26, 0, col);
            x.add.on(Paint.FLARE).billboard(head, 38, t * 2 + i, c(WHITE, 0.8 * mask));
        }
    }

    private static void dust(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t;
        x.add.on(Paint.GLOW);
        for (int i = 0; i < 110; i++) {
            Vec3 base = new Vec3(rnd(s.seed, 8200 + i, 1), rnd(s.seed, 8200 + i, 2), rnd(s.seed, 8200 + i, 3));
            double speed = 6 + 10 * rnd(s.seed, 8200 + i, 4);
            double ox = ((base.x * 360 + s.flow.x * speed * t) % 360 + 360) % 360 - 180, oy = ((base.y * 360 + s.flow.y * speed * t) % 360 + 360) % 360 - 180,
                    oz = ((base.z * 360 + s.flow.z * speed * t) % 360 + 360) % 360 - 180;
            Vec3 p = x.cam.add(ox, oy, oz);
            double d = Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (d < 6) {
                continue;
            }
            x.add.billboard(p, 0.25 + 0.5 * rnd(s.seed, 8200 + i, 5), 0, c(i % 4 == 0 ? GOLD : CY, 0.5 * Math.min(1, (180 - d) / 60.0)));
        }
    }

    private static void gunLight(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t, power = StargunModel.power(t), charge = charge(t);
        Vec3 axis = s.axis;
        Vec3[] bs = RedVfxRenderer.basis(axis);
        Vec3 bu = bs[0], bw = bs[1];
        Vec3 heart = StargunModel.at(s, t, StargunModel.REACTOR_AT);
        double hm = x.mask(heart);
        if (hm > 0.02) {
            double beat = 0.85 + 0.15 * Math.sin(t * (3 + 9 * power));
            x.add.on(Paint.GLOW).billboard(heart, (44 + 46 * power) * beat, 0, c(CY, (0.22 + 0.45 * power) * hm));
            x.add.on(Paint.FLARE).billboard(heart, (30 + 90 * power) * beat, t * 0.2, c(WHITE, (0.20 + 0.75 * power) * hm));
        }
        Object[][] rings = StargunModel.rings(s, t);
        for (int k = 0; k < rings.length; k++) {
            Vec3 u = (Vec3) rings[k][1], w = (Vec3) rings[k][2];
            double radius = (Double) rings[k][3], turn = StargunModel.spin(t) * (k % 2 == 0 ? 0.6 : -0.6);
            x.add.on(Paint.RUNES).plane(heart, u, w, radius * 0.97 / RUNE_RIM, turn, c(k == 1 ? MG : CY, 0.30 + 0.65 * power), 6, x::mask);
            x.add.plane(heart, u, w, radius * 0.97 / RUNE_RIM, turn, c(WHITE, 0.25 * power), 6, x::mask);
        }
        x.add.on(Paint.GLOW);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * (k + 0.5) / 8;
            Vec3 p = StargunModel.at(s, t, 84).add(bu.scale(Math.cos(a) * 17)).add(bw.scale(Math.sin(a) * 17));
            double wave = 0.5 + 0.5 * Math.sin(t * (3 + 9 * power) - k * 0.8);
            x.add.billboard(p, 9 + 6 * power, 0, c(CY, (0.10 + 0.5 * power * wave) * x.mask(p)));
        }
        Vec3 mz = StargunModel.at(s, t, -12);
        double mask = x.mask(mz);
        if (mask < 0.02) {
            return;
        }
        double held = power * rayStrength(Math.max(t, FIRE));
        double shot = t >= FIRE - 0.05 ? Math.exp(-(t - FIRE + 0.05) / 0.9) : 0;
        for (int k = 0; k < 3; k++) {
            double openAt = Curves.window(t, CHARGE + 1.5 + 2.2 * k, CHARGE + 2.6 + 2.2 * k);
            double grow = backOut(openAt) * (t < FIRE ? 1 : held);
            if (grow <= 0.01) {
                continue;
            }
            Vec3 p = StargunModel.at(s, t, -(12 + 13 * k));
            double radius = (14 + 7 * k) * grow, turn = t * (0.5 + 1.6 * power) * (k % 2 == 0 ? 1 : -1);
            x.add.on(Paint.RUNES).plane(p, bu, bw, radius / RUNE_RIM, turn, c(CY, 0.9), 4, x::mask);
            x.add.plane(p, bu, bw, radius / RUNE_RIM, turn, c(WHITE, 0.35), 4, x::mask);
            x.add.on(Paint.SEAL).plane(p, bu, bw, radius * 0.72, -turn * 1.4, c(k == 1 ? GOLD : MG, 0.75), 4, x::mask);
        }
        double core = 3 + 17 * charge + 30 * shot + 8 * held;
        x.add.on(Paint.GLOW).billboard(mz, Math.min(core * 2.6, 90), 0, c(0.35, 0.8, 1.0, (0.18 + 0.5 * charge) * mask));
        x.add.billboard(mz, Math.min(core * 1.1, 22), 0, c(WHITE, (0.30 + 0.7 * charge) * mask));
        if (t >= CHARGE + 0.5) {
            x.add.on(Paint.FLARE).billboard(mz, Math.min(core * 4.2, 110), t * 0.4, c(WHITE, Math.min(0.8, 0.6 * charge + 0.5 * shot) * mask));
            x.add.billboard(mz, Math.min(core * 2.6, 70), -t * 0.7 + 0.8, c(CY, Math.min(0.7, 0.5 * charge + 0.4 * shot) * mask));
        }
        if (t >= CHARGE && t < FIRE) {
            double ch = Curves.window(t, CHARGE, FIRE);
            for (int ring = 0; ring < 4; ring++) {
                double phase = frac((t - CHARGE) * (0.20 + 0.28 * ch) + ring * 0.25);
                double envelope = Math.sin(phase * Math.PI) * (0.20 + 0.50 * ch) * mask;
                double radius = 12 + 76 * Math.pow(1 - phase, 2);
                Vec3 centre = mz.subtract(axis.scale(18 * (1 - phase)));
                x.add.on(Paint.RUNES).plane(centre, bu, bw, radius / RUNE_RIM, -t * 0.32 + ring,
                        c(CY, envelope), 6, x::mask);
                x.add.on(Paint.SHOCK).plane(centre, bu, bw, radius * 1.06 / SHOCK_RIM, t * 0.18,
                        c(GOLD, envelope * 0.38), 6, x::mask);
            }
            x.add.on(Paint.STREAK);
            for (int k = 0; k < 60; k++) {
                double phase = frac(t * (0.45 + 0.5 * ch) + rnd(s.seed, 3600 + k, 1));
                double in = (1 - phase) * (1 - phase);
                double rho = 78 * in + 2, th = k * 2.39996 + phase * 4.2;
                double lift = (rnd(s.seed, 3600 + k, 3) - 0.3) * 70 * in;
                Vec3 p = mz.add(axis.scale(lift)).add(bu.scale(Math.cos(th) * rho)).add(bw.scale(Math.sin(th) * rho));
                double rho0 = rho + 5 + 10 * in, th0 = th - 0.16;
                Vec3 tail = mz.add(axis.scale(lift * 1.08)).add(bu.scale(Math.cos(th0) * rho0)).add(bw.scale(Math.sin(th0) * rho0));
                double vis = Math.min(1, phase * 5) * (0.25 + ch) * mask;
                x.add.strip(tail, p, 0.3, 0.9, 0, 1, c(CY, 0), c(k % 4 == 0 ? GOLD : WHITE, Math.min(1, vis)));
            }
        }
    }

private static double rung(int k) {
        return 0.36 + 0.13 * k;
    }

    private static final int RUNGS = 5;

    private static void aimLight(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t, aim = aim(t), power = StargunModel.power(t);
        if (aim > 0.01) {
            Vec3 c = s.centre.add(0, 0.18, 0);
            double pulse = 0.85 + 0.15 * Math.sin(t * 2.2), turn = t * 0.05;
            double a = Math.min(1, aim);
            x.add.on(Paint.RUNES).plane(c, X, Z, RADIUS * aim / RUNE_RIM, turn, c(CY, 0.9 * a * pulse));
            x.add.plane(c, X, Z, RADIUS * aim / RUNE_RIM, turn, c(WHITE, 0.35 * a));
            x.add.plane(c, X, Z, RADIUS * 0.34 * aim / RUNE_RIM, -turn * 4, c(MG, 0.8 * a));
            x.add.on(Paint.SEAL).plane(c, X, Z, RADIUS * 0.74 * aim, -turn * 1.5, c(MG, 0.65 * a * pulse));
            x.add.plane(c, X, Z, RADIUS * 0.74 * aim, -turn * 1.5, c(WHITE, 0.25 * a));
            x.add.on(Paint.STREAK);
            int n = 72;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3 p0 = c.add(Math.cos(a0) * RADIUS * aim, 0, Math.sin(a0) * RADIUS * aim), p1 = c.add(Math.cos(a1) * RADIUS * aim, 0, Math.sin(a1) * RADIUS * aim);
                double h = 9 + 4 * Math.sin(a0 * 9 + t * 1.5);
                float[] bottom = c(CY, 0.42 * a), top = c(VI, 0);
                double u = t * 0.12, v0 = i * 6.0 / n, v1 = (i + 1) * 6.0 / n;
                x.add.quad(p0, p0.add(0, h, 0), p1.add(0, h, 0), p1, u, v0, u + 0.4, v1, bottom, top, top, bottom);
            }
            for (int i = 0; i < 6; i++) {
                double ang = -turn * 1.5 + Math.PI * 2 * i / 6 + Math.PI / 2;
                Vec3 p = c.add(Math.cos(ang) * RADIUS * 0.70 * aim, 0, Math.sin(ang) * RADIUS * 0.70 * aim);
                x.add.on(Paint.STREAK).strip(p, p.add(0, 34, 0), 1.6, 0.2, t * 0.3, t * 0.3 + 0.8, c(MG, 0.6 * a), c(MG, 0));
                x.add.on(Paint.FLARE).billboard(p.add(0, 0.6, 0), 5, t + i, c(WHITE, 0.8 * a));
            }
        }
        Vec3[] bs = RedVfxRenderer.basis(s.axis);
        double head = rayLength(t), gone = 1 - Curves.smoothstep(Curves.window(t, RAY_FADE, RAY_GONE));
        for (int k = 0; k < RUNGS; k++) {
            double open = backOut(Curves.window(t, CHARGE + 2.4 + 0.9 * k, CHARGE + 3.3 + 0.9 * k)) * gone;
            if (open <= 0.01) {
                continue;
            }
            Vec3 p = s.onRay(rung(k));
            double radius = (32 - 4.5 * k) * open, turn = t * (0.25 + 0.9 * power) * (k % 2 == 0 ? 1 : -1);
            double past = (head - rung(k)) * s.rayLength;
            double struck = t >= FIRE && past >= 0 && t < IMPACT + 1.5 ? Math.exp(-past / 70) : 0;
            double a = Math.min(1, 0.75 + struck);
            x.add.on(Paint.RUNES).plane(p, bs[0], bs[1], radius / RUNE_RIM, turn, c(CY, 0.85 * a));
            x.add.plane(p, bs[0], bs[1], radius / RUNE_RIM, turn, c(WHITE, 0.30 + 0.6 * struck));
            x.add.on(Paint.SEAL).plane(p, bs[0], bs[1], radius * 0.72, -turn * 1.3, c(k % 2 == 0 ? MG : GOLD, 0.6 * a));
            if (struck > 0.02) {
                x.add.on(Paint.SHOCK).plane(p, bs[0], bs[1], radius * (1.0 + 1.6 * (1 - struck)) / SHOCK_RIM, 0, c(WHITE, 0.9 * struck));
                x.add.on(Paint.FLARE).billboard(p, radius * 2.4 * struck, t, c(WHITE, struck));
            }
        }
    }

private static void ray(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t;
        double strength = rayStrength(t);
        if (t < FIRE - 0.02 || strength <= 0.005) {
            return;
        }
        Vec3 axis = s.axis, origin = StargunModel.at(s, t, -6);
        double len = rayLength(t) * s.centre.distanceTo(origin);
        if (len < 1) {
            return;
        }
        Vec3[] bs = RedVfxRenderer.basis(axis);
        Vec3 bu = bs[0], bw = bs[1];
        double breathe = 1 + 0.05 * Math.sin(t * 23);
        double punch = backOut(Curves.window(t, FIRE, FIRE + 0.5));
        double rOuter = 17 * strength * breathe * punch, rMid = 9.5 * strength * breathe * punch, rCore = 4.2 * strength * breathe * (0.5 + 0.5 * punch);
        x.m.tube(origin, axis, len, rOuter * 1.15, rOuter, 1.0, Math.max(8, (int) (len / 6)), 18, (u, v, out) -> {
            double pulse = 0.55 + 0.45 * Math.sin(u * len * 0.11 - t * 26);
            out[0] = 0.50f;
            out[1] = 0.32f;
            out[2] = 1f;
            out[3] = (float) (0.15 * strength * pulse);
        });
        x.m.tube(origin, axis, len, rMid * 1.15, rMid, 1.0, Math.max(8, (int) (len / 6)), 16, (u, v, out) -> {
            double pulse = 0.65 + 0.35 * Math.sin(u * len * 0.2 - t * 31 + v * 6);
            out[0] = 0.32f;
            out[1] = 0.85f;
            out[2] = 1f;
            out[3] = (float) (0.40 * strength * pulse);
        });
        x.m.tube(origin, axis, len, rCore * 1.15, rCore, 1.0, Math.max(6, (int) (len / 10)), 12, (u, v, out) -> {
            out[0] = 1f;
            out[1] = 0.98f;
            out[2] = 0.93f;
            out[3] = (float) (0.95 * strength);
        });
        x.add.on(Paint.STREAK);
        int segs = Math.max(2, (int) Math.ceil(len / 24));
        for (int i = 0; i < segs; i++) {
            double d0 = len * i / segs, d1 = len * (i + 1) / segs;
            Vec3 a = origin.add(axis.scale(d0)), b = origin.add(axis.scale(d1));
            x.add.strip(a, b, rOuter * 1.5, rOuter * 1.5, d0 / 60 - t * 2.4, d1 / 60 - t * 2.4, c(CY, 0.55 * strength), c(CY, 0.55 * strength));
            x.add.strip(a, b, rMid * 1.1, rMid * 1.1, d0 / 36 - t * 3.6 + 0.4, d1 / 36 - t * 3.6 + 0.4, c(WHITE, 0.8 * strength), c(WHITE, 0.8 * strength));
        }
        for (int j = 0; j < 3; j++) {
            Vec3 prev = null;
            int n = Math.max(20, (int) (len / 2.5));
            for (int k = 0; k <= n; k++) {
                double dist = len * k / n;
                double phase = dist * 0.13 - t * 15 + j * 2.094;
                double rad = rMid * (1.7 + 0.4 * Math.sin(dist * 0.05 + j));
                Vec3 p = origin.add(axis.scale(dist)).add(bu.scale(Math.cos(phase) * rad)).add(bw.scale(Math.sin(phase) * rad));
                if (prev != null) {
                    float[] col = j == 1 ? c(MG, 0.6 * strength) : c(CY, 0.6 * strength);
                    x.m.ribbon(prev, p, 0.7, 0.7, col, col);
                }
                prev = p;
            }
        }
        double lead = frac(t * 1.6) * 44;
        for (double d = lead; d < len; d += 44) {
            Vec3 p = origin.add(axis.scale(d));
            double grow = 1.2 + 0.6 * frac(d / 44 + t);
            x.add.on(Paint.SHOCK).plane(p, bu, bw, rOuter * grow / SHOCK_RIM, 0, c(CY, 0.55 * strength));
            x.add.on(Paint.FLARE).billboard(p, rOuter * 1.5, t * 1.7 + d, c(CY, 0.28 * strength));
        }
        Vec3 head = origin.add(axis.scale(len));
        if (t < IMPACT + 0.05) {
            x.add.on(Paint.GLOW).billboard(head, 55, 0, c(CY, 0.8));
            x.add.billboard(head, 24, 0, c(WHITE, 1));
            x.add.on(Paint.FLARE).billboard(head, 120, t * 2.0, c(WHITE, 1));
            x.add.billboard(head, 70, -t * 3.0 + 0.8, c(CY, 0.9));
        }
        double leave = Math.exp(-(t - FIRE) / 0.5);
        if (t >= FIRE) {
            x.add.on(Paint.GLOW).billboard(origin, 24 + 40 * leave, 0, c(WHITE, (0.30 + 0.4 * leave) * strength));
            x.add.on(Paint.FLARE).billboard(origin, 50 + 100 * leave, t * 0.6, c(WHITE, (0.4 + 0.4 * leave) * strength));
            x.add.on(Paint.SHOCK).plane(origin, bu, bw, (20 + 160 * (1 - leave)) / SHOCK_RIM, 0, c(CY, 0.9 * leave));
        }
        double seal = Curves.smoothstep(Curves.window(t, FIRE + 0.15, FIRE + 0.9)) * strength;
        for (double d = 30 + frac(t * 0.5) * 60; d < len; d += 60) {
            Vec3 p = origin.add(axis.scale(d));
            double turn = t * 1.4 + d * 0.02;
            x.add.on(Paint.RUNES).plane(p, bu, bw, rOuter * 2.0 / RUNE_RIM, turn, c(CY, 0.55 * seal));
            x.add.plane(p, bu, bw, rOuter * 2.0 / RUNE_RIM, turn, c(WHITE, 0.25 * seal));
            x.add.on(Paint.SEAL).plane(p, bu, bw, rOuter * 1.35, -turn * 1.7, c(MG, 0.4 * seal));
        }
        long key = (long) Math.floor(t * 10);
        for (int i = 0; i < 14; i++) {
            double along = rnd(x.seed ^ key, 7400 + i, 1) * len, ang = rnd(x.seed ^ key, 7400 + i, 2) * Math.PI * 2;
            Vec3 from = origin.add(axis.scale(along)).add(bu.scale(Math.cos(ang) * rMid)).add(bw.scale(Math.sin(ang) * rMid));
            double reach = 30 + 46 * rnd(x.seed ^ key, 7400 + i, 3);
            Vec3 to = from.add(bu.scale(Math.cos(ang) * reach)).add(bw.scale(Math.sin(ang) * reach)).add(axis.scale((rnd(x.seed ^ key, 7400 + i, 4) - 0.3) * 30));
            bolt(x, from, to, reach * 0.12, x.seed ^ key ^ (i * 977L), 0.85 * strength);
        }
        blast(x, origin, axis, bu, bw);
    }

    private static void blast(Ctx x, Vec3 origin, Vec3 axis, Vec3 bu, Vec3 bw) {
        double dt = x.t - FIRE;
        if (dt < -1.6 || dt > 4.0) {
            return;
        }
        if (dt < 0) {
            double in = Curves.smoothstep((dt + 1.6) / 1.6);
            x.add.on(Paint.FLARE).billboard(origin, 30 + 170 * in, x.t * 2, c(WHITE, 0.4 + 0.6 * in));
            x.add.on(Paint.GLOW).billboard(origin, 60 + 90 * in, 0, c(GOLD, 0.6 * in));
            x.add.on(Paint.SHOCK);
            for (int i = 0; i < 3; i++) {
                double u = frac(x.t * 1.2 + i / 3.0);
                x.add.plane(origin.add(axis.scale(6)), bu, bw, (260 * (1 - u) + 10) / SHOCK_RIM, 0, c(i == 1 ? GOLD : CY, 0.5 * in * u));
            }
            return;
        }
        x.add.on(Paint.SHOCK);
        for (int i = 0; i < 4; i++) {
            double k = dt - i * 0.16;
            if (k < 0 || k > 2.8) {
                continue;
            }
            double e = 1 - Math.pow(1 - k / 2.8, 3);
            x.add.plane(origin.add(axis.scale(4 * i)), bu, bw, (20 + 430 * e) / SHOCK_RIM, i * 0.3, c(i == 0 ? WHITE : i == 2 ? GOLD : CY, 0.95 * (1 - k / 2.8)));
        }
        double fan = Math.exp(-dt / 1.1);
        x.add.on(Paint.STREAK);
        for (int i = 0; i < 30; i++) {
            double a = Math.PI * 2 * i / 30 + rnd(x.seed, 7500 + i, 1) * 0.3 + dt * 0.1 * (i % 2 == 0 ? 1 : -1);
            double len = (150 + 200 * rnd(x.seed, 7500 + i, 2)) * Math.min(1, dt / 0.18 + 0.05);
            Vec3 dir = x.forward.cross(UP).lengthSqr() < 1e-6 ? bu : plane(Vec3.ZERO, x.s.pu, x.s.pw, a, 1).add(axis.scale(0.15 * (rnd(x.seed, 7500 + i, 3) - 0.5)));
            x.add.strip(origin, origin.add(dir.normalize().scale(len)), 3.4, 0.4, 0, 1, c(i % 3 == 0 ? GOLD : WHITE, 0.85 * fan), c(CY, 0));
        }
        x.add.on(Paint.FLARE);
        for (int i = 0; i < 44; i++) {
            double a = rnd(x.seed, 7600 + i, 1) * Math.PI * 2, speed = 60 + 150 * rnd(x.seed, 7600 + i, 2);
            double lift = (rnd(x.seed, 7600 + i, 3) - 0.3) * 1.2;
            Vec3 p = origin.add(plane(Vec3.ZERO, bu, bw, a, speed * dt * (1 - dt * 0.12))).add(axis.scale(lift * speed * dt));
            x.add.billboard(p, 3 + 4 * rnd(x.seed, 7600 + i, 4), x.t * 3 + i, c(GOLD, 0.9 * Math.max(0, 1 - dt / 2.4)));
        }
    }

private static double groundAt(Minecraft mc, double x, double z, double fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (mc.level == null || !mc.level.hasChunk(bx >> 4, bz >> 4)) {
            return fallback;
        }
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    private static final int PIECES = 2600;
    private static final float[] PIECE_LIGHT = {0.55f, 0.95f, 1.0f};

    private static void pieces(Ctx x) {
        double t = x.t;
        if (t < IMPACT) {
            return;
        }
        Stargun.Scene s = x.s;
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = s.centre;
        double rf = front(t);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[] base = new float[3];
        for (int i = 0; i < PIECES; i++) {
            double radius = RADIUS * Math.sqrt(rnd(s.seed, 6000 + i, 1));
            double born = IMPACT + radius / SPEED + rnd(s.seed, 6000 + i, 2) * (BAND / SPEED), life = 2.0 + 1.4 * rnd(s.seed, 6000 + i, 3);
            double age = t - born;
            if (age < 0 || age > life) {
                continue;
            }
            double u = age / life, a = rnd(s.seed, 6000 + i, 4) * Math.PI * 2;
            double sample = Math.min(RADIUS + 2, Math.max(radius, rf) + 2.5);
            double sx = c.x + Math.cos(a) * sample, sz = c.z + Math.sin(a) * sample;
            double ground = groundAt(mc, sx, sz, c.y);
            pos.set(Mth.floor(sx), Mth.floor(ground) - 1, Mth.floor(sz));
            int colour = mc.level.getBlockState(pos).getMapColor(mc.level, pos).col;
            if (colour == 0) {
                colour = 0x7F7F7F;
            }
            base[0] = (colour >> 16 & 255) / 255f;
            base[1] = (colour >> 8 & 255) / 255f;
            base[2] = (colour & 255) / 255f;
            double sway = 0.8 * u * Math.sin(age * 2 + i);
            Vec3 p = new Vec3(c.x + Math.cos(a) * radius + sway, ground + 0.4 + 1.2 * u + 8.5 * Math.pow(u, 1.5), c.z + Math.sin(a) * radius + Math.cos(age * 1.7 + i) * 0.8 * u);
            double half = (0.30 + 0.28 * rnd(s.seed, 6000 + i, 5)) * Math.pow(1 - u, 0.6);
            StargunModel.cube(x, p, half, age * (1.5 + 2 * rnd(s.seed, 6000 + i, 6)) + i, base, 0.15 + 1.3 * u * u, PIECE_LIGHT);
        }
        if (t > FRONT_END - 4) {
            for (int i = 0; i < 360; i++) {
                double radius = RADIUS - 2 + (ERODE_BAND + 2) * Math.pow(rnd(s.seed, 6400 + i, 1), 0.85);
                double born = FRONT_END - 3 + (radius - RADIUS) * 0.30 + 2.2 * rnd(s.seed, 6400 + i, 2), life = 4.2 + 2.4 * rnd(s.seed, 6400 + i, 3);
                double age = t - born;
                if (age < 0 || age > life) {
                    continue;
                }
                double u = age / life, a = rnd(s.seed, 6400 + i, 4) * Math.PI * 2;
                double sx = c.x + Math.cos(a) * radius, sz = c.z + Math.sin(a) * radius;
                double ground = groundAt(mc, sx, sz, c.y);
                pos.set(Mth.floor(sx), Mth.floor(ground) - 1, Mth.floor(sz));
                int colour = mc.level.getBlockState(pos).getMapColor(mc.level, pos).col;
                if (colour == 0) {
                    colour = 0x7F7F7F;
                }
                base[0] = (colour >> 16 & 255) / 255f;
                base[1] = (colour >> 8 & 255) / 255f;
                base[2] = (colour & 255) / 255f;
                double lift = 1.2 * Math.min(1, age / 0.6) + 5.5 * Math.pow(u, 1.3);
                Vec3 p = new Vec3(sx + Math.sin(age * 1.3 + i) * 0.6, ground + 0.5 + lift, sz + Math.cos(age * 1.1 + i) * 0.6);
                double half = (0.28 + 0.34 * rnd(s.seed, 6400 + i, 5)) * (1 - 0.8 * Math.pow(u, 2));
                StargunModel.cube(x, p, half, age * (0.6 + 1.2 * rnd(s.seed, 6400 + i, 6)) + i, base, 0.10 + 0.9 * u * u, PIECE_LIGHT);
            }
        }
        double dt = t - IMPACT;
        if (dt < 3.0) {
            double ground = groundAt(mc, c.x, c.z, c.y);
            base[0] = base[1] = base[2] = 0.55f;
            for (int i = 0; i < 46; i++) {
                double a = rnd(s.seed, 6900 + i, 1) * Math.PI * 2, speed = 12 + 22 * rnd(s.seed, 6900 + i, 2), lift = 16 + 22 * rnd(s.seed, 6900 + i, 3);
                Vec3 p = new Vec3(c.x + Math.cos(a) * speed * dt, ground + lift * dt - 9 * dt * dt, c.z + Math.sin(a) * speed * dt);
                if (p.y < ground - 1) {
                    continue;
                }
                StargunModel.cube(x, p, (0.5 + 0.9 * rnd(s.seed, 6900 + i, 4)) * (1 - dt / 3.0), dt * 5 + i, base, 0.9 * (1 - dt / 3.0), PIECE_LIGHT);
            }
        }
    }

    private static void spread(Ctx x) {
        double t = x.t;
        if (t < IMPACT) {
            return;
        }
        Stargun.Scene s = x.s;
        double rf = front(t);
        double fade = 1 - Curves.smoothstep(Curves.window(t, FRONT_END, RAY_GONE + 1.0));
        Vec3 c = s.centre;
        if (rf <= 0.5 || fade <= 0.01) {
            return;
        }
        x.m.sphere(c.x, c.y, c.z, rf + 0.4, 18, 36, (nx, ny, nz, fres, out) -> {
            double cell = Noise3.fbm(nx * 7 + t * 0.15, ny * 7, nz * 7 - t * 0.1);
            double lines = Math.pow(Math.abs(Math.sin(ny * 26 + cell * 3.0)), 30) + Math.pow(Math.abs(Math.sin(Math.atan2(nz, nx) * 14 + cell * 3.0)), 30);
            double rim = Math.pow(fres, 2.2);
            out[0] = (float) (0.5 + 0.5 * lines);
            out[1] = (float) (0.85 + 0.1 * lines);
            out[2] = 1f;
            out[3] = (float) ((0.03 + 0.38 * rim + 0.22 * lines * (0.3 + rim)) * fade);
        });
        Minecraft mc = Minecraft.getInstance();
        x.add.on(Paint.SHOCK).plane(c.add(0, 0.5, 0), X, Z, rf / SHOCK_RIM, 0, c(CY, 0.7 * fade));
        x.add.plane(c.add(0, 0.5, 0), X, Z, rf / SHOCK_RIM, 0, c(WHITE, 0.35 * fade));
        x.add.on(Paint.STREAK);
        int n = 144;
        Vec3 prev = null;
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            double px = c.x + Math.cos(a) * rf, pz = c.z + Math.sin(a) * rf;
            Vec3 p = new Vec3(px, groundAt(mc, px, pz, c.y) + 0.6, pz);
            if (prev != null && Math.abs(p.y - prev.y) < 14) {
                double flick = 0.7 + 0.3 * Math.sin(a * 23 + t * 9);
                x.m.ribbon(prev, p, 1.3, 1.3, c(WHITE, 0.95 * fade * flick), c(CY, 0.9 * fade * flick));
                double h = 12 + 7 * Math.sin(a * 11 + t * 2.3);
                float[] bottom = c(CY, 0.55 * fade * flick), top = c(VI, 0);
                double u = t * 0.18, v0 = i * 9.0 / n, v1 = (i + 1) * 9.0 / n;
                x.add.quad(prev, prev.add(0, h, 0), p.add(0, h, 0), p, u, v0, u + 0.45, v1, bottom, top, top, bottom);
            }
            prev = p;
        }
        x.add.on(Paint.FLARE);
        for (int i = 0; i < 150; i++) {
            double life = frac(t * 0.45 + rnd(s.seed, 3700 + i, 1));
            double u = rnd(s.seed, 3700 + i, 2) * 2 - 1, a = rnd(s.seed, 3700 + i, 3) * Math.PI * 2;
            double rr = Math.sqrt(1 - u * u);
            double rad = rf - BAND * rnd(s.seed, 3700 + i, 4) * 0.8;
            Vec3 p = c.add(new Vec3(Math.cos(a) * rr, Math.abs(u) * 0.9 + 0.05, Math.sin(a) * rr).scale(rad)).add(0, life * 9, 0);
            x.add.billboard(p, 1.0 + 1.8 * rnd(s.seed, 3700 + i, 5), life * 3 + i, c(i % 5 == 0 ? MG : CY, Math.sin(life * Math.PI) * 0.9 * fade));
        }
        x.add.on(Paint.GLOW);
        for (int i = 0; i < 140; i++) {
            double life = frac(t * 0.12 + rnd(s.seed, 3900 + i, 1));
            double rad = rf * Math.sqrt(rnd(s.seed, 3900 + i, 2)) * 0.95, a = rnd(s.seed, 3900 + i, 3) * Math.PI * 2;
            Vec3 p = c.add(Math.cos(a) * rad, -6 + life * (20 + 0.5 * rf), Math.sin(a) * rad);
            x.add.billboard(p, 0.5 + 0.9 * rnd(s.seed, 3900 + i, 4), 0, c(i % 4 == 0 ? WHITE : CY, Math.sin(life * Math.PI) * 0.7 * fade));
        }
        crumble(x, rf, fade);
        entityDust(x, rf, fade);
    }

    private static final int CRACK_SPOTS = 900;
    private static long lastCrackTick = Long.MIN_VALUE;

    private static void crumble(Ctx x, double rf, double fade) {
        double t = x.t;
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = x.s.centre;
        double edge = Curves.smoothstep(Curves.window(t, FRONT_END - 5, FRONT_END + 1));
        long gt = mc.level.getGameTime();
        if (gt != lastCrackTick && gt % 3 == 0) {
            lastCrackTick = gt;
            crackBlocks(x, mc, t);
        }
        x.add.on(Paint.STREAK);
        for (int i = 0; i < 46; i++) {
            double a = rnd(x.seed, 8700 + i, 1) * Math.PI * 2, r0 = rf - 2 + (RADIUS + ERODE_BAND - rf) * rnd(x.seed, 8700 + i, 2) * edge;
            Vec3 prev = null;
            double ang = a, len = 5 + 10 * rnd(x.seed, 8700 + i, 3);
            double step = len / 6;
            double px = c.x + Math.cos(a) * r0, pz = c.z + Math.sin(a) * r0;
            double travel = Math.min(1, (t - (IMPACT + r0 / SPEED)) / 2.0);
            if (travel <= 0) {
                continue;
            }
            double glow = Math.sin(Math.PI * Math.min(1, travel)) * fade * (0.35 + 0.65 * edge);
            for (int k = 0; k <= 6; k++) {
                Vec3 p = new Vec3(px, groundAt(mc, px, pz, c.y) + 0.35, pz);
                if (prev != null && Math.abs(p.y - prev.y) < 3) {
                    x.add.strip(prev, p, 0.28 * (1 - k * 0.1), 0.28 * (1 - k * 0.1), k * 0.3, (k + 1) * 0.3, c(CY, 0.7 * glow), c(WHITE, 0.9 * glow));
                }
                prev = p;
                ang += (rnd(x.seed, 8700 + i, 10 + k) - 0.5) * 1.1;
                px += Math.cos(ang) * step;
                pz += Math.sin(ang) * step;
            }
        }
        x.add.on(Paint.GLOW);
        for (int i = 0; i < 160; i++) {
            double life = frac(t * (0.18 + 0.12 * rnd(x.seed, 8400 + i, 1)) + rnd(x.seed, 8400 + i, 2));
            double a = rnd(x.seed, 8400 + i, 3) * Math.PI * 2;
            double rad = rf + 1 + (RADIUS + ERODE_BAND - rf) * Math.pow(rnd(x.seed, 8400 + i, 4), 1.1) * edge;
            double px = c.x + Math.cos(a) * rad, pz = c.z + Math.sin(a) * rad;
            Vec3 p = new Vec3(px + Math.sin(life * 5 + i) * 0.8, groundAt(mc, px, pz, c.y) + 0.8 + life * (4 + 8 * rnd(x.seed, 8400 + i, 5)), pz + Math.cos(life * 4 + i) * 0.8);
            x.add.billboard(p, 0.5 + 1.0 * (1 - life), 0, c(i % 6 == 0 ? MG : CY, Math.sin(life * Math.PI) * 0.65 * fade * (0.3 + 0.7 * edge)));
        }
    }

    private static void setCrack(Minecraft mc, int id, BlockPos pos, int stage) {
        try {
            mc.levelRenderer.destroyBlockProgress(id, pos.immutable(), stage);
        } catch (RuntimeException forgotten) {
        }
    }

    private static void crackBlocks(Ctx x, Minecraft mc, double t) {
        if (t < FRONT_END - 4.5) {
            return;
        }
        Vec3 c = x.s.centre, cam = x.cam;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double end = RAY_GONE + 3.5;
        for (int i = 0; i < CRACK_SPOTS; i++) {
            double a = rnd(x.seed, 9000 + i, 1) * Math.PI * 2, r = RADIUS - BAND + (ERODE_BAND + BAND) * Math.pow(rnd(x.seed, 9000 + i, 2), 0.8);
            double px = c.x + Math.cos(a) * r, pz = c.z + Math.sin(a) * r;
            if ((px - cam.x) * (px - cam.x) + (pz - cam.z) * (pz - cam.z) > 34 * 34) {
                continue;
            }
            int bx = Mth.floor(px), bz = Mth.floor(pz);
            if (!mc.level.hasChunk(bx >> 4, bz >> 4)) {
                continue;
            }
            int by = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
            pos.set(bx, by, bz);
            int id = -700000 - i;
            if (mc.level.getBlockState(pos).isAir() || t > end) {
                setCrack(mc, id, pos, -1);
                continue;
            }
            double start = FRONT_END - 3.5 + (r - (RADIUS - BAND)) * 0.30 + 2.5 * rnd(x.seed, 9000 + i, 3);
            double u = Curves.clamp01((t - start) / 3.2);
            if (u <= 0) {
                continue;
            }
            setCrack(mc, id, pos, Math.min(8, (int) (u * 9)));
        }
    }

    private static void entityDust(Ctx x, double rf, double fade) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = x.s.centre;
        AABB box = new AABB(c, c).inflate(Math.min(RADIUS, rf) + 3);
        int done = 0;
        x.add.on(Paint.GLOW);
        for (Entity e : mc.level.getEntities((Entity) null, box, en -> true)) {
            if (done >= 48) {
                break;
            }
            double p = Stargun.dissolveProgress(e, 0);
            if (p <= 0 || p >= 1) {
                continue;
            }
            done++;
            AABB b = e.getBoundingBox();
            int n = 8 + (int) (30 * Math.sin(p * Math.PI));
            for (int i = 0; i < n; i++) {
                int id = e.getId() * 31 + i;
                double life = frac(x.t * 0.7 + rnd(x.seed, id, 1));
                Vec3 base = new Vec3(Curves.lerp(b.minX, b.maxX, rnd(x.seed, id, 2)), Curves.lerp(b.minY, b.maxY, rnd(x.seed, id, 3)),
                        Curves.lerp(b.minZ, b.maxZ, rnd(x.seed, id, 4)));
                Vec3 pos = base.add((rnd(x.seed, id, 5) - 0.5) * life * 1.4, life * (1.5 + 3 * p), (rnd(x.seed, id, 6) - 0.5) * life * 1.4);
                x.add.billboard(pos, 0.16 + 0.28 * (1 - life), 0, c(i % 4 == 0 ? MG : CY, (1 - life) * 0.95 * fade));
            }
        }
    }

private static void impact(Ctx x) {
        double t = x.t;
        if (t < IMPACT - 0.05) {
            return;
        }
        double dt = Math.max(0, t - IMPACT);
        Vec3 c = x.s.centre;
        Minecraft mc = Minecraft.getInstance();
        double ground = groundAt(mc, c.x, c.z, c.y);
        Vec3 g = new Vec3(c.x, ground + 0.4, c.z);
        double flash = Math.exp(-dt / 0.6);
        x.add.on(Paint.GLOW).billboard(g.add(0, 5, 0), 60 + 150 * flash, 0, c(WHITE, 0.95 * flash));
        x.add.billboard(g.add(0, 5, 0), 160, 0, c(CY, 0.5 * Math.exp(-dt / 2.0)));
        x.add.on(Paint.FLARE).billboard(g.add(0, 5, 0), 260 * backOut(dt / 0.2) * (0.3 + flash), dt * 0.5, c(WHITE, Math.exp(-dt / 1.1)));
        double pillar = Math.exp(-dt / 1.4);
        x.m.tube(g, UP, 320, 8 + 10 * pillar, 4 + 4 * pillar, 1.0, 10, 14, (u, v, out) -> {
            out[0] = 1f;
            out[1] = 0.96f;
            out[2] = 0.9f;
            out[3] = (float) (0.75 * pillar * (1 - u * 0.6));
        });
        if (dt < 5.5) {
            double rise = Curves.smoothstep(Curves.window(dt, 0, 0.55));
            double fade = 1 - Curves.smoothstep(Curves.window(dt, 1.4, 5.5));
            x.add.on(Paint.STREAK);
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI * 2 / 12 + 0.10 * Math.sin(dt * 0.6 + i);
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                double radius = 10 + 38 * rise;
                Vec3 previous = g.add(radial.scale(radius));
                for (int k = 1; k <= 8; k++) {
                    double u = k / 8.0;
                    Vec3 next = g.add(radial.scale(radius + 18 * Math.sin(u * Math.PI)))
                            .add(0, (55 + 32 * rnd(x.seed, 9100 + i, 1)) * rise * u, 0);
                    x.add.strip(previous, next, 2.8 * (1 - u + 0.125), 2.8 * (1 - u),
                            u - dt * 0.7, u + 0.125 - dt * 0.7,
                            c(CY, 0.32 * fade * (1 - u + 0.125)), c(GOLD, 0.22 * fade * (1 - u)));
                    previous = next;
                }
                x.add.on(Paint.FLARE).billboard(previous, 5 + 3 * rise, angle,
                        c(GOLD, 0.5 * fade * rise));
                x.add.on(Paint.STREAK);
            }
        }
        x.add.on(Paint.SHOCK);
        for (int i = 0; i < 3; i++) {
            double k = dt - i * 0.22;
            if (k < 0 || k > 2.6) {
                continue;
            }
            double e = 1 - Math.pow(1 - k / 2.6, 3);
            x.add.plane(g.add(0, 0.2 * i, 0), X, Z, (8 + RADIUS * 1.7 * e) / SHOCK_RIM, 0, c(i == 1 ? MG : i == 0 ? WHITE : CY, 0.9 * (1 - k / 2.6)));
        }
        if (dt < 1.6) {
            double e = 1 - Math.pow(1 - dt / 1.6, 3), rr = 4 + RADIUS * 1.15 * e;
            x.m.sphere(c.x, ground, c.z, rr, 12, 24, (nx, ny, nz, fres, out) -> {
                out[0] = 0.8f;
                out[1] = 0.95f;
                out[2] = 1f;
                out[3] = (float) (Math.pow(fres, 3) * 0.7 * (1 - dt / 1.6));
            });
        }
        if (dt < 9) {
            double flight = 1 - Math.exp(-dt / 1.9);
            double fade = 1 - Curves.smoothstep(Curves.window(dt, 2, 9));
            x.add.on(Paint.FLARE);
            for (int i = 0; i < 36; i++) {
                double angle = i * 2.399963 + rnd(x.seed, 9200 + i, 1) * 0.4;
                double reach = (18 + 55 * rnd(x.seed, 9200 + i, 2)) * flight;
                double lift = (18 + 80 * rnd(x.seed, 9200 + i, 3)) * flight - dt * dt * 0.38;
                Vec3 ember = g.add(Math.cos(angle) * reach, Math.max(1, lift), Math.sin(angle) * reach);
                double twinkle = 0.55 + 0.45 * Math.pow(Math.sin(dt * 1.7 + i), 2);
                x.add.billboard(ember, (1.3 + 1.6 * rnd(x.seed, 9200 + i, 4)) * twinkle,
                        dt * 0.22 + angle, c(GOLD, 0.65 * fade * flight));
            }
        }
        double cracks = Math.exp(-dt / 2.8);
        if (cracks > 0.02) {
            x.add.on(Paint.STREAK);
            for (int i = 0; i < 20; i++) {
                double a = Math.PI * 2 * i / 20 + rnd(x.seed, 7100 + i, 1) * 0.3;
                double len = (26 + 40 * rnd(x.seed, 7100 + i, 2)) * Math.min(1, dt / 0.25);
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a)), side = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(1.6);
                Vec3 far = g.add(dir.scale(len));
                float[] in = c(WHITE, 0.9 * cracks), out = c(CY, 0);
                x.add.quad(g.subtract(side), far.subtract(side.scale(0.2)), far.add(side.scale(0.2)), g.add(side), 0, 0, 1, 1, in, out, out, in);
            }
        }
        double str = rayStrength(t);
        if (str > 0.01) {
            x.add.on(Paint.GLOW).plane(g, X, Z, 30, 0, c(CY, 0.7 * str));
            x.add.billboard(g.add(0, 3, 0), 34, 0, c(WHITE, 0.45 * str));
            x.add.on(Paint.FLARE).billboard(g.add(0, 3, 0), 40 + 8 * Math.sin(t * 17), t, c(WHITE, 0.7 * str));
        }
    }

private static void postFx(Ctx x, Vec3 cam, Matrix4f view, Matrix4f projection) {
        double t = x.t;
        if (!x.cast.local || t >= END) {
            CastPostFx.afterglow(x.s.centre, cam, view, projection, (float) StargunAtmosphere.red(t), StargunAtmosphere.RED_RADIUS);
            return;
        }
        double weight = x.cast.local ? StargunCamera.weight(t) : 0;
        double dusk = dusk(t);
        double vignette = 0.20 * weight + 0.30 * dusk * weight, darken = (x.alien ? 0.04 : 0.36 * dusk) * weight;
        double chroma = (0.06 + 0.12 * charge(t)) * weight;
        if (weight > 0 && t >= FIRE - 0.05) {
            chroma = Math.max(chroma, 0.7 * Math.exp(-(t - FIRE + 0.05) / 0.25));
        }
        if (weight > 0 && t >= IMPACT) {
            chroma = Math.max(chroma, 1.0 * Math.exp(-(t - IMPACT) / 0.5));
            vignette = Math.max(vignette, 0.5 * Math.exp(-(t - IMPACT) / 1.5));
        }
        Vec3 focus = t < RIFT_END ? x.s.portal : t < FIRE ? StargunModel.at(x.s, t, -12)
                : t < IMPACT ? lerp(StargunModel.at(x.s, t, -6), x.s.centre, rayLength(t)) : x.s.centre.add(0, 6, 0);
        double rays = 0.25 * portal(t) + 0.55 * charge(t) + (t >= FIRE ? 0.9 * Math.exp(-(t - FIRE) / 2.5) : 0) + 0.35 * rayStrength(t);
        double flash = StargunAtmosphere.flash(t) * weight;
        double corona = charge(t) * (1 - Curves.smoothstep(Curves.window(t, FIRE, FIRE + 1.8))) * weight;
        double distortion = (0.055 * corona + 0.16 * flash) * weight;
        CastPostFx.glow((float) ((0.60 + 0.32 * corona + 0.30 * flash) * weight),
                (float) (Math.min(0.8, rays * 0.65) * weight), (float) ((0.28 + 0.55 * corona + 0.40 * flash) * weight));
        CastPostFx.request(focus, cam, view, projection, (float) distortion, (float) Math.min(1, chroma),
                (float) Math.min(1, vignette), (float) flash, (float) (8 + 14 * corona),
                0.32f, 0.30f, 0.9f, (float) Math.min(1, darken));
        CastPostFx.celestial(x.s.centre, cam, view, projection, (float) StargunAtmosphere.red(t), StargunAtmosphere.RED_RADIUS, (float) corona);
    }
}
