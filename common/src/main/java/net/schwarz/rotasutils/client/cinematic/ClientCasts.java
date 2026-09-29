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
import net.schwarz.rotasutils.ability.PurpleTimings;
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
            if (cast.local && !cast.cancelled && cast.time(0) < cast.endSeconds()) {
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
                ability.getPath().equals("hollow_purple") ? buildPurpleTimeline()
                        : buildTimeline(ability.getPath().endsWith("_max")));
        CASTS.put(casterId, cast);
    }

    private static void release(int caster, Vec3 origin, Vec3 impact, int hit, int travelTicks) {
        ClientCast cast = CASTS.get(caster);
        if (cast == null) {
            return;
        }
        cast.releaseAt = Math.max(cast.releaseSeconds(), cast.time(0));
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
            if ((cast.max() || cast.purple()) && cast.impactHandled) {
                aftermath(mc, cast, cast.sinceImpact(t));
            }
            faceTarget(mc, cast, t);
        }
    }

    /** Keeps the caster's body turned to the target for the whole sequence, whatever the player's mouse does. */
    private static void faceTarget(Minecraft mc, ClientCast cast, double t) {
        if (cast.cancelled || t > cast.endSeconds()) {
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
        Vec3 at = cast.purple() ? cast.frame(0).feet().add(0, 1.3, 0)
                : RedPose.sockets(RedPose.sample(cast.time(0)), cast.frame(0).feet(), cast.frame(0).yawDeg()).core();
        mc.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static Timeline<ClientCast> buildTimeline(boolean max) {
        Timeline<ClientCast> tl = new Timeline<>();
        if (max) {
            // MAX: a low swell as the point forms and builds, silence in the collapse, a roar on release.
            tl.at(RedTimings.CORE_FORMS, "seed", c -> play(c, SoundEvents.BEACON_ACTIVATE, 0.6f, 0.4f));
            tl.at(RedTimings.CLOSE_UP, "swell", c -> play(c, SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.7f));
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

    /** Silence, Blue, Red, the forces, the collapse, the hush, Purple's birth, the stillness, the release. */
    private static Timeline<ClientCast> buildPurpleTimeline() {
        Timeline<ClientCast> tl = new Timeline<>();
        double silence = PurpleTimings.SILENCE;
        tl.at(0.2, "hush", c -> play(c, SoundEvents.AMBIENT_CAVE.value(), 0.5f, 0.5f));
        tl.at(PurpleTimings.BLUE, "blue", c -> {
            play(c, SoundEvents.BEACON_AMBIENT, 0.6f, 0.45f);
            play(c, SoundEvents.PORTAL_AMBIENT, 0.5f, 0.5f);
        });
        tl.at(PurpleTimings.RED, "red", c -> play(c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5f, 0.55f));
        // Two heartbeats of opposing forces, growing, and a warping tone that turns more unnatural.
        for (double s = 2.6; s < silence - 0.2; s += 0.8) {
            final double at = s;
            tl.at(s, "rumble", c -> {
                float grow = (float) Curves.window(at, 2.6, silence);
                play(c, SoundEvents.WARDEN_HEARTBEAT, 0.5f + 0.6f * grow, 0.5f + 0.1f * grow);
                play(c, SoundEvents.BEACON_POWER_SELECT, 0.10f + 0.15f * grow, 1.6f + 0.5f * grow);
            });
        }
        for (double s = 5.0; s < silence - 0.3; s += 1.5) {
            final double at = s;
            tl.at(s, "warp", c -> play(c, SoundEvents.PORTAL_AMBIENT, 0.6f, 0.9f - 0.12f * (float) (at - 5.0)));
        }
        for (double s = PurpleTimings.SPARKS; s < PurpleTimings.COLLAPSE; s += 0.35) {
            final double at = s;
            tl.at(s, "spark", c -> play(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.3f, 1.4f + 0.3f * (float) (at - PurpleTimings.SPARKS)));
        }
        tl.at(PurpleTimings.COLLAPSE, "fall", c -> play(c, SoundEvents.END_PORTAL_SPAWN, 0.5f, 0.5f));
        // The hush: nothing from the silence on but one thin tone on the spark.
        tl.at(PurpleTimings.POINT, "tone", c -> play(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.25f, 2.0f));
        tl.at(PurpleTimings.BORN, "born", c -> {
            play(c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.6f);
            play(c, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
            play(c, SoundEvents.END_PORTAL_SPAWN, 0.8f, 1.4f);
            play(c, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.5f);
        });
        // Each pulse of the finished sphere is a deep bass hit; then it stops for the stillness.
        for (double s = PurpleTimings.BORN + 0.5; s < PurpleTimings.STABLE - 0.4; s += 1.25) {
            tl.at(s, "pulse", c -> {
                play(c, SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.4f);
                play(c, SoundEvents.BEACON_AMBIENT, 0.5f, 0.4f);
            });
        }
        tl.at(PurpleTimings.COMPRESS, "squeeze", c -> play(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.3f, 0.7f));
        tl.at(PurpleTimings.RELEASE, "fire", c -> {
            play(c, SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 0.5f);
            play(c, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0f, 0.8f);
            play(c, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.5f);
            play(c, SoundEvents.END_PORTAL_SPAWN, 1.2f, 0.5f);
            play(c, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.2f, 0.6f);
        });
        return tl;
    }

    private static void impactSounds(Minecraft mc, ClientCast cast) {
        Vec3 at = cast.impact;
        if (cast.purple()) {
            // The world was silent for the compression; now a section of it is erased.
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.4f, false);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.5f, 0.5f, false);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.5f, false);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0f, 0.4f, false);
            return;
        }
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.75f, false);
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 1.3f, false);
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 2.0f, 0.6f, false);
        if (cast.max()) {
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.4f, false);
        }
    }

    private static final double[] AFTERMATH_AT = {0.30, 0.70, 1.60, 2.80};
    /** Hollow Purple: only a deep, distant rumble, then silence. */
    private static final double[] PURPLE_AFTERMATH_AT = {0.9, 2.2, 3.8, 5.2};

    /** MAX: the second pulse, then a deep rumble that fades over a few seconds. */
    private static void aftermath(Minecraft mc, ClientCast cast, double sinceImpact) {
        double[] table = cast.purple() ? PURPLE_AFTERMATH_AT : AFTERMATH_AT;
        if (cast.aftermath >= table.length || sinceImpact < table[cast.aftermath]) {
            return;
        }
        int stage = cast.aftermath++;
        Vec3 at = cast.impact;
        if (stage == 0 && !cast.purple()) {
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.2f, false);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 1.9f, false);
        } else {
            float v = cast.purple() ? 2.6f - 0.6f * stage : 2.2f - 0.5f * stage;
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS.value(), SoundSource.PLAYERS, v, 0.5f, false);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, v * 0.7f, 0.5f, false);
        }
    }

    /** Extra names for tools and tests that want the list without the map. */
    public static List<ClientCast> snapshot() {
        return new ArrayList<>(CASTS.values());
    }
}
