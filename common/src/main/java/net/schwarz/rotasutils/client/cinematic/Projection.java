package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.ability.ProjectionStage;
import net.schwarz.rotasutils.ability.Timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.schwarz.rotasutils.ability.ProjectionTimings.*;

@Environment(EnvType.CLIENT)
public final class Projection {
    private Projection() {
    }

    record Crack(double u0, double v0, double u1, double v1, double reach, double length) {
    }

    record Shard(double[] u, double[] v, double delay, Vec3 velocity, Vec3 axis, double spin, double gone) {
    }

    static final class Glass {
        final List<Crack> cracks = new ArrayList<>();
        final List<Shard> shards = new ArrayList<>();
        double reach;
    }

    static final class Scene {
        final ProjectionStage stage;
        final int targetId;
        final Glass[] glass = {new Glass(), new Glass(), new Glass()};
        double frozenAge = Double.NaN;

        Scene(ProjectionStage stage, int targetId) {
            this.stage = stage;
            this.targetId = targetId;
        }
    }

static Scene scene(ClientCast cast) {
        if (cast.scene != null) {
            return cast.scene;
        }
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(cast.targetEntityId) instanceof LivingEntity target)) {
            return null;
        }
        Entity attacker = level.getEntity(cast.casterId);
        Vec3 start = cast.eye.subtract(0, attacker != null ? attacker.getEyeHeight() : 1.62, 0);
        Vec3 at = cast.target.subtract(0, target.getBbHeight() * 0.55, 0);
        Scene scene = new Scene(ProjectionStage.of(start, at, target, attacker != null ? attacker : target), cast.targetEntityId);
        Random random = new Random(cast.seed);
        for (int i = 0; i < 3; i++) {
            ProjectionStage.Cell cell = scene.stage.cells[i];
            double cu = cell.contact().subtract(cell.pane()).dot(scene.stage.across), cv = cell.contact().y - cell.pane().y;
            crack(scene.stage, scene.glass[i], random, cu, cv);
            shatter(scene.stage, scene.glass[i], random, cu, cv, i == 2);
        }
        cast.scene = scene;
        return scene;
    }

    private static void crack(ProjectionStage s, Glass glass, Random r, double cu, double cv) {
        int rays = 8;
        for (int i = 0; i < rays; i++) {
            walk(s, glass, r, cu, cv, Math.PI * 2 * (i + r.nextDouble() * 0.7) / rays, 0, 0);
        }
    }

    private static void walk(ProjectionStage s, Glass glass, Random r, double u, double v, double angle, double reach, int depth) {
        int steps = depth == 0 ? 40 : 7 - 2 * depth;
        for (int i = 0; i < steps; i++) {
            double length = 0.09 + 0.13 * r.nextDouble();
            angle += (r.nextDouble() - 0.5) * 0.75;
            double nu = u + Math.cos(angle) * length, nv = v + Math.sin(angle) * length;
            glass.cracks.add(new Crack(u, v, nu, nv, reach, length));
            reach += length;
            glass.reach = Math.max(glass.reach, reach);
            u = nu;
            v = nv;
            if (Math.abs(u) > s.paneHalfWidth || Math.abs(v) > s.paneHalfHeight) {
                return;
            }
            if (depth < 2 && r.nextDouble() < 0.24) {
                walk(s, glass, r, u, v, angle + (r.nextBoolean() ? 1 : -1) * (0.5 + 0.6 * r.nextDouble()), reach, depth + 1);
            }
        }
    }

    private static void shatter(ProjectionStage s, Glass glass, Random r, double cu, double cv, boolean fragments) {
        int nx = fragments ? 4 : 10, ny = fragments ? 6 : 14;
        double[][] gu = new double[nx + 1][ny + 1], gv = new double[nx + 1][ny + 1];
        double du = 2 * s.paneHalfWidth / nx, dv = 2 * s.paneHalfHeight / ny;
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) {
                boolean edge = i == 0 || j == 0 || i == nx || j == ny;
                gu[i][j] = -s.paneHalfWidth + i * du + (edge ? 0 : (r.nextDouble() - 0.5) * du * 0.7);
                gv[i][j] = -s.paneHalfHeight + j * dv + (edge ? 0 : (r.nextDouble() - 0.5) * dv * 0.7);
            }
        }
        int last = -1;
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                int[][] tris = r.nextBoolean() ? new int[][]{{0, 0, 1, 0, 1, 1}, {0, 0, 1, 1, 0, 1}} : new int[][]{{0, 0, 1, 0, 0, 1}, {1, 0, 1, 1, 0, 1}};
                for (int[] t : tris) {
                    double[] u = {gu[i + t[0]][j + t[1]], gu[i + t[2]][j + t[3]], gu[i + t[4]][j + t[5]]};
                    double[] v = {gv[i + t[0]][j + t[1]], gv[i + t[2]][j + t[3]], gv[i + t[4]][j + t[5]]};
                    double pu = (u[0] + u[1] + u[2]) / 3, pv = (v[0] + v[1] + v[2]) / 3;
                    double mu = pu - cu, mv = pv - cv;
                    double far = Math.sqrt(mu * mu + mv * mv);
                    Vec3 out = far < 1.0e-6 ? Vec3.ZERO : s.across.scale(mu / far).add(0, mv / far, 0);
                    Vec3 axis = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
                    if (fragments) {
                        Vec3 velocity = s.away.scale(-(0.3 + 1.1 * r.nextDouble())).add(out.scale(0.35 + 0.5 * r.nextDouble()))
                                .add(0, 0.25 * (r.nextDouble() - 0.3), 0);
                        double gone = WALK + (0.10 + 0.75 * r.nextDouble()) * (WALK_END - WALK);
                        double head = Math.abs(pu) + Math.abs(pv - 0.45 * s.paneHalfHeight);
                        if (head < nearest) {
                            nearest = head;
                            last = glass.shards.size();
                        }
                        glass.shards.add(new Shard(u, v, 0, velocity, axis, 0.25 + 0.5 * r.nextDouble(), gone));
                    } else {
                        Vec3 velocity = s.away.scale(-(2.0 + 7.0 * r.nextDouble()) / (1 + far)).add(out.scale(1.2 + 3.0 * r.nextDouble()))
                                .add(0, 0.6 + 1.6 * r.nextDouble(), 0);
                        glass.shards.add(new Shard(u, v, far * 0.025, velocity, axis, 2 + 8 * r.nextDouble(), Double.MAX_VALUE));
                    }
                }
            }
        }
        if (last >= 0) {
            Shard face = glass.shards.get(last);
            glass.shards.set(last, new Shard(face.u(), face.v(), 0, face.velocity().scale(0.5), face.axis(), 0.15, LAST_CLICK));
        }
    }

private static ClientCast attackerCast(Entity entity) {
        ClientCast cast = ClientCasts.forCaster(entity.getId());
        return cast != null && cast.projection() && !cast.cancelled ? cast : null;
    }

    private static ClientCast targetCast(Entity entity) {
        for (ClientCast cast : ClientCasts.all()) {
            if (cast.projection() && !cast.cancelled && cast.targetEntityId == entity.getId()) {
                return cast;
            }
        }
        return null;
    }

    public static Vec3 renderOffset(Entity entity, float partialTick) {
        ClientCast cast = attackerCast(entity);
        if (cast != null) {
            Scene scene = scene(cast);
            double t = cast.time(partialTick);
            if (scene == null || t < 0 || t > END) {
                return null;
            }
            double k = 1 - Curves.smoothstep(Curves.window(t, END - 0.6, END - 0.2));
            return scene.stage.attackerAt(t).subtract(entity.getPosition(partialTick)).scale(k);
        }
        cast = targetCast(entity);
        if (cast == null || cast.scene == null) {
            return null;
        }
        double t = cast.time(partialTick);
        return t < PUNCH || t >= RESUME ? null : cast.scene.stage.targetAt(t).subtract(entity.getPosition(partialTick));
    }

    public static boolean staged(Entity entity) {
        return attackerCast(entity) != null || targetCast(entity) != null;
    }

    public static boolean hidden(Entity entity, float partialTick) {
        ClientCast cast = attackerCast(entity);
        Scene scene = cast == null ? null : scene(cast);
        return scene != null && cast.time(partialTick) < END && !scene.stage.visible(cast.time(partialTick));
    }

    public static float partialTick(Entity entity, float partialTick) {
        ClientCast cast = targetCast(entity);
        if (cast == null || cast.scene == null) {
            return partialTick;
        }
        if (cast.scene.stage.cellAt(cast.time(partialTick)) < 0) {
            cast.scene.frozenAge = Double.NaN;
            return partialTick;
        }
        if (Double.isNaN(cast.scene.frozenAge)) {
            cast.scene.frozenAge = entity.tickCount + partialTick;
        }
        return (float) (cast.scene.frozenAge - entity.tickCount);
    }

    public static org.joml.Quaternionf tumble(Entity entity, float partialTick) {
        ClientCast cast = targetCast(entity);
        if (cast == null || cast.scene == null) {
            return null;
        }
        double t = cast.time(partialTick);
        if (t < PUNCH || t >= RESUME) {
            return null;
        }
        ProjectionStage s = cast.scene.stage;
        double[] a = s.tumble(t);
        return new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(a[0]), (float) s.across.x, 0f, (float) s.across.z)
                .mul(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(a[1]), (float) s.away.x, 0f, (float) s.away.z));
    }

    public static double braced(Entity entity, float partialTick) {
        ClientCast cast = targetCast(entity);
        return cast == null ? 0 : ProjectionStage.braced(cast.time(partialTick));
    }

    public static boolean holdsLocalPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        ClientCast cast = targetCast(player);
        return cast != null && cast.time(0) < RESUME;
    }

    static boolean black(ClientCast cast, double t) {
        return t >= BLACK && t < BLACK_END;
    }

    static double yaw(ClientCast cast, double t, double fallback) {
        Scene scene = scene(cast);
        return scene == null ? fallback : scene.stage.yawAt(t);
    }

static Vec3 clip(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return to;
        }
        var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) {
            return to;
        }
        Vec3 back = from.subtract(hit.getLocation());
        return back.lengthSqr() < 0.1 ? hit.getLocation() : hit.getLocation().add(back.normalize().scale(0.3));
    }

    static CameraRig.Shot shot(ClientCast cast, Camera camera, float partialTick, double baseFov) {
        Scene scene = scene(cast);
        if (scene == null) {
            return null;
        }
        Vec3 normal = camera.getPosition();
        Vec3 look = normal.add(Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).scale(10));
        return ProjectionCamera.shot(cast.time(partialTick), cast.realTime(partialTick), scene.stage, Projection::clip, normal, look, baseFov);
    }

    static double roll(ClientCast cast, float partialTick) {
        Scene scene = scene(cast);
        return scene == null ? 0 : ProjectionCamera.rollAt(cast.time(partialTick), cast.realTime(partialTick), scene.stage);
    }

    static double fov(ClientCast cast, float partialTick, double base) {
        Scene scene = scene(cast);
        return scene == null ? base : ProjectionCamera.fovAt(cast.time(partialTick), base, scene.stage);
    }

private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(Rotasutils.id("projection." + name));
    }

    private static final SoundEvent CLICK = event("click"), CLICK_HARD = event("click_hard"), TICK = event("tick"),
            WHOOSH = event("whoosh"), WHOOSH_HEAVY = event("whoosh_heavy"), CRACK = event("crack"), SHATTER_SOUND = event("shatter"),
            BOOM_SOUND = event("boom"), HIT = event("hit"), SONIC = event("sonic");

    private static void play(SoundEvent sound, Vec3 at, float volume, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null && at != null) {
            level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    private interface Where {
        Vec3 of(ProjectionStage stage);
    }

    private static void at(Timeline<ClientCast> tl, double time, String name, SoundEvent sound, double volume, double pitch, Where where) {
        tl.at(time, name, cast -> {
            Scene scene = scene(cast);
            if (scene != null) {
                play(sound, where.of(scene.stage).add(0, 1, 0), (float) volume, (float) pitch);
            }
        });
    }

    private static void flinch(ClientCast cast) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(cast.targetEntityId) instanceof LivingEntity target) {
            target.hurtDuration = 10;
            target.hurtTime = 10;
        }
    }

    static Timeline<ClientCast> timeline() {
        Timeline<ClientCast> tl = new Timeline<>();
        at(tl, CLICK_1, "click", CLICK, 0.7, 1.0, s -> s.start);
        at(tl, CLICK_2, "click", CLICK, 0.8, 0.94, s -> s.start);
        for (int i = 0; i < ProjectionStage.FRAMES; i++) {
            final int cell = i;
            at(tl, ProjectionStage.cellTime(i), "cell", TICK, 0.45, 0.9 + 0.025 * i, s -> s.cell(cell));
        }
        at(tl, VANISH, "wipe", CLICK, 1.0, 0.8, s -> s.start);
        at(tl, DASH, "dash", WHOOSH, 0.8, 1.15, s -> s.start);
        tl.at(TOUCH, "hush", Projection::hush);
        at(tl, TOUCH, "freeze", CLICK_HARD, 1.3, 1.0, s -> s.target);
        at(tl, GONE, "gone", WHOOSH_HEAVY, 1.2, 1.0, s -> s.far);
        int n = 0;
        for (double time = CONTACT; time < SHATTER - 0.05; time += 0.2 - 0.025 * n, n++) {
            double grow = Curves.window(time, CONTACT, SHATTER);
            at(tl, time, "crack", CRACK, 0.45 + 0.7 * grow, 1.15 - 0.3 * grow, s -> s.contact.subtract(0, 1, 0));
        }
        at(tl, SHATTER, "shatter", SHATTER_SOUND, 1.5, 1.0, s -> s.contact.subtract(0, 1, 0));
        at(tl, BOOM, "boom", BOOM_SOUND, 2.0, 1.25, s -> s.target);
        tl.at(PUNCH, "flinch", Projection::flinch);

        for (double when : new double[]{UNDER, OVER, SIDE}) {
            at(tl, when - 0.08, "appear", WHOOSH, 0.7, 1.4, s -> s.centre);
            at(tl, when, "hit", HIT, 1.3, 1.0, s -> s.centre);
            tl.at(when, "flinch", Projection::flinch);
        }
        at(tl, TRAP, "trap", CLICK_HARD, 1.2, 1.1, s -> s.trap);

        for (int k = 0; k < ProjectionStage.FRAMES; k++) {
            boolean heavy = k >= 21;
            at(tl, BEATS[k], "beat", HIT, heavy ? 1.4 : 0.7, heavy ? 0.85 : 1.1 + 0.02 * k, s -> s.trap);
            at(tl, BEATS[k], "beat", TICK, 0.5, 0.9 + 0.03 * k, s -> s.trap);
            if (k % 3 == 2) {
                at(tl, BEATS[k], "crack", CRACK, 0.5 + 0.03 * k, 1.1, s -> s.trap);
            }
            if (heavy || k % 4 == 0) {
                tl.at(BEATS[k], "flinch", Projection::flinch);
            }
        }
        at(tl, BREAK, "shatter", SHATTER_SOUND, 1.5, 1.05, s -> s.trap);
        at(tl, BREAK + 0.10, "boom", BOOM_SOUND, 1.6, 1.3, s -> s.trap);

        double[] crashes = {CRASH_1, CRASH_2, CRASH_3}, kicks = {KICK_1, KICK_2};
        for (int k = 0; k < 3; k++) {
            final int surface = k;
            at(tl, crashes[k], "crash", BOOM_SOUND, 1.6, 1.5, s -> s.crash[surface]);
            at(tl, crashes[k], "crash", HIT, 1.4, 0.7, s -> s.crash[surface]);
            tl.at(crashes[k], "flinch", Projection::flinch);
            if (k < 2) {
                at(tl, kicks[k] - 0.10, "appear", WHOOSH, 0.8, 1.3, s -> s.crash[surface]);
                at(tl, kicks[k], "hit", HIT, 1.3, 0.95, s -> s.crash[surface]);
            }
        }

        tl.at(REST, "hush", Projection::hush);
        for (int i = 1; i <= 3; i++) {
            at(tl, STANCE + 0.3 * i, "click", CLICK, 0.6, 1.0 + 0.05 * i, s -> s.far2);
        }
        for (int i = 0; i < PASSES.length; i++) {
            at(tl, PASSES[i], "pass", i < 2 ? WHOOSH : WHOOSH_HEAVY, 0.8 + 0.3 * i, 1.2 - 0.12 * i, s -> s.centre);
        }
        int lap = 0;
        for (double time = LAPS; time < LAPS_END; time += 0.01) {
            if ((int) (ProjectionStage.lapAngle(time) / (Math.PI * 2)) > lap) {
                lap++;
                double grow = Curves.window(time, LAPS, LAPS_END);
                at(tl, time, "lap", WHOOSH_HEAVY, 0.9 + 1.3 * grow, 1.0 - 0.4 * grow, s -> s.centre);
            }
        }
        for (double time = LAPS + 0.4; time < LAPS_END; time += 0.45) {
            double grow = Curves.window(time, LAPS, LAPS_END);
            at(tl, time, "rumble", BOOM_SOUND, 0.4 + 1.0 * grow, 0.55, s -> s.centre);
        }

        at(tl, CHARGE, "sonic", SONIC, 2.2, 1.0, s -> s.far2);
        tl.at(TOUCH_2, "hush", Projection::hush);
        at(tl, TOUCH_2, "freeze", CLICK_HARD, 1.4, 0.95, s -> s.centre);
        for (int i = 0; i < ProjectionStage.FRAMES; i++) {
            final int image = i;
            at(tl, ProjectionStage.ringTime(i), "image", TICK, 0.5, 0.8 + 0.03 * i, s -> s.ring(image));
        }
        at(tl, COLLAPSE, "wipe", CLICK, 1.0, 0.75, s -> s.centre);

        at(tl, REVEAL_2, "click", CLICK, 0.7, 1.0, s -> s.strikeFrom);
        at(tl, FOOT, "foot", HIT, 0.5, 0.6, s -> s.strikeFrom.subtract(0, 1, 0));
        for (int i = 0; i < SHOWS; i++) {
            at(tl, STRIKE + i * SHOW_TIME, "cut", TICK, 0.6, 1.0, s -> s.centre);
        }
        at(tl, FINAL_HIT, "crack", CRACK, 1.2, 0.8, s -> s.centre);
        tl.at(FINAL_HIT, "flinch", Projection::flinch);
        at(tl, BOOM_2, "boom", BOOM_SOUND, 4.0, 0.85, s -> s.centre);
        at(tl, BOOM_2, "boom", SONIC, 2.0, 0.7, s -> s.centre);
        at(tl, BOOM_2 + 0.05, "shatter", SHATTER_SOUND, 1.4, 0.8, s -> s.centre);

        for (double time = WALK + 0.25; time < WALK_END; time += 0.16) {
            at(tl, time, "fragment", TICK, 0.25, 1.3, s -> s.centre);
        }
        at(tl, LAST_CLICK, "last", CLICK, 0.6, 1.1, s -> s.centre);
        at(tl, RESUME, "resume", BOOM_SOUND, 1.6, 1.2, s -> s.centre);
        at(tl, RESUME, "resume", WHOOSH_HEAVY, 1.2, 0.9, s -> s.centre);
        return tl;
    }

    private static void hush(ClientCast cast) {
        if (!cast.local) {
            return;
        }
        var sounds = Minecraft.getInstance().getSoundManager();
        for (SoundSource source : new SoundSource[]{SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.BLOCKS, SoundSource.HOSTILE,
                SoundSource.NEUTRAL, SoundSource.PLAYERS}) {
            sounds.stop(null, source);
        }
    }
}
