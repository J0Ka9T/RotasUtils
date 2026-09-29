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

/**
 * Draws every running Red Reversal in the world, from a handful of GPU batches rather than thousands of
 * particles. The Red itself is layered: a near-black core; dense plasma shells turning in opposite
 * directions on different noise scales, so no repeat is findable; broken, irregular filaments; sparks;
 * a halo and a glare, with white only on the hottest veins. Around the caster a vortex of dust, rock,
 * grass and embers is drawn in; a red light pools on the ground; at release a pressure volume bursts,
 * a barely visible shockwave rolls out, and the red mass flies, wake churning, to where the server said
 * it lands, where it bursts, throws debris and lingers.
 *
 * <p>Every element is a function of the sequence time and a seed, so nothing is stored between frames
 * and every client draws the same thing. Detail thins with distance: the full version to 24 blocks,
 * fewer particles to 48, only the core, projectile and impact to 96, and only the projectile and impact beyond.</p>
 */
@Environment(EnvType.CLIENT)
public final class RedVfxRenderer {
    private RedVfxRenderer() {
    }

    private static final float[] DEEP = {0.45f, 0.01f, 0.03f, 1f};
    private static final float[] HOT = {1.0f, 0.22f, 0.10f, 1f};
    private static final float[] WHITE_HOT = {1.0f, 0.86f, 0.70f, 1f};
    private static final Vec3 LIGHT = new Vec3(-0.4, 0.8, 0.45).normalize();

    private record Draw(ClientCast cast, double t, CameraRig.Frame frame, RedPose.Pose pose, RedPose.Sockets sockets, int lod,
                         double distance) {
        /** Where the core sat at the instant of release, which is where the pressure volume and the mass begin. */
        Vec3 releaseCore() {
            return RedPose.sockets(RedPose.sample(RedTimings.RELEASE), frame.feet(), frame.yawDeg()).core();
        }
    }

    // Entry ------------------------------------------------------------------------------------------

    public static void render(PoseStack poseStack, Camera camera, float partialTick, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientCasts.all().isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        List<Draw> draws = new ArrayList<>();
        for (ClientCast cast : ClientCasts.all()) {
            double t = cast.time(partialTick);
            if (t < 0 || t > cast.finishedAt() || (cast.cancelled && !cast.released())) {
                continue;
            }
            CameraRig.Frame frame = cast.frame(partialTick);
            RedPose.Pose pose = RedPose.sample(t);
            RedPose.Sockets sockets = RedPose.sockets(pose, frame.feet(), frame.yawDeg());
            double distance = cam.distanceTo(sockets.core());
            int lod = distance <= 24 ? 0 : distance <= 48 ? 1 : distance <= 96 ? 2 : 3;
            if (lod == 3 && !cast.released()) {
                continue;
            }
            draws.add(new Draw(cast, t, frame, pose, sockets, lod, distance));
        }
        if (draws.isEmpty()) {
            return;
        }
        Mesh mesh = new Mesh(poseStack.last().pose(), cam, camera.getLeftVector(), camera.getUpVector());
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            // The hand: opaque, depth-written, so its fingers sort among themselves.
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            mesh.begin();
            for (Draw d : draws) {
                if (d.lod() <= 1) {
                    HandRig.draw(mesh, d.t(), d.sockets(), d.pose());
                }
            }
            mesh.draw();
            // Dark and dusty things, blended normally.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            mesh.begin();
            for (Draw d : draws) {
                alphaLayers(mesh, d);
            }
            mesh.draw();
            // Light, added.
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
        requestPostFx(draws, cam, poseStack.last().pose(), projection);
    }

    // Helpers ----------------------------------------------------------------------------------------

    private static double rnd(long seed, int i, int k) {
        long x = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (k * 0xC2B2AE3D27D4EB4FL);
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= x >>> 33;
        return (x >>> 11) * (1.0 / (1L << 53));
    }

    private static float[] c(float r, float g, float b, double a) {
        return new float[]{r, g, b, (float) a};
    }

    private static Vec3 randomDir(long seed, int i) {
        double u = rnd(seed, i, 1) * 2 - 1, a = rnd(seed, i, 2) * Math.PI * 2;
        double r = Math.sqrt(1 - u * u);
        return new Vec3(Math.cos(a) * r, u, Math.sin(a) * r);
    }

    private static double vis(double t) {
        return Curves.smoothstep((t - RedTimings.CORE_FORMS) / 0.4);
    }

    /** A plasma surface: ridged, scrolling noise on a sphere turned by {@code rot}, hot only on its brightest veins. */
    private static Mesh.Surface plasma(double rot, double scale, double scroll, double t, int seed, float[] deep, float[] hot,
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

    private static Mesh.Surface flat(float r, float g, float b, double a) {
        return (nx, ny, nz, fres, out) -> {
            out[0] = r;
            out[1] = g;
            out[2] = b;
            out[3] = (float) a;
        };
    }

    private static int latOf(int lod) {
        return lod == 0 ? 14 : lod == 1 ? 10 : 8;
    }

    private static int lonOf(int lod) {
        return lod == 0 ? 22 : lod == 1 ? 16 : 12;
    }

    // Alpha layers -----------------------------------------------------------------------------------

    private static void alphaLayers(Mesh m, Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        if (t < RedTimings.RELEASE) {
            double radius = RedProfile.coreRadius(t);
            if (radius > 0.004) {
                Vec3 p = d.sockets().core();
                double v = vis(t);
                double density = RedProfile.density(t);
                // A near-black heart with a dark crimson skin, so the bright shells have something to sit on.
                m.sphere(p.x, p.y, p.z, radius * 0.72, latOf(d.lod()), lonOf(d.lod()), flat(0.02f, 0.0f, 0.005f, 0.97 * v));
                m.sphere(p.x, p.y, p.z, radius * 0.92, latOf(d.lod()), lonOf(d.lod()),
                        flat(0.30f, 0.01f, 0.03f, (0.45 + 0.25 * density) * v));
            }
            if (d.lod() <= 1) {
                debrisSolid(m, d);
            }
        } else {
            double dt = t - RedTimings.RELEASE;
            Vec3 o = d.releaseCore();
            if (dt < 0.4) {
                double r = RedProfile.pressureRadius(dt), a = RedProfile.pressureAlpha(dt);
                m.sphere(o.x, o.y, o.z, r * 0.6, 10, 16, flat(0.03f, 0.0f, 0.01f, 0.9 * a));
            }
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

    /** Rocks and grass torn up into the vortex: dull, solid, and dragged in by the same force as the dust. */
    private static void debrisSolid(Mesh m, Draw d) {
        double t = d.t();
        double drive = RedProfile.debris(t);
        if (drive <= 0) {
            return;
        }
        int count = d.lod() == 0 ? 36 : 14;
        double ts = Math.max(0, t - RedTimings.DEBRIS);
        Vec3 feet = d.frame().feet();
        for (int i = 0; i < count; i++) {
            Vec3 p = vortexPoint(d.cast().seed, 500 + i, ts, drive, feet);
            double a = 0.85 * Curves.smoothstep(ts / 0.5) * (1 - Curves.smoothstep((t - RedTimings.RELEASE) / 0.12));
            boolean grass = rnd(d.cast().seed, 500 + i, 9) < 0.35;
            float[] col = grass ? c(0.22f, 0.42f, 0.16f, a) : c(0.33f, 0.31f, 0.30f, a);
            m.billboard(p, 0.03 + 0.05 * rnd(d.cast().seed, 500 + i, 4), col, ts * 3 + i);
        }
    }

    // Additive layers --------------------------------------------------------------------------------

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
                if (di >= 0) {
                    impactAdditive(m, d, di);
                }
            }
        }
    }

    private static void charging(Mesh m, Draw d) {
        double t = d.t();
        long seed = d.cast().seed;
        double charge = RedProfile.charge(t);
        double light = RedProfile.light(t);
        Vec3 feet = d.frame().feet();
        // The red light on the ground round the caster, growing with the light.
        m.groundFan(feet.x, feet.y + 0.03, feet.z, 3.0 + 4.0 * charge, c(1f, 0.06f, 0.04f, Math.min(0.5, 0.30 * light)));
        if (t >= RedTimings.DEBRIS && d.lod() <= 1) {
            debrisGlow(m, d);
        }
        double radius = RedProfile.coreRadius(t) * (1 + 0.03 * Math.sin(t * 14 + (seed & 7)));
        if (radius <= 0.004) {
            return;
        }
        Vec3 p = d.sockets().core();
        double v = vis(t), density = RedProfile.density(t);
        double dense = 0.8 + 0.6 * density;
        int lat = latOf(d.lod()), lon = lonOf(d.lod());
        m.sphere(p.x, p.y, p.z, radius * 1.00, lat, lon, plasma(t * 0.5236, 2.4, 0.6, t, (int) seed, DEEP, HOT, 0.55 * v * dense, 1.6));
        if (d.lod() <= 1) {
            m.sphere(p.x, p.y, p.z, radius * 1.14, lat, lon,
                    plasma(-t * 0.3142, 3.6, -0.4, t, (int) seed + 5, c(0.36f, 0f, 0.05f, 1), c(0.95f, 0.18f, 0.10f, 1), 0.34 * v * dense, 1.9));
        }
        if (d.lod() == 0) {
            m.sphere(p.x, p.y, p.z, radius * 1.30, lat, lon,
                    plasma(t * 0.19, 5.2, 0.3, t, (int) seed + 11, c(0.30f, 0f, 0.04f, 1), c(0.9f, 0.14f, 0.08f, 1), 0.20 * v * dense, 2.2));
        }
        // The halo and a lens-like glare, strongest as the core is squeezed.
        m.glow(p, radius * 4.6, c(0.9f, 0.06f, 0.04f, (0.10 + 0.14 * charge) * v));
        m.glow(p, radius * 2.3, c(1f, 0.16f, 0.08f, (0.16 + 0.20 * charge) * v));
        if (t > RedTimings.CLOSE_UP && d.lod() == 0) {
            double glare = 0.18 * density * Curves.window(t, RedTimings.CLOSE_UP, RedTimings.RELEASE);
            double len = radius * 9;
            Vec3 l = new Vec3(m.left.x(), m.left.y(), m.left.z()).scale(len);
            Vec3 u = new Vec3(m.up.x(), m.up.y(), m.up.z()).scale(len * 0.5);
            m.ribbon(p.subtract(l), p.add(l), radius * 0.05, radius * 0.05, c(1f, 0.2f, 0.12f, 0), c(1f, 0.2f, 0.12f, 0));
            m.ribbon(p.subtract(l), p, radius * 0.06, radius * 0.01, c(1f, 0.2f, 0.12f, 0), c(1f, 0.25f, 0.15f, glare));
            m.ribbon(p, p.add(l), radius * 0.01, radius * 0.06, c(1f, 0.25f, 0.15f, glare), c(1f, 0.2f, 0.12f, 0));
            m.ribbon(p.subtract(u), p, radius * 0.04, radius * 0.008, c(1f, 0.2f, 0.12f, 0), c(1f, 0.25f, 0.15f, glare * 0.6));
            m.ribbon(p, p.add(u), radius * 0.008, radius * 0.04, c(1f, 0.25f, 0.15f, glare * 0.6), c(1f, 0.2f, 0.12f, 0));
        }
        filaments(m, d, p, radius, charge);
        sparks(m, d, p, radius, charge);
        // The core lights the casting hand and the face.
        if (t > 1.6 && d.lod() <= 1) {
            double faceLight = Math.min(0.32, 0.30 * light) * Curves.smoothstep((t - 1.6) / 1.0);
            m.glow(d.sockets().hand(), 0.55, c(1f, 0.08f, 0.05f, faceLight));
            m.glow(d.sockets().eye(), 0.75, c(1f, 0.08f, 0.05f, faceLight * 0.7));
        }
    }

    /** 6 to 14 curved, broken arcs orbiting and wrapping the core: uneven in length, width, brightness and life. */
    private static void filaments(Mesh m, Draw d, Vec3 center, double radius, double charge) {
        if (d.lod() > 1) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        int count = (int) Math.round(Curves.lerp(6, 14, charge)) / (d.lod() == 0 ? 1 : 2);
        for (int i = 0; i < count; i++) {
            Vec3 axis = randomDir(seed, 100 + i);
            Vec3 ref = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 u = axis.cross(ref).normalize(), w = axis.cross(u).normalize();
            double orbit = radius * (1.25 + 1.6 * rnd(seed, 100 + i, 3));
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
                double wob = 1 + 0.14 * (Curves.noise(k * 0.7 + t * 1.3 + i * 5.1, (int) seed) );
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

    /** Small sparks thrown off the core, streaking outward and dying. */
    private static void sparks(Mesh m, Draw d, Vec3 center, double radius, double charge) {
        if (d.lod() > 1) {
            return;
        }
        long seed = d.cast().seed;
        double t = d.t();
        int count = d.lod() == 0 ? 40 : 16;
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomDir(seed, 300 + i);
            double rate = 0.6 + 0.9 * rnd(seed, 300 + i, 3);
            double life = (t * rate + rnd(seed, 300 + i, 4)) % 1.0;
            double dist = radius * (1.1 + life * (2.5 + 2 * charge));
            Vec3 p = center.add(dir.scale(dist));
            Vec3 tail = p.subtract(dir.scale(radius * (0.4 + 0.8 * rnd(seed, 300 + i, 5))));
            double a = (1 - life) * 0.9 * charge * vis(t);
            m.ribbon(tail, p, 0.004, 0.006, c(1f, 0.3f, 0.12f, 0), c(1f, 0.55f, 0.32f, a));
        }
    }

    /** A vortex of dust, rock and ember drawn inward: each mote's radius shrinks, its spin quickens and it rises. */
    private static Vec3 vortexPoint(long seed, int i, double ts, double drive, Vec3 feet) {
        double r0 = 0.8 + 5.2 * Math.pow(rnd(seed, i, 1), 0.7);
        double angle = rnd(seed, i, 2) * Math.PI * 2;
        double omega = 0.8 + 1.4 * rnd(seed, i, 3);
        double r = r0 * (1 - 0.6 * drive);
        double a = angle + omega * (ts * 0.9 + 1.4 * ts * drive);
        double h = -0.05 + 0.9 * rnd(seed, i, 5) + ts * (0.25 + 0.5 * rnd(seed, i, 6)) * (0.4 + drive);
        h = Math.min(h, 3.6);
        return new Vec3(feet.x + Math.cos(a) * r, feet.y + h, feet.z + Math.sin(a) * r);
    }

    private static void debrisGlow(Mesh m, Draw d) {
        double t = d.t();
        double drive = RedProfile.debris(t);
        if (drive <= 0) {
            return;
        }
        long seed = d.cast().seed;
        int count = d.lod() == 0 ? 90 : 36;
        double ts = Math.max(0, t - RedTimings.DEBRIS);
        Vec3 feet = d.frame().feet();
        double fade = Curves.smoothstep(ts / 0.6) * (0.5 + 0.5 * drive);
        for (int i = 0; i < count; i++) {
            Vec3 p = vortexPoint(seed, i, ts, drive, feet);
            double k = rnd(seed, i, 8);
            double size = 0.03 + 0.07 * rnd(seed, i, 4);
            float[] col = k < 0.45 ? c(0.62f, 0.44f, 0.34f, 0.30 * fade)
                    : k < 0.8 ? c(1f, 0.30f, 0.10f, 0.85 * fade) : c(1f, 0.12f, 0.08f, 0.55 * fade);
            m.billboard(p, size * (k < 0.45 ? 1.6 : 1.0), col, ts * 2 + i);
        }
    }

    // Release ----------------------------------------------------------------------------------------

    private static void releasing(Mesh m, Draw d, double dt) {
        Vec3 o = d.releaseCore();
        long seed = d.cast().seed;
        // The pressure volume: a violent shove, 0.16 -> 0.3 -> 0.8 -> beyond two blocks, thinning as it goes.
        if (dt < 0.4) {
            double r = RedProfile.pressureRadius(dt), a = RedProfile.pressureAlpha(dt);
            m.sphere(o.x, o.y, o.z, r, latOf(d.lod()), lonOf(d.lod()), plasma(dt * 6, 2.0, 2.0, dt, (int) seed, DEEP, HOT, 0.9 * a, 1.4));
            m.glow(o, r * 3.2, c(1f, 0.30f, 0.14f, 0.7 * Math.exp(-dt * 14)));
            m.glow(o, r * 1.6, c(1f, 0.85f, 0.65f, 0.9 * Math.exp(-dt * 30)));
        }
        Vec3 feet = d.frame().feet();
        // The shockwave: a transparent dome that is hard to see directly; what shows is the ground ring and the dust.
        double sw = dt / 0.5;
        if (sw < 1.2) {
            double x = Math.min(1, sw);
            double radius = RedTimings.SHOCKWAVE_RADIUS * (1 - Math.pow(1 - x, 3));
            double fade = Math.pow(Math.max(0, 1 - sw / 1.2), 1.5);
            if (d.lod() <= 1) {
                m.sphere(feet.x, feet.y + 0.1, feet.z, radius, 12, 24, (nx, ny, nz, fres, out) -> {
                    out[0] = 0.9f;
                    out[1] = 0.12f;
                    out[2] = 0.08f;
                    out[3] = ny < -0.02 ? 0f : (float) (0.05 * fade * (0.4 + 0.6 * fres));
                });
            }
            m.groundRing(feet.x, feet.y + 0.05, feet.z, radius, 0.5 + 0.7 * x, c(0.9f, 0.14f, 0.09f, 0.20 * fade));
        }
        m.groundFan(feet.x, feet.y + 0.03, feet.z, 8, c(1f, 0.06f, 0.04f, Math.min(0.6, 0.30 * RedProfile.light(RedTimings.RELEASE + dt))));
    }

    private static void shockwaveDust(Mesh m, Draw d, double dt) {
        long seed = d.cast().seed;
        Vec3 feet = d.frame().feet();
        double x = Math.min(1, dt / 0.5);
        double front = RedTimings.SHOCKWAVE_RADIUS * (1 - Math.pow(1 - x, 3));
        int count = d.lod() == 0 ? 70 : 30;
        for (int i = 0; i < count; i++) {
            double a = rnd(seed, 700 + i, 1) * Math.PI * 2;
            double r = front * (0.75 + 0.25 * rnd(seed, 700 + i, 2));
            double h = 0.1 + rnd(seed, 700 + i, 3) * (0.3 + 1.6 * dt);
            Vec3 p = new Vec3(feet.x + Math.cos(a) * r, feet.y + h, feet.z + Math.sin(a) * r);
            double fade = Math.max(0, 1 - dt / 1.1);
            m.billboard(p, 0.25 + 0.7 * dt + 0.3 * rnd(seed, 700 + i, 4), c(0.55f, 0.45f, 0.38f, 0.32 * fade), i);
        }
    }

    // Projectile -------------------------------------------------------------------------------------

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
        double rp = RedProfile.projectileRadius(dtp);
        m.sphere(p.x, p.y, p.z, rp * 0.72, 10, 16, flat(0.03f, 0.0f, 0.01f, 0.95));
        // A wake of dark, churning smoke behind it.
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
        Vec3 dir = cast.impact.subtract(origin).normalize();
        double rp = RedProfile.projectileRadius(dtp);
        double t = d.t();
        int seed = (int) cast.seed;
        // Front compressed, tail stretched: the shape of something moving faster than the air can get out of its way.
        Mesh.Surface shape = new Mesh.Surface() {
            @Override
            public double radius(double nx, double ny, double nz) {
                double dp = nx * dir.x + ny * dir.y + nz * dir.z;
                return dp > 0 ? 1 - 0.28 * dp : 1 + 0.9 * -dp;
            }

            @Override
            public void color(double nx, double ny, double nz, double fres, float[] out) {
                plasma(t * 2.6, 2.6, 2.4, t, seed, DEEP, HOT, 0.8, 1.5).color(nx, ny, nz, fres, out);
            }
        };
        m.sphere(p.x, p.y, p.z, rp, latOf(d.lod()), lonOf(d.lod()), shape);
        if (d.lod() <= 1) {
            m.sphere(p.x, p.y, p.z, rp * 1.25, 10, 16, plasma(-t * 3.1, 3.8, -2.0, t, seed + 3, c(0.4f, 0, 0.05f, 1), c(1f, 0.2f, 0.1f, 1), 0.35, 1.9));
        }
        m.glow(p, rp * 4.5, c(1f, 0.14f, 0.07f, 0.35));
        m.glow(p, rp * 2, c(1f, 0.5f, 0.32f, 0.5));
        // The wake: hot red ribbons whose width and turbulence grow with age.
        int samples = d.lod() == 0 ? 26 : 10;
        Vec3 prev = p;
        for (int k = 1; k <= samples; k++) {
            double tk = dtp - k * 0.012;
            Vec3 q = pathAt(cast, origin, Math.max(0, tk));
            double width = rp * (0.45 + 1.5 * k / samples);
            Vec3 off = randomDir(cast.seed, 900 + k).scale(width * 0.9 * Curves.noise(k * 0.6 + t * 8, seed));
            q = q.add(off);
            double a = 0.75 * Math.pow(1 - (double) k / samples, 1.3);
            m.ribbon(prev, q, width, width * 1.15, c(1f, 0.16f, 0.08f, a), c(1f, 0.16f, 0.08f, a * 0.85));
            prev = q;
            // Glowing fragments shed along it.
            if (d.lod() == 0 && k % 2 == 0) {
                Vec3 frag = q.add(randomDir(cast.seed, 950 + k).scale(width * 1.4));
                m.billboard(frag, 0.03 + 0.06 * rnd(cast.seed, 950 + k, 3), c(1f, 0.4f, 0.2f, a), k);
            }
        }
    }

    // Impact -----------------------------------------------------------------------------------------

    private static void impactSolid(Mesh m, Draw d, double di) {
        if (d.lod() > 2) {
            return;
        }
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 at = cast.impact;
        int chunks = d.lod() == 0 ? 60 : 24;
        for (int i = 0; i < chunks; i++) {
            Vec3 dir = randomDir(seed, 1100 + i);
            dir = new Vec3(dir.x, Math.abs(dir.y) * 0.9 + 0.25, dir.z).normalize();
            double v = 6 + 10 * rnd(seed, 1100 + i, 3);
            Vec3 p = at.add(dir.scale(v * di)).add(0, -6.0 * di * di, 0);
            double a = Math.max(0, 1 - di / 1.7) * 0.95;
            if (a > 0.01) {
                m.billboard(p, 0.06 + 0.12 * rnd(seed, 1100 + i, 4), c(0.30f, 0.28f, 0.27f, a), di * 4 + i);
            }
        }
        if (d.lod() <= 1) {
            for (int i = 0; i < 20; i++) {
                double grow = 1.2 + 2.6 * Curves.smoothstep(di / 1.4);
                Vec3 p = at.add(randomDir(seed, 1300 + i).scale(grow * 0.9 * rnd(seed, 1300 + i, 3))).add(0, 0.4 + di * 0.7, 0);
                m.billboard(p, grow * (0.4 + 0.5 * rnd(seed, 1300 + i, 4)), c(0.42f, 0.36f, 0.32f, 0.22 * Math.max(0, 1 - di / 2.4)), i);
            }
        }
    }

    private static void impactAdditive(Mesh m, Draw d, double di) {
        ClientCast cast = d.cast();
        long seed = cast.seed;
        Vec3 at = cast.impact;
        // Frame zero: a single, extremely bright point.
        if (di < 0.09) {
            double k = 1 - di / 0.09;
            m.glow(at, 0.6 + 1.6 * (1 - k), c(1f, 0.92f, 0.78f, k));
            m.glow(at, 3.5, c(1f, 0.25f, 0.12f, 0.6 * k));
        }
        // The mass deforms and swells, its surface torn by noise.
        if (di < 0.7) {
            double r = Curves.Track.of(0, 0.2, 0.05, 0.5, 0.15, 2.4, 0.4, 3.0, 0.7, 3.4).at(di);
            double a = 0.95 * (1 - Curves.smoothstep(di / 0.7));
            final double dd = di;
            Mesh.Surface torn = new Mesh.Surface() {
                @Override
                public double radius(double nx, double ny, double nz) {
                    return 1 + 0.4 * (Noise3.fbm(nx * 2.2 + dd * 6, ny * 2.2, nz * 2.2) - 0.5) * 2;
                }

                @Override
                public void color(double nx, double ny, double nz, double fres, float[] out) {
                    plasma(dd * 5, 2.4, 2.0, dd, (int) seed, DEEP, HOT, a, 1.4).color(nx, ny, nz, fres, out);
                }
            };
            m.sphere(at.x, at.y, at.z, r, latOf(d.lod()), lonOf(d.lod()), torn);
        }
        // The repulsion wave, 50-150 ms in, then rolling out.
        double wave = (di - 0.05) / 0.45;
        if (wave > 0 && wave < 1.3) {
            double x = Math.min(1, wave);
            double radius = RedTimings.BLAST_RADIUS * (1 - Math.pow(1 - x, 3));
            double fade = Math.pow(Math.max(0, 1 - wave / 1.3), 1.6);
            if (d.lod() <= 1) {
                m.sphere(at.x, at.y, at.z, radius, 12, 24, (nx, ny, nz, fres, out) -> {
                    out[0] = 1f;
                    out[1] = 0.16f;
                    out[2] = 0.09f;
                    out[3] = (float) (0.08 * fade * (0.3 + 0.7 * fres));
                });
            }
            m.groundRing(at.x, at.y + 0.05, at.z, radius, 0.6 + 0.6 * x, c(1f, 0.16f, 0.09f, 0.28 * fade));
        }
        m.groundFan(at.x, at.y + 0.04, at.z, 8, c(1f, 0.08f, 0.05f, 0.5 * Math.exp(-di * 2)));
        // Hot chunks and red particles that linger for seconds.
        if (d.lod() <= 1) {
            for (int i = 0; i < 24; i++) {
                Vec3 dir = randomDir(seed, 1100 + i);
                dir = new Vec3(dir.x, Math.abs(dir.y) * 0.9 + 0.25, dir.z).normalize();
                double v = 6 + 10 * rnd(seed, 1100 + i, 3);
                Vec3 p = at.add(dir.scale(v * di)).add(0, -6.0 * di * di, 0);
                double a = Math.max(0, 1 - di / 1.4);
                if (a > 0.01) {
                    m.billboard(p, 0.05 + 0.08 * rnd(seed, 1100 + i, 4), c(1f, 0.35f, 0.15f, a), i);
                }
            }
            int motes = d.lod() == 0 ? 44 : 18;
            for (int i = 0; i < motes; i++) {
                double life = Math.max(0, 1 - di / RedTimings.LINGER);
                if (life <= 0) {
                    break;
                }
                Vec3 off = randomDir(seed, 1500 + i).scale(1.0 + 3.0 * rnd(seed, 1500 + i, 3) * Math.pow(Math.min(1, di * 2), 0.4));
                double sway = di * (0.3 + 0.5 * rnd(seed, 1500 + i, 4));
                Vec3 p = at.add(off.x, Math.abs(off.y) * 0.6 + sway, off.z);
                double flick = 0.6 + 0.4 * Math.sin(di * 7 + i);
                m.billboard(p, 0.04 + 0.05 * rnd(seed, 1500 + i, 5), c(1f, 0.16f, 0.08f, 0.7 * Math.pow(life, 1.5) * flick), i);
            }
        }
    }

    // Post effect ------------------------------------------------------------------------------------

    /** Asks the screen-space pass to bend the view round whichever core is strongest: the local caster's first. */
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
        float chroma = (float) (RedProfile.chroma(t) * proximity);
        float vignette = best.cast().local ? (float) RedProfile.vignette(t) : 0f;
        float flash = (float) Math.max(0, RedProfile.light(t) - 1.0);
        CastPostFx.request(where, cam, view, projection, (float) (strengthOf(best) * proximity), chroma, vignette, flash, (float) radius);
    }

    private static double strengthOf(Draw d) {
        double t = d.t();
        ClientCast cast = d.cast();
        double s = RedProfile.distortion(t);
        if (cast.released()) {
            double di = cast.sinceImpact(t);
            double dtp = t - cast.releaseAt;
            if (dtp >= 0 && dtp <= cast.travelSeconds) {
                s = Math.max(s, 0.5);
            }
            if (di >= 0) {
                s = Math.max(s, 0.7 * Math.exp(-di * 3.0));
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
        if (t < RedTimings.RELEASE) {
            return RedProfile.coreRadius(t);
        }
        return Math.max(0.4, RedProfile.pressureRadius(t - RedTimings.RELEASE) * 0.5);
    }
}
