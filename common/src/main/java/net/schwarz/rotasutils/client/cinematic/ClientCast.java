package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.ProjectionTimings;
import net.schwarz.rotasutils.ability.PurpleTimings;
import net.schwarz.rotasutils.ability.RedTimings;
import net.schwarz.rotasutils.ability.StargunTimings;
import net.schwarz.rotasutils.ability.Timeline;

@Environment(EnvType.CLIENT)
public final class ClientCast {
    public final int casterId;
    public final ResourceLocation ability;
    public final long seed;
    public final Vec3 eye;
    public final Vec3 target;
    public final int targetEntityId;
    public final long startTick;
    public final boolean local;

    public double releaseAt = -1;
    public Vec3 releaseOrigin;
    public Vec3 impact;
    public int hitEntityId = -1;
    public Vec3 dissolveFocus;
    public double travelSeconds;

    public boolean cancelled;
    RedPose.Sockets live;
    long liveNanos;
    boolean impactHandled;
    double previous = -1;
    final Timeline<ClientCast> timeline;

    ClientCast(int casterId, ResourceLocation ability, long seed, Vec3 eye, Vec3 target, int targetEntityId, long startTick,
               boolean local, Timeline<ClientCast> timeline) {
        this.casterId = casterId;
        this.ability = ability;
        this.seed = seed;
        this.eye = eye;
        this.target = target;
        this.targetEntityId = targetEntityId;
        this.startTick = startTick;
        this.local = local;
        this.timeline = timeline;
    }

    public double realTime(float pt) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : (level.getGameTime() - startTick + pt) / 20.0;
    }

    public double time(float pt) {
        double real = realTime(pt);
        return projection() ? ProjectionTimings.film(real) : real;
    }

    public boolean max() {
        return ability.getPath().endsWith("_max");
    }

    public double scale() {
        return max() ? 1.8 : 1.0;
    }

    int aftermath;

    public boolean purple() {
        return ability.getPath().equals("hollow_purple");
    }

    public boolean projection() {
        return ability.getPath().equals("projection_sorcery");
    }

    public boolean stargun() {
        return ability.getPath().equals("annihilator_stargun");
    }

    Projection.Scene scene;

    Stargun.Scene stargunScene;

    public double releaseSeconds() {
        if (projection() || stargun()) {
            return Double.MAX_VALUE;
        }
        return purple() ? PurpleTimings.RELEASE : RedTimings.RELEASE;
    }

    public double endSeconds() {
        if (projection()) {
            return ProjectionTimings.END - 0.2;
        }
        if (stargun()) {
            return StargunTimings.END - 0.2;
        }
        if (!purple()) {
            return RedTimings.END;
        }
        return released() ? releaseAt + travelSeconds + PurpleTimings.TAIL : PurpleTimings.RELEASE + 3.0;
    }

    public double linger() {
        return purple() ? PurpleTimings.LINGER : max() ? 7.0 : RedTimings.LINGER;
    }

    public boolean released() {
        return releaseAt >= 0 && impact != null;
    }

    public double sinceImpact(double t) {
        return released() ? t - (releaseAt + travelSeconds) : -1;
    }

    public double finishedAt() {
        if (stargun() && !cancelled) {
            return StargunAtmosphere.AFTERGLOW_END;
        }
        double end = endSeconds();
        if (released()) {
            end = Math.max(end, releaseAt + travelSeconds + linger());
        }
        return end + 0.5;
    }

    public RedPose.Sockets socketsForCamera(double t, CameraRig.Frame frame) {
        if (live != null && System.nanoTime() - liveNanos < 250_000_000L) {
            return live;
        }
        return RedPose.sockets(RedPose.sample(t), frame.feet(), frame.yawDeg());
    }

    public Vec3 followPoint(double t) {
        if (!released() || releaseOrigin == null) {
            return null;
        }
        double dt = t - releaseAt;
        if (dt < 0) {
            return null;
        }
        double travel = Math.max(0.05, travelSeconds);
        double launch = Math.min(0.10, travel * 0.4);
        double u = Curves.clamp01((dt - launch) / (travel - launch));
        Vec3 origin = liveOrigin != null ? liveOrigin : releaseOrigin;
        return origin.add(impact.subtract(origin).scale(Math.pow(u, 1.5)));
    }

    Vec3 liveOrigin;

    public CameraRig.Frame frame(float pt) {
        var level = Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(casterId);
        Vec3 feet = entity != null ? entity.getPosition(pt) : eye.subtract(0, 1.62, 0);
        return CameraRig.Frame.of(feet, target, released() ? impact : null);
    }
}
