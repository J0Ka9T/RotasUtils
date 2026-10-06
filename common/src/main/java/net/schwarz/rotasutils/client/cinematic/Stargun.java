package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.ability.StargunTimings;
import net.schwarz.rotasutils.ability.Timeline;

import java.util.Random;

import static net.schwarz.rotasutils.ability.StargunTimings.*;

@Environment(EnvType.CLIENT)
public final class Stargun {
    private Stargun() {
    }

static final class Scene {
        private static final Vec3 UP = new Vec3(0, 1, 0);

        final Vec3 centre, caster, fwd, back, side;
        final Vec3 portal, axis, pu, pw;
        final Vec3 muzzle, breech, gunCentre, reactor;
        final Vec3 planet;
        final Vec3 view, vu, vw;
        final double planetRadius = 110;
        final Vec3[] islands = new Vec3[8];
        final double[] islandSize = new double[8];
        final Vec3[] stars = new Vec3[720];
        final double[] starSize = new double[720];
        static final int ASTEROIDS = 900;
        static final double WRAP = 900;
        final Vec3 flow;
        final Vec3[] astBase = new Vec3[ASTEROIDS], astAxis = new Vec3[ASTEROIDS];
        final double[] astStart = new double[ASTEROIDS], astSpeed = new double[ASTEROIDS], astSize = new double[ASTEROIDS], astSpin = new double[ASTEROIDS];
        final long seed;
        final double rayLength;

        Scene(Vec3 centre, Vec3 caster, long seed) {
            this.centre = centre;
            this.caster = caster;
            this.seed = seed;
            Vec3 flat = new Vec3(caster.x - centre.x, 0, caster.z - centre.z);
            this.back = flat.lengthSqr() < 1.0 ? new Vec3(0, 0, 1) : flat.normalize();
            this.fwd = back.scale(-1);
            this.side = new Vec3(-fwd.z, 0, fwd.x);
            this.portal = centre.add(back.scale(-PORTAL_BACK)).add(0, PORTAL_HEIGHT, 0);
            this.axis = centre.subtract(portal).normalize();
            this.pu = UP.cross(axis).normalize();
            this.pw = axis.cross(pu).normalize();
            this.muzzle = portal.add(axis.scale(GUN_OUT));
            this.breech = muzzle.subtract(axis.scale(140));
            this.gunCentre = muzzle.subtract(axis.scale(70));
            this.reactor = muzzle.subtract(axis.scale(102));
            this.rayLength = centre.distanceTo(muzzle);
            Vec3 eye = caster.add(0, 1.6, 0);
            this.view = portal.subtract(eye).normalize();
            this.vu = UP.cross(view).normalize();
            this.vw = view.cross(vu).normalize();
            double distance = portal.distanceTo(eye);
            this.planet = seen(distance, 300, 0.30, 0.55);
            Random r = new Random(seed);
            for (int i = 0; i < islands.length; i++) {
                double depth = 90 + r.nextDouble() * 210, angle = r.nextDouble() * Math.PI * 2, out = 0.15 + r.nextDouble() * 0.6;
                islands[i] = i < 5 ? seen(distance, depth, out, angle)
                        : portal.subtract(axis.scale(depth)).add(pu.scale((r.nextDouble() * 2 - 1) * (40 + depth * 0.25)))
                        .add(pw.scale((r.nextDouble() * 2 - 1) * (25 + depth * 0.18)));
                islandSize[i] = 8 + r.nextDouble() * 10;
            }
            for (int i = 0; i < stars.length; i++) {
                double depth = 70 + r.nextDouble() * 330, angle = r.nextDouble() * Math.PI * 2, out = r.nextDouble() * 0.85;
                if (i % 5 < 2) {
                    stars[i] = seen(distance, depth, out, angle);
                } else if (i % 5 == 2) {
                    stars[i] = portal.subtract(axis.scale(depth)).add(pu.scale((r.nextDouble() * 2 - 1) * (55 + depth * 0.4)))
                            .add(pw.scale((r.nextDouble() * 2 - 1) * (40 + depth * 0.3)));
                } else {
                    double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(1 - u * u);
                    stars[i] = portal.add(new Vec3(Math.cos(a) * rr, u, Math.sin(a) * rr).scale(380 + r.nextDouble() * 160));
                }
                starSize[i] = 0.5 + r.nextDouble() * 1.6;
            }
            this.flow = vu.scale(0.93).add(view.scale(-0.22)).add(vw.scale(0.12)).normalize();
            for (int i = 0; i < ASTEROIDS; i++) {
                double d = 14 + Math.pow(r.nextDouble(), 0.9) * 480, half = 60 + 0.55 * d;
                Vec3 p = portal.add(view.scale(d)).add(vu.scale((r.nextDouble() * 2 - 1) * half)).add(vw.scale((r.nextDouble() * 2 - 1) * half * 0.55));
                double along = p.subtract(portal).dot(flow);
                astBase[i] = p.subtract(flow.scale(along));
                astStart[i] = (r.nextDouble() - 0.5) * WRAP;
                astSpeed[i] = 4 + r.nextDouble() * 10;
                double u = r.nextDouble();
                astSize[i] = u < 0.62 ? 4 + r.nextDouble() * 7 : u < 0.92 ? 12 + r.nextDouble() * 16 : 30 + r.nextDouble() * 40;
                astSpin[i] = (r.nextDouble() - 0.5) * 0.9;
                astAxis[i] = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
            }
        }

        double astOffset(int i, double t) {
            double o = astStart[i] + astSpeed[i] * t + WRAP / 2;
            return o - Math.floor(o / WRAP) * WRAP - WRAP / 2;
        }

        Vec3 asteroidAt(int i, double t) {
            return portal.add(astBase[i]).add(flow.scale(astOffset(i, t)));
        }

        double astFade(int i, double t) {
            return Math.min(1, (WRAP / 2 - Math.abs(astOffset(i, t))) / 90);
        }

        private Vec3 seen(double distance, double depth, double out, double angle) {
            double cone = PORTAL_RADIUS * (distance + depth) / distance * out;
            return portal.add(view.scale(depth)).add(vu.scale(Math.cos(angle) * cone)).add(vw.scale(Math.sin(angle) * cone));
        }

        Vec3 muzzleAt(double t) {
            double settle = Curves.smoothstep(Curves.window(t, StargunTimings.EMERGE_END - 1.0, StargunTimings.EMERGE_END + 3.0));
            double recoil = t < FIRE ? 0 : 20 * Math.exp(-(t - FIRE) / 0.9) * (1 - Math.exp(-(t - FIRE) / 0.05));
            Vec3 sway = new Vec3(Math.sin(t * 0.55) * 1.2 * settle, Math.sin(t * 0.8) * 1.6 * settle, Math.cos(t * 0.47) * 1.2 * settle);
            return portal.add(axis.scale(StargunTimings.muzzleOffset(t) - recoil)).add(sway);
        }

        Vec3 gunAt(double t, double back) {
            return muzzleAt(t).subtract(axis.scale(back));
        }

        Vec3 sun() {
            return portal.add(view.scale(360)).add(vu.scale(-130)).add(vw.scale(70));
        }

        Vec3 hand() {
            return caster.add(fwd.scale(0.7)).add(0, 2.1, 0);
        }

        Vec3 signal(double t) {
            double u = Curves.window(t, SIGNAL, RIFT);
            Vec3 from = hand();
            return from.add(portal.subtract(from).scale(u * u));
        }

        Vec3 onRay(double u) {
            return muzzle.add(axis.scale(rayLength * u));
        }
    }

    static Scene scene(ClientCast cast) {
        if (cast.stargunScene == null) {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return null;
            }
            Entity caster = level.getEntity(cast.casterId);
            Vec3 feet = caster != null ? caster.position() : cast.eye.subtract(0, 1.62, 0);
            cast.stargunScene = new Scene(cast.target, feet, cast.seed);
        }
        return cast.stargunScene;
    }

public static double dissolveProgress(Entity entity, float partialTick) {
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            return 0;
        }
        double best = 0;
        for (ClientCast cast : ClientCasts.all()) {
            if (!cast.stargun() || cast.cancelled) {
                continue;
            }
            double t = cast.time(partialTick);
            if (t < IMPACT || t >= END) {
                continue;
            }
            double p = entityProgress(entity.getBoundingBox().getCenter().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(net.minecraft.core.BlockPos.containing(cast.target))), t);
            best = Math.max(best, p);
        }
        return best;
    }

    public static double duskAmount(float partialTick) {
        double best = 0;
        for (ClientCast cast : ClientCasts.all()) {
            if (cast.stargun() && !cast.cancelled) {
                best = Math.max(best, StargunVfxRenderer.dusk(cast.time(partialTick)));
            }
        }
        return best;
    }

    public static boolean hidesClouds(float partialTick) {
        return duskAmount(partialTick) > 0.12;
    }

private static final class Sounds {
        private static SoundEvent event(String name) {
            return SoundEvent.createVariableRangeEvent(Rotasutils.id("stargun." + name));
        }

        static final SoundEvent RIFT_SOUND = event("rift"), HUM = event("hum"), CHARGE_SOUND = event("charge"), FIRE_SOUND = event("fire"),
                BEAM = event("beam"), DISSOLVE_SOUND = event("dissolve"), RUMBLE = event("rumble"), CLOSE_SOUND = event("close");
    }

    private static void play(ClientCast cast, SoundEvent sound, double volume, double pitch) {
        var level = Minecraft.getInstance().level;
        var mc = Minecraft.getInstance();
        if (level == null || mc.player == null) {
            return;
        }
        Vec3 at = mc.player.position().add(0, 1, 0);
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, (float) volume, (float) pitch, false);
    }

    static Timeline<ClientCast> timeline() {
        Timeline<ClientCast> tl = new Timeline<>();
        tl.at(0.3, "hush", c -> play(c, Sounds.RUMBLE, 0.5, 1.4));
        tl.at(SIGNAL, "signal", c -> play(c, Sounds.FIRE_SOUND, 0.7, 1.9));
        tl.at(RIFT, "rift", c -> play(c, Sounds.RIFT_SOUND, 2.0, 1.0));
        tl.at(EMERGE, "emerge", c -> {
            play(c, Sounds.RUMBLE, 2.2, 0.55);
            play(c, Sounds.HUM, 1.6, 0.6);
        });
        tl.at(FIRE - 1.6, "inhale", c -> play(c, Sounds.CHARGE_SOUND, 1.4, 1.5));
        for (double t = RIFT + 2; t < RAY_GONE; t += 8) {
            tl.at(t, "hum", c -> play(c, Sounds.HUM, 1.0, 1.0));
        }
        for (double t = CHARGE; t < FIRE - 1; t += 3.2) {
            final double at = t;
            tl.at(t, "charge", c -> play(c, Sounds.CHARGE_SOUND, 1.0 + 0.6 * charge(at), 0.8 + 0.6 * charge(at)));
        }
        tl.at(FIRE - 0.05, "fire", c -> play(c, Sounds.FIRE_SOUND, 3.0, 1.0));
        for (double t = FIRE + 1; t < RAY_FADE; t += 6) {
            tl.at(t, "beam", c -> play(c, Sounds.BEAM, 1.4, 1.0));
        }
        tl.at(IMPACT + 0.35, "impact", c -> {
            play(c, Sounds.FIRE_SOUND, 3.5, 0.55);
            play(c, Sounds.RUMBLE, 3.0, 0.7);
        });
        for (double t = IMPACT + 3; t < FRONT_END; t += 5) {
            tl.at(t, "dissolve", c -> play(c, Sounds.DISSOLVE_SOUND, 1.6, 1.0));
        }
        for (double t = IMPACT + 6; t < FRONT_END; t += 7) {
            tl.at(t, "rumble", c -> play(c, Sounds.RUMBLE, 1.6, 0.8));
        }
        tl.at(RAY_FADE, "fade", c -> play(c, Sounds.CLOSE_SOUND, 1.4, 0.8));
        tl.at(RAY_GONE, "close", c -> play(c, Sounds.CLOSE_SOUND, 2.0, 1.0));
        return tl;
    }
}
