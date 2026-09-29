package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.RedTimings;
import net.schwarz.rotasutils.ability.Timeline;

/**
 * One running cast as a client sees it: who cast it, what at, when it began on this client's clock,
 * and, once the server has fired it, where the red mass starts, where it lands and when. Everything the
 * client draws - the pose, the core, the camera, the sounds - is a function of {@link #time}, so it
 * replays the same on every client and stays in step with the server's release.
 */
@Environment(EnvType.CLIENT)
public final class ClientCast {
    public final int casterId;
    public final ResourceLocation ability;
    public final long seed;
    public final Vec3 eye;
    public final Vec3 target;
    public final int targetEntityId;
    /** The client's game time when the start arrived; time 0 of the sequence. */
    public final long startTick;
    /** True when this client's own player is the caster: only then is the camera taken over. */
    public final boolean local;

    /** Set when the server fires the attack: seconds since start, and where it goes. */
    public double releaseAt = -1;
    public Vec3 releaseOrigin;
    public Vec3 impact;
    public int hitEntityId = -1;
    public double travelSeconds;

    public boolean cancelled;
    /** The core's sockets as the last frame drew them, in the world; the camera frames the real hand. */
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

    /** Seconds since the sequence began, at partial tick {@code pt}. */
    public double time(float pt) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : (level.getGameTime() - startTick + pt) / 20.0;
    }

    /** Red Reversal MAX: the same sequence, drawn bigger and crowned with extra layers. */
    public boolean max() {
        return ability.getPath().endsWith("_max");
    }

    /** How much larger than the base Red everything this cast draws is. */
    public double scale() {
        return max() ? 1.8 : 1.0;
    }

    /** Sound stages of the aftermath already played. */
    int aftermath;

    /** How long lingering effects last after impact. */
    public double linger() {
        return max() ? 7.0 : RedTimings.LINGER;
    }

    public boolean released() {
        return releaseAt >= 0 && impact != null;
    }

    /** Seconds since the attack landed (negative while it is still in flight). */
    public double sinceImpact(double t) {
        return released() ? t - (releaseAt + travelSeconds) : -1;
    }

    /** When this cast stops needing to be drawn: after its own end, and after its impact has finished lingering. */
    public double finishedAt() {
        double end = RedTimings.END;
        if (released()) {
            end = Math.max(end, releaseAt + travelSeconds + linger());
        }
        return end + 0.5;
    }

    /** The sockets the camera should frame: the ones drawn last frame if they are recent, else the pose's own. */
    public RedPose.Sockets socketsForCamera(double t, CameraRig.Frame frame) {
        if (live != null && System.nanoTime() - liveNanos < 250_000_000L) {
            return live;
        }
        return RedPose.sockets(RedPose.sample(t), frame.feet(), frame.yawDeg());
    }

    /** Where the fired mass is at {@code t}, or null before it is fired or once it has landed. */
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

    /** Where the mass actually starts (the core at the instant of release), recorded by the renderer. */
    Vec3 liveOrigin;

    /** The caster and target as the camera and the sockets see them. */
    public CameraRig.Frame frame(float pt) {
        var level = Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(casterId);
        Vec3 feet = entity != null ? entity.getPosition(pt) : eye.subtract(0, 1.62, 0);
        return CameraRig.Frame.of(feet, target, released() ? impact : null);
    }
}
