package net.schwarz.rotasutils.ability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static net.schwarz.rotasutils.ability.ProjectionTimings.*;

public final class ProjectionStage {
    public static final int FRAMES = 24;

    public static final int LEAN = 0, RUN_A = 1, RUN_B = 2, PALM = 3, STAND = 4, PRESS = 5, JAB = 6, KICK_UP = 7, HAMMER = 8,
            HOOK = 9, ELBOW = 10, SWEEP = 11, UPPER = 12, STOMP = 13, LOW = 14, STROLL = 15, COCK = 16;

    public interface Clearance {
        double free(Vec3 from, Vec3 direction, double max);
    }

    public record Seg(double t0, double t1, Vec3 from, Vec3 to, int pose, double yaw, boolean frames, double peak) {
    }

    public record Cell(double on, double off, Vec3 feet, Vec3 pane, Vec3 contact) {
    }

    public record Hit(double time, Vec3 at, Vec3 direction, double power, int kind) {
    }

    public static final int BLOW = 0, LIGHT = 1, WALL = 2, SHEET = 3;

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double PANE_GAP = 0.30;
    private static final double HOVER = 0.45;
    private static final double[] DASH_AT = {0, 0.07, 0.15, 0.22, 0.28};
    private static final double[] DASH_TO = {0.0, 0.30, 0.58, 0.82, 1.0};
    private static final double[] LEAVE_AT = {0.12, 0.18, 0.24};
    private static final double[] LEAVE_TO = {0.30, 0.65, 1.0};
    private static final double ACT_1_END = PUNCH + 0.17;

    public final Vec3 start;
    public final Vec3 target;
    public final double width;
    public final double height;
    public final Vec3 dir;
    public final Vec3 side;
    public final double scale;
    public final Vec3 away;
    public final Vec3 across;
    public final Vec3 open;
    public final Vec3 touch;
    public final Vec3 far;
    public final Vec3 punch;
    public final Vec3 pane;
    public final double paneHalfWidth;
    public final double paneHalfHeight;
    public final Vec3 contact;
    public final Vec3 centre;
    public final double radius;
    public final double headroom;
    public final Vec3 trap;
    public final Vec3[] crash = new Vec3[3];
    public final Vec3[] crashDir = new Vec3[3];
    public final boolean[] crashReal = new boolean[3];
    public final Vec3 far2;
    public final Vec3 opposite;
    public final double lapRadius;
    public final Vec3 touch2;
    public final Vec3 strikeFrom;
    public final Vec3 strikeTo;
    public final Vec3 walkBy;
    public final Vec3 walkEnd;

    public final Cell[] cells = new Cell[3];
    private final List<Seg> segs = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Vec3[] images = new Vec3[FRAMES];
    private final double[] imageYaw = new double[FRAMES];
    private final int[] imagePose = new int[FRAMES];
    private final Vec3[] ring = new Vec3[FRAMES];
    private final double[] ringYaw = new double[FRAMES];
    private final int[] ringPose = new int[FRAMES];
    private final Vec3[] beatDir = new Vec3[FRAMES];
    private final Vec3[] juggle = new Vec3[3];

    public ProjectionStage(Vec3 start, Vec3 target, double width, double height, Clearance space) {
        this.start = start;
        this.target = target;
        this.width = width;
        this.height = height;
        Vec3 flat = new Vec3(target.x - start.x, 0, target.z - start.z);
        this.dir = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        this.side = new Vec3(-dir.z, 0, dir.x);
        this.scale = Math.max(0.5, Math.min(6.0, Math.max(width / 0.6, height / 1.8)));
        double half = width / 2, plane = half + PANE_GAP;
        Vec3 mid = target.add(0, Math.max(0.5, height / 2), 0);

        Vec3[] sides = {dir, side.scale(-1), dir.scale(-1), side};
        Vec3 best = dir;
        double bestScore = -1;
        for (Vec3 candidate : sides) {
            double score = Math.min(8, space.free(mid.add(candidate.scale(half + 0.1)), candidate, 20))
                    + 0.75 * Math.min(6, space.free(mid.subtract(candidate.scale(half + 0.1)), candidate.scale(-1), 20));
            if (score > bestScore + 1.0e-6) {
                bestScore = score;
                best = candidate;
            }
        }
        this.away = best;
        this.across = new Vec3(-away.z, 0, away.x);

        double clear = space.free(mid.add(away.scale(half + 0.1)), away, 20);
        this.touch = target.subtract(side.scale(half + 1.0));
        this.far = target.add(away.scale(Math.max(plane + 1.5, Math.min(12.0 + 2.0 * scale, clear - 1.0))));
        this.punch = target.add(away.scale(plane + 0.80));
        this.pane = target.add(away.scale(plane)).add(0, height / 2, 0);
        this.paneHalfWidth = width * 0.65 + 0.35;
        this.paneHalfHeight = height * 0.55 + 0.20;
        this.contact = contactOn(target);

        double behind = space.free(mid.subtract(away.scale(half + 0.1)), away.scale(-1), 20);
        Vec3 thrown = target.subtract(away.scale(Math.max(0.5, Math.min(4.0, behind - 1.0 - half))));
        Vec3 tmid = thrown.add(0, Math.max(0.5, height / 2), 0);
        double right = space.free(tmid, across, 24), left = space.free(tmid, across.scale(-1), 24);
        this.open = right > left + 1.0 ? across : across.scale(-1);
        double need = Math.max(0, 2.6 + half - Math.min(right, left));
        this.centre = thrown.add(open.scale(Math.min(need, Math.max(0, Math.max(right, left) - 2.6 - half))));
        Vec3 cmid = centre.add(0, Math.max(0.5, height / 2), 0);
        this.headroom = Math.max(0.6, Math.min(5.0, space.free(centre.add(0, height + 0.1, 0), UP, 12) - 0.4));
        double least = 24;
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            least = Math.min(least, space.free(cmid, away.scale(Math.cos(a)).add(across.scale(Math.sin(a))), 24));
        }
        this.radius = Math.max(2.5, Math.min(10.0, least - half - 0.4));

        this.trap = centre.add(open.scale(Math.min(2.0, radius * 0.4)));
        Vec3[] toward = {away.scale(-1), open, open.scale(-1)};
        double[] lift = {1.4, 2.2, 1.0};
        for (int k = 0; k < 3; k++) {
            double room = space.free(cmid, toward[k], 24);
            crashDir[k] = toward[k];
            crashReal[k] = room < 7.0 + half + 0.35;
            crash[k] = centre.add(toward[k].scale(Math.max(1.5, Math.min(7.0, room - half - 0.35)))).add(0, Math.min(headroom, lift[k]), 0);
        }
        this.far2 = centre.add(away.scale(Math.max(4.0, Math.min(16.0, space.free(cmid, away, 24) - 1.5))));
        this.opposite = centre.subtract(away.scale(Math.max(3.0, Math.min(12.0, space.free(cmid, away.scale(-1), 24) - 1.5))));
        this.lapRadius = Math.max(2.2, Math.min(6.0, radius - 0.3));
        this.touch2 = centre.add(across.scale(half + 1.0));
        this.strikeFrom = centre.add(away.scale(Math.max(plane + 1.6, Math.min(3.2 + 0.5 * width, space.free(cmid, away, 24) - 0.6))));
        this.strikeTo = centre.add(away.scale(plane + 0.45));
        this.walkBy = centre.add(open.scale(half + 0.8));
        this.walkEnd = centre.add(open.scale(Math.max(half + 1.5, Math.min(3.5 + half, space.free(cmid, open, 24) - 0.8)))).subtract(away.scale(1.0));

        Vec3 held = trap.add(0, HOVER, 0);
        cells[0] = new Cell(TOUCH, SHATTER, target, pane, contact);
        cells[1] = new Cell(TRAP, BREAK, held, held.add(away.scale(plane)).add(0, height / 2, 0), contactOn(held));
        cells[2] = new Cell(TOUCH_2, FINAL_HIT, centre, centre.add(away.scale(plane)).add(0, height / 2, 0), contactOn(centre));

        juggle[0] = centre.add(0, 0.45 * headroom, 0);
        juggle[1] = centre.add(0, headroom, 0);
        juggle[2] = centre.add(0, 0.5, 0);
        layOutImages();
        layOutRing();
        choreograph();
    }

    private Vec3 contactOn(Vec3 feet) {
        Vec3 sheet = feet.add(away.scale(width / 2 + PANE_GAP)).add(0, height / 2, 0);
        double fist = Math.max(sheet.y - paneHalfHeight + 0.15, Math.min(sheet.y + paneHalfHeight - 0.15, feet.y + 1.30));
        return new Vec3(sheet.x - across.x * 0.25, fist, sheet.z - across.z * 0.25);
    }

    public static ProjectionStage of(Vec3 start, Vec3 at, LivingEntity target, Entity context) {
        return new ProjectionStage(start, at, target.getBbWidth(), target.getBbHeight(), (from, direction, max) -> {
            var hit = context.level().clip(new ClipContext(from, from.add(direction.scale(max)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, context));
            return hit.getType() == HitResult.Type.MISS ? max : hit.getLocation().distanceTo(from);
        });
    }

    public static double yawOf(Vec3 v) {
        return Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static double window(double x, double a, double b) {
        return x <= a ? 0 : x >= b ? 1 : (x - a) / (b - a);
    }

    private Vec3 round(double angle) {
        return away.scale(Math.cos(angle)).add(across.scale(Math.sin(angle)));
    }

    public Vec3 centreOf(Vec3 feet) {
        return feet.add(0, height / 2, 0);
    }

    public Vec3 targetCentre() {
        return centreOf(target);
    }

private void layOutImages() {
        double out = yawOf(dir), back = yawOf(away.scale(-1));
        image(0, start.add(dir.scale(0.35)), out, LEAN);
        double[] approach = {0.06, 0.13, 0.22, 0.33, 0.45, 0.58, 0.71, 0.83, 0.93};
        for (int k = 0; k < approach.length; k++) {
            image(1 + k, lerp(start, touch, approach[k]).add(side.scale(k % 2 == 0 ? 0.35 : -0.35)), out, k % 2 == 0 ? RUN_A : RUN_B);
        }
        image(10, touch, out, PALM);
        double[] leave = {0.2, 0.45, 0.7, 0.9};
        for (int k = 0; k < leave.length; k++) {
            image(11 + k, lerp(touch, far, leave[k]), yawOf(far.subtract(touch)), k % 2 == 0 ? RUN_B : RUN_A);
        }
        image(15, far, yawOf(away), STAND);
        double[] ret = {0.15, 0.35, 0.55, 0.75, 0.9};
        for (int k = 0; k < ret.length; k++) {
            image(16 + k, lerp(far, punch, ret[k]), back, k % 2 == 0 ? RUN_A : RUN_B);
        }
        image(21, target.add(away.scale(width / 2 + 0.2)).add(across.scale(width / 2 + 0.3)).add(0, height + 0.25, 0), back, PRESS);
        image(22, punch.add(away.scale(0.55)), back, PRESS);
        image(23, punch, back, JAB);
    }

    private void image(int i, Vec3 at, double yaw, int pose) {
        images[i] = at;
        imageYaw[i] = yaw;
        imagePose[i] = pose;
    }

    public Vec3 cell(int i) {
        return images[i];
    }

    public double cellYaw(int i) {
        return imageYaw[i];
    }

    public int cellPose(int i) {
        return imagePose[i];
    }

    public static double cellTime(int i) {
        return CALC + i * CELL_STEP;
    }

private void layOutRing() {
        double wide = Math.max(3.0, Math.min(8.0, radius));
        double near = strikeFrom.subtract(centre).dot(away);
        for (int i = 0; i < FRAMES; i++) {
            double k = i / (double) (FRAMES - 1);
            double angle = (1 - k) * Math.PI * 1.8;
            double lift = i % 5 == 2 ? 0.9 : i % 7 == 4 ? 1.6 : 0;
            ring[i] = centre.add(round(angle).scale(wide + (near - wide) * k * k)).add(0, Math.min(headroom, lift) * (1 - k), 0);
            ringYaw[i] = yawOf(away.scale(Math.sin(angle)).subtract(across.scale(Math.cos(angle))));
            ringPose[i] = i >= FRAMES - 2 ? COCK : i == FRAMES - 3 ? LOW : lift > 0 ? LEAN : i % 2 == 0 ? RUN_A : RUN_B;
        }
        ring[FRAMES - 1] = strikeFrom;
        ringYaw[FRAMES - 1] = ringYaw[FRAMES - 2] = yawOf(away.scale(-1));
    }

    public Vec3 ring(int i) {
        return ring[i];
    }

    public double ringYaw(int i) {
        return ringYaw[i];
    }

    public int ringPose(int i) {
        return ringPose[i];
    }

    public static double ringTime(int i) {
        return RING + i * RING_STEP;
    }

private void seg(double t0, double t1, Vec3 from, Vec3 to, int pose, double yaw, boolean frames) {
        segs.add(new Seg(t0, t1, from, to, pose, yaw, frames, -1));
    }

    private void blow(double t0, double peak, double t1, Vec3 at, int pose, Vec3 facing, double lunge) {
        Vec3 flat = new Vec3(facing.x, 0, facing.z);
        Vec3 step = flat.lengthSqr() < 1.0e-6 ? Vec3.ZERO : flat.normalize().scale(lunge);
        segs.add(new Seg(t0, t1, at, at.add(step), pose, yawOf(facing), true, peak));
    }

    private void hold(double t0, double t1, Vec3 at, int pose, Vec3 facing) {
        seg(t0, t1, at, at, pose, yawOf(facing), false);
    }

    private void hit(double time, Vec3 at, Vec3 direction, double power, int kind) {
        hits.add(new Hit(time, at, direction, power, kind));
    }

    public Vec3 beatFrom(int k) {
        return beatDir[k];
    }

    private void choreograph() {
        double half = width / 2, reach = half + 0.95;
        Vec3 back = away.scale(-1), down = UP.scale(-1);
        hit(PUNCH, contact, back, 0.6, BLOW);

        blow(UNDER - 0.22, UNDER, UNDER + 0.18, centre.add(away.scale(0.5)), KICK_UP, back, 0.3);
        hit(UNDER, centreOf(juggle[0]), UP, 0.5, BLOW);
        segs.add(new Seg(OVER - 0.22, OVER + 0.14, juggle[1].add(0, height + 0.3, 0).add(away.scale(0.4)),
                juggle[1].add(0, height - 0.2, 0).add(away.scale(0.4)), HAMMER, yawOf(back), true, OVER));
        hit(OVER, centreOf(juggle[1]), down, 0.5, BLOW);
        blow(SIDE - 0.22, SIDE, SIDE + 0.18, centre.subtract(open.scale(reach)), JAB, open, 0.35);
        hit(SIDE, centreOf(juggle[2]), open, 0.5, BLOW);

        Vec3 held = cells[1].feet();
        for (int k = 0; k < FRAMES; k++) {
            Vec3 from = round(0.7 + k * 2.399963);
            int pk = (int) Math.floor(BEATS[k] * FPS + 1.0e-6);
            int pPrev = k > 0 ? (int) Math.floor(BEATS[k - 1] * FPS + 1.0e-6) : pk - 6;
            int pNext = k + 1 < FRAMES ? (int) Math.floor(BEATS[k + 1] * FPS + 1.0e-6) : pk + 12;
            int startF = Math.max(pPrev + 2, pk - (k >= 21 ? 5 : 3));
            int endF = Math.min(pk + 2 + (k >= 21 ? 4 : 3), k + 1 < FRAMES ? Math.max(pk + 2, pNext - 2) : Integer.MAX_VALUE);
            double before = BEATS[k] - startF / FPS, after = endF / FPS - BEATS[k];
            Vec3 at = held.subtract(0, HOVER, 0).add(from.scale(reach));
            int pose = k % 4 == 1 ? ELBOW : k % 4 == 2 ? SWEEP : HOOK;
            Vec3 force = from.scale(-1);
            double power = 0.18;
            int kind = LIGHT;
            if (k == 21) {
                pose = UPPER;
                force = UP;
                power = 0.45;
                kind = BLOW;
                before = 0.08;
                after = 0.14;
            } else if (k == 22) {
                from = away;
                at = held.add(0, Math.min(1.4, headroom) + height + 0.2, 0).add(away.scale(0.3));
                pose = STOMP;
                force = down;
                power = 0.5;
                kind = BLOW;
                before = 0.08;
                after = 0.16;
            } else if (k == 23) {
                from = away;
                at = held.subtract(0, HOVER, 0).add(away.scale(half + PANE_GAP + 0.45));
                pose = JAB;
                force = back;
                power = 0.8;
                kind = BLOW;
                before = 0.12;
                after = 0.20;
            } else if (k % 4 == 3) {
                at = held.add(0, height + 0.15, 0).add(from.scale(half + 0.3));
                pose = HAMMER;
                force = down;
            }
            beatDir[k] = from;
            blow(BEATS[k] - before, BEATS[k], BEATS[k] + after, at, pose, from.scale(-1), k == 23 ? 0.2 : 0.3);
            hit(BEATS[k], k == 23 ? cells[1].contact() : centreOf(held).add(from.scale(half)), force, power, kind);
        }

        for (int k = 0; k < 3; k++) {
            double when = k == 0 ? CRASH_1 : k == 1 ? CRASH_2 : CRASH_3;
            hit(when, centreOf(crash[k]).add(crashDir[k].scale(half + 0.35)), crashDir[k], 0.7, crashReal[k] ? WALL : SHEET);
            if (k < 2) {
                Vec3 onward = crash[k + 1].subtract(crash[k]).normalize();
                double kick = k == 0 ? KICK_1 : KICK_2;
                blow(when + 0.06, kick, kick + 0.16, crash[k].subtract(onward.scale(reach)).subtract(0, 0.2, 0), k == 0 ? KICK_UP : JAB, onward, 0.3);
                hit(kick, centreOf(crash[k]), onward, 0.5, BLOW);
            }
        }

        hold(REST - 0.4, STANCE, far2, STAND, back);
        hold(STANCE, PASSES[0], far2, LOW, back);
        Vec3 at = far2;
        for (int i = 0; i < PASSES.length; i++) {
            Vec3 lane = across.scale((i % 2 == 0 ? 1 : -1) * (half + 1.4));
            Vec3 to = (i % 2 == 0 ? opposite : far2).add(lane);
            seg(PASSES[i], PASSES[i] + PASS_TIME, at, to, i % 2 == 0 ? RUN_A : RUN_B, yawOf(to.subtract(at)), true);
            hold(PASSES[i] + PASS_TIME, i + 1 < PASSES.length ? PASSES[i + 1] : LAPS, to, LOW, centre.subtract(to));
            at = to;
        }

        hold(LAPS_END, CHARGE, far2, LOW, back);
        seg(CHARGE, TOUCH_2, far2, touch2.add(away.scale(0.6)), RUN_A, yawOf(back), true);
        seg(TOUCH_2, TOUCH_2 + 0.10, touch2.add(away.scale(0.6)), touch2.subtract(away.scale(0.9)), PALM, yawOf(back), false);
        Vec3 off = opposite.add(across.scale(Math.min(6.0, radius)));
        seg(TOUCH_2 + 0.10, TOUCH_2 + 0.45, touch2.subtract(away.scale(0.9)), off, RUN_B, yawOf(off.subtract(touch2)), true);

        hold(REVEAL_2, STRIKE, strikeFrom, COCK, back);
        hit(FINAL_HIT, cells[2].contact(), back, 1.0, BLOW);

        hold(WALK_END, END + 1, walkEnd, STAND, walkEnd.subtract(centre));
    }

    public List<Hit> hits() {
        return hits;
    }

public static int dashStep(double t) {
        int i = -1;
        while (i + 1 < DASH_AT.length && t >= DASH + DASH_AT[i + 1]) {
            i++;
        }
        return i;
    }

    public static double dashStepTime(int i) {
        return DASH + DASH_AT[i];
    }

    public Vec3 dashPoint(int i) {
        return lerp(start, touch, DASH_TO[i]);
    }

    private Vec3 firstAct(double t) {
        if (t < DASH) {
            return start;
        }
        double glide = TOUCH - 0.04;
        if (t < glide) {
            int i = dashStep(t);
            double next = i + 1 < DASH_AT.length ? dashStepTime(i + 1) : glide;
            return lerp(start, touch, Math.min(1, DASH_TO[i] + 0.04 * window(t, dashStepTime(i), next)));
        }
        Vec3 past = touch.add(far.subtract(touch).normalize().scale(1.2));
        if (t < TOUCH + LEAVE_AT[0]) {
            return lerp(touch, past, window(t, glide, TOUCH + LEAVE_AT[0]));
        }
        if (t < REVEAL) {
            int i = 0;
            while (i + 1 < LEAVE_AT.length && t >= TOUCH + LEAVE_AT[i + 1]) {
                i++;
            }
            return lerp(past, far, LEAVE_TO[i]);
        }
        return punch.subtract(away.scale(0.35 * window(t, SHATTER, PUNCH)));
    }

    private Seg segAt(double t) {
        double f = frame(t) + 1.0e-7;
        for (Seg seg : segs) {
            if (f >= seg.t0() && f < seg.t1()) {
                return seg;
            }
        }
        return null;
    }

    public static double lapAngle(double t) {
        double u = window(t, LAPS, LAPS_END), span = LAPS_END - LAPS;
        return span * (7.0 * u + (42.0 - 7.0) * u * u * u / 3);
    }

    public static double strikeReach(double t) {
        double u = Math.min(1, strikeAction(t) / STRIKE_LANDS);
        return 1 - (1 - u) * (1 - u) * (1 - u);
    }

    private static double walked(double t) {
        double u = window(t, WALK, WALK_END);
        return u * u * (3 - 2 * u);
    }

    private Vec3 walk(double u) {
        double first = strikeTo.distanceTo(walkBy), second = walkBy.distanceTo(walkEnd);
        double d = u * (first + second);
        return d < first ? lerp(strikeTo, walkBy, d / first) : lerp(walkBy, walkEnd, (d - first) / Math.max(1.0e-6, second));
    }

    public Vec3 attackerAt(double t) {
        if (t < ACT_1_END) {
            return firstAct(t);
        }
        if (t >= LAPS && t < LAPS_END) {
            return centre.add(round(lapAngle(t)).scale(lapRadius));
        }
        if (t >= STRIKE && t < WALK) {
            return lerp(strikeFrom, strikeTo, strikeReach(t));
        }
        if (t >= WALK && t < WALK_END) {
            return walk(walked(t));
        }
        Seg seg = segAt(t);
        if (seg == null) {
            for (Seg next : segs) {
                if (next.t0() > t) {
                    return next.from();
                }
            }
            return walkEnd;
        }
        double f = seg.frames() ? frame(t) : t;
        double k;
        if (seg.peak() >= 0) {
            double u = window(t, seg.peak() - 0.06, seg.peak());
            k = u * u * (3 - 2 * u);
        } else {
            k = window(f, seg.t0(), seg.t1());
        }
        return lerp(seg.from(), seg.to(), k);
    }

    public Seg segOf(double t) {
        return segAt(t);
    }

    public int phaseAt(double t) {
        Seg seg = segAt(t);
        if (seg == null || seg.peak() < 0) {
            return -1;
        }
        long f = (long) Math.floor(t * FPS + 1.0e-6), p = (long) Math.floor(seg.peak() * FPS + 1.0e-6);
        return f < p ? 0 : f < p + 2 ? 1 : 2;
    }

    public Vec3 finalFeet() {
        return walkEnd;
    }

    public Vec3 finalTarget() {
        return centre;
    }

    public double yawAt(double t) {
        if (t < ACT_1_END) {
            return t < TOUCH + LEAVE_AT[0] ? yawOf(dir) : t < REVEAL ? yawOf(away) : yawOf(away.scale(-1));
        }
        if (t >= LAPS && t < LAPS_END) {
            double a = lapAngle(t);
            return yawOf(across.scale(Math.cos(a)).subtract(away.scale(Math.sin(a))));
        }
        if (t >= STRIKE && t < WALK) {
            return yawOf(away.scale(-1));
        }
        if (t >= WALK && t < WALK_END) {
            double u = walked(t);
            return yawOf(walk(Math.min(1, u + 0.02)).subtract(walk(Math.max(0, u - 0.02))));
        }
        Seg seg = segAt(t);
        return seg == null ? yawOf(away.scale(-1)) : seg.yaw();
    }

    public int poseAt(double t) {
        if (t >= LAPS && t < LAPS_END) {
            return (int) Math.floor(t * FPS) % 2 == 0 ? RUN_A : RUN_B;
        }
        if (t >= STRIKE && t < WALK) {
            return JAB;
        }
        if (t >= WALK && t < WALK_END) {
            return STROLL;
        }
        Seg seg = segAt(t);
        return seg == null ? STAND : seg.pose();
    }

    public boolean visible(double t) {
        if (t < ACT_1_END) {
            return t < GONE || t >= REVEAL;
        }
        return (t >= LAPS && t < LAPS_END) || (t >= STRIKE && t < WALK_END) || segAt(t) != null;
    }

private static double out(double u) {
        return 1 - (1 - u) * (1 - u);
    }

    public Vec3 targetAt(double t) {
        Vec3 p = trackAt(t);
        double drop = Math.max(0, height / 2 - width / 2);
        double sink = drop * Math.abs(Math.sin(Math.toRadians(tumble(t)[1]))) * window(t, CRASH_3 + 0.30, CRASH_3 + 0.45);
        return sink > 0 ? p.subtract(0, sink, 0) : p;
    }

    private static double keyed(double t, double... tv) {
        if (t <= tv[0]) {
            return tv[1];
        }
        for (int i = 2; i < tv.length; i += 2) {
            if (t < tv[i]) {
                double u = (t - tv[i - 2]) / (tv[i] - tv[i - 2]);
                return tv[i - 1] + (tv[i + 1] - tv[i - 1]) * u * u * (3 - 2 * u);
            }
        }
        return tv[tv.length - 1];
    }

    public double[] tumble(double t) {
        double sgn = open.dot(across) >= 0 ? 1 : -1;
        double pitch = keyed(t, PUNCH, 0, PUNCH + 0.08, -22, UNDER - 0.03, 62, UNDER + 0.02, 70, OVER, 250, OVER + 0.12, 268,
                SIDE, 344, TRAP, 360, BEATS[21], 360, BEATS[22], 384, BEATS[22] + 0.12, 335, BREAK, 338, CRASH_1, 448, KICK_1, 448,
                CRASH_2, 360, CRASH_3, 360);
        double roll = keyed(t, SIDE - 0.05, 0, SIDE + 0.12, 38 * sgn, TRAP, 0, KICK_1, 0, CRASH_2, 80 * sgn, KICK_2, 80 * sgn,
                CRASH_3, -80 * sgn, CRASH_3 + 0.45, -88 * sgn, REST - 0.1, -88 * sgn, REST + 0.7, 0);
        if (t >= TRAP && t < BEATS[21]) {
            for (int k = 0; k < 21; k++) {
                if (t >= BEATS[k] && t < BEATS[k] + 0.5) {
                    double a = 7.0 * Math.exp(-(t - BEATS[k]) / 0.07);
                    Vec3 force = beatDir[k].scale(-1);
                    pitch += a * force.dot(away.scale(-1));
                    roll += a * force.dot(across);
                }
            }
        }
        return new double[]{pitch, roll};
    }

    private Vec3 trackAt(double t) {
        if (t < PUNCH) {
            return target;
        }
        Vec3 held = cells[1].feet();
        if (t < UNDER) {
            double u = window(t, PUNCH, UNDER);
            return lerp(target, juggle[0], u).add(0, 0.8 * 4 * u * (1 - u) * Math.min(1, headroom / 2), 0);
        }
        if (t < OVER) {
            return lerp(juggle[0], juggle[1], out(window(t, UNDER, OVER)));
        }
        if (t < SIDE) {
            double u = window(t, OVER, SIDE);
            return lerp(juggle[1], juggle[2], u * u);
        }
        if (t < TRAP) {
            return lerp(juggle[2], held, out(window(t, SIDE, TRAP)));
        }
        if (t < BEATS[21]) {
            for (int k = 20; k >= 0; k--) {
                if (t >= BEATS[k]) {
                    return held.subtract(beatDir[k].scale(0.14 * Math.exp(-(t - BEATS[k]) / 0.05)));
                }
            }
            return held;
        }
        Vec3 lifted = held.add(0, Math.min(1.4, headroom), 0);
        if (t < BEATS[22]) {
            return lerp(held, lifted, out(window(t, BEATS[21], BEATS[22])));
        }
        if (t < BREAK) {
            double u = window(t, BEATS[22], BEATS[22] + 0.12);
            return lerp(lifted, held, u * u);
        }
        if (t < CRASH_1) {
            return lerp(held, crash[0], window(t, BREAK, CRASH_1));
        }
        if (t < KICK_1) {
            return crash[0];
        }
        if (t < CRASH_2) {
            return lerp(crash[0], crash[1], window(t, KICK_1, CRASH_2));
        }
        if (t < KICK_2) {
            return crash[1];
        }
        if (t < CRASH_3) {
            return lerp(crash[1], crash[2], window(t, KICK_2, CRASH_3));
        }
        if (t < REST) {
            Vec3 lands = lerp(new Vec3(crash[2].x, centre.y, crash[2].z), centre, 0.6);
            double fall = CRASH_3 + 0.45;
            if (t < fall) {
                double u = window(t, CRASH_3, fall);
                return lerp(crash[2], lands, u).add(0, 0.5 * 4 * u * (1 - u), 0);
            }
            return lerp(lands, centre, out(window(t, fall, REST)));
        }
        return centre;
    }

    public int cellAt(double t) {
        for (int i = 0; i < cells.length; i++) {
            if (t >= cells[i].on() && t < (i == 2 ? DRIFT : cells[i].off())) {
                return i;
            }
        }
        return -1;
    }

    public static double braced(double t) {
        return window(t, LAPS + 1.0, LAPS + 1.5) * (1 - window(t, FINAL_HIT, FINAL_HIT + 0.1));
    }
}
