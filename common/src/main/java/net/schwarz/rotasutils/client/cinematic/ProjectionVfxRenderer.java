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
import net.schwarz.rotasutils.ability.ProjectionStage;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static net.schwarz.rotasutils.ability.ProjectionTimings.*;

@Environment(EnvType.CLIENT)
public final class ProjectionVfxRenderer {
    private ProjectionVfxRenderer() {
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int LAYERS = 5;

    private record Draw(ClientCast cast, Projection.Scene scene, double t) {
        ProjectionStage stage() {
            return scene.stage;
        }

        long seed() {
            return cast.seed;
        }
    }

    private static float[] c(double r, double g, double b, double a) {
        return new float[]{(float) r, (float) g, (float) b, (float) Math.max(0, Math.min(1, a))};
    }

    private static float[] glass(double a) {
        return c(0.84, 0.91, 1.0, a);
    }

    private static float[] dust(double a) {
        return c(0.62, 0.60, 0.58, a);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static double rnd(long seed, int i, int k) {
        return RedVfxRenderer.rnd(seed, i, k);
    }

    private static Vec3[] square(Vec3 axis) {
        Vec3 u = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : axis.cross(UP).normalize();
        return new Vec3[]{u, axis.cross(u).normalize()};
    }

private static void v(Mesh m, Vec3 p, float[] col) {
        m.v(p.x, p.y, p.z, col[0], col[1], col[2], col[3]);
    }

    private static void tri(Mesh m, Vec3 a, Vec3 b, Vec3 c, float[] col) {
        if (col[3] <= 0.004f) {
            return;
        }
        v(m, a, col);
        v(m, b, col);
        v(m, c, col);
    }

    private static void quad(Mesh m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col) {
        tri(m, a, b, c, col);
        tri(m, a, c, d, col);
    }

    private static void rect(Mesh m, Vec3 centre, Vec3 u, Vec3 v, float[] col) {
        quad(m, centre.subtract(u).subtract(v), centre.add(u).subtract(v), centre.add(u).add(v), centre.subtract(u).add(v), col);
    }

    private static void border(Mesh m, Vec3 centre, Vec3 u, Vec3 v, double thick, float[] col) {
        Vec3 tu = u.normalize().scale(thick), tv = v.normalize().scale(thick);
        rect(m, centre.add(v).subtract(tv.scale(0.5)), u, tv.scale(0.5), col);
        rect(m, centre.subtract(v).add(tv.scale(0.5)), u, tv.scale(0.5), col);
        rect(m, centre.add(u).subtract(tu.scale(0.5)), tu.scale(0.5), v.subtract(tv), col);
        rect(m, centre.subtract(u).add(tu.scale(0.5)), tu.scale(0.5), v.subtract(tv), col);
    }

    private static void line(Mesh m, Vec3 a, Vec3 b, double w0, double w1, float[] col) {
        Vec3 side = b.subtract(a).cross(a.add(b).scale(0.5).subtract(m.camera));
        if (side.lengthSqr() < 1.0e-12) {
            return;
        }
        side = side.normalize();
        quad(m, a.subtract(side.scale(w0)), a.add(side.scale(w0)), b.add(side.scale(w1)), b.subtract(side.scale(w1)), col);
    }

    private static void circle(Mesh m, Vec3 centre, Vec3 u, Vec3 w, double radius, double width, float[] col) {
        if (col[3] <= 0.004f) {
            return;
        }
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            line(m, centre.add(u.scale(Math.cos(a0) * radius)).add(w.scale(Math.sin(a0) * radius)),
                    centre.add(u.scale(Math.cos(a1) * radius)).add(w.scale(Math.sin(a1) * radius)), width, width, col);
        }
    }

    private static Vec3 left(Mesh m) {
        return new Vec3(m.left.x(), m.left.y(), m.left.z());
    }

    private static Vec3 upOf(Mesh m) {
        return new Vec3(m.up.x(), m.up.y(), m.up.z());
    }

    private static void dot(Mesh m, Vec3 p, double size, float[] col) {
        m.glow(p, Math.min(size, 0.03 * p.distanceTo(m.camera) + 0.01), col);
    }

    private static void figure(Mesh m, RedPose.Pose pose, Vec3 feet, double yawDeg, Vec3 shift, float[] col) {
        for (ProjectionPose.Stroke s : ProjectionPose.strokes(pose, feet, yawDeg)) {
            line(m, s.from().add(shift), s.to().add(shift), s.halfWidth(), s.halfWidth(), col);
        }
    }

    private static void image(Mesh m, RedPose.Pose pose, Vec3 feet, double yawDeg, double alpha, boolean edges) {
        if (alpha < 0.01) {
            return;
        }
        Vec3 l = left(m), u = upOf(m), centre = feet.add(0, 0.95, 0);
        if (edges) {
            border(m, centre, l.scale(0.66), u.scale(1.15), 0.010, glass(0.32 * alpha));
            return;
        }
        rect(m, centre, l.scale(0.66), u.scale(1.15), c(0.70, 0.80, 0.95, 0.07 * alpha));
        figure(m, pose, feet, yawDeg, l.scale(0.03), c(1.0, 0.35, 0.35, 0.16 * alpha));
        figure(m, pose, feet, yawDeg, l.scale(-0.03), c(0.30, 0.90, 1.0, 0.16 * alpha));
        figure(m, pose, feet, yawDeg, Vec3.ZERO, c(0.52, 0.64, 0.86, 0.50 * alpha));
    }

public static void render(PoseStack poseStack, Camera camera, float partialTick, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientCasts.all().isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        List<Draw> draws = new ArrayList<>();
        for (ClientCast cast : ClientCasts.all()) {
            if (!cast.projection() || cast.cancelled) {
                continue;
            }
            Projection.Scene scene = Projection.scene(cast);
            double t = cast.time(partialTick);
            if (scene == null || t < 0 || t > cast.finishedAt() || cam.distanceTo(scene.stage.target) > 140) {
                continue;
            }
            draws.add(new Draw(cast, scene, t));
        }
        if (draws.isEmpty()) {
            return;
        }
        Matrix4f view = poseStack.last().pose();
        Mesh mesh = new Mesh(view, cam, camera.getLeftVector(), camera.getUpVector());
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            for (int pass = 0; pass < 2; pass++) {
                boolean edges = pass == 1;
                if (edges) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                } else {
                    RenderSystem.defaultBlendFunc();
                }
                RenderSystem.depthMask(false);
                mesh.begin();
                for (Draw d : draws) {
                    flickers(mesh, d, edges);
                    path(mesh, d, edges);
                    ghosts(mesh, d, edges);
                    firstDash(mesh, d, edges);
                    passes(mesh, d, edges);
                    laps(mesh, d, edges);
                    for (int i = 0; i < 3; i++) {
                        cell(mesh, d, i, edges);
                        cracks(mesh, d, i, edges);
                        shards(mesh, d, i, edges);
                    }
                    for (ProjectionStage.Hit hit : d.stage().hits()) {
                        blow(mesh, d, hit, edges);
                    }
                    ground(mesh, d, edges);
                }
                mesh.draw();
            }
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        for (Draw d : draws) {
            if (d.cast().local) {
                postFx(d, cam, view, projection);
            }
        }
    }

private static void flicker(Mesh m, Draw d, Vec3 at, double age, int id, int count, boolean edges) {
        if (age < 0 || age > 0.30) {
            return;
        }
        Vec3 l = left(m), u = upOf(m);
        double a = Math.pow(1 - age / 0.30, 2);
        for (int i = 0; i < count; i++) {
            Vec3 centre = at.add(0, 0.5 + 1.2 * rnd(d.seed(), id + i, 1), 0).add(l.scale((rnd(d.seed(), id + i, 2) - 0.5) * 1.3));
            Vec3 hu = l.scale(0.22 + 0.3 * rnd(d.seed(), id + i, 3)), hv = u.scale(0.3 + 0.45 * rnd(d.seed(), id + i, 4));
            if (edges) {
                border(m, centre, hu, hv, 0.010, glass(0.5 * a));
            } else {
                rect(m, centre, hu, hv, glass(0.06 * a));
            }
        }
    }

    private static void flickers(Mesh m, Draw d, boolean edges) {
        ProjectionStage s = d.stage();
        double t = d.t();
        flicker(m, d, s.start, t - CLICK_1, 100, 3, edges);
        flicker(m, d, s.start, t - CLICK_2, 110, 4, edges);
        for (int i = 1; i <= 3; i++) {
            flicker(m, d, s.far2.add(s.away.scale(0.5 * i)), t - (STANCE + 0.3 * i), 120 + 10 * i, 2 + i, edges);
        }
        double age = t - REVEAL_2;
        if (age >= 0 && age < 0.5) {
            double a = 1 - Curves.smoothstep(age / 0.5), snap = 1 + 0.3 * (1 - Curves.smoothstep(age / 0.08));
            Vec3 centre = s.strikeFrom.add(0, 0.95, 0), u = s.across.scale(0.75 * snap), v = UP.scale(1.2 * snap);
            if (edges) {
                border(m, centre, u, v, 0.02, glass(0.8 * a));
            } else {
                rect(m, centre, u, v, glass(0.10 * a));
            }
        }
    }

    private static void path(Mesh m, Draw d, boolean edges) {
        double t = d.t();
        ProjectionStage s = d.stage();
        if (t >= CALC && t < VANISH) {
            for (int i = 0; i < ProjectionStage.FRAMES; i++) {
                double age = t - ProjectionStage.cellTime(i);
                if (age < 0) {
                    break;
                }
                image(m, ProjectionPose.of(s.cellPose(i)), s.cell(i), s.cellYaw(i), 0.55 + 0.9 * Math.exp(-age / 0.07), edges);
            }
        }
        if (t >= RING && t < BLACK) {
            double fall = Curves.window(t, COLLAPSE, BLACK);
            for (int i = 0; i < ProjectionStage.FRAMES; i++) {
                double age = t - ProjectionStage.ringTime(i);
                if (age < 0) {
                    break;
                }
                Vec3 at = lerp(s.ring(i), s.centre, fall * fall);
                image(m, ProjectionPose.of(s.ringPose(i)), at, s.ringYaw(i), 0.55 + 0.9 * Math.exp(-age / 0.07) + 1.2 * fall, edges);
            }
        }
    }

    private static void ghosts(Mesh m, Draw d, boolean edges) {
        double t = d.t();
        if (t < DASH || t > WALK) {
            return;
        }
        ProjectionStage s = d.stage();
        Vec3 now = s.attackerAt(t);
        boolean here = s.visible(t);
        double[] lags = {0.06, 0.12, 0.18};
        for (int i = 0; i < lags.length; i++) {
            double then = frame(t - lags[i]);
            if (then < DASH || !s.visible(then)) {
                continue;
            }
            Vec3 was = s.attackerAt(then);
            if (!here || was.distanceTo(now) > 0.6) {
                image(m, ProjectionPose.sample(then, s), was, s.yawAt(then), 0.9 - 0.28 * i, edges);
            }
        }
    }

private static void dustLine(Mesh m, Draw d, Vec3 from, Vec3 to, double t0, double t1, int id, double amount, boolean edges) {
        double t = d.t();
        if (t < t0 || t > t1 + 0.7) {
            return;
        }
        double front = Curves.window(t, t0, t1);
        Vec3 along = to.subtract(from);
        if (along.lengthSqr() < 1.0e-6) {
            return;
        }
        Vec3 dir = along.normalize(), side = new Vec3(-dir.z, 0, dir.x);
        if (edges) {
            if (front < 1) {
                Vec3 a = lerp(from, to, Math.max(0, front - 0.3)).add(0, 0.06, 0), b = lerp(from, to, front).add(0, 0.06, 0);
                line(m, a, b, 0.0, 0.02 * amount, glass(0.7));
                line(m, a.add(0, 0.9, 0), b.add(0, 0.9, 0), 0.0, 0.012 * amount, glass(0.5));
                line(m, a.add(0, 1.5, 0), b.add(0, 1.5, 0), 0.0, 0.008 * amount, glass(0.35));
            }
            return;
        }
        int n = 28;
        for (int k = 0; k < n; k++) {
            double at = k / (n - 1.0);
            double age = t - (t0 + at * (t1 - t0));
            if (age < 0 || age > 0.7) {
                continue;
            }
            Vec3 p = lerp(from, to, at).add(side.scale((rnd(d.seed(), id + k, 1) - 0.5) * (0.4 + 1.6 * age) * amount))
                    .subtract(dir.scale(0.8 * age)).add(0, 0.1 + 0.9 * age * rnd(d.seed(), id + k, 2), 0);
            dot(m, p, (0.3 + 0.7 * age) * amount, dust(0.28 * (1 - age / 0.7)));
        }
    }

    private static void firstDash(Mesh m, Draw d, boolean edges) {
        ProjectionStage s = d.stage();
        for (int j = 1; j <= 4; j++) {
            dustLine(m, d, s.dashPoint(j - 1), s.dashPoint(j), ProjectionStage.dashStepTime(j) - 0.02, ProjectionStage.dashStepTime(j), 300 + j * 30, 0.6, edges);
        }
        dustLine(m, d, s.far, s.punch, GONE, REVEAL, 400, 1.0, edges);
        dustLine(m, d, s.far2, s.touch2, CHARGE, TOUCH_2, 440, 1.8, edges);
    }

    private static void passes(Mesh m, Draw d, boolean edges) {
        ProjectionStage s = d.stage();
        Vec3 at = s.far2;
        for (int i = 0; i < PASSES.length; i++) {
            Vec3 lane = s.across.scale((i % 2 == 0 ? 1 : -1) * (s.width / 2 + 1.4));
            Vec3 to = (i % 2 == 0 ? s.opposite : s.far2).add(lane);
            dustLine(m, d, at, to, PASSES[i], PASSES[i] + PASS_TIME, 700 + i * 30, 0.7 + 0.45 * i, edges);
            double age = d.t() - (PASSES[i] + PASS_TIME * 0.5);
            if (edges && age >= 0 && age < 0.6) {
                double out = 1 - Math.pow(1 - age / 0.6, 3);
                m.groundRing(s.centre.x + lane.x, s.centre.y + 0.05, s.centre.z + lane.z, 0.6 + (1.5 + 1.3 * i) * out, 0.15 + 0.1 * i,
                        glass((0.12 + 0.08 * i) * (1 - age / 0.6)));
            }
            at = to;
        }
    }

    private static void laps(Mesh m, Draw d, boolean edges) {
        double t = d.t();
        if (t < LAPS || t > LAPS_END + 0.5) {
            return;
        }
        ProjectionStage s = d.stage();
        double grow = Curves.window(t, LAPS, LAPS_END), fade = 1 - Curves.window(t, LAPS_END, LAPS_END + 0.5);
        double angle = ProjectionStage.lapAngle(Math.min(t, LAPS_END));
        double r = s.lapRadius;
        if (edges) {
            double tail = Math.min(Math.PI * 2, angle - ProjectionStage.lapAngle(Math.max(LAPS, Math.min(t, LAPS_END) - (0.12 + 0.22 * grow))));
            int n = 36;
            double[] heights = {0.35, 0.95, 1.5};
            for (double height : heights) {
                for (int k = 0; k < n; k++) {
                    double a0 = angle - tail * k / n, a1 = angle - tail * (k + 1) / n;
                    double alpha = (0.55 - 0.15 * height / 1.5) * (1 - (double) k / n) * fade;
                    line(m, s.centre.add(ringAt(s, a0).scale(r)).add(0, height, 0), s.centre.add(ringAt(s, a1).scale(r)).add(0, height, 0),
                            0.02 + 0.03 * grow, 0.02 + 0.03 * grow, glass(alpha));
                }
            }
            for (int i = 0; i < 26; i++) {
                double a0 = rnd(d.seed(), 800 + i, 1) * Math.PI * 2 + angle * 0.15;
                double rr = r * (0.55 + 0.9 * rnd(d.seed(), 800 + i, 2));
                Vec3 p = s.centre.add(ringAt(s, a0).scale(rr)).add(0, 0.15 + 1.8 * rnd(d.seed(), 800 + i, 3) * grow, 0);
                Vec3 wind = ringAt(s, a0 + Math.PI / 2);
                line(m, p, p.add(wind.scale(0.15 + 0.9 * grow)), 0.006, 0.0, glass(0.4 * grow * fade));
            }
            m.groundRing(s.centre.x, s.centre.y + 0.05, s.centre.z, r, 0.25 + 0.35 * grow, glass(0.18 * grow * fade));
        } else {
            for (int i = 0; i < 48; i++) {
                double a0 = Math.PI * 2 * i / 48 + rnd(d.seed(), 850 + i, 1) + angle * 0.2;
                double rr = r * (0.9 + 0.35 * rnd(d.seed(), 850 + i, 2));
                Vec3 p = s.centre.add(ringAt(s, a0).scale(rr)).add(0, 0.15 + (0.3 + 1.6 * rnd(d.seed(), 850 + i, 3)) * grow, 0);
                dot(m, p, 0.35 + 0.5 * grow, dust(0.22 * grow * fade));
            }
        }
    }

    private static Vec3 ringAt(ProjectionStage s, double angle) {
        return s.away.scale(Math.cos(angle)).add(s.across.scale(Math.sin(angle)));
    }

    private static void ground(Mesh m, Draw d, boolean edges) {
        ProjectionStage s = d.stage();
        double t = d.t();
        double foot = t - FOOT;
        if (foot >= 0 && foot < 1.0) {
            double out = 1 - Math.pow(1 - foot, 3);
            Vec3 at = s.strikeFrom.subtract(s.away.scale(0.25));
            if (edges) {
                m.groundRing(at.x, at.y + 0.04, at.z, 0.15 + 0.7 * out, 0.06, glass(0.45 * (1 - foot)));
            } else {
                for (int i = 0; i < 10; i++) {
                    double a0 = rnd(d.seed(), 900 + i, 1) * Math.PI * 2;
                    dot(m, at.add(Math.cos(a0) * (0.2 + 0.6 * out), 0.05 + 0.12 * out * rnd(d.seed(), 900 + i, 2), Math.sin(a0) * (0.2 + 0.6 * out)),
                            0.10 + 0.12 * out, dust(0.35 * (1 - foot)));
                }
            }
        }
        double resume = t - RESUME;
        if (resume >= 0 && resume < 1.4) {
            double out = 1 - Math.pow(1 - Curves.clamp01(resume / 0.9), 3);
            if (edges) {
                m.groundRing(s.centre.x, s.centre.y + 0.05, s.centre.z, 1 + 13 * out, 0.4 + 0.5 * out, glass(0.35 * (1 - resume / 1.4)));
            } else {
                for (int i = 0; i < 60; i++) {
                    double a0 = rnd(d.seed(), 950 + i, 1) * Math.PI * 2, r = (1 + 13 * out) * (0.7 + 0.3 * rnd(d.seed(), 950 + i, 2));
                    dot(m, s.centre.add(Math.cos(a0) * r, 0.15 + 1.4 * resume * rnd(d.seed(), 950 + i, 3), Math.sin(a0) * r),
                            0.5 + 0.9 * resume, dust(0.26 * (1 - resume / 1.4)));
                }
            }
        }
    }

private static Vec3 onPane(ProjectionStage s, ProjectionStage.Cell cell, double u, double v) {
        return cell.pane().add(s.across.scale(u)).add(0, v, 0);
    }

    private static double bend(int i, double t) {
        if (i != 2 || t < STRIKE || t >= FINAL_HIT) {
            return 0;
        }
        return Curves.window(strikeAction(t), STRIKE_LANDS * 0.9, STRIKE_SPAN);
    }

    private static void cell(Mesh m, Draw d, int i, boolean edges) {
        ProjectionStage s = d.stage();
        ProjectionStage.Cell cell = s.cells[i];
        double t = d.t();
        if (t < cell.on() || t >= cell.off() + 0.1) {
            return;
        }
        Vec3 middle = s.centreOf(cell.feet());
        double depth = cell.pane().subtract(middle).dot(s.away), bend = bend(i, t);
        for (int j = 0; j < LAYERS; j++) {
            if (j == 0 && t >= cell.off()) {
                continue;
            }
            double age = t - (cell.on() + 0.04 * j);
            if (age < 0) {
                continue;
            }
            double snap = 1 + 0.25 * (1 - Curves.smoothstep(age / 0.07));
            double arrive = 1 + 1.6 * Math.exp(-age / 0.05);
            double leave = t < cell.off() ? 1 : 2.5 * (1 - (t - cell.off()) / 0.1);
            Vec3 centre = middle.add(s.away.scale(depth - 2 * depth * j / (LAYERS - 1)));
            double hw = s.paneHalfWidth * (1 + 0.05 * j) * snap, hh = s.paneHalfHeight * (1 + 0.05 * j) * snap;
            Vec3 u = s.across.scale(hw), v = UP.scale(hh);
            double back = j == 0 ? 1 : 0.55, thick = 0.018 * Math.max(1, Math.sqrt(s.scale));
            if (j == 0 && bend > 0) {
                bent(m, s, cell, bend, thick, edges);
                continue;
            }
            if (edges) {
                border(m, centre, u, v, thick, glass(0.6 * back * arrive * leave));
                border(m, centre.add(s.across.scale(0.014)), u, v, thick * 0.6, c(1.0, 0.25, 0.25, 0.22 * back * leave));
                border(m, centre.subtract(s.across.scale(0.014)), u, v, thick * 0.6, c(0.25, 1.0, 1.0, 0.22 * back * leave));
            } else {
                rect(m, centre, u, v, c(0.78, 0.82, 0.88, (j == 0 ? 0.08 : 0.04) * Math.min(1.5, arrive)));
            }
        }
    }

    private static void bent(Mesh m, ProjectionStage s, ProjectionStage.Cell cell, double bend, double thick, boolean edges) {
        Vec3 tip = cell.contact().subtract(s.away.scale(0.32 * bend));
        int per = 6;
        Vec3[] rim = new Vec3[per * 4];
        for (int k = 0; k < per; k++) {
            double f = -1 + 2.0 * k / per;
            rim[k] = onPane(s, cell, f * s.paneHalfWidth, -s.paneHalfHeight);
            rim[per + k] = onPane(s, cell, s.paneHalfWidth, f * s.paneHalfHeight);
            rim[2 * per + k] = onPane(s, cell, -f * s.paneHalfWidth, s.paneHalfHeight);
            rim[3 * per + k] = onPane(s, cell, -s.paneHalfWidth, -f * s.paneHalfHeight);
        }
        for (int k = 0; k < rim.length; k++) {
            Vec3 a = rim[k], b = rim[(k + 1) % rim.length];
            if (edges) {
                line(m, a, b, thick * 0.5, thick * 0.5, glass(0.7));
                if (k % 2 == 0) {
                    line(m, lerp(a, tip, 0.35), tip, 0.0, 0.004, glass(0.5 * bend));
                }
            } else {
                tri(m, a, b, tip, c(0.80, 0.86, 0.95, 0.08 + 0.10 * bend));
            }
        }
    }

    private static double cracked(int i, double t) {
        return switch (i) {
            case 0 -> t < CONTACT ? 0 : 0.03 + 0.97 * Math.pow(Curves.window(t, CONTACT, SHATTER - 0.08), 1.7);
            case 1 -> {
                int landed = 0;
                while (landed < BEATS.length && t >= BEATS[landed]) {
                    landed++;
                }
                yield Math.pow(landed / (double) BEATS.length, 1.3);
            }
            default -> {
                if (t >= FINAL_HIT) {
                    yield 0.45 + 0.55 * Curves.window(t, FINAL_HIT, FINAL_HIT + 0.10);
                }
                yield t < STRIKE ? 0 : 0.45 * Curves.window(strikeAction(t), STRIKE_LANDS, STRIKE_SPAN);
            }
        };
    }

    private static void cracks(Mesh m, Draw d, int i, boolean edges) {
        double t = d.t();
        ProjectionStage s = d.stage();
        ProjectionStage.Cell cell = s.cells[i];
        if (!edges || t < cell.on() || t >= (i == 2 ? DRIFT : cell.off())) {
            return;
        }
        Projection.Glass glass = d.scene().glass[i];
        double k = cracked(i, t);
        if (k <= 0) {
            return;
        }
        double reach = glass.reach * k;
        for (Projection.Crack crack : glass.cracks) {
            if (crack.reach() >= reach) {
                continue;
            }
            double part = Math.min(1, (reach - crack.reach()) / crack.length());
            Vec3 a = onPane(s, cell, crack.u0(), crack.v0());
            Vec3 b = onPane(s, cell, crack.u0() + (crack.u1() - crack.u0()) * part, crack.v0() + (crack.v1() - crack.v0()) * part);
            double w = 0.002 + 0.005 * Math.max(0, 1 - crack.reach() / Math.max(0.3, reach));
            line(m, a, b, w, part < 1 ? 0.001 : w * 0.85, glass(0.95));
        }
        m.glow(cell.contact(), 0.10 + 0.05 * k, glass(0.5));
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1 - cos)));
    }

    private static void shards(Mesh m, Draw d, int i, boolean edges) {
        ProjectionStage s = d.stage();
        ProjectionStage.Cell cell = s.cells[i];
        double since = d.t() - cell.off();
        boolean hanging = i == 2;
        if (since < 0 || since > (hanging ? LAST_CLICK - FINAL_HIT + 0.2 : 1.6)) {
            return;
        }
        for (Projection.Shard shard : d.scene().glass[i].shards) {
            double age = Math.max(0, since - shard.delay());
            double life, travel, fall;
            if (hanging) {
                travel = 0.05 * Curves.smoothstep(since / 0.15) + 0.32 * Math.max(0, d.t() - DRIFT);
                fall = 0;
                life = 1 - Curves.window(d.t(), shard.gone(), shard.gone() + 0.08);
            } else {
                travel = (1 - Math.exp(-age * 3.0)) / 3.0;
                fall = 1.6 * age * age;
                life = 1 - Curves.smoothstep((age - 0.5) / 0.9);
            }
            if (life < 0.01) {
                continue;
            }
            double cu = (shard.u()[0] + shard.u()[1] + shard.u()[2]) / 3, cv = (shard.v()[0] + shard.v()[1] + shard.v()[2]) / 3;
            Vec3 centre = onPane(s, cell, cu, cv).add(shard.velocity().scale(travel)).add(0, -fall, 0);
            double angle = shard.spin() * (hanging ? travel * 2.2 : age);
            Vec3[] p = new Vec3[3];
            for (int k = 0; k < 3; k++) {
                Vec3 rel = s.across.scale(shard.u()[k] - cu).add(0, shard.v()[k] - cv, 0);
                p[k] = centre.add(rotate(rel, shard.axis(), angle));
            }
            double flare = hanging && d.t() >= shard.gone() ? 3.0 : 1.0;
            if (edges) {
                Vec3 normal = p[1].subtract(p[0]).cross(p[2].subtract(p[0]));
                double facing = normal.lengthSqr() < 1.0e-12 ? 0 : Math.abs(normal.normalize().dot(centre.subtract(m.camera).normalize()));
                tri(m, p[0], p[1], p[2], glass((0.10 + 0.7 * Math.pow(facing, 10)) * life * flare));
                for (int k = 0; k < 3; k++) {
                    double w = hanging ? 0.009 : 0.004;
                    line(m, p[k], p[(k + 1) % 3], w, w, glass((hanging ? 0.75 : 0.55) * life * flare));
                }
            } else {
                tri(m, p[0], p[1], p[2], hanging ? c(0.62, 0.72, 0.90, 0.36 * life) : c(0.80, 0.86, 0.95, 0.16 * life));
            }
        }
    }

private static void blow(Mesh m, Draw d, ProjectionStage.Hit hit, boolean edges) {
        double age = d.t() - hit.time();
        if (age < 0 || age > 1.2) {
            return;
        }
        ProjectionStage s = d.stage();
        long seed = d.seed();
        int id = 1000 + (int) (hit.time() * 97) % 5000;
        double power = hit.power();
        Vec3[] plane = square(hit.direction());
        boolean light = hit.kind() == ProjectionStage.LIGHT;
        double span = light ? 0.25 : 0.55;
        double out = 1 - Math.pow(1 - Curves.clamp01(age / span), 3);
        boolean surface = hit.kind() == ProjectionStage.WALL || hit.kind() == ProjectionStage.SHEET;
        if (edges) {
            m.glow(hit.at(), light ? 0.18 : 0.30 + 0.55 * power, glass((light ? 0.6 : 0.85) * Math.exp(-age / 0.05)));
            double la = 0.7 * (1 - Curves.clamp01(age / 0.18));
            int lines = light ? 3 : 12;
            for (int k = 0; k < lines && la > 0.01 && !surface; k++) {
                Vec3 off = plane[0].scale((rnd(seed, id + k, 1) - 0.5) * (0.6 + 3.0 * power)).add(plane[1].scale((rnd(seed, id + k, 2) - 0.5) * (0.6 + 2.4 * power)));
                Vec3 from = hit.at().add(off).add(hit.direction().scale(0.5 + 6 * age));
                line(m, from, from.add(hit.direction().scale((0.4 + 1.8 * rnd(seed, id + k, 3)) * (0.4 + power))), 0.012, 0.0, glass(la));
            }
            if (!light) {
                circle(m, hit.at(), plane[0], plane[1], 0.3 + (2 + 8 * power) * out, 0.03, glass(0.55 * (1 - Curves.clamp01(age / span))));
            }
            if (power >= 0.95) {
                circle(m, hit.at(), plane[0], plane[1], 0.3 + 16 * out, 0.05, glass(0.4 * (1 - Curves.clamp01(age / span))));
                double wide = 1 - Math.pow(1 - Curves.clamp01(age / 1.1), 3);
                m.groundRing(s.centre.x, s.centre.y + 0.05, s.centre.z, 1 + 22 * wide, 0.4 + 0.8 * wide, glass(0.4 * (1 - age / 1.2)));
            }
            if (hit.kind() == ProjectionStage.SHEET) {
                double a = 1 - Curves.clamp01(age / 0.5);
                Vec3 u = plane[0].scale(1.4 + s.width), v = plane[1].scale(1.2 + 0.5 * s.height);
                border(m, hit.at(), u, v, 0.02, glass(0.7 * a));
                border(m, hit.at(), u.scale(0.6 + 0.6 * out), v.scale(0.6 + 0.6 * out), 0.012, glass(0.35 * a));
            }
        } else {
            if (hit.kind() == ProjectionStage.SHEET) {
                rect(m, hit.at(), plane[0].scale(1.4 + s.width), plane[1].scale(1.2 + 0.5 * s.height), glass(0.10 * (1 - Curves.clamp01(age / 0.5))));
            }
            double life = 1 - Curves.clamp01(age / 1.2);
            if (hit.kind() == ProjectionStage.WALL) {
                for (int k = 0; k < 40; k++) {
                    Vec3 spread = plane[0].scale((rnd(seed, id + k, 1) - 0.5) * (1 + 5 * out)).add(plane[1].scale((rnd(seed, id + k, 2) - 0.5) * (1 + 4 * out)));
                    Vec3 p = hit.at().add(spread).subtract(hit.direction().scale((0.3 + 2.5 * rnd(seed, id + k, 3)) * out)).add(0, -0.8 * age * age, 0);
                    dot(m, p, 0.3 + 0.9 * age, dust(0.30 * life));
                }
            } else if (!light && !surface && hit.at().y - s.centre.y < 2.5 + 3 * power) {
                int n = (int) (20 + 60 * power);
                double reach = 0.8 + (4 + 16 * power) * out;
                for (int k = 0; k < n; k++) {
                    double a0 = rnd(seed, id + k, 1) * Math.PI * 2, r = reach * (0.75 + 0.25 * rnd(seed, id + k, 2));
                    dot(m, new Vec3(hit.at().x + Math.cos(a0) * r, s.centre.y + 0.15 + 1.1 * age * rnd(seed, id + k, 3), hit.at().z + Math.sin(a0) * r),
                            0.4 + 0.8 * age, dust(0.26 * life));
                }
            }
        }
    }

private static void postFx(Draw d, Vec3 cam, Matrix4f view, Matrix4f projection) {
        double t = d.t();
        ProjectionStage s = d.stage();
        double weight = ProjectionCamera.weight(t);
        double vignette = 0.25 * weight, darken = 0.08 * weight, chroma = 0, strength = 0, flash = 0;
        Vec3 focus = s.centreOf(s.targetAt(t));
        int held = s.cellAt(t);
        if (held >= 0) {
            chroma = 0.22 + 0.6 * Math.exp(-(t - s.cells[held].on()) / 0.08);
            vignette = 0.38;
            darken = 0.16;
        }
        if (t >= LAPS && t < TOUCH_2) {
            double grow = Curves.window(t, LAPS, LAPS_END);
            vignette = Math.max(vignette, 0.25 + 0.3 * grow);
            chroma = Math.max(chroma, 0.18 * grow);
        }
        for (ProjectionStage.Hit hit : s.hits()) {
            double age = t - hit.time();
            if (age < 0 || age > 1.0 || hit.kind() == ProjectionStage.LIGHT) {
                continue;
            }
            double bend = hit.power() * Math.exp(-age / 0.18);
            if (bend > strength) {
                strength = bend;
                focus = hit.at();
            }
            chroma = Math.max(chroma, 0.35 * hit.power() * Math.exp(-age / 0.2));
            flash = Math.max(flash, 0.5 * hit.power() * Math.exp(-age / 0.06));
        }
        if (vignette < 0.01 && chroma < 0.01 && strength < 0.01) {
            return;
        }
        CastPostFx.request(focus, cam, view, projection, (float) Math.min(1, strength * 1.2), (float) Math.min(1, chroma), (float) vignette,
                (float) flash, (float) (1.2 * s.scale), 0.85f, 0.90f, 1.0f, (float) darken);
    }
}
