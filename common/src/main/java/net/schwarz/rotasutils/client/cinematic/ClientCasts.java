package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.AbilityNet;
import net.schwarz.rotasutils.ability.RedTimings;
import net.schwarz.rotasutils.ability.Timeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The client's book of running casts, fed by the server's start, release and end packets, and the
 * clock that plays each one's client timeline: sounds, and the moments that only the presentation cares
 * about. Layers of sound are built from short one-shots that stop at the hold, so the world can go quiet
 * right before the release, which is what makes the release feel large.
 */
@Environment(EnvType.CLIENT)
public final class ClientCasts {
    private ClientCasts() {
    }

    private static final Map<Integer, ClientCast> CASTS = new HashMap<>();

    public static Collection<ClientCast> all() {
        return CASTS.values();
    }

    public static ClientCast forCaster(int id) {
        return CASTS.get(id);
    }

    /** The cast whose caster is this client's own player, while it still owns the camera. */
    public static ClientCast local() {
        for (ClientCast cast : CASTS.values()) {
            if (cast.local && !cast.cancelled && cast.time(0) < RedTimings.END) {
                return cast;
            }
        }
        return null;
    }

    public static void clear() {
        CASTS.clear();
        CastPostFx.reset();
        HandCapture.clear();
        restoreGui(Minecraft.getInstance());
    }

    /** The game's interface is hidden while the player's own cutscene plays, and put back exactly as it was. */
    private static boolean guiHidden;

    private static void syncGui(Minecraft mc) {
        boolean wants = local() != null;
        if (wants && !guiHidden && !mc.options.hideGui) {
            mc.options.hideGui = true;
            guiHidden = true;
        } else if (!wants && guiHidden) {
            restoreGui(mc);
        }
    }

    private static void restoreGui(Minecraft mc) {
        if (guiHidden) {
            mc.options.hideGui = false;
            guiHidden = false;
        }
    }

    // Packets ----------------------------------------------------------------------------------------

    public static void receiveStart(FriendlyByteBuf buf, Consumer<Runnable> queue) {
        ResourceLocation ability = buf.readResourceLocation();
        int caster = buf.readVarInt();
        long seed = buf.readLong();
        Vec3 eye = AbilityNet.readVec(buf);
        Vec3 target = AbilityNet.readVec(buf);
        int targetEntity = buf.readVarInt() - 1;
        queue.accept(() -> start(ability, caster, seed, eye, target, targetEntity));
    }

    public static void receiveRelease(FriendlyByteBuf buf, Consumer<Runnable> queue) {
        int caster = buf.readVarInt();
        Vec3 origin = AbilityNet.readVec(buf);
        Vec3 impact = AbilityNet.readVec(buf);
        int hit = buf.readVarInt() - 1;
        int travel = buf.readVarInt();
        queue.accept(() -> release(caster, origin, impact, hit, travel));
    }

    public static void receiveEnd(FriendlyByteBuf buf, Consumer<Runnable> queue) {
        int caster = buf.readVarInt();
        boolean completed = buf.readBoolean();
        queue.accept(() -> end(caster, completed));
    }

    private static void start(ResourceLocation ability, int casterId, long seed, Vec3 eye, Vec3 target, int targetEntity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        boolean local = mc.player != null && mc.player.getId() == casterId;
        ClientCast cast = new ClientCast(casterId, ability, seed, eye, target, targetEntity, mc.level.getGameTime(), local,
                buildTimeline(ability.getPath().endsWith("_max")));
        CASTS.put(casterId, cast);
    }

    private static void release(int caster, Vec3 origin, Vec3 impact, int hit, int travelTicks) {
        ClientCast cast = CASTS.get(caster);
        if (cast == null) {
            return;
        }
        cast.releaseAt = Math.max(RedTimings.RELEASE, cast.time(0));
        cast.releaseOrigin = origin;
        cast.impact = impact;
        cast.hitEntityId = hit;
        cast.travelSeconds = travelTicks / 20.0;
    }

    private static void end(int caster, boolean completed) {
        ClientCast cast = CASTS.get(caster);
        if (cast != null && !completed) {
            cast.cancelled = true;
            if (!cast.released()) {
                CASTS.remove(caster);
            }
        }
    }

    // Tick -------------------------------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            if (!CASTS.isEmpty()) {
                clear();
            }
            return;
        }
        syncGui(mc);
        if (CASTS.isEmpty() || mc.isPaused()) {
            return;
        }
        for (Iterator<ClientCast> it = CASTS.values().iterator(); it.hasNext(); ) {
            ClientCast cast = it.next();
            double t = cast.time(0);
            if (t > cast.finishedAt()) {
                it.remove();
                continue;
            }
            if (!cast.cancelled) {
                cast.timeline.advance(cast.previous, t, cast);
                cast.previous = t;
            }
            if (cast.released() && !cast.impactHandled && t >= cast.releaseAt + cast.travelSeconds) {
                cast.impactHandled = true;
                impactSounds(mc, cast);
            }
            faceTarget(mc, cast, t);
        }
    }

    /** Keeps the caster's body turned to the target for the whole sequence, whatever the player's mouse does. */
    private static void faceTarget(Minecraft mc, ClientCast cast, double t) {
        if (cast.cancelled || t > RedTimings.END) {
            return;
        }
        if (mc.level.getEntity(cast.casterId) instanceof LivingEntity e) {
            float yaw = (float) cast.frame(0).yawDeg();
            e.yBodyRot = yaw;
            e.yBodyRotO = yaw;
            e.yHeadRot = yaw;
            e.yHeadRotO = yaw;
            e.setYRot(yaw);
            e.yRotO = yaw;
        }
    }

    // Sound ------------------------------------------------------------------------------------------

    private static void play(ClientCast cast, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 at = RedPose.sockets(RedPose.sample(cast.time(0)), cast.frame(0).feet(), cast.frame(0).yawDeg()).core();
        mc.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static Timeline<ClientCast> buildTimeline(boolean max) {
        Timeline<ClientCast> tl = new Timeline<>();
        if (max) {
            // MAX: a choir-like swell as the sigil opens, a bell tolling the hold, a roar on release.
            tl.at(RedTimings.CORE_FORMS, "sigil", c -> play(c, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.5f));
            tl.at(RedTimings.CLOSE_UP, "swell", c -> play(c, SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.7f));
            tl.at(RedTimings.HOLD, "toll", c -> play(c, SoundEvents.BELL_BLOCK, 1.6f, 0.5f));
            tl.at(RedTimings.RELEASE, "roar", c -> play(c, SoundEvents.WITHER_SPAWN, 0.8f, 0.7f));
        }
        tl.at(RedTimings.CORE_FORMS, "core", c -> play(c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5f, 0.6f));
        // Charging: a low rumble whose pulses match the core's compressions, a thin electrical shimmer, a suction swell.
        for (double s = RedTimings.CHARGE_SOUND; s < RedTimings.HOLD - 0.1; s += 0.55) {
            final double at = s;
            tl.at(s, "rumble", c -> {
                float grow = (float) Curves.window(at, RedTimings.CHARGE_SOUND, RedTimings.HOLD);
                play(c, SoundEvents.WARDEN_HEARTBEAT, 0.7f + 0.5f * grow, 0.5f + 0.15f * grow);
                play(c, SoundEvents.BEACON_POWER_SELECT, 0.12f + 0.15f * grow, 1.9f + 0.4f * grow);
            });
        }
        tl.at(RedTimings.CHARGE_SOUND, "suction", c -> play(c, SoundEvents.BEACON_AMBIENT, 0.6f, 0.5f));
        for (double s = RedTimings.DEBRIS; s < RedTimings.HOLD - 0.1; s += 0.45) {
            tl.at(s, "debris", c -> play(c, SoundEvents.GRAVEL_STEP, 0.35f, 0.7f));
        }
        // The hold: everything thins to one faint tone.
        tl.at(RedTimings.HOLD, "tone", c -> play(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35f, 2.0f));
        // The release: a hard crack, a deep hit, an explosion and a long tail, all on the same instant.
        tl.at(RedTimings.RELEASE, "crack", c -> {
            play(c, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0f, 1.7f);
            play(c, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.55f);
            play(c, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.3f, 0.6f);
            play(c, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.1f);
        });
        tl.at(RedTimings.RELEASE + 0.35, "tail", c -> play(c, SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS.value(), 1.0f, 0.6f));
        return tl;
    }

    private static void impactSounds(Minecraft mc, ClientCast cast) {
        Vec3 at = cast.impact;
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.75f, false);
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 1.3f, false);
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 2.0f, 0.6f, false);
        if (cast.max()) {
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.4f, false);
        }
    }

    /** Extra names for tools and tests that want the list without the map. */
    public static List<ClientCast> snapshot() {
        return new ArrayList<>(CASTS.values());
    }
}
