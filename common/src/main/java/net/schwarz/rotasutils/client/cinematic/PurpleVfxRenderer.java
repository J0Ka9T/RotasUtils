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
import net.schwarz.rotasutils.ability.PurpleTimings;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class PurpleVfxRenderer {
    private PurpleVfxRenderer() {
    }

    private static final float[] BLUE_DEEP = {0.0f, 0.05f, 0.40f, 1f};
    private static final float[] BLUE_HOT = {0.35f, 0.85f, 1.0f, 1f};
    private static final float[] RED_DEEP = {0.40f, 0.0f, 0.04f, 1f};
    private static final float[] RED_HOT = {1.0f, 0.20f, 0.10f, 1f};
    private static final float[] VIO_DEEP = {0.14f, 0.0f, 0.50f, 1f};
    private static final float[] VIO_HOT = {0.60f, 0.30f, 1.0f, 1f};
    private static final double COMPRESS_WINDOW = 0.25;
    private static final Vec3 LIGHT = new Vec3(0.3, 0.8, 0.5).normalize();

    private record Draw(ClientCast cast, double t, CameraRig.Frame frame, PurplePose.Sockets s, int lod, double distance) {
        double ta() {
            return PurpleProfile.animTime(t);
        }
    }

    private record Orbs(Vec3 blue, Vec3 red, Vec3 mid, double rB, double rR) {
    }

private static double rnd(long seed, int i, int k) {
        return RedVfxRenderer.rnd(seed, i, k);
    }

    private static float[] c(float r, float g, float b, double a) {
        return RedVfxRenderer.c(r, g, b, a);
    }

    private static Vec3 randomDir(long seed, int i) {
        return RedVfxRenderer.randomDir(seed, i);
    }

    private static Vec3[] basis(Vec3 axis) {
        return RedVfxRenderer.basis(axis);
    }

    private static int latOf(int lod) {
        return RedVfxRenderer.latOf(lod);
    }

    private static int lonOf(int lod) {
        return RedVfxRenderer.lonOf(lod);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Vec3 unit(Vec3 v) {
        return v.lengthSqr() < 1.0e-9 ? new Vec3(0, 0, 1) : v.normalize();
    }

    private static Mesh.Surface rim(float r, float g, float b, double alpha, double power) {
        return (nx, ny, nz, fres, out) -> {
            out[0] = r;
            out[1] = g;
            out[2] = b;
            out[3] = (float) (alpha * Math.pow(fres, power));
        };
    }

    private static Mesh.Surface darkShell(double alpha) {
        return (nx, ny, nz, fres, out) -> {
            out[0] = 0.03f;
            out[1] = 0.0f;
            out[2] = 0.08f;
            out[3] = (float) (alpha * (0.15 + 0.85 * Math.pow(fres, 1.4)));
        };
    }

    private static Mesh.Surface deformed(Vec3 axis, double amount, Mesh.Surface inner) {
        return new Mesh.Surface() {
            @Override
            public double radius(double nx, double ny, double nz) {
                double dp = nx * axis.x + ny * axis.y + nz * axis.z;
                return 1 + amount * (0.55 * Math.pow(Math.max(0, dp), 3) - 0.15 * (1 - dp * dp));
            }

            @Override
            public void color(double nx, double ny, double nz, double fres, float[] out) {
                inner.color(nx, ny, nz, fres, out);
            }
        };
    }

    private static int bigLat(int lod) {
        return lod == 0 ? 32 : 14;
    }

    private static int bigLon(int lod) {
        return lod == 0 ? 48 : 24;
    }

    private static Mesh.Surface body(double alpha) {
        return RedVfxRenderer.flat(0.13f, 0.0f, 0.30f, alpha);
    }

    private static void dot(Mesh m, Vec3 p, double size, float[] col) {
        m.glow(p, Math.min(size * 1.4, 0.022 * p.distanceTo(m.camera) + 0.004), col);
    }

    private static void chunk(Mesh m, Vec3 p, double size, float[] col, double spin) {
        Vec3 side = new Vec3(Math.cos(spin), 0, Math.sin(spin)), norm = new Vec3(-Math.sin(spin), 0, Math.cos(spin));
        col[3] *= (float) Curves.clamp01((p.distanceTo(m.camera) - 1.5) / 2.0);
        if (col[3] > 0.01f) {
            m.box(p.add(0, -size, 0), p.add(0, size, 0), side, norm, size, size, col, LIGHT);
        }
    }

    private static void bolt(Mesh m, Vec3 a, Vec3 b, double width, double jag, float[] col, long seed, int id) {
        Vec3 prev = a;
        int segs = 7;
        float[] hot = c(1f, 0.95f, 1f, col[3]);
        for (int k = 1; k <= segs; k++) {
            double u = (double) k / segs;
            Vec3 p = lerp(a, b, u);
            if (k < segs) {
                p = p.add(randomDir(seed, id * 31 + k).scale(jag * (0.35 + 0.65 * Math.sin(Math.PI * u))));
            }
            double w = width * (1 - 0.6 * u);
            m.ribbon(prev, p, w, w, col, col);
            m.ribbon(prev, p, w * 0.35, w * 0.35, hot, hot);
            prev = p;
        }
    }

    private static void crackle(Mesh m, Vec3 at, double r, double reach, int count, double amount, double time, long seed, int base) {
        int key = (int) Math.floor(time * 10);
        for (int i = 0; i < count; i++) {
            int id = base + i * 131 + key * 977;
            if (rnd(seed, id, 5) > 0.6) {
                continue;
            }
            Vec3 dir = randomDir(seed, id);
            Vec3 a = at.add(dir.scale(r));
            Vec3 b = at.add(dir.scale(r * (1 + reach * (0.4 + 0.6 * rnd(seed, id, 6))))).add(randomDir(seed, id + 1).scale(r * reach * 0.5));
            bolt(m, a, b, Math.max(0.012, r * 0.05), r * reach * 0.22, c(0.8f, 0.5f, 1f, 0.9 * amount), seed, id);
        }
    }

private static Vec3 orbAt(Vec3 hand, Vec3 dir, double r) {
        return hand.add(dir.scale(0.12 + 0.8 * r)).add(0, 0.02, 0);
    }

private static Vec3 coreCenter(Draw d) {
        return PurplePose.core(d.s(), d.t());
    }

    private static Vec3 releaseCore(Draw d) {
        ClientCast cast = d.cast();
        if (cast.liveOrigin != null) {
            return cast.liveOrigin;
        }
        return PurplePose.core(PurplePose.sockets(PurplePose.sample(PurpleTimings.RELEASE), d.frame().feet(), d.frame().yawDeg()), PurpleTimings.RELEASE);
    }

    private static Orbs orbs(Draw d) {
        double t = d.t();
        PurplePose.Sockets s = d.s();
        double rB = PurpleProfile.orbRadius(t, PurpleTimings.BLUE), rR = PurpleProfile.orbRadius(t, PurpleTimings.RED);
        Vec3 b = orbAt(s.handL(), s.dirL(), rB), r = orbAt(s.handR(), s.dirR(), rR);
        Vec3 axis = r.subtract(b);
        if (axis.lengthSqr() > 1.0e-6) {
            Vec3 n = axis.normalize().scale(PurpleProfile.deform(t) * 0.05);
            b = b.add(n);
            r = r.subtract(n);
        }
        Vec3 mid = b.add(r).scale(0.5);
        double mk = PurpleProfile.merge(t);
        return new Orbs(lerp(b, mid, mk), lerp(r, mid, mk), mid, rB, rR);
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
            if (!cast.purple()) {
                continue;
            }
            double t = cast.time(partialTick);
            if (t < 0 || t > cast.finishedAt() || (cast.cancelled && !cast.released())) {
                continue;
            }
            CameraRig.Frame frame = cast.frame(partialTick);
            PurplePose.Sockets s = PurplePose.sockets(PurplePose.sample(t), frame.feet(), frame.yawDeg());
            if (cast.liveOrigin == null && t >= PurpleTimings.RELEASE - 0.02) {
                cast.liveOrigin = PurplePose.core(s, t);
            }
            double distance = cam.distanceTo(s.mid());
            int lod = distance <= 30 ? 0 : distance <= 60 ? 1 : distance <= 120 ? 2 : 3;
            if (lod == 3 && !cast.released()) {
                continue;
            }
            draws.add(new Draw(cast, t, frame, s, lod, distance));
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
            int density = switch (mc.options.particles().get()) {
                case MINIMAL -> 20;
                case DECREASED -> 48;
                default -> 100;
            };
            PurpleDissolve.fragments(mesh, cam, partialTick, density);
            mesh.draw();
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        requestPostFx(draws, cam, view, projection);
    }

private static void alphaLayers(Mesh m, Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        if (d.lod() <= 1 && t < PurpleTimings.RELEASE) {
            environmentSolid(m, d);
        }
        if (t < PurpleTimings.POINT) {
            Orbs o = orbs(d);
            heart(m, d, o.blue(), o.rB(), new float[]{0.0f, 0.01f, 0.06f}, new float[]{0.0f, 0.03f, 0.18f});
            heart(m, d, o.red(), o.rR(), new float[]{0.03f, 0.0f, 0.005f}, new float[]{0.30f, 0.01f, 0.03f});
        } else if (t >= PurpleTimings.BORN && t < PurpleTimings.RELEASE) {
            double R = PurpleProfile.coreRadius(t);
            if (R > 0.01) {
                Vec3 at = coreCenter(d);
                double born = Curves.smoothstep((t - PurpleTimings.BORN) / 0.12);
                m.sphere(at.x, at.y, at.z, R * 0.98, latOf(d.lod()), lonOf(d.lod()), body(0.92 * born));
                m.sphere(at.x, at.y, at.z, R * 1.30, latOf(d.lod()), lonOf(d.lod()), darkShell(0.40 * born));
            }
        } else if (t >= PurpleTimings.RELEASE && cast.released()) {
            scorch(m, d, false);
            projectileSolid(m, d);
            double di = cast.sinceImpact(t);
            if (di >= 0) {
                impactSolid(m, d, di);
            } else if (di > -COMPRESS_WINDOW) {
                double u = 1 - (-di) / COMPRESS_WINDOW;
                Vec3 at = cast.impact;
                m.sphere(at.x, at.y, at.z, 2.6 * (1 - 0.5 * u), 10, 18, darkShell(0.4 * u));
            }
        }
    }

    private static void heart(Mesh m, Draw d, Vec3 at, double r, float[] heart, float[] skin) {
        if (r < 0.004) {
            return;
        }
        m.sphere(at.x, at.y, at.z, r * 0.74, latOf(d.lod()), lonOf(d.lod()), RedVfxRenderer.flat(heart[0], heart[1], heart[2], 0.97));
        m.sphere(at.x, at.y, at.z, r * 0.93, latOf(d.lod()), lonOf(d.lod()), RedVfxRenderer.flat(skin[0], skin[1], skin[2], 0.5));
    }

    private static void environmentSolid(Mesh m, Draw d) {
        double t = d.t(), ta = d.ta();
        double env = PurpleProfile.env(t);
        if (env < 0.02) {
            return;
        }
        long seed = d.cast().seed;
        Vec3 feet = d.frame().feet(), mid = d.s().mid();
        double pull = Curves.smootherstep(Curves.window(t, PurpleTimings.COLLAPSE, PurpleTimings.SILENCE));
        if (t >= PurpleTimings.BORN) {
            pull = 1 - Curves.smoothstep((t - PurpleTimings.BORN) / 0.6);
        }
        int rocks = d.lod() == 0 ? 20 : 8;
        for (int i = 0; i < rocks; i++) {
            double a0 = rnd(seed, 6100 + i, 1) * Math.PI * 2, r0 = 1.4 + 4.2 * rnd(seed, 6100 + i, 2);
            double delay = rnd(seed, 6100 + i, 3) * 2.0;
            double k = t >= PurpleTimings.BORN ? 1 : Curves.smoothstep(Curves.window(t, PurpleTimings.ROCKS + delay, PurpleTimings.ROCKS + delay + 2.5));
            double spin = a0 + ta * 0.08 * (1 + rnd(seed, 6100 + i, 4));
            double h = (0.3 + 2.6 * rnd(seed, 6100 + i, 5)) * k + 0.08 * Math.sin(ta * 1.3 + i);
            Vec3 p = new Vec3(feet.x + Math.cos(spin) * r0, feet.y + 0.15 + h, feet.z + Math.sin(spin) * r0);
            p = lerp(p, mid, pull * 0.9);
            double a = 0.9 * k * (1 - pull) * Math.min(1, env * 2);
            if (a > 0.01) {
                chunk(m, p, 0.04 + 0.07 * rnd(seed, 6100 + i, 6), c(0.33f, 0.31f, 0.30f, a), ta * 2 + i);
            }
        }
        int dust = d.lod() == 0 ? 40 : 16;
        for (int i = 0; i < dust; i++) {
            double a0 = rnd(seed, 6300 + i, 1) * Math.PI * 2, r0 = 0.8 + 5.2 * rnd(seed, 6300 + i, 2);
            double frac = (ta * 0.25 * (0.6 + rnd(seed, 6300 + i, 3)) + rnd(seed, 6300 + i, 4)) % 1.0;
            Vec3 p = new Vec3(feet.x + Math.cos(a0) * r0, feet.y + 0.1 + frac * 2.4, feet.z + Math.sin(a0) * r0);
            dot(m, p, 0.3 + 0.5 * rnd(seed, 6300 + i, 5), c(0.5f, 0.42f, 0.36f, 0.16 * env * Math.sin(Math.PI * frac)));
        }
    }

private static void additiveLayers(Mesh m, Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        bodyLights(m, d);
        if (d.lod() <= 1 && t < PurpleTimings.RELEASE) {
            environmentGlow(m, d);
        }
        if (t < PurpleTimings.POINT) {
            Orbs o = orbs(d);
            Vec3 toRed = unit(o.red().subtract(o.blue()));
            double def = PurpleProfile.deform(t);
            blueOrb(m, d, o.blue(), o.rB(), toRed, def);
            redOrb(m, d, o.red(), o.rR(), toRed.scale(-1), def);
            links(m, d, o);
        } else if (t < PurpleTimings.BORN) {
            point(m, d);
        } else if (t < PurpleTimings.RELEASE) {
            core(m, d);
        } else {
            releasing(m, d, t - PurpleTimings.RELEASE);
            if (cast.released()) {
                scorch(m, d, true);
                projectileAdditive(m, d);
                trail(m, d);
                double di = cast.sinceImpact(t);
                if (di < 0 && di > -COMPRESS_WINDOW) {
                    converge(m, d, 1 - (-di) / COMPRESS_WINDOW);
                }
                if (di >= 0) {
                    impactAdditive(m, d, di);
                }
            }
        }
    }

private static void blueOrb(Mesh m, Draw d, Vec3 at, double r, Vec3 toOther, double def) {
        if (r < 0.004) {
            return;
        }
        double t = d.t();
        long seed = d.cast().seed;
        int lat = latOf(d.lod()), lon = lonOf(d.lod());
        double v = Curves.smoothstep((t - PurpleTimings.BLUE) / 0.4);
        double charge = Curves.window(t, PurpleTimings.BLUE, PurpleTimings.COLLAPSE);
        m.sphere(at.x, at.y, at.z, r, lat, lon,
                deformed(toOther, def, RedVfxRenderer.plasma(-t * 0.9, 3.0, -1.2, t, (int) seed + 31, BLUE_DEEP, BLUE_HOT, 0.75 * v, 1.5)));
        if (d.lod() <= 1) {
            m.sphere(at.x, at.y, at.z, r * 1.16, lat, lon, deformed(toOther, def * 0.7, RedVfxRenderer.plasma(t * 0.5, 4.2, 0.8, t,
                    (int) seed + 37, new float[]{0f, 0.02f, 0.3f, 1f}, new float[]{0.2f, 0.6f, 1f, 1f}, 0.3 * v, 1.9)));
            m.sphere(at.x, at.y, at.z, r * 1.05, lat, lon, rim(0.55f, 0.92f, 1f, 0.9 * v, 3.0));
        }
        m.glow(at, r * 3.6, c(0.1f, 0.5f, 1f, (0.10 + 0.12 * charge) * v));
        m.glow(at, r * 1.9, c(0.3f, 0.8f, 1f, (0.14 + 0.14 * charge) * v));
        if (d.lod() <= 1) {
            for (int k = 0; k < 2; k++) {
                Vec3[] b = basis(randomDir(seed, 7100 + k));
                RedVfxRenderer.ring(m, at, b[0], b[1], r * (1.7 + 0.6 * k), r * 0.03, c(0.4f, 0.85f, 1f, 0.55 * v), 56,
                        t * (k == 0 ? 2.2 : -1.6) + rnd(seed, 7100 + k, 3) * 6, rnd(seed, 7100 + k, 4) * 6, -0.3);
            }
            trails(m, d, at, r, new float[]{0.35f, 0.8f, 1f}, true, 7200, d.lod() == 0 ? 8 : 4, v * charge);
            fragments(m, d, at, r, new float[]{0.45f, 0.9f, 1f}, true, 7300, v);
        }
    }

    private static void redOrb(Mesh m, Draw d, Vec3 at, double r, Vec3 toOther, double def) {
        if (r < 0.004) {
            return;
        }
        double t = d.t();
        long seed = d.cast().seed;
        int lat = latOf(d.lod()), lon = lonOf(d.lod());
        double v = Curves.smoothstep((t - PurpleTimings.RED) / 0.4);
        double charge = Curves.window(t, PurpleTimings.RED, PurpleTimings.COLLAPSE);
        m.sphere(at.x, at.y, at.z, r, lat, lon,
                deformed(toOther, def * 1.2, RedVfxRenderer.plasma(t * 0.55, 2.6, 0.7, t, (int) seed + 41, RED_DEEP, RED_HOT, 0.8 * v, 1.6)));
        if (d.lod() <= 1) {
            m.sphere(at.x, at.y, at.z, r * 1.14, lat, lon, deformed(toOther, def * 0.8, RedVfxRenderer.plasma(-t * 0.3, 3.8, -0.4, t,
                    (int) seed + 47, new float[]{0.36f, 0f, 0.05f, 1f}, new float[]{0.95f, 0.18f, 0.10f, 1f}, 0.34 * v, 1.9)));
            m.sphere(at.x, at.y, at.z, r * 1.04, lat, lon, rim(1f, 0.42f, 0.26f, 0.85 * v, 3.2));
        }
        m.glow(at, r * 4.5, c(0.9f, 0.06f, 0.04f, (0.10 + 0.14 * charge) * v));
        m.glow(at, r * 2.2, c(1f, 0.16f, 0.08f, (0.16 + 0.20 * charge) * v));
        if (d.lod() <= 1) {
            arcs(m, d, at, r, d.lod() == 0 ? 9 : 4, charge * v);
            trails(m, d, at, r, new float[]{1f, 0.2f, 0.10f}, false, 7400, d.lod() == 0 ? 7 : 4, v * charge);
            fragments(m, d, at, r, new float[]{1f, 0.4f, 0.18f}, false, 7450, v);
        }
    }

    private static void arcs(Mesh m, Draw d, Vec3 at, double r, int count, double amount) {
        long seed = d.cast().seed;
        double t = d.t();
        for (int i = 0; i < count; i++) {
            Vec3 axis = randomDir(seed, 7500 + i);
            Vec3[] b = basis(axis);
            double orbit = r * (1.25 + 1.8 * rnd(seed, 7500 + i, 3));
            double span = 0.7 + 1.1 * rnd(seed, 7500 + i, 4);
            double speed = (0.7 + 1.4 * rnd(seed, 7500 + i, 5)) * (rnd(seed, 7500 + i, 6) < 0.5 ? -1 : 1);
            double phase = rnd(seed, 7500 + i, 7) * Math.PI * 2;
            double width = r * (0.05 + 0.09 * rnd(seed, 7500 + i, 8));
            double life = Math.pow(Math.max(0, Math.sin(t * (1.2 + 2 * rnd(seed, 7500 + i, 9)) + phase * 3)), 0.7) * (0.4 + 0.6 * amount);
            if (life < 0.03) {
                continue;
            }
            Vec3 prev = null;
            int segs = 14;
            for (int k = 0; k <= segs; k++) {
                double a = phase + speed * t + span * k / segs;
                double wob = 1 + 0.14 * Curves.noise(k * 0.7 + t * 1.3 + i * 5.1, (int) seed);
                Vec3 p = at.add(b[0].scale(Math.cos(a) * orbit * wob)).add(b[1].scale(Math.sin(a) * orbit * wob))
                        .add(axis.scale(r * 0.25 * Curves.noise(k * 0.5 - t * 0.9 + i * 3.7, (int) seed + 3)));
                if (prev != null && Curves.noise(k * 0.9 + i * 7 + t * 2.0, (int) seed + 9) > -0.25) {
                    double taper = Math.sin(Math.PI * (k - 0.5) / segs);
                    float[] col = c(1f, 0.20f, 0.10f, 0.85 * life * taper);
                    m.ribbon(prev, p, width * taper, width * taper, col, col);
                }
                prev = p;
            }
        }
    }

    private static void trails(Mesh m, Draw d, Vec3 at, double r, float[] col, boolean inward, int base, int count, double amount) {
        if (amount < 0.02) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        for (int i = 0; i < count; i++) {
            Vec3[] b = basis(randomDir(seed, base + i));
            Vec3 axis = b[0].cross(b[1]);
            double phase = rnd(seed, base + i, 3), turns = 0.9 + 1.1 * rnd(seed, base + i, 4), far = r * (3.5 + 4.5 * rnd(seed, base + i, 5));
            double head = (t * (0.35 + 0.25 * rnd(seed, base + i, 6)) + phase) % 1.0;
            int segs = 18;
            Vec3 prev = null;
            for (int k = 0; k <= segs; k++) {
                double s = head - 0.5 * k / segs;
                if (s < 0) {
                    prev = null;
                    continue;
                }
                double rad = inward ? Curves.lerp(far, r * 1.05, Math.pow(s, 1.5)) : Curves.lerp(r * 1.1, far, Math.pow(s, 0.9));
                double ang = phase * Math.PI * 2 + (inward ? 1 : -1) * turns * Math.PI * 2 * s;
                Vec3 q = at.add(b[0].scale(Math.cos(ang) * rad)).add(b[1].scale(Math.sin(ang) * rad)).add(axis.scale(rad * 0.35 * Math.sin(ang * 0.7 + i)));
                if (prev != null) {
                    double a = Math.pow(1 - (double) k / segs, 1.4) * 0.7 * amount * Curves.smoothstep(s * 3);
                    double w = r * 0.06 * (1 - 0.6 * k / segs) + 0.004;
                    m.ribbon(prev, q, w, w, c(col[0], col[1], col[2], a), c(col[0], col[1], col[2], a * 0.9));
                }
                prev = q;
            }
        }
    }

    private static void fragments(Mesh m, Draw d, Vec3 at, double r, float[] col, boolean inward, int base, double amount) {
        long seed = d.cast().seed;
        double t = d.t();
        int n = d.lod() == 0 ? 30 : 12;
        for (int i = 0; i < n; i++) {
            Vec3 dir = randomDir(seed, base + i);
            double life = (t * (0.25 + 0.4 * rnd(seed, base + i, 3)) + rnd(seed, base + i, 4)) % 1.0;
            double far = 0.6 + 1.4 * rnd(seed, base + i, 5);
            double dist = inward ? r * 1.1 + far * Math.pow(1 - life, 1.3) : r * 1.1 + far * Math.pow(life, 0.7);
            double a = (inward ? Math.sin(Math.PI * life) : 1 - life) * amount * 0.8;
            dot(m, at.add(dir.scale(dist)), 0.02 + 0.035 * rnd(seed, base + i, 6), c(col[0], col[1], col[2], a));
        }
    }

    private static void links(Mesh m, Draw d, Orbs o) {
        double t = d.t();
        double def = PurpleProfile.deform(t), sp = PurpleProfile.sparks(t);
        Vec3 gap = o.red().subtract(o.blue());
        double len = gap.length();
        if ((def < 0.03 && sp < 0.03) || len < 1.0e-3) {
            return;
        }
        long seed = d.cast().seed;
        Vec3 dir = gap.scale(1 / len);
        Vec3[] b = basis(dir);
        int n = d.lod() == 0 ? 5 : 3;
        for (int i = 0; i < n; i++) {
            double a0 = rnd(seed, 7600 + i, 1) * Math.PI * 2;
            Vec3 side = b[0].scale(Math.cos(a0)).add(b[1].scale(Math.sin(a0)));
            Vec3 p0 = o.blue().add(dir.scale(o.rB() * 0.9)).add(side.scale(o.rB() * 0.6 * rnd(seed, 7600 + i, 2)));
            Vec3 p1 = o.red().subtract(dir.scale(o.rR() * 0.9)).add(side.scale(o.rR() * 0.6 * rnd(seed, 7600 + i, 3)));
            Vec3 ctl = lerp(p0, p1, 0.5).add(side.scale(len * (0.25 + 0.35 * rnd(seed, 7600 + i, 4)) * (0.6 + 0.4 * Math.sin(t * 4 + i))));
            double life = def * Math.pow(Math.max(0, Math.sin(t * (2 + 3 * rnd(seed, 7600 + i, 5)) + i * 1.7)), 0.6);
            if (life < 0.03) {
                continue;
            }
            double width = 0.012 + 0.02 * rnd(seed, 7600 + i, 6);
            Vec3 prev = null;
            int segs = 14;
            for (int k = 0; k <= segs; k++) {
                double u = (double) k / segs;
                Vec3 q = p0.scale((1 - u) * (1 - u)).add(ctl.scale(2 * (1 - u) * u)).add(p1.scale(u * u));
                if (prev != null) {
                    float[] col = c((float) (0.3 + 0.7 * u), (float) (0.7 * (1 - u) + 0.15 * u), (float) (1 - 0.8 * u), 0.7 * life);
                    double w = width * (0.5 + Math.sin(Math.PI * u));
                    m.ribbon(prev, q, w, w, col, col);
                }
                prev = q;
            }
        }
        int key = (int) Math.floor(t * 12);
        for (int i = 0; i < 3 && sp > 0.05; i++) {
            int id = 7800 + i * 131 + key * 977;
            if (rnd(seed, id, 5) < 0.3 + 0.5 * sp) {
                bolt(m, o.blue().add(dir.scale(o.rB())), o.red().subtract(dir.scale(o.rR())), 0.02, len * 0.18, c(0.8f, 0.5f, 1f, 0.9 * sp), seed, id);
            }
        }
        int ns = (int) ((d.lod() == 0 ? 4 + 14 : 4 + 6) * sp);
        for (int i = 0; i < ns; i++) {
            Vec3 p = lerp(o.blue(), o.red(), rnd(seed, 7700 + i, 1)).add(randomDir(seed, 7700 + i).scale(0.08 + 0.22 * rnd(seed, 7700 + i, 2)));
            double a = sp * (0.5 + 0.5 * Math.sin(t * 30 + i * 2.3));
            dot(m, p, 0.02 + 0.04 * rnd(seed, 7700 + i, 3), c(0.8f, 0.5f, 1f, a));
        }
    }

    private static void point(Mesh m, Draw d) {
        double r = PurpleProfile.pointRadius(d.t());
        if (r <= 0) {
            return;
        }
        Vec3 at = coreCenter(d);
        m.sphere(at.x, at.y, at.z, r, 8, 12, RedVfxRenderer.flat(1f, 0.94f, 1f, 1.0));
        m.glow(at, 0.08, c(0.9f, 0.6f, 1f, 0.9));
        m.glow(at, 0.28, c(0.6f, 0.25f, 1f, 0.25));
    }

private static void core(Mesh m, Draw d) {
        double t = d.t(), ta = d.ta();
        long seed = d.cast().seed;
        double R = PurpleProfile.coreRadius(t);
        if (R < 0.01) {
            return;
        }
        Vec3 at = coreCenter(d);
        int lat = latOf(d.lod()), lon = lonOf(d.lod());
        double born = Curves.smoothstep((t - PurpleTimings.BORN) / 0.10);
        double L = Math.min(1.5, PurpleProfile.light(t));
        double sq = 1 - 0.85 * Curves.window(t, PurpleTimings.COMPRESS, PurpleTimings.COMPRESS_END);
        m.sphere(at.x, at.y, at.z, R, lat, lon, RedVfxRenderer.plasma(ta * 0.9, 2.2, 0.9, ta, (int) seed + 7, VIO_DEEP, VIO_HOT, 0.9 * born, 1.4));
        m.sphere(at.x, at.y, at.z, R * 1.04, lat, lon, rim(0.8f, 0.55f, 1f, 0.9 * born, 3.0));
        m.sphere(at.x, at.y, at.z, R * 0.30, lat, lon, RedVfxRenderer.flat(1f, 0.96f, 1f, 0.95 * born));
        m.glow(at, R * 0.70, c(1f, 0.92f, 1f, 0.75 * born));
        m.glow(at, R * 3.0, c(0.5f, 0.2f, 1f, 0.26 * L * born));
        m.glow(at, R * 1.6, c(0.7f, 0.45f, 1f, 0.22 * L * born));
        if (d.lod() <= 1) {
            double merging = born * (1 - Curves.smoothstep((t - PurpleTimings.BORN) / 1.4));
            if (merging > 0.02) {
                helices(m, at, R, ta, seed, merging);
            }
            ribbons(m, d, at, R, ta, seed, born * sq);
            halo(m, d, at, R, ta, seed, born * sq);
            crackle(m, at, R, 1.3, d.lod() == 0 ? 10 : 5, born, ta, seed, 8600);
        }
        if (t < PurpleTimings.BORN + 0.6) {
            m.glow(at, 3.0 + R * 6, c(1f, 0.95f, 1f, 0.9 * Math.exp(-(t - PurpleTimings.BORN) / 0.12)));
        }
    }

    private static void helices(Mesh m, Vec3 at, double R, double ta, long seed, double amount) {
        Vec3[] b = basis(randomDir(seed, 8100));
        Vec3 axis = b[0].cross(b[1]);
        for (int strand = 0; strand < 4; strand++) {
            boolean red = strand % 2 == 0;
            double phase = strand * Math.PI / 2;
            Vec3 prev = null;
            int segs = 28;
            for (int k = 0; k <= segs; k++) {
                double s = (double) k / segs;
                double along = (s - 0.5) * 2 * R * 0.95;
                double radius = R * 0.62 * Math.sqrt(Math.max(0, 1 - Math.pow(2 * s - 1, 2))) + R * 0.05;
                double ang = ta * (red ? 3.0 : -3.0) + s * 7 + phase;
                Vec3 p = at.add(axis.scale(along)).add(b[0].scale(Math.cos(ang) * radius)).add(b[1].scale(Math.sin(ang) * radius));
                if (prev != null) {
                    float[] col = red ? c(1f, 0.25f, 0.15f, 0.8 * amount) : c(0.3f, 0.7f, 1f, 0.8 * amount);
                    double w = R * 0.035 * (0.5 + 0.5 * Math.sin(Math.PI * s));
                    m.ribbon(prev, p, w, w, col, col);
                }
                prev = p;
            }
        }
    }

    private static void ribbons(Mesh m, Draw d, Vec3 at, double R, double ta, long seed, double amount) {
        int n = d.lod() == 0 ? 8 : 4;
        for (int i = 0; i < n; i++) {
            Vec3[] b = basis(randomDir(seed, 8000 + i));
            double a = R * (1.5 + 2.2 * rnd(seed, 8000 + i, 3)), bb = a * (0.45 + 0.4 * rnd(seed, 8000 + i, 4));
            double speed = (0.3 + 0.7 * rnd(seed, 8000 + i, 5)) * (rnd(seed, 8000 + i, 6) < 0.5 ? -1 : 1);
            double phase = rnd(seed, 8000 + i, 7) * Math.PI * 2, span = 0.8 + 1.4 * rnd(seed, 8000 + i, 8);
            double width = R * (0.03 + 0.06 * rnd(seed, 8000 + i, 9));
            boolean flash = ((long) Math.floor(ta * 20) + i * 7L) % 23 == 0;
            double alpha = (flash ? 1.0 : 0.12 + 0.35 * rnd(seed, 8000 + i, 10)) * amount;
            Vec3 prev = null;
            int segs = 20;
            for (int k = 0; k <= segs; k++) {
                double ang = phase + speed * ta + span * k / segs;
                Vec3 p = at.add(b[0].scale(Math.cos(ang) * a)).add(b[1].scale(Math.sin(ang) * bb));
                if (prev != null && Curves.noise(k * 0.9 + i * 7 + ta, (int) seed + 5) > -0.2) {
                    double taper = Math.sin(Math.PI * (k - 0.5) / segs);
                    float[] col = flash ? c(0.95f, 0.85f, 1f, alpha * taper) : c(0.6f, 0.25f, 1f, alpha * taper);
                    m.ribbon(prev, p, width * taper * (flash ? 1.6 : 1), width * taper * (flash ? 1.6 : 1), col, col);
                }
                prev = p;
            }
        }
    }

    private static void halo(Mesh m, Draw d, Vec3 at, double R, double ta, long seed, double amount) {
        m.glow(at, R * 7.5, c(0.5f, 0.2f, 0.95f, 0.09 * amount));
        int stars = d.lod() == 0 ? 40 : 20;
        for (int i = 0; i < stars; i++) {
            Vec3 p = at.add(randomDir(seed, 8300 + i).scale(R * (2.2 + 3.4 * rnd(seed, 8300 + i, 3))));
            double tw = 0.5 + 0.5 * Math.sin(ta * 2 + i * 1.9);
            dot(m, p, 0.02 + 0.025 * rnd(seed, 8300 + i, 4), c(0.95f, 0.9f, 1f, 0.9 * tw * amount));
        }
        int dust = d.lod() == 0 ? 30 : 12;
        for (int i = 0; i < dust; i++) {
            Vec3 o = randomDir(seed, 8400 + i).scale(R * (1.8 + 3.6 * rnd(seed, 8400 + i, 3)));
            double ang = ta * 0.08 * (0.5 + rnd(seed, 8400 + i, 4));
            Vec3 p = at.add(o.x * Math.cos(ang) - o.z * Math.sin(ang), o.y, o.x * Math.sin(ang) + o.z * Math.cos(ang));
            dot(m, p, 0.15 + 0.3 * rnd(seed, 8400 + i, 5), c(0.55f, 0.25f, 0.9f, 0.07 * amount));
        }
    }

private static void bodyLights(Mesh m, Draw d) {
        double t = d.t();
        PurplePose.Sockets s = d.s();
        double gy = d.frame().feet().y + 0.03;
        double side = PurpleProfile.sideLight(t);
        double L = PurpleProfile.light(t);
        if (side > 0.01 && t < PurpleTimings.RELEASE) {
            Orbs o = orbs(d);
            double vb = Math.min(1, o.rB() * 6), vr = Math.min(1, o.rR() * 6);
            if (t < PurpleTimings.POINT) {
                m.groundFan(s.handL().x, gy, s.handL().z, 3.6, c(0.1f, 0.35f, 1f, Math.min(0.3, 0.22 * side * vb)));
                m.groundFan(s.handR().x, gy, s.handR().z, 3.6, c(1f, 0.06f, 0.05f, Math.min(0.3, 0.22 * side * vr)));
                m.glow(s.handL(), 0.9, c(0.1f, 0.4f, 1f, 0.16 * side * vb));
                m.glow(s.handR(), 0.9, c(1f, 0.1f, 0.06f, 0.16 * side * vr));
            } else {
                m.glow(s.handL(), 0.7, c(0.2f, 0.5f, 1f, 0.10 * side / 0.15 * 0.15));
                m.glow(s.handR(), 0.7, c(1f, 0.2f, 0.12f, 0.10 * side / 0.15 * 0.15));
            }
        }
        if (t >= PurpleTimings.BORN && t < PurpleTimings.RELEASE && L > 0.01) {
            Vec3 feet = d.frame().feet();
            m.groundFan(feet.x, gy, feet.z, 9, c(0.5f, 0.15f, 1f, Math.min(0.4, 0.26 * L)));
            m.glow(s.chest(), 1.7, c(0.6f, 0.25f, 1f, 0.14 * Math.min(L, 1.5)));
        }
        if (t >= PurpleTimings.RELEASE && L > 0.01) {
            Vec3 feet = d.frame().feet();
            m.groundFan(feet.x, gy, feet.z, 9, c(0.55f, 0.2f, 1f, Math.min(0.5, 0.3 * L)));
        }
    }

    private static void environmentGlow(Mesh m, Draw d) {
        double t = d.t(), ta = d.ta();
        double env = PurpleProfile.env(t);
        long seed = d.cast().seed;
        Vec3 feet = d.frame().feet();
        double dr = Curves.smoothstep(Curves.window(t, 0.4, 2.0));
        int n = d.lod() == 0 ? 50 : 20;
        for (int i = 0; i < n; i++) {
            double a0 = rnd(seed, 6500 + i, 1) * Math.PI * 2, r0 = 2.5 + 5 * rnd(seed, 6500 + i, 2);
            double frac = (ta * 0.06 * (0.6 + rnd(seed, 6500 + i, 3)) + rnd(seed, 6500 + i, 4)) % 1.0;
            double r = r0 * (1 - 0.55 * frac);
            Vec3 p = new Vec3(feet.x + Math.cos(a0) * r, feet.y + 0.2 + 2.6 * rnd(seed, 6500 + i, 5), feet.z + Math.sin(a0) * r);
            dot(m, p, 0.02 + 0.03 * rnd(seed, 6500 + i, 6), c(0.7f, 0.75f, 1f, 0.35 * dr * Math.sin(Math.PI * frac)));
        }
        if (env < 0.02) {
            return;
        }
        double gy = feet.y + 0.05;
        if (t < PurpleTimings.POINT) {
            Orbs o = orbs(d);
            int n2 = d.lod() == 0 ? 40 : 16;
            if (o.rB() > 0.02) {
                for (int i = 0; i < n2; i++) {
                    Vec3 start = o.blue().add(randomDir(seed, 6600 + i).scale(1.5 + 3.5 * rnd(seed, 6600 + i, 1)));
                    double frac = (t * (0.2 + 0.3 * rnd(seed, 6600 + i, 2)) + rnd(seed, 6600 + i, 3)) % 1.0;
                    dot(m, lerp(start, o.blue(), Math.pow(frac, 2.2)), 0.03 + 0.03 * rnd(seed, 6600 + i, 4),
                            c(0.35f, 0.8f, 1f, 0.5 * env * Math.sin(Math.PI * frac)));
                }
            }
            if (o.rR() > 0.02) {
                for (int i = 0; i < n2; i++) {
                    double frac = (t * (0.25 + 0.35 * rnd(seed, 6700 + i, 2)) + rnd(seed, 6700 + i, 3)) % 1.0;
                    Vec3 p = o.red().add(randomDir(seed, 6700 + i).scale(o.rR() * 1.1 + Math.pow(frac, 0.7) * (3 + 3 * rnd(seed, 6700 + i, 1))));
                    dot(m, p, 0.03 + 0.03 * rnd(seed, 6700 + i, 4), c(1f, 0.35f, 0.15f, 0.5 * env * (1 - frac)));
                }
            }
            double k = (t * 0.6 + 0.2) % 1.0;
            m.groundRing(d.s().handR().x, gy, d.s().handR().z, 0.5 + 5 * k, 0.4 + 0.4 * k, c(1f, 0.15f, 0.1f, 0.22 * (1 - k) * env));
            m.groundRing(d.s().handL().x, gy, d.s().handL().z, 0.3 + 5 * (1 - k), 0.4 + 0.4 * (1 - k), c(0.2f, 0.55f, 1f, 0.22 * k * env));
        } else if (t >= PurpleTimings.BORN) {
            double k = ((ta - PurpleTimings.BORN) / 1.25) % 1.0;
            m.groundRing(feet.x, gy, feet.z, 1 + 8 * k, 0.5 + 0.5 * k, c(0.6f, 0.3f, 1f, 0.22 * (1 - k) * env));
        }
    }

private static Vec3 shotDir(Draw d, Vec3 origin) {
        ClientCast cast = d.cast();
        return cast.released() ? unit(cast.impact.subtract(origin)) : d.frame().forward();
    }

    private static void releasing(Mesh m, Draw d, double dt) {
        Vec3 o = releaseCore(d);
        long seed = d.cast().seed;
        Vec3 shot = shotDir(d, o);
        if (dt < 0.6) {
            m.glow(o, 9.0, c(0.95f, 0.85f, 1f, 0.95 * Math.exp(-dt * 9)));
            m.glow(o, 2.2, c(1f, 1f, 1f, Math.exp(-dt * 14)));
        }
        double x = Curves.clamp01(dt / 0.4);
        double ease = 1 - Math.pow(1 - x, 3);
        if (dt < 0.35 && d.lod() <= 1) {
            double a = 0.8 * (1 - dt / 0.35);
            for (int i = 0; i < 18; i++) {
                Vec3 out = randomDir(seed, 9850 + i);
                m.ribbon(o.add(out.scale(1.2 + 5 * ease)), o.add(out.scale(2.2 + 9 * ease)), 0.05, 0.0, c(0.95f, 0.85f, 1f, a), c(0.7f, 0.4f, 1f, 0));
            }
        }
        Vec3 feet = d.frame().feet();
        double ang = Math.atan2(shot.z, shot.x);
        double wf = 1 - Curves.smoothstep(dt / 1.0);
        if (wf > 0.01) {
            m.groundWedge(feet.x, feet.y + 0.04, feet.z, ang, 24 * ease, 0.5, c(0.6f, 0.3f, 1f, 0.35 * wf));
            m.groundRing(feet.x, feet.y + 0.05, feet.z, 14 * ease, 0.6 + 0.6 * x, c(0.6f, 0.3f, 1f, 0.4 * wf));
        }
        if (dt < 1.3 && d.lod() <= 1) {
            double front = 26 * ease;
            int count = d.lod() == 0 ? 90 : 36;
            for (int i = 0; i < count; i++) {
                double a = ang + (rnd(seed, 9800 + i, 1) - 0.5) * 2.2;
                double r = front * (0.35 + 0.65 * rnd(seed, 9800 + i, 2));
                Vec3 p = new Vec3(feet.x + Math.cos(a) * r, feet.y + 0.1 + rnd(seed, 9800 + i, 3) * (0.3 + 1.6 * dt), feet.z + Math.sin(a) * r);
                dot(m, p, 0.25 + 0.7 * dt + 0.3 * rnd(seed, 9800 + i, 4), c(0.5f, 0.42f, 0.5f, 0.3 * Math.max(0, 1 - dt / 1.2)));
            }
        }
    }

private static Vec3 pathAt(ClientCast cast, double dtp) {
        Vec3 origin = cast.liveOrigin != null ? cast.liveOrigin : cast.releaseOrigin;
        double travel = Math.max(0.05, cast.travelSeconds);
        double launch = Math.min(0.10, travel * 0.4);
        double u = Curves.clamp01((dtp - launch) / (travel - launch));
        return origin.add(cast.impact.subtract(origin).scale(Math.pow(u, 1.5)));
    }

    private static double squeeze(ClientCast cast, double t) {
        double di = cast.sinceImpact(t);
        return di < 0 && di > -COMPRESS_WINDOW ? 1 - 0.55 * Curves.smoothstep((di + COMPRESS_WINDOW) / COMPRESS_WINDOW) : 1;
    }

    private static final double STRETCH = 1.25;

    private static boolean flying(Draw d, double dtp) {
        return dtp >= 0 && dtp <= d.cast().travelSeconds && d.lod() <= 2;
    }

    private static void projectileSolid(Mesh m, Draw d) {
        ClientCast cast = d.cast();
        double dtp = d.t() - cast.releaseAt;
        if (!flying(d, dtp)) {
            return;
        }
        long seed = cast.seed;
        Vec3 p = pathAt(cast, dtp);
        Vec3 dir = unit(cast.impact.subtract(cast.liveOrigin != null ? cast.liveOrigin : cast.releaseOrigin));
        double rp = PurpleProfile.projectileRadius(dtp) * squeeze(cast, d.t());
        m.sphere(p.x, p.y, p.z, rp * 0.98, bigLat(d.lod()), bigLon(d.lod()), RedVfxRenderer.elongated(dir, 1.0, STRETCH, body(0.94)));
        m.sphere(p.x, p.y, p.z, rp * 1.25, 14, 24, RedVfxRenderer.elongated(dir, 1.0, STRETCH, darkShell(0.35)));
        if (d.lod() <= 1) {
            double gy = d.frame().feet().y;
            for (int k = 0; k < 12; k++) {
                double tk = dtp - k * 0.09;
                if (tk < 0) {
                    break;
                }
                Vec3 q = pathAt(cast, tk);
                if (Math.abs(q.y - gy) > 8) {
                    continue;
                }
                double age = dtp - tk, x = age / 1.4;
                if (x > 1) {
                    continue;
                }
                for (int j = 0; j < 4; j++) {
                    int id = 9500 + k * 4 + j;
                    double a0 = rnd(seed, id, 1) * Math.PI * 2;
                    double r = age < 0.14 ? 6 * (1 - age / 0.14) : 10 * Curves.smoothstep((age - 0.14) / 0.9) * (0.5 + 0.5 * rnd(seed, id, 2));
                    dot(m, new Vec3(q.x + Math.cos(a0) * r, gy + 0.2 + 1.5 * rnd(seed, id, 3) * x, q.z + Math.sin(a0) * r),
                            0.5 + 1.2 * x, c(0.5f, 0.4f, 0.5f, 0.25 * (1 - x)));
                }
            }
        }
    }

    private static void projectileAdditive(Mesh m, Draw d) {
        ClientCast cast = d.cast();
        double t = d.t();
        double dtp = t - cast.releaseAt;
        if (!flying(d, dtp)) {
            return;
        }
        long seed = cast.seed;
        Vec3 p = pathAt(cast, dtp);
        Vec3 dir = unit(cast.impact.subtract(cast.liveOrigin != null ? cast.liveOrigin : cast.releaseOrigin));
        double rp = PurpleProfile.projectileRadius(dtp) * squeeze(cast, t);
        int lat = bigLat(d.lod()), lon = bigLon(d.lod());
        m.sphere(p.x, p.y, p.z, rp, lat, lon, RedVfxRenderer.elongated(dir, 1.0, STRETCH,
                RedVfxRenderer.plasma(t * 2.2, 2.4, 2.0, t, (int) seed, VIO_DEEP, VIO_HOT, 0.92, 1.5)));
        m.sphere(p.x, p.y, p.z, rp * 1.04, lat, lon, RedVfxRenderer.elongated(dir, 1.0, STRETCH, rim(0.8f, 0.55f, 1f, 0.9, 3.0)));
        m.sphere(p.x, p.y, p.z, rp * 0.32, 10, 16, RedVfxRenderer.flat(1f, 0.96f, 1f, 0.95));
        m.glow(p, rp * 0.75, c(1f, 0.92f, 1f, 0.75));
        m.glow(p, rp * 3.4, c(0.5f, 0.2f, 1f, 0.22));
        m.glow(p, rp * 1.6, c(0.7f, 0.45f, 1f, 0.22));
        if (d.lod() <= 1) {
            crackle(m, p, rp, 0.9, d.lod() == 0 ? 12 : 6, 1.0, t, seed, 9900);
            for (int i = 0; i < (d.lod() == 0 ? 16 : 6); i++) {
                Vec3 sdv = randomDir(seed, 9900 + i);
                double life = (t * 3 + rnd(seed, 9900 + i, 1)) % 1.0;
                Vec3 from = p.add(sdv.scale(rp * 1.05));
                m.ribbon(from, from.add(sdv.scale(rp * (0.3 + 0.8 * life))).subtract(dir.scale(rp * 3 * life)), 0.08, 0.005,
                        c(0.85f, 0.55f, 1f, 0.8 * (1 - life)), c(0.6f, 0.3f, 1f, 0));
            }
        }
    }

    private static void trail(Mesh m, Draw d) {
        ClientCast cast = d.cast();
        double t = d.t();
        double dtp = t - cast.releaseAt, travel = cast.travelSeconds;
        if (dtp < 0.02 || dtp > travel + 1.6 || d.lod() > 2) {
            return;
        }
        long seed = cast.seed;
        double now = Math.min(dtp, travel);
        double rp = PurpleProfile.projectileRadius(now);
        Vec3 head = pathAt(cast, now), tail = pathAt(cast, Math.max(0, Math.min(travel, dtp - 1.1)));
        double length = head.distanceTo(tail);
        if (length > 0.3) {
            Vec3 dir = head.subtract(tail).scale(1 / length);
            double fade = 1 - Curves.smoothstep((dtp - travel) / 1.1);
            m.tube(tail, dir, length, rp * 0.08, rp * 0.75, 1.6, 16, 20, (u, v, out) -> {
                out[0] = 0.5f;
                out[1] = 0.18f;
                out[2] = 1f;
                out[3] = (float) (0.30 * u * fade);
            });
            m.tube(tail, dir, length, rp * 0.03, rp * 0.30, 1.6, 16, 14, (u, v, out) -> {
                out[0] = 0.85f;
                out[1] = 0.65f;
                out[2] = 1f;
                out[3] = (float) (0.35 * u * fade);
            });
            if (d.lod() == 0) {
                for (int k = 0; k < 30; k++) {
                    double u = rnd(seed, 9300 + k, 1);
                    Vec3 sp = lerp(tail, head, u).add(randomDir(seed, 9300 + k).scale(rp * (0.8 + 1.2 * rnd(seed, 9300 + k, 3)) * u));
                    dot(m, sp, 0.06, c(0.95f, 0.85f, 1f, 0.85 * u * fade * (0.5 + 0.5 * Math.sin(t * 6 + k))));
                }
            }
        }
        double gy = d.frame().feet().y;
        for (int k = 0; k < 12; k++) {
            double tk = now - k * 0.09;
            if (tk < 0) {
                break;
            }
            Vec3 q = pathAt(cast, tk);
            double age = dtp - tk, x = age / 1.4;
            if (x > 1 || Math.abs(q.y - gy) > 8) {
                continue;
            }
            m.groundRing(q.x, gy + 0.05, q.z, 1.0 + age * 16, 0.6 + 0.8 * x, c(0.6f, 0.25f, 1f, 0.30 * (1 - x) * (1 - x)));
        }
    }

    private static void scorch(Mesh m, Draw d, boolean light) {
        ClientCast cast = d.cast();
        double dtp = d.t() - cast.releaseAt, di = Math.max(0, cast.sinceImpact(d.t()));
        if (dtp <= 0 || d.lod() > 2) {
            return;
        }
        Vec3 a = pathAt(cast, 0), b = pathAt(cast, Math.min(dtp, cast.travelSeconds));
        double gy = d.frame().feet().y + 0.03;
        Vec3 along = new Vec3(b.x - a.x, 0, b.z - a.z);
        if (along.lengthSqr() < 9 || Math.abs(a.y - gy) > 5 || Math.abs(b.y - gy) > 5) {
            return;
        }
        Vec3 side = new Vec3(-along.z, 0, along.x).normalize();
        a = a.add(along.normalize().scale(2.5));
        double fade = 1 - Curves.smoothstep(di / cast.linger());
        double w = PurpleProfile.projectileRadius(1) * 0.8;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            if (light) {
                float[] hot = c(0.75f, 0.4f, 1f, 0.7 * fade * (1 - Curves.smoothstep(di / 2.5))), none = c(0.75f, 0.4f, 1f, 0);
                strip(m, a, b, side, sgn * w * 0.85, sgn * w * 1.15, gy + 0.01, hot, none);
                strip(m, a, b, side, sgn * w * 0.85, sgn * w * 0.55, gy + 0.01, hot, none);
            } else {
                strip(m, a, b, side, 0, sgn * w, gy, c(0.04f, 0.0f, 0.09f, 0.75 * fade), c(0.04f, 0.0f, 0.09f, 0));
            }
        }
    }

    private static void strip(Mesh m, Vec3 a, Vec3 b, Vec3 side, double o0, double o1, double y, float[] c0, float[] c1) {
        double[][] p = {{a.x + side.x * o0, a.z + side.z * o0}, {b.x + side.x * o0, b.z + side.z * o0},
                {b.x + side.x * o1, b.z + side.z * o1}, {a.x + side.x * o1, a.z + side.z * o1}};
        float[][] col = {c0, c0, c1, c1};
        for (int i : new int[]{0, 1, 2, 0, 2, 3}) {
            m.v(p[i][0], y, p[i][1], col[i][0], col[i][1], col[i][2], col[i][3]);
        }
    }

private static void converge(Mesh m, Draw d, double u) {
        long seed = d.cast().seed;
        Vec3 at = d.cast().impact;
        int n = d.lod() == 0 ? 60 : 24;
        for (int i = 0; i < n; i++) {
            double dist = 10 * Math.pow(1 - u, 2) + 0.5;
            dot(m, at.add(randomDir(seed, 9600 + i).scale(dist)), 0.06, c(0.7f, 0.4f, 1f, 0.7 * u));
        }
        m.glow(at, 1.2 + 2 * u, c(0.9f, 0.7f, 1f, 0.4 * u));
    }

    private static void impactSolid(Mesh m, Draw d, double di) {
        if (d.lod() > 2) {
            return;
        }
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 at = cast.impact;
        double rb = PurpleProfile.blastRadius(di);
        if (rb > 0.05) {
            double opacity = 0.92 * (1 - Curves.smoothstep(Curves.window(di, 0.22, 0.72)));
            m.sphere(at.x, at.y, at.z, rb * 0.98, bigLat(d.lod()), bigLon(d.lod()), body(opacity));
        }
        if (d.lod() <= 1) {
            double x = Curves.clamp01(di / 1.3);
            double front = 40 * (1 - Math.pow(1 - x, 3));
            double life = Math.max(0, 1 - di / 4.5);
            int n = d.lod() == 0 ? 90 : 40;
            for (int i = 0; i < n && life > 0; i++) {
                double a0 = rnd(seed, 6800 + i, 1) * Math.PI * 2, r = front * (0.5 + 0.5 * rnd(seed, 6800 + i, 2));
                double h = 0.3 + (1 + 4 * x) * rnd(seed, 6800 + i, 3);
                dot(m, at.add(Math.cos(a0) * r, h, Math.sin(a0) * r), 1.0 + 3 * x * rnd(seed, 6800 + i, 4), c(0.42f, 0.32f, 0.45f, 0.28 * life));
            }
            int chunks = d.lod() == 0 ? 90 : 36;
            for (int i = 0; i < chunks; i++) {
                double a0 = rnd(seed, 6900 + i, 1) * Math.PI * 2, v = 18 + 22 * rnd(seed, 6900 + i, 2);
                Vec3 p = at.add(Math.cos(a0) * v * di, 0.4 + 2.0 * rnd(seed, 6900 + i, 3) * di - 5.0 * di * di, Math.sin(a0) * v * di);
                double a = Math.max(0, 1 - di / 2.0);
                if (a > 0.01) {
                    chunk(m, p, 0.10 + 0.14 * rnd(seed, 6900 + i, 4), c(0.30f, 0.28f, 0.30f, a), di * 4 + i);
                }
            }
        }
    }

    private static void impactAdditive(Mesh m, Draw d, double di) {
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 at = cast.impact;
        Vec3 shot = shotDir(d, releaseCore(d));
        Vec3[] side = basis(shot);
        double rb = PurpleProfile.blastRadius(di);
        if (rb > 0.05) {
            int lat = bigLat(d.lod()), lon = bigLon(d.lod());
            double skin = 0.9 * (1 - Curves.smoothstep(Curves.window(di, 0.25, 0.8)));
            m.sphere(at.x, at.y, at.z, rb, lat, lon, RedVfxRenderer.plasma(di * 3, 1.6, 2, di, (int) seed, VIO_DEEP, VIO_HOT, skin, 1.3));
            m.sphere(at.x, at.y, at.z, rb * 1.02, lat, lon, rim(0.75f, 0.5f, 1f, 0.7, 4.0));
            m.glow(at, rb, c(1f, 0.94f, 1f, 0.9 * (1 - Curves.smoothstep(di / 0.6))));
            m.glow(at, rb * 2.2, c(0.65f, 0.25f, 1f, 0.3));
            if (d.lod() <= 1) {
                crackle(m, at, rb, 0.5, 14, 1.0, di, seed, 9750);
            }
        }
        if (di < 0.4) {
            m.glow(at, 40, c(1f, 0.95f, 1f, Math.exp(-di * 9)));
        }
        double x = Curves.clamp01(di / 0.6);
        double R = PurpleProfile.BLAST_RADIUS * 1.3 * (1 - Math.pow(1 - x, 3));
        if (d.lod() <= 1 && di < 2.0) {
            int n = d.lod() == 0 ? 12 : 6;
            for (int i = 0; i < n; i++) {
                Vec3[] b = basis(randomDir(seed, 9700 + i));
                double sign = rnd(seed, 9700 + i, 1) < 0.5 ? -1 : 1, phi0 = rnd(seed, 9700 + i, 2) * Math.PI * 2;
                Vec3 prev = null;
                for (int k = 0; k <= 14; k++) {
                    double s = k / 14.0, rr = R * 1.15 * s, ang = phi0 + sign * 1.5 * s;
                    Vec3 p = at.add(b[0].scale(Math.cos(ang) * rr)).add(b[1].scale(Math.sin(ang) * rr));
                    if (prev != null) {
                        double taper = 1 - s, a = 0.75 * (1 - Curves.smoothstep(di / 1.6)) * (0.4 + 0.6 * taper);
                        float[] col = c((float) (0.6 + 0.4 * taper), (float) (0.3 + 0.6 * taper), 1f, a);
                        m.ribbon(prev, p, 0.5 * taper + 0.05, 0.5 * taper + 0.05, col, col);
                    }
                    prev = p;
                }
            }
        }
        for (int k = 0; k < 3; k++) {
            double w0 = (di - 0.05 * k) / 1.5;
            if (w0 <= 0 || w0 >= 1) {
                continue;
            }
            double Rw = 30 * (1 - Math.pow(1 - w0, 3)) * (1 - 0.1 * k);
            m.groundRing(at.x, at.y + 0.06, at.z, Rw * 1.05, 1.5 + 2 * w0, c(0.6f, 0.3f, 1f, 0.5 * (1 - w0)));
        }
        m.groundFan(at.x, at.y + 0.04, at.z, 40, c(0.55f, 0.2f, 1f, 0.5 * Math.exp(-di * 1.2)));
        double linger = cast.linger();
        double life = Math.max(0, 1 - di / linger);
        double fadeIn = Curves.smoothstep((di - 0.5) / 0.6);
        if (d.lod() <= 1 && life > 0) {
            int n = d.lod() == 0 ? 100 : 40;
            for (int i = 0; i < n; i++) {
                Vec3 p = at.add(randomDir(seed, 6950 + i).scale(3 + 12 * rnd(seed, 6950 + i, 1))).add(0, di * 0.3 * rnd(seed, 6950 + i, 2), 0);
                double tw = 0.5 + 0.5 * Math.sin(di * 3 + i * 1.7);
                boolean star = rnd(seed, 6950 + i, 3) < 0.4;
                dot(m, p, star ? 0.08 : 0.12 + 0.2 * rnd(seed, 6950 + i, 4), star ? c(0.95f, 0.9f, 1f, 0.8 * life * tw * fadeIn) : c(0.55f, 0.25f, 0.9f, 0.08 * life * fadeIn));
            }
            double part = Math.max(0, 1 - di / 3.0);
            for (int i = 0; i < 24 && part > 0; i++) {
                Vec3 base = at.add(randomDir(seed, 7000 + i).scale(2 + 4 * rnd(seed, 7000 + i, 1)));
                double drift = di * (0.9 + 0.6 * rnd(seed, 7000 + i, 2));
                dot(m, base.add(side[0].scale(drift)), 0.08, c(0.8f, 0.45f, 1f, 0.65 * part * fadeIn));
                dot(m, base.subtract(side[0].scale(drift)), 0.08, c(0.55f, 0.25f, 1f, 0.5 * part * fadeIn));
            }
        }
    }

private static double strengthOf(Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        double s = PurpleProfile.distortion(t);
        if (cast.released()) {
            double di = cast.sinceImpact(t), dtp = t - cast.releaseAt;
            if (dtp >= 0 && dtp <= cast.travelSeconds) {
                s = Math.max(s, 0.9);
            }
            if (di > -COMPRESS_WINDOW && di < 0) {
                s = Math.max(s, 0.5 + 0.5 * (1 + di / COMPRESS_WINDOW));
            }
            if (di >= 0) {
                s = Math.max(s, Math.max(Math.exp(-di * 1.0), 0.35 * (1 - di / cast.linger())));
            }
        }
        return Math.min(1.0, s);
    }

    private static Vec3 focus(Draw d) {
        ClientCast cast = d.cast();
        double t = d.t();
        if (t < PurpleTimings.POINT) {
            return orbs(d).mid();
        }
        if (cast.released()) {
            double dtp = t - cast.releaseAt, di = cast.sinceImpact(t);
            if (di >= -COMPRESS_WINDOW) {
                return cast.impact;
            }
            if (dtp >= 0.1) {
                return pathAt(cast, dtp);
            }
            return releaseCore(d);
        }
        return coreCenter(d);
    }

    private static double focusRadius(Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        if (t < PurpleTimings.POINT) {
            return 1.4;
        }
        if (t < PurpleTimings.BORN) {
            return 0.3;
        }
        if (t < PurpleTimings.RELEASE) {
            return Math.max(0.6, PurpleProfile.coreRadius(t) * 3.4);
        }
        if (cast.released()) {
            double di = cast.sinceImpact(t);
            if (di >= 0) {
                return 3 + 14 * Curves.smoothstep(di / 0.8) * (1 - Curves.smoothstep(di / 4));
            }
            if (di >= -COMPRESS_WINDOW) {
                return 5.0;
            }
            return 9.0;
        }
        return 3.0;
    }

    private static void requestPostFx(List<Draw> draws, Vec3 cam, Matrix4f view, Matrix4f projection) {
        Draw best = null;
        double bestScore = 0;
        for (Draw d : draws) {
            double proximity = d.cast().local ? 1.0 : Math.max(0, 1 - d.distance() / 60.0) * 0.6;
            double score = strengthOf(d) * proximity;
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        if (best == null) {
            return;
        }
        ClientCast cast = best.cast();
        double t = best.t();
        double proximity = cast.local ? 1.0 : Math.max(0, 1 - best.distance() / 60.0) * 0.6;
        double di = cast.sinceImpact(t);
        double darken = cast.local ? PurpleProfile.darken(t) : 0;
        if (cast.local && di > -COMPRESS_WINDOW && di < 0) {
            darken = Math.max(darken, 0.6 * (1 + di / COMPRESS_WINDOW));
        }
        if (cast.local && di >= 0) {
            darken = Math.max(darken, 0.3 * Math.exp(-di * 0.4) * Curves.smoothstep(di / 0.3));
        }
        double flash = Math.max(0, PurpleProfile.light(t) - 1.0);
        if (di >= 0) {
            flash = Math.max(flash, 1.6 * Math.exp(-di * 7));
        }
        if (bestScore < 0.01 && darken < 0.01 && flash < 0.01) {
            return;
        }
        CastPostFx.request(focus(best), cam, view, projection, (float) (strengthOf(best) * proximity),
                (float) (PurpleProfile.chroma(t) * proximity), cast.local ? (float) PurpleProfile.vignette(t) : 0f, (float) flash,
                (float) focusRadius(best), 0.6f, 0.2f, 1.0f, (float) darken);
    }
}
