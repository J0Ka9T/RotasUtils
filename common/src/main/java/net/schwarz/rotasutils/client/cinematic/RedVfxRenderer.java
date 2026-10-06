package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.RedTimings;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class RedVfxRenderer {
    private RedVfxRenderer() {
    }

    private static final float[] DEEP = {0.45f, 0.01f, 0.03f, 1f};
    private static final float[] HOT = {1.0f, 0.22f, 0.10f, 1f};
    private static final float[] WHITE_HOT = {1.0f, 0.86f, 0.70f, 1f};

    private record Draw(ClientCast cast, double t, CameraRig.Frame frame, RedPose.Sockets sockets, int lod, double distance) {
        Vec3 releaseCore() {
            if (cast.liveOrigin != null) {
                return cast.liveOrigin;
            }
            return RedPose.sockets(RedPose.sample(RedTimings.RELEASE), frame.feet(), frame.yawDeg()).core();
        }
    }

public static void render(PoseStack poseStack, Camera camera, float partialTick, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientCasts.all().isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Matrix4f view = poseStack.last().pose();
        List<Draw> draws = new ArrayList<>();
        for (ClientCast cast : ClientCasts.all()) {
            if (cast.purple() || cast.projection() || cast.stargun()) {
                continue;
            }
            double t = cast.time(partialTick);
            if (t < 0 || t > cast.finishedAt() || (cast.cancelled && !cast.released())) {
                continue;
            }
            CameraRig.Frame frame = cast.frame(partialTick);
            RedPose.Sockets guess = RedPose.sockets(RedPose.sample(t), frame.feet(), frame.yawDeg());
            RedPose.Sockets sockets = HandCapture.resolve(cast.casterId, view, cam, guess, RedProfile.coreRadius(Math.min(t, RedTimings.RELEASE), cast.max()));
            cast.live = sockets;
            cast.liveNanos = System.nanoTime();
            if (cast.liveOrigin == null && t >= RedTimings.RELEASE - 0.02) {
                cast.liveOrigin = sockets.core();
            }
            double distance = cam.distanceTo(sockets.core());
            int lod = distance <= 24 ? 0 : distance <= 48 ? 1 : distance <= 96 ? 2 : 3;
            if (lod == 3 && !cast.released()) {
                continue;
            }
            draws.add(new Draw(cast, t, frame, sockets, lod, distance));
        }
        if (draws.isEmpty()) {
            return;
        }
        Mesh mesh = new Mesh(view, cam, camera.getLeftVector(), camera.getUpVector());
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            mesh.begin();
            for (Draw d : draws) {
                alphaLayers(mesh, d);
            }
            mesh.draw();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            mesh.begin();
            for (Draw d : draws) {
                additiveLayers(mesh, d);
            }
            mesh.draw();
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        requestPostFx(draws, cam, view, projection);
    }

static double rnd(long seed, int i, int k) {
        long x = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (k * 0xC2B2AE3D27D4EB4FL);
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= x >>> 33;
        return (x >>> 11) * (1.0 / (1L << 53));
    }

    static float[] c(float r, float g, float b, double a) {
        return new float[]{r, g, b, (float) a};
    }

    static Vec3 randomDir(long seed, int i) {
        double u = rnd(seed, i, 1) * 2 - 1, a = rnd(seed, i, 2) * Math.PI * 2;
        double r = Math.sqrt(1 - u * u);
        return new Vec3(Math.cos(a) * r, u, Math.sin(a) * r);
    }

    static Vec3[] basis(Vec3 axis) {
        Vec3 ref = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = axis.cross(ref).normalize();
        return new Vec3[]{u, axis.cross(u).normalize()};
    }

    private static double vis(double t) {
        return Curves.smoothstep((t - RedTimings.CORE_FORMS) / 0.4);
    }

    static Mesh.Surface plasma(double rot, double scale, double scroll, double t, int seed, float[] deep, float[] hot,
                                       double alpha, double sharp) {
        double cr = Math.cos(rot), sr = Math.sin(rot);
        return (nx, ny, nz, fres, out) -> {
            double px = (nx * cr - nz * sr) * scale + seed * 0.37, pz = (nx * sr + nz * cr) * scale, py = ny * scale + scroll * t;
            double v = Math.pow(Noise3.ridged(px, py, pz), sharp);
            double n = Noise3.fbm(px * 0.7, py * 0.7 - scroll * t * 0.5, pz * 0.7);
            float k = (float) Math.min(1, v * 0.85 + n * 0.25);
            float spot = (float) Curves.smoothstep((v - 0.88) / 0.11) * 0.55f;
            out[0] = deep[0] + (hot[0] - deep[0]) * k + (WHITE_HOT[0] - hot[0]) * spot;
            out[1] = deep[1] + (hot[1] - deep[1]) * k + (WHITE_HOT[1] - hot[1]) * spot;
            out[2] = deep[2] + (hot[2] - deep[2]) * k + (WHITE_HOT[2] - hot[2]) * spot;
            double rim = 0.30 + 0.70 * Math.pow(fres, 1.3);
            out[3] = (float) (alpha * rim * (0.35 + 0.65 * k));
        };
    }

    static Mesh.Surface flat(float r, float g, float b, double a) {
        return (nx, ny, nz, fres, out) -> {
            out[0] = r;
            out[1] = g;
            out[2] = b;
            out[3] = (float) a;
        };
    }

    static int latOf(int lod) {
        return lod == 0 ? 16 : lod == 1 ? 10 : 8;
    }

    static int lonOf(int lod) {
        return lod == 0 ? 26 : lod == 1 ? 16 : 12;
    }

    static void ring(Mesh m, Vec3 centre, Vec3 u, Vec3 w, double radius, double width, float[] col, int segments, double spin,
                             double gapPhase, double gapCut) {
        Vec3 prev = null;
        for (int k = 0; k <= segments; k++) {
            double a = spin + Math.PI * 2 * k / segments;
            Vec3 p = centre.add(u.scale(Math.cos(a) * radius)).add(w.scale(Math.sin(a) * radius));
            if (prev != null && Math.sin(a * 2 + gapPhase) > gapCut) {
                m.ribbon(prev, p, width, width, col, col);
            }
            prev = p;
        }
    }

    private static void rays(Mesh m, double t, long seed, Vec3 p, double radius, double amount) {
        if (amount < 0.02) {
            return;
        }
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a = i * (Math.PI * 2 / n) + t * 0.15 * (i % 2 == 0 ? 1 : -1) + rnd(seed, 2600 + i, 1) * 0.5;
            double len = radius * (10 + 8 * rnd(seed, 2600 + i, 2)) * (0.7 + 0.3 * Math.sin(t * 3 + i));
            Vec3 dir = new Vec3(m.left.x(), m.left.y(), m.left.z()).scale(Math.cos(a)).add(new Vec3(m.up.x(), m.up.y(), m.up.z()).scale(Math.sin(a)));
            double alpha = amount * 0.34 * (0.5 + 0.5 * rnd(seed, 2600 + i, 3));
            m.ribbon(p, p.add(dir.scale(len)), radius * 0.08, radius * 0.005, c(1f, 0.14f, 0.07f, alpha), c(1f, 0.14f, 0.07f, 0));
        }
    }

private static void alphaLayers(Mesh m, Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        if (t < RedTimings.RELEASE) {
            double radius = RedProfile.coreRadius(t, cast.max());
            if (radius > 0.004) {
                Vec3 p = d.sockets().core();
                double v = vis(t);
                double density = RedProfile.density(t);
                m.sphere(p.x, p.y, p.z, radius * 0.74, latOf(d.lod()), lonOf(d.lod()), flat(0.015f, 0.0f, 0.004f, 0.98 * v));
                m.sphere(p.x, p.y, p.z, radius * 0.93, latOf(d.lod()), lonOf(d.lod()),
                        flat(0.30f, 0.01f, 0.03f, (0.45 + 0.25 * density) * v));
            }
            if (d.lod() <= 1) {
                debrisSolid(m, d);
            }
        } else {
            double dt = t - RedTimings.RELEASE;
            Vec3 o = d.releaseCore();
            if (d.lod() <= 1 && dt < 1.2) {
                shockwaveDust(m, d, dt);
            }
            if (cast.released()) {
                projectileSolid(m, d, o);
                double di = cast.sinceImpact(t);
                if (di >= 0) {
                    impactSolid(m, d, di);
                }
            }
        }
    }

    private static void debrisSolid(Mesh m, Draw d) {
        double t = d.t();
        double drive = RedProfile.debris(t);
        if (drive <= 0) {
            return;
        }
        int count = d.lod() == 0 ? 44 : 16;
        double ts = Math.max(0, t - RedTimings.DEBRIS);
        double kk = d.cast().max() ? Curves.smoothstep(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + RedProfile.COLLAPSE)) : 0;
        if (kk >= 1) {
            return;
        }
        Vec3 core = d.sockets().core();
        Vec3 feet = d.frame().feet();
        for (int i = 0; i < count; i++) {
            Vec3 p = vortexPoint(d.cast().seed, 500 + i, ts, drive, feet);
            p = p.add(core.subtract(p).scale(kk));
            double a = 0.85 * Curves.smoothstep(ts / 0.5) * (1 - Curves.smoothstep((t - RedTimings.RELEASE) / 0.12)) * (1 - kk);
            boolean grass = rnd(d.cast().seed, 500 + i, 9) < 0.35;
            float[] col = grass ? c(0.22f, 0.42f, 0.16f, a) : c(0.33f, 0.31f, 0.30f, a);
            m.billboard(p, 0.03 + 0.05 * rnd(d.cast().seed, 500 + i, 4), col, ts * 3 + i);
        }
    }

private static void additiveLayers(Mesh m, Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        if (t < RedTimings.RELEASE) {
            charging(m, d);
        } else {
            double dt = t - RedTimings.RELEASE;
            releasing(m, d, dt);
            if (cast.released()) {
                projectileAdditive(m, d);
                double di = cast.sinceImpact(t);
                if (cast.max() && di < 0 && di > -COMPRESS_WINDOW) {
                    compress(m, d, -di);
                }
                if (di >= 0) {
                    impactAdditive(m, d, di);
                }
            }
        }
    }

    private static void charging(Mesh m, Draw d) {
        double t = d.t();
        long seed = d.cast().seed;
        boolean max = d.cast().max();
        double collapse = max ? Curves.smoothstep(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + RedProfile.COLLAPSE)) : 0;
        double charge = RedProfile.charge(t) * (1 - collapse);
        double light = RedProfile.light(t, max);
        double pk = max ? 1 + 0.5 * RedProfile.pulse(t) : 1;
        Vec3 feet = d.frame().feet();
        m.groundFan(feet.x, feet.y + 0.03, feet.z, (3.5 + 5.0 * charge) * d.cast().scale(), c(1f, 0.06f, 0.04f, Math.min(0.55, 0.32 * light)));
        if (t >= RedTimings.DEBRIS && d.lod() <= 1) {
            debrisGlow(m, d);
        }
        double radius = RedProfile.coreRadius(t, max) * (1 + (max ? 0.015 : 0.03) * Math.sin(t * 14 + (seed & 7)));
        if (radius <= 0.004) {
            return;
        }
        Vec3 p = d.sockets().core();
        if (max && collapse >= 1) {
            calm(m, d, p, radius);
            return;
        }
        double v = vis(t), density = RedProfile.density(t);
        double dense = 0.8 + 0.6 * density;
        int lat = latOf(d.lod()), lon = lonOf(d.lod());
        m.sphere(p.x, p.y, p.z, radius * 1.00, lat, lon, plasma(t * 0.5236, 2.4, 0.6, t, (int) seed, DEEP, HOT, 0.6 * v * dense, 1.6));
        if (d.lod() <= 1) {
            m.sphere(p.x, p.y, p.z, radius * 1.14, lat, lon,
                    plasma(-t * 0.3142, 3.6, -0.4, t, (int) seed + 5, c(0.36f, 0f, 0.05f, 1), c(0.95f, 0.18f, 0.10f, 1), 0.36 * v * dense, 1.9));
            double rim = (0.35 + 0.65 * density) * v;
            m.sphere(p.x, p.y, p.z, radius * 1.04, lat, lon, (nx, ny, nz, fres, out) -> {
                out[0] = 1f;
                out[1] = 0.42f;
                out[2] = 0.26f;
                out[3] = (float) (rim * Math.pow(fres, 3.4));
            });
        }
        if (d.lod() == 0) {
            m.sphere(p.x, p.y, p.z, radius * 1.30, lat, lon,
                    plasma(t * 0.19, 5.2, 0.3, t, (int) seed + 11, c(0.30f, 0f, 0.04f, 1), c(0.9f, 0.14f, 0.08f, 1), 0.20 * v * dense, 2.2));
        }
        m.glow(p, radius * 5.4, c(0.9f, 0.06f, 0.04f, (0.10 + 0.16 * charge) * v * pk));
        m.glow(p, radius * 2.5, c(1f, 0.16f, 0.08f, (0.18 + 0.22 * charge) * v * pk));
        if (d.lod() == 0) {
            rays(m, t, seed, p, radius, (0.35 * charge + 0.9 * density * Curves.window(t, RedTimings.CLOSE_UP, RedTimings.RELEASE)) * v);
        }
        streams(m, d, p, radius, charge);
        rings(m, d, p, radius, charge, v);
        filaments(m, d, p, radius, charge);
        lightning(m, d, p, radius, charge);
        sparks(m, d, p, radius, charge);
        if (t > 1.6 && d.lod() <= 1) {
            double faceLight = Math.min(0.34, 0.32 * light) * Curves.smoothstep((t - 1.6) / 1.0);
            m.glow(d.sockets().hand(), 0.6, c(1f, 0.08f, 0.05f, faceLight));
            m.glow(d.sockets().eye(), 0.8, c(1f, 0.08f, 0.05f, faceLight * 0.7));
        }
    }

    private static void calm(Mesh m, Draw d, Vec3 p, double radius) {
        double tremble = 1 + 0.06 * Math.sin(d.t() * 140);
        m.sphere(p.x, p.y, p.z, radius * 0.7 * tremble, 10, 16, flat(1f, 0.86f, 0.78f, 0.98));
        m.sphere(p.x, p.y, p.z, radius * 1.05, 10, 16, (nx, ny, nz, fres, out) -> {
            out[0] = 0.8f;
            out[1] = 0.08f;
            out[2] = 0.05f;
            out[3] = (float) (0.5 * Math.pow(fres, 2.0));
        });
        m.glow(p, radius * 3.4, c(1f, 0.3f, 0.18f, 0.32));
    }

    private static void streams(Mesh m, Draw d, Vec3 p, double radius, double charge) {
        if (d.lod() > 1 || charge < (d.cast().max() ? 0.01 : 0.08)) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        int count = d.lod() == 0 ? 14 : 6;
        for (int i = 0; i < count; i++) {
            Vec3[] b = basis(randomDir(seed, 2000 + i));
            Vec3 axis = b[0].cross(b[1]);
            double phase = rnd(seed, 2000 + i, 3);
            double turns = 0.9 + 1.1 * rnd(seed, 2000 + i, 4);
            double far = 2.6 + 2.4 * rnd(seed, 2000 + i, 5);
            double head = (t * (0.35 + 0.25 * rnd(seed, 2000 + i, 6)) + phase) % 1.0;
            int segs = 22;
            Vec3 prev = null;
            for (int k = 0; k <= segs; k++) {
                double s = head - 0.5 * k / segs;
                if (s < 0) {
                    prev = null;
                    continue;
                }
                double r = Curves.lerp(far, radius * 1.05, Math.pow(s, 1.5));
                double ang = phase * Math.PI * 2 + turns * Math.PI * 2 * s;
                Vec3 q = p.add(b[0].scale(Math.cos(ang) * r)).add(b[1].scale(Math.sin(ang) * r)).add(axis.scale(r * 0.35 * Math.sin(ang * 0.7 + i)));
                if (prev != null) {
                    double a = Math.pow(1 - (double) k / segs, 1.4) * 0.7 * charge * Curves.smoothstep(s * 3);
                    double w = radius * 0.06 * (1 - 0.6 * k / segs);
                    m.ribbon(prev, q, w, w, c(1f, 0.16f, 0.08f, a), c(1f, 0.16f, 0.08f, a * 0.9));
                }
                prev = q;
            }
        }
    }

    private static void rings(Mesh m, Draw d, Vec3 p, double radius, double charge, double vis) {
        if (d.lod() > 1 || charge < 0.15) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        for (int r = 0; r < 2; r++) {
            Vec3[] b = basis(randomDir(seed, 2400 + r));
            double spin = t * (r == 0 ? 1.1 : -0.8) + rnd(seed, 2400 + r, 3) * 6;
            ring(m, p, b[0], b[1], radius * (1.75 + 0.5 * r), radius * 0.035, c(1f, 0.20f, 0.10f, 0.5 * charge * vis), 48, spin,
                    rnd(seed, 2400 + r, 4) * 6, -0.6);
        }
    }

    private static void filaments(Mesh m, Draw d, Vec3 center, double radius, double charge) {
        if (d.lod() > 1) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        int count = (int) Math.round(Curves.lerp(6, 14, charge) * (d.cast().max() ? 1.7 : 1)) / (d.lod() == 0 ? 1 : 2);
        for (int i = 0; i < count; i++) {
            Vec3 axis = randomDir(seed, 100 + i);
            Vec3[] b = basis(axis);
            Vec3 u = b[0], w = b[1];
            double orbit = radius * (1.25 + (d.cast().max() ? 3.0 : 1.6) * rnd(seed, 100 + i, 3));
            double span = 0.7 + 1.1 * rnd(seed, 100 + i, 4);
            double speed = (0.7 + 1.4 * rnd(seed, 100 + i, 5)) * (rnd(seed, 100 + i, 6) < 0.5 ? -1 : 1);
            double phase = rnd(seed, 100 + i, 7) * Math.PI * 2;
            double width = radius * (0.05 + 0.09 * rnd(seed, 100 + i, 8));
            double flicker = Math.pow(Math.max(0, Math.sin(t * (1.2 + 2 * rnd(seed, 100 + i, 9)) + phase * 3)), 0.7);
            double life = flicker * (0.4 + 0.6 * charge) * vis(t);
            if (life < 0.03) {
                continue;
            }
            int segs = 14;
            Vec3 prev = null;
            for (int k = 0; k <= segs; k++) {
                double a = phase + speed * t + span * k / segs;
                double wob = 1 + 0.14 * Curves.noise(k * 0.7 + t * 1.3 + i * 5.1, (int) seed);
                double out = radius * 0.25 * Curves.noise(k * 0.5 - t * 0.9 + i * 3.7, (int) seed + 3);
                Vec3 p = center.add(u.scale(Math.cos(a) * orbit * wob)).add(w.scale(Math.sin(a) * orbit * wob)).add(axis.scale(out));
                if (prev != null && Curves.noise(k * 0.9 + i * 7 + t * 2.0, (int) seed + 9) > -0.25) {
                    double taper = Math.sin(Math.PI * (k - 0.5) / segs);
                    float[] col = c(1f, 0.20f, 0.10f, 0.85 * life * taper);
                    m.ribbon(prev, p, width * taper, width * taper, col, col);
                    if (rnd(seed, 100 + i, 12) < 0.4) {
                        float[] core = c(1f, 0.62f, 0.42f, 0.55 * life * taper);
                        m.ribbon(prev, p, width * 0.3 * taper, width * 0.3 * taper, core, core);
                    }
                }
                prev = p;
            }
        }
    }

    private static void lightning(Mesh m, Draw d, Vec3 p, double radius, double charge) {
        if (d.lod() > 1 || charge < 0.25) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        int bolts = (int) Math.round(Curves.lerp(2, 7, charge)) + (t >= RedTimings.HOLD ? 3 : 0);
        long bucket = (long) Math.floor(t * 20 / 2.0);
        for (int i = 0; i < bolts; i++) {
            long s = seed ^ (bucket * 7919L) ^ (i * 31L);
            if (rnd(s, i, 1) < 0.35) {
                continue;
            }
            Vec3 dir = randomDir(s, i);
            Vec3 start = p.add(dir.scale(radius * 0.95));
            double len = radius * (1.4 + 2.6 * rnd(s, i, 2));
            Vec3 end = p.add(dir.scale(radius * 0.95 + len));
            Vec3 prev = start;
            int segs = 8;
            for (int k = 1; k <= segs; k++) {
                Vec3 q = start.add(end.subtract(start).scale((double) k / segs));
                if (k < segs) {
                    q = q.add(randomDir(s, i * 13 + k).scale(len * 0.10 * Math.sin(Math.PI * k / segs)));
                }
                float[] halo = c(1f, 0.16f, 0.08f, 0.8);
                m.ribbon(prev, q, radius * 0.05, radius * 0.05, halo, halo);
                float[] core = c(1f, 0.85f, 0.75f, 0.95);
                m.ribbon(prev, q, radius * 0.014, radius * 0.014, core, core);
                prev = q;
            }
        }
    }

    private static void sparks(Mesh m, Draw d, Vec3 center, double radius, double charge) {
        if (d.lod() > 1) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        int count = (d.lod() == 0 ? 48 : 18) * (d.cast().max() ? 2 : 1);
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomDir(seed, 300 + i);
            double rate = 0.6 + 0.9 * rnd(seed, 300 + i, 3);
            double life = (t * rate + rnd(seed, 300 + i, 4)) % 1.0;
            double dist = radius * (1.1 + life * (2.5 + 2 * charge));
            Vec3 p = center.add(dir.scale(dist));
            Vec3 tail = p.subtract(dir.scale(radius * (0.4 + 0.8 * rnd(seed, 300 + i, 5))));
            double a = (1 - life) * 0.9 * charge * vis(t);
            m.ribbon(tail, p, 0.004, 0.007, c(1f, 0.3f, 0.12f, 0), c(1f, 0.55f, 0.32f, a));
        }
    }

    private static Vec3 vortexPoint(long seed, int i, double ts, double drive, Vec3 feet) {
        double r0 = 0.8 + 5.4 * Math.pow(rnd(seed, i, 1), 0.7);
        double angle = rnd(seed, i, 2) * Math.PI * 2;
        double omega = 0.8 + 1.4 * rnd(seed, i, 3);
        double r = r0 * (1 - 0.6 * drive);
        double a = angle + omega * (ts * 0.9 + 1.4 * ts * drive);
        double h = -0.05 + 0.9 * rnd(seed, i, 5) + ts * (0.25 + 0.5 * rnd(seed, i, 6)) * (0.4 + drive);
        h = Math.min(h, 3.8);
        return new Vec3(feet.x + Math.cos(a) * r, feet.y + h, feet.z + Math.sin(a) * r);
    }

    private static void debrisGlow(Mesh m, Draw d) {
        double t = d.t();
        double drive = RedProfile.debris(t);
        if (drive <= 0) {
            return;
        }
        long seed = d.cast().seed;
        int count = d.lod() == 0 ? 110 : 44;
        double ts = Math.max(0, t - RedTimings.DEBRIS);
        double kk = d.cast().max() ? Curves.smoothstep(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + RedProfile.COLLAPSE)) : 0;
        if (kk >= 1) {
            return;
        }
        Vec3 core = d.sockets().core();
        Vec3 feet = d.frame().feet();
        double fade = Curves.smoothstep(ts / 0.6) * (0.5 + 0.5 * drive) * (1 - kk);
        for (int i = 0; i < count; i++) {
            Vec3 p = vortexPoint(seed, i, ts, drive, feet);
            p = p.add(core.subtract(p).scale(kk));
            double k = rnd(seed, i, 8);
            double size = 0.03 + 0.07 * rnd(seed, i, 4);
            float[] col = k < 0.45 ? c(0.62f, 0.44f, 0.34f, 0.30 * fade)
                    : k < 0.8 ? c(1f, 0.30f, 0.10f, 0.85 * fade) : c(1f, 0.12f, 0.08f, 0.55 * fade);
            m.billboard(p, size * (k < 0.45 ? 1.6 : 1.0), col, ts * 2 + i);
        }
    }

static Mesh.Surface elongated(Vec3 dir, double front, double tail, Mesh.Surface colour) {
        return new Mesh.Surface() {
            @Override
            public double radius(double nx, double ny, double nz) {
                double dp = nx * dir.x + ny * dir.y + nz * dir.z;
                double a = dp > 0 ? front : tail;
                return 1.0 / Math.sqrt(dp * dp / (a * a) + (1 - dp * dp));
            }

            @Override
            public void color(double nx, double ny, double nz, double fres, float[] out) {
                colour.color(nx, ny, nz, fres, out);
            }
        };
    }

    private static Vec3 shotDir(Draw d, Vec3 origin) {
        ClientCast cast = d.cast();
        return cast.released() ? cast.impact.subtract(origin).normalize() : d.frame().forward();
    }

    private static void releasing(Mesh m, Draw d, double dt) {
        Vec3 o = d.releaseCore();
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 shot = shotDir(d, o);
        Vec3[] b = basis(shot);
        double sc = cast.scale();
        if (cast.max() && dt < 0.45 && d.lod() <= 1) {
            double pr = RedProfile.pressureRadius(dt) * 1.5;
            double pa = RedProfile.pressureAlpha(dt);
            m.sphere(o.x, o.y, o.z, pr, 14, 24, plasma(dt * 4, 1.8, 3, dt, (int) seed, DEEP, HOT, 0.75 * pa, 1.3));
            m.sphere(o.x, o.y, o.z, pr * 0.55, 12, 20, flat(1f, 0.72f, 0.62f, 0.9 * pa * (1 - Curves.smoothstep(dt / 0.1))));
            m.glow(o, pr * 1.6 + 0.6, c(1f, 0.9f, 0.8f, Math.exp(-dt * 25)));
            ring(m, o, b[0], b[1], pr * 1.5, 0.05 + 0.12 * dt, c(1f, 0.35f, 0.2f, 0.6 * pa), 64, 0, 0, -2);
        }
        if (dt < 0.55) {
            double x = Curves.clamp01(dt / 0.16);
            double len = 11 * sc * (1 - Math.pow(1 - x, 3));
            double fade = 1 - Curves.smoothstep(dt / 0.55);
            final double fdt = dt;
            final int sd = (int) seed;
            m.tube(o, shot, len, 0.30 * sc, (0.30 + 1.7 * (len / (11 * sc))) * sc, 1.7, 16, 26, (u, v, out) -> {
                double sr = Noise3.ridged(v * 8 + sd * 0.1, u * 3 - fdt * 10, fdt * 2);
                double band = 0;
                for (int k = 0; k < 3; k++) {
                    double q = (u - (fdt / 0.20 - k * 0.28)) / 0.07;
                    band += Math.exp(-q * q);
                }
                float heat = (float) Math.min(1, sr * 0.9 + band * 0.5);
                out[0] = Math.min(1f, DEEP[0] + (HOT[0] - DEEP[0]) * heat + (float) (band * 0.45));
                out[1] = Math.min(1f, DEEP[1] + (HOT[1] - DEEP[1]) * heat + (float) (band * 0.55));
                out[2] = Math.min(1f, DEEP[2] + (HOT[2] - DEEP[2]) * heat + (float) (band * 0.45));
                out[3] = (float) (fade * (0.22 + 0.78 * sr) * (1 - 0.75 * u) * (0.55 + 0.9 * Math.min(1, band)));
            });
            m.tube(o, shot, len * 1.15, 0.09 * sc, 0.15 * sc, 1.0, 1, 8, (u, v, out) -> {
                out[0] = 1f;
                out[1] = 0.9f;
                out[2] = 0.75f;
                out[3] = (float) ((1 - u) * fade);
            });
            m.ribbon(o.subtract(shot.scale(0.6)), o.add(shot.scale(7 * x)), 0.5, 0.02, c(1f, 0.5f, 0.3f, 0.8 * fade), c(1f, 0.2f, 0.1f, 0));
            m.ribbon(o.subtract(b[0].scale(2.4)), o.add(b[0].scale(2.4)), 0.03, 0.03, c(1f, 0.4f, 0.25f, 0.7 * Math.exp(-dt * 12)),
                    c(1f, 0.4f, 0.25f, 0.7 * Math.exp(-dt * 12)));
            m.glow(o, 0.7, c(1f, 0.85f, 0.65f, 0.9 * Math.exp(-dt * 40)));
        }
        Vec3 feet = d.frame().feet();
        double ang = Math.atan2(shot.z, shot.x);
        double reach = 16 * sc * (1 - Math.pow(1 - Curves.clamp01(dt / 0.4), 3));
        double wedgeFade = 1 - Curves.smoothstep(dt / 0.9);
        if (wedgeFade > 0.01) {
            m.groundWedge(feet.x, feet.y + 0.04, feet.z, ang, reach, 0.55, c(0.9f, 0.14f, 0.08f, 0.35 * wedgeFade));
            m.groundWedge(feet.x, feet.y + 0.05, feet.z, ang, reach * 1.1, 0.12, c(1f, 0.3f, 0.15f, 0.55 * wedgeFade));
        }
        m.groundFan(feet.x, feet.y + 0.03, feet.z, 7, c(1f, 0.06f, 0.04f, Math.min(0.5, 0.28 * RedProfile.light(RedTimings.RELEASE + dt, cast.max()))));
    }

    private static void shockwaveDust(Mesh m, Draw d, double dt) {
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 feet = d.frame().feet();
        Vec3 shot = shotDir(d, d.releaseCore());
        double ang = Math.atan2(shot.z, shot.x);
        double front = 18 * cast.scale() * (1 - Math.pow(1 - Curves.clamp01(dt / 0.5), 3));
        int count = (d.lod() == 0 ? 90 : 36) * (cast.max() ? 2 : 1);
        for (int i = 0; i < count; i++) {
            double a = ang + (rnd(seed, 700 + i, 1) - 0.5) * 2.2;
            double r = front * (0.35 + 0.65 * rnd(seed, 700 + i, 2));
            double h = 0.1 + rnd(seed, 700 + i, 3) * (0.3 + 1.6 * dt);
            Vec3 p = new Vec3(feet.x + Math.cos(a) * r, feet.y + h, feet.z + Math.sin(a) * r);
            double fade = Math.max(0, 1 - dt / 1.1);
            m.billboard(p, 0.25 + 0.7 * dt + 0.3 * rnd(seed, 700 + i, 4), c(0.55f, 0.45f, 0.38f, 0.32 * fade), i);
        }
    }

private static Vec3 pathAt(ClientCast cast, Vec3 origin, double dtp) {
        double travel = Math.max(0.05, cast.travelSeconds);
        double launch = Math.min(0.10, travel * 0.4);
        double u = Curves.clamp01((dtp - launch) / (travel - launch));
        return origin.add(cast.impact.subtract(origin).scale(Math.pow(u, 1.5)));
    }

    private static void projectileSolid(Mesh m, Draw d, Vec3 origin) {
        ClientCast cast = d.cast();
        double dtp = d.t() - cast.releaseAt;
        if (dtp < 0 || dtp > cast.travelSeconds || d.lod() > 2) {
            return;
        }
        Vec3 p = pathAt(cast, origin, dtp);
        Vec3 dir = shotDir(d, origin);
        double rp = RedProfile.projectileRadius(dtp) * cast.scale();
        m.sphere(p.x, p.y, p.z, rp * 0.62, 10, 16, elongated(dir, 2.2, 4.5, flat(0.03f, 0.0f, 0.01f, 0.95)));
        if (cast.max() && d.lod() <= 1) {
            for (int k = 1; k <= 30; k++) {
                double tk = dtp - k * 0.02;
                if (tk < 0) {
                    break;
                }
                Vec3 q = pathAt(cast, origin, tk).add(randomDir(cast.seed, 4200 + k).scale(rp * (0.8 + 2.0 * k / 30.0)));
                m.billboard(q, rp * (0.5 + 1.3 * k / 30.0), c(0.36f, 0.13f, 0.10f, 0.22 * (1 - k / 30.0)), k * 1.3);
            }
        }
        int samples = d.lod() == 0 ? 22 : 10;
        Vec3 prev = p;
        for (int k = 1; k <= samples; k++) {
            double tk = dtp - k * 0.012;
            Vec3 q = pathAt(cast, origin, Math.max(0, tk));
            double width = rp * (0.6 + 1.8 * k / samples);
            Vec3 off = randomDir(cast.seed, 900 + k).scale(width * 0.9 * Curves.noise(k * 0.6 + d.t() * 8, (int) cast.seed));
            q = q.add(off);
            float[] col = c(0.10f, 0.01f, 0.02f, 0.5 * Math.pow(1 - (double) k / samples, 1.2));
            m.ribbon(prev, q, width * 1.3, width * 1.5, col, col);
            prev = q;
        }
    }

    private static void projectileAdditive(Mesh m, Draw d) {
        ClientCast cast = d.cast();
        Vec3 origin = d.releaseCore();
        double dtp = d.t() - cast.releaseAt;
        if (dtp < 0 || dtp > cast.travelSeconds || d.lod() > 2) {
            return;
        }
        Vec3 p = pathAt(cast, origin, dtp);
        Vec3 dir = shotDir(d, origin);
        Vec3[] b = basis(dir);
        double rp = RedProfile.projectileRadius(dtp) * cast.scale();
        double t = d.t();
        int seed = (int) cast.seed;
        Mesh.Surface plasmaSurface = plasma(t * 2.6, 2.6, 2.4, t, seed, DEEP, HOT, 0.9, 1.5);
        m.sphere(p.x, p.y, p.z, rp, latOf(d.lod()), lonOf(d.lod()), elongated(dir, 2.4, 5.0, plasmaSurface));
        if (cast.max()) {
            m.sphere(p.x, p.y, p.z, rp * 0.45, 10, 16, elongated(dir, 2.0, 4.0, flat(1f, 0.85f, 0.75f, 0.95)));
            final double ct = t;
            m.tube(p.add(dir.scale(rp * 2.6)), dir.scale(-1), rp * 16, rp * 0.7, rp * 6.5, 1.3, 10, 22, (u, v, out) -> {
                double sr = Noise3.ridged(v * 5 + seed * 0.1, u * 2 + ct * 6, ct);
                out[0] = 0.9f;
                out[1] = 0.10f;
                out[2] = 0.06f;
                out[3] = (float) (0.09 * (0.4 + 0.6 * sr) * (1 - u));
            });
            for (int i = 0; i < (d.lod() == 0 ? 16 : 6); i++) {
                Vec3 sd = randomDir(cast.seed, 3500 + i);
                double life = (t * 3 + rnd(cast.seed, 3500 + i, 1)) % 1.0;
                Vec3 from = p.add(sd.scale(rp * 1.1));
                Vec3 to = from.add(sd.scale(rp * (0.6 + 1.6 * life))).subtract(dir.scale(rp * 2.5 * life));
                m.ribbon(from, to, 0.05, 0.005, c(1f, 0.55f, 0.3f, 0.8 * (1 - life)), c(1f, 0.2f, 0.1f, 0));
            }
        }
        if (d.lod() <= 1) {
            m.sphere(p.x, p.y, p.z, rp * 1.25, 10, 18, elongated(dir, 2.2, 5.5,
                    plasma(-t * 3.1, 3.8, -2.0, t, seed + 3, c(0.4f, 0, 0.05f, 1), c(1f, 0.2f, 0.1f, 1), 0.34, 1.9)));
            m.sphere(p.x, p.y, p.z, rp * 1.04, 12, 20, elongated(dir, 2.4, 5.0, (nx, ny, nz, fres, out) -> {
                out[0] = 1f;
                out[1] = 0.45f;
                out[2] = 0.3f;
                out[3] = (float) (0.9 * Math.pow(fres, 3.2));
            }));
        }
        m.ribbon(p.add(dir.scale(rp * 2.4)), p.subtract(dir.scale(rp * 5.2)), rp * 0.28, rp * 0.02, c(1f, 0.88f, 0.72f, 0.95), c(1f, 0.4f, 0.2f, 0));
        m.ribbon(p.add(dir.scale(rp * 3.0)), p.subtract(dir.scale(rp * 7.0)), rp * 1.5, rp * 0.1, c(1f, 0.14f, 0.07f, 0.4), c(1f, 0.14f, 0.07f, 0));
        if (d.lod() <= 1) {
            final double ft = t;
            m.tube(p.subtract(dir.scale(rp * 1.4)), dir.scale(-1), 6.5 * rp + 2.0, rp * 0.55, rp * 2.4 + 0.6, 1.25, 8, 22, (u, v, out) -> {
                double sr = Noise3.ridged(v * 7 + seed * 0.1, u * 3 + ft * 9, ft);
                out[0] = 1f;
                out[1] = (float) (0.16 + 0.35 * sr);
                out[2] = (float) (0.08 + 0.25 * sr);
                out[3] = (float) (0.30 * (0.3 + 0.7 * sr) * (1 - u));
            });
        }
        int lines = d.lod() == 0 ? 18 : 7;
        for (int i = 0; i < lines; i++) {
            double a = rnd(seed, 3000 + i, 1) * Math.PI * 2;
            double off = rp * (0.7 + 1.6 * rnd(seed, 3000 + i, 2));
            double slide = ((t * (4 + 5 * rnd(seed, 3000 + i, 3)) + rnd(seed, 3000 + i, 4)) % 1.0);
            Vec3 lateral = b[0].scale(Math.cos(a) * off).add(b[1].scale(Math.sin(a) * off));
            Vec3 head = p.add(dir.scale(rp * 2.0)).add(lateral).subtract(dir.scale(slide * 7.0));
            double len = 2.0 + 5.0 * rnd(seed, 3000 + i, 5);
            m.ribbon(head, head.subtract(dir.scale(len)), 0.02 + 0.03 * rp, 0.004, c(1f, 0.5f, 0.32f, 0.65 * (1 - slide)), c(1f, 0.2f, 0.1f, 0));
        }
        int samples = d.lod() == 0 ? 26 : 10;
        Vec3 prev = p.subtract(dir.scale(rp * 2.5));
        int helixN = cast.max() ? 6 : 3;
        Vec3[] helixPrev = new Vec3[helixN];
        for (int k = 1; k <= samples; k++) {
            double tk = dtp - k * 0.012;
            Vec3 q0 = pathAt(cast, origin, Math.max(0, tk)).subtract(dir.scale(rp * 2.5));
            double width = rp * (0.45 + 1.5 * k / samples);
            Vec3 off = randomDir(cast.seed, 900 + k).scale(width * 0.9 * Curves.noise(k * 0.6 + t * 8, seed));
            Vec3 q = q0.add(off);
            double a = 0.75 * Math.pow(1 - (double) k / samples, 1.3);
            m.ribbon(prev, q, width, width * 1.15, c(1f, 0.16f, 0.08f, a), c(1f, 0.16f, 0.08f, a * 0.85));
            prev = q;
            if (d.lod() == 0) {
                for (int h = 0; h < helixN; h++) {
                    double ang = k * 0.55 + t * 14 + h * (Math.PI * 2 / helixN);
                    double hr = rp * (1.1 + 0.6 * k / samples);
                    Vec3 hp = q0.add(b[0].scale(Math.cos(ang) * hr)).add(b[1].scale(Math.sin(ang) * hr));
                    if (helixPrev[h] != null) {
                        m.ribbon(helixPrev[h], hp, rp * 0.05, rp * 0.05, c(1f, 0.55f, 0.35f, 0.7 * a), c(1f, 0.55f, 0.35f, 0.7 * a));
                    }
                    helixPrev[h] = hp;
                }
                if (k % 2 == 0) {
                    Vec3 frag = q.add(randomDir(cast.seed, 950 + k).scale(width * 1.4));
                    m.billboard(frag, 0.03 + 0.06 * rnd(cast.seed, 950 + k, 3), c(1f, 0.4f, 0.2f, a), k);
                }
            }
        }
    }

private static Vec3 flungDir(long seed, int i, Vec3 shot) {
        Vec3 r = randomDir(seed, 1100 + i);
        Vec3 dir = shot.scale(0.75).add(r.x * 0.9, Math.abs(r.y) * 0.9 + 0.25, r.z * 0.9);
        return dir.normalize();
    }

    private static void impactSolid(Mesh m, Draw d, double di) {
        if (d.lod() > 2) {
            return;
        }
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 at = cast.impact;
        Vec3 shot = shotDir(d, d.releaseCore());
        if (cast.max() && d.lod() <= 1) {
            for (int wave = 0; wave < 2; wave++) {
                double dw = di - (wave == 0 ? 0 : 0.30);
                double life = Math.max(0, 1 - dw / (wave == 0 ? 3.5 : 2.0));
                if (dw < 0 || life <= 0) {
                    continue;
                }
                double x = Curves.clamp01(dw / (wave == 0 ? 1.0 : 0.5));
                double front = RedTimings.BLAST_RADIUS * (wave == 0 ? 2.0 : 1.3) * (1 - Math.pow(1 - x, 3));
                int n = (d.lod() == 0 ? 130 : 55) / (wave == 0 ? 1 : 2);
                for (int i = 0; i < n; i++) {
                    int k = 6000 + wave * 500 + i;
                    double a0 = rnd(seed, k, 1) * Math.PI * 2;
                    double r = front * (0.5 + 0.5 * rnd(seed, k, 2));
                    double h = 0.3 + (0.6 + 2.6 * x) * rnd(seed, k, 3);
                    m.billboard(at.add(Math.cos(a0) * r, h, Math.sin(a0) * r), 0.9 + 2.2 * x * rnd(seed, k, 4),
                            c(0.46f, 0.30f, 0.26f, 0.26 * life), i);
                }
            }
        }
        double ang = Math.atan2(shot.z, shot.x);
        double scorch = 0.6 * (1 - Curves.smoothstep(di / 5.0));
        m.groundWedge(at.x, at.y + 0.035, at.z, ang, 9.0, 0.5, c(0.02f, 0.0f, 0.005f, scorch));
        m.groundFan(at.x, at.y + 0.035, at.z, 3.0, c(0.02f, 0.0f, 0.005f, scorch));
        int chunks = d.lod() == 0 ? 80 : 30;
        for (int i = 0; i < chunks; i++) {
            Vec3 dir = flungDir(seed, i, shot);
            double v = 6 + 12 * rnd(seed, 1100 + i, 3);
            Vec3 p = at.add(dir.scale(v * di)).add(0, -6.0 * di * di, 0);
            double a = Math.max(0, 1 - di / 1.8) * 0.95;
            if (a > 0.01) {
                m.billboard(p, 0.06 + 0.14 * rnd(seed, 1100 + i, 4), c(0.30f, 0.28f, 0.27f, a), di * 4 + i);
            }
        }
        if (d.lod() <= 1) {
            for (int i = 0; i < 24; i++) {
                double grow = 1.6 + 3.4 * Curves.smoothstep(di / 1.4);
                Vec3 p = at.add(shot.scale(grow * 0.9 * rnd(seed, 1300 + i, 3))).add(randomDir(seed, 1300 + i).scale(grow * 0.5))
                        .add(0, 0.4 + di * 0.8, 0);
                m.billboard(p, grow * (0.4 + 0.5 * rnd(seed, 1300 + i, 4)), c(0.42f, 0.36f, 0.32f, 0.22 * Math.max(0, 1 - di / 2.6)), i);
            }
        }
    }

    private static void impactAdditive(Mesh m, Draw d, double di) {
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 at = cast.impact;
        Vec3 shot = shotDir(d, d.releaseCore());
        Vec3[] b = basis(shot);
        double ang = Math.atan2(shot.z, shot.x);
        if (cast.max()) {
            impactSecond(m, d, di, shot, ang);
        }
        if (di < 0.10) {
            double k = 1 - di / 0.10;
            m.glow(at, 0.7 + 1.4 * (1 - k), c(1f, 0.92f, 0.78f, k));
            m.ribbon(at.subtract(shot.scale(3.5)), at.add(shot.scale(9 * (1 - k) + 1)), 0.6, 0.03, c(1f, 0.6f, 0.4f, 0.8 * k), c(1f, 0.2f, 0.1f, 0));
        }
        if (di < 0.8) {
            double r = Curves.Track.of(0, 0.3, 0.05, 0.9, 0.15, 3.4, 0.4, 4.2, 0.8, 4.8).at(di) * cast.scale();
            double a = 0.95 * (1 - Curves.smoothstep(di / 0.8));
            final double dd = di;
            Mesh.Surface torn = new Mesh.Surface() {
                @Override
                public double radius(double nx, double ny, double nz) {
                    double dp = nx * shot.x + ny * shot.y + nz * shot.z;
                    double ell = 1.0 / Math.sqrt(dp * dp / 6.0 + (1 - dp * dp));
                    return ell * (1 + 0.4 * (Noise3.fbm(nx * 2.2 + dd * 6, ny * 2.2, nz * 2.2) - 0.5) * 2);
                }

                @Override
                public void color(double nx, double ny, double nz, double fres, float[] out) {
                    plasma(dd * 5, 2.4, 2.0, dd, (int) seed, DEEP, HOT, a, 1.4).color(nx, ny, nz, fres, out);
                }
            };
            m.sphere(at.x, at.y, at.z, r, latOf(d.lod()), lonOf(d.lod()), torn);
        }
        if (di < 0.7 && d.lod() <= 1) {
            double x = Curves.clamp01(di / 0.2);
            double len = 16 * cast.scale() * (1 - Math.pow(1 - x, 3));
            double fade = 1 - Curves.smoothstep(di / 0.7);
            final double fdi = di;
            final int sd = (int) seed;
            m.tube(at.subtract(shot.scale(1.0)), shot, len, 1.2, 1.2 + 5.0 * (len / 16), 1.5, 14, 24, (u, v, out) -> {
                double sr = Noise3.ridged(v * 7 + sd * 0.1, u * 3 - fdi * 9, fdi * 2);
                double band = Math.exp(-Math.pow((u - fdi / 0.4) / 0.08, 2));
                out[0] = 1f;
                out[1] = (float) Math.min(1, 0.16 + 0.35 * sr + band * 0.5);
                out[2] = (float) Math.min(1, 0.08 + 0.25 * sr + band * 0.4);
                out[3] = (float) (fade * (0.2 + 0.8 * sr) * (1 - 0.7 * u) * (0.6 + 0.9 * band));
            });
        }
        if (d.lod() == 0) {
            double sf = Math.exp(-di * 3.5);
            for (int i = 0; i < 9; i++) {
                double spread = (rnd(seed, 3300 + i, 1) - 0.5) * 0.55;
                double lift = (rnd(seed, 3300 + i, 2) - 0.5) * 0.4;
                Vec3 dir = shot.add(b[0].scale(spread)).add(b[1].scale(lift)).normalize();
                m.ribbon(at, at.add(dir.scale(10 + 10 * rnd(seed, 3300 + i, 3) * Math.min(1, di * 6))), 0.09, 0.005,
                        c(1f, 0.35f, 0.18f, 0.7 * sf), c(1f, 0.15f, 0.08f, 0));
            }
        }
        double up = Curves.smoothstep(di / 0.22);
        double fade = 1 - Curves.smoothstep(di / 1.8);
        if (fade > 0.01) {
            Vec3 top = at.add(0, (cast.max() ? 90 : 38) * up, 0);
            m.ribbon(at, top, 1.2 * fade + 0.25, 0.4, c(1f, 0.10f, 0.05f, 0.30 * fade), c(1f, 0.10f, 0.05f, 0.04 * fade));
            m.ribbon(at, top, 0.42 * fade + 0.08, 0.1, c(1f, 0.6f, 0.45f, 0.7 * fade), c(1f, 0.6f, 0.45f, 0.08 * fade));
        }
        for (int k = 0; k < 3; k++) {
            double wave = (di - 0.05 - k * 0.12) / 0.5;
            if (wave <= 0 || wave >= 1.3) {
                continue;
            }
            double x = Math.min(1, wave);
            double base = RedTimings.BLAST_RADIUS * (cast.max() ? 2.0 : 1.0) * (1 - Math.pow(1 - x, 3)) * (1 - 0.12 * k);
            double wf = Math.pow(Math.max(0, 1 - wave / 1.3), 1.6);
            m.groundEllipseRing(at.x, at.y + 0.05, at.z, ang, base * 1.55, base * 0.7, 0.6 + 0.6 * x, c(1f, 0.16f, 0.09f, 0.32 * wf));
        }
        double crackLife = Math.max(0, 1 - di / cast.linger());
        if (crackLife > 0 && d.lod() <= 1) {
            double reach = Curves.smoothstep(di / 0.35);
            int cracks = d.lod() == 0 ? 16 : 8;
            for (int i = 0; i < cracks; i++) {
                double a0 = rnd(seed, 1700 + i, 1) * Math.PI * 2;
                double along = 0.5 + 0.5 * Math.cos(a0 - ang);
                double len = (2.0 + 3.0 * rnd(seed, 1700 + i, 2) + 6.0 * along) * reach;
                Vec3 prev = at.add(0, 0.05, 0);
                int segs = 10;
                double a = a0;
                for (int k = 1; k <= segs; k++) {
                    a += (rnd(seed, 1700 + i, 10 + k) - 0.5) * 0.7;
                    Vec3 q = prev.add(Math.cos(a) * len / segs, 0, Math.sin(a) * len / segs);
                    float[] col = c(1f, 0.20f, 0.08f, 0.85 * crackLife * (1 - 0.6 * k / segs));
                    m.ribbon(prev, q, 0.07, 0.06, col, col);
                    prev = q;
                }
            }
        }
        m.groundFan(at.x, at.y + 0.04, at.z, cast.max() ? 18 : 7, c(1f, 0.08f, 0.05f, 0.5 * Math.exp(-di * 1.6)));
        if (d.lod() <= 1) {
            for (int i = 0; i < 30; i++) {
                Vec3 dir = flungDir(seed, i, shot);
                double v = 6 + 12 * rnd(seed, 1100 + i, 3);
                Vec3 p = at.add(dir.scale(v * di)).add(0, -6.0 * di * di, 0);
                double a = Math.max(0, 1 - di / 1.5);
                if (a > 0.01) {
                    m.billboard(p, 0.05 + 0.09 * rnd(seed, 1100 + i, 4), c(1f, 0.35f, 0.15f, a), i);
                }
            }
            int motes = (d.lod() == 0 ? 60 : 24) * (cast.max() ? 3 : 1);
            for (int i = 0; i < motes; i++) {
                double life = Math.max(0, 1 - di / cast.linger());
                if (life <= 0) {
                    break;
                }
                Vec3 off = randomDir(seed, 1500 + i).scale(1.0 + 3.6 * rnd(seed, 1500 + i, 3) * Math.pow(Math.min(1, di * 2), 0.4)).add(shot.scale(2.0 * rnd(seed, 1500 + i, 6)));
                double sway = di * (0.3 + 0.5 * rnd(seed, 1500 + i, 4));
                Vec3 p = at.add(off.x, Math.abs(off.y) * 0.6 + sway, off.z);
                double flick = 0.6 + 0.4 * Math.sin(di * 7 + i);
                m.billboard(p, 0.04 + 0.05 * rnd(seed, 1500 + i, 5), c(1f, 0.16f, 0.08f, 0.7 * Math.pow(life, 1.5) * flick), i);
            }
        }
    }

private static final double COMPRESS_WINDOW = 0.10;

    private static void compress(Mesh m, Draw d, double before) {
        ClientCast cast = d.cast();
        double u = 1 - before / COMPRESS_WINDOW;
        Vec3 at = cast.impact;
        Vec3 shot = shotDir(d, d.releaseCore());
        Vec3[] b = basis(shot);
        double rp = RedProfile.projectileRadius(cast.travelSeconds) * cast.scale() * (1 + 0.8 * u);
        final double squash = 1.0 - 0.75 * u;
        final double bt = before;
        final int sd = (int) cast.seed;
        Mesh.Surface skin = plasma(bt * 9, 2.6, 2.4, bt, sd, DEEP, HOT, 0.9, 1.5);
        Mesh.Surface flattened = new Mesh.Surface() {
            @Override
            public double radius(double nx, double ny, double nz) {
                double dp = nx * shot.x + ny * shot.y + nz * shot.z;
                return 1.0 / Math.sqrt(dp * dp / (squash * squash) + (1 - dp * dp));
            }

            @Override
            public void color(double nx, double ny, double nz, double fres, float[] out) {
                skin.color(nx, ny, nz, fres, out);
            }
        };
        m.sphere(at.x - shot.x * rp * squash * 0.5, at.y - shot.y * rp * squash * 0.5, at.z - shot.z * rp * squash * 0.5, rp,
                latOf(d.lod()), lonOf(d.lod()), flattened);
        m.glow(at, rp * (0.8 + u), c(1f, 0.75f, 0.6f, 0.5 + 0.5 * u));
        ring(m, at, b[0], b[1], rp * (1.1 + 0.6 * u), 0.07, c(1f, 0.4f, 0.25f, 0.8 * u), 48, 0, 0, -2);
    }

    private static void impactSecond(Mesh m, Draw d, double di, Vec3 shot, double ang) {
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 at = cast.impact;
        Vec3 mid = at.add(0, 0.4, 0);
        double remnant = Curves.smoothstep((di - 0.10) / 0.06) * (1 - Curves.smoothstep((di - 0.28) / 0.04));
        if (remnant > 0.01) {
            double r = 0.5 + 0.06 * Math.sin(di * 120);
            m.sphere(mid.x, mid.y, mid.z, r, latOf(d.lod()), lonOf(d.lod()), plasma(di * 6, 2.8, 2.0, di, (int) seed, DEEP, HOT, 0.9 * remnant, 1.4));
            m.sphere(mid.x, mid.y, mid.z, r * 0.4, 10, 16, flat(1f, 0.85f, 0.75f, 0.9 * remnant));
            m.glow(mid, 2.0, c(1f, 0.15f, 0.08f, 0.35 * remnant));
        }
        double dd = di - 0.30;
        if (dd < 0 || dd > 0.9) {
            return;
        }
        double x = Curves.clamp01(dd / 0.22);
        double fade = 1 - Curves.smoothstep(dd / 0.9);
        m.glow(mid, 3 + 6 * x, c(1f, 0.55f, 0.4f, Math.exp(-dd * 14)));
        double base = RedTimings.BLAST_RADIUS * 1.5 * (1 - Math.pow(1 - x, 3));
        m.groundEllipseRing(at.x, at.y + 0.05, at.z, ang, base * 1.5, base * 0.75, 0.5 + 0.5 * x, c(1f, 0.2f, 0.1f, 0.55 * Math.pow(fade, 1.5)));
        m.groundFan(at.x, at.y + 0.04, at.z, 10, c(1f, 0.08f, 0.05f, 0.4 * Math.exp(-dd * 9)));
        if (d.lod() <= 1) {
            int n = d.lod() == 0 ? 70 : 30;
            for (int i = 0; i < n; i++) {
                double a0 = rnd(seed, 5000 + i, 1) * Math.PI * 2;
                double v = 25 + 30 * rnd(seed, 5000 + i, 2);
                Vec3 p = at.add(Math.cos(a0) * v * dd, 0.2 + 1.2 * rnd(seed, 5000 + i, 3) * dd, Math.sin(a0) * v * dd);
                m.billboard(p, 0.08 + 0.16 * rnd(seed, 5000 + i, 4), c(1f, 0.4f, 0.2f, 0.9 * Math.max(0, 1 - dd / 0.6)), i);
            }
        }
    }

private static void requestPostFx(List<Draw> draws, Vec3 cam, Matrix4f view, Matrix4f projection) {
        Draw best = null;
        double bestScore = 0;
        for (Draw d : draws) {
            double strength = strengthOf(d);
            double proximity = d.cast().local ? 1.0 : Math.max(0, 1 - d.distance() / 40.0) * 0.6;
            double score = strength * proximity;
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        if (best == null || bestScore < 0.01) {
            return;
        }
        double t = best.t();
        double proximity = best.cast().local ? 1.0 : Math.max(0, 1 - best.distance() / 40.0) * 0.6;
        Vec3 where = focus(best);
        double radius = focusRadius(best);
        boolean max = best.cast().max();
        float chroma = (float) (RedProfile.chroma(t, max) * proximity);
        float vignette = best.cast().local ? (float) RedProfile.vignette(t, max) : 0f;
        float flash = (float) Math.max(0, RedProfile.light(t, max) - 1.0);
        double di = best.cast().sinceImpact(t);
        if (max && di >= 0) {
            flash = (float) Math.max(flash, Math.max(0.8 * Math.exp(-di * 9), di >= 0.3 ? 0.6 * Math.exp(-(di - 0.3) * 14) : 0));
        }
        CastPostFx.request(where, cam, view, projection, (float) (strengthOf(best) * proximity), chroma, vignette, flash, (float) radius);
    }

    private static double strengthOf(Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        boolean max = cast.max();
        double s = RedProfile.distortion(t, max);
        if (cast.released()) {
            double di = cast.sinceImpact(t);
            double dtp = t - cast.releaseAt;
            if (dtp >= 0 && dtp <= cast.travelSeconds) {
                s = Math.max(s, max ? 0.8 : 0.6);
            }
            if (di >= 0) {
                s = Math.max(s, (max ? 1.0 : 0.9) * Math.exp(-di * (max ? 2.0 : 2.5)));
                if (max) {
                    s = Math.max(s, 0.15 * Math.max(0, 1 - di / cast.linger()));
                    if (di >= 0.3) {
                        s = Math.max(s, 0.85 * Math.exp(-(di - 0.3) * 6));
                    }
                }
            }
        }
        return s;
    }

    private static Vec3 focus(Draw d) {
        ClientCast cast = d.cast();
        double t = d.t();
        if (cast.released()) {
            double dtp = t - cast.releaseAt;
            double di = cast.sinceImpact(t);
            if (di >= 0) {
                return cast.impact;
            }
            if (dtp >= 0.1) {
                return pathAt(cast, d.releaseCore(), dtp);
            }
        }
        return t >= RedTimings.RELEASE ? d.releaseCore() : d.sockets().core();
    }

    private static double focusRadius(Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        boolean max = cast.max();
        if (cast.released() && cast.sinceImpact(t) >= 0) {
            double di = cast.sinceImpact(t);
            return max ? 4.0 + 8.0 * Curves.smoothstep(di / 0.5) * (1 - Curves.smoothstep(di / 3.0)) : 2.5;
        }
        if (t < RedTimings.RELEASE) {
            return RedProfile.coreRadius(t, max) * (max ? 2.4 : 1);
        }
        if (max && cast.released() && t - cast.releaseAt >= 0.1 && t - cast.releaseAt <= cast.travelSeconds) {
            return 6.0;
        }
        return Math.max(max ? 1.5 : 0.6, RedProfile.pressureRadius(t - RedTimings.RELEASE) * (max ? 1.2 : 0.5));
    }
}
