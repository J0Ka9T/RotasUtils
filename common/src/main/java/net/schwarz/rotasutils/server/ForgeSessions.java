package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.core.ForgeTiming;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.entity.RiftFx;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The forge timing game, server side. Starting one only checks that the attempt could be made; nothing
 * is spent until the last strike resolves it through {@link RefineService}, which checks everything
 * again. Each strike is graded from the server's own clock against the marker function in
 * {@link ForgeTiming}, so a client cannot claim a better strike than it made.
 */
public final class ForgeSessions {
    private ForgeSessions() {
    }

    private static final class Session {
        final RefineService.Options options;
        final long start;
        final double[] centers;
        final List<ForgeTiming.Grade> grades = new ArrayList<>();

        Session(RefineService.Options options, long start, double[] centers) {
            this.options = options;
            this.start = start;
            this.centers = centers;
        }
    }

    /** What the last finished game did, shown once as a banner when the bench reopens. */
    private record Finished(long at, RefineService.Outcome outcome, List<ForgeTiming.Grade> grades) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Finished> FINISHED = new ConcurrentHashMap<>();

    public static void clear(UUID player) {
        SESSIONS.remove(player);
    }

    /** Starts a game for the held item, or tells the player why it cannot. */
    public static void begin(ServerPlayer player, RotasData data, RefineService.Options options) {
        RefineService.Quote quote = RefineService.quote(player, data, options, 0);
        String lack = quote.possible() ? RefineService.missing(player, data, options, quote) : quote.message();
        if (!lack.isEmpty()) {
            net.schwarz.rotasutils.network.RotasNetwork.feedback(player, false, lack);
            return;
        }
        long now = player.serverLevel().getGameTime();
        SESSIONS.put(player.getUUID(), new Session(options, now, ForgeTiming.centers(now * 31 + player.getUUID().hashCode())));
        FINISHED.remove(player.getUUID());
    }

    /** One strike. The last one resolves the attempt with the chance the strikes earned. */
    public static void strike(ServerPlayer player, RotasData data) {
        Session session = SESSIONS.get(player.getUUID());
        long now = player.serverLevel().getGameTime();
        if (session == null || now - session.start > ForgeTiming.SESSION_TICKS) {
            SESSIONS.remove(player.getUUID());
            net.schwarz.rotasutils.network.RotasNetwork.feedback(player, false, ThaiText.t("rotasutils.msg.refine.session_gone"));
            return;
        }
        int index = session.grades.size();
        ForgeTiming.Grade grade = ForgeTiming.grade(ForgeTiming.marker(now - session.start), session.centers[index]);
        session.grades.add(grade);
        strikeEffects(player, grade);
        if (session.grades.size() < ForgeTiming.STRIKES) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        RefineService.Outcome outcome = RefineService.refine(player, data, session.options, ForgeTiming.total(session.grades));
        if (!outcome.started()) {
            net.schwarz.rotasutils.network.RotasNetwork.feedback(player, false, outcome.message());
            return;
        }
        FINISHED.put(player.getUUID(), new Finished(now, outcome, List.copyOf(session.grades)));
    }

    private static void strikeEffects(ServerPlayer player, ForgeTiming.Grade grade) {
        ServerLevel world = player.serverLevel();
        BlockPos forge = StationService.find(player, RotasRegistry.REFINE_FORGE.get());
        if (forge == null) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(forge).add(0, 0.9, 0);
        switch (grade) {
            case PERFECT -> {
                RiftFx.send(world, RiftFx.Kind.IMPACT, RiftFx.WHITE, at, 0.8f, 9);
                world.playSound(null, forge, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9f, 1.6f);
            }
            case GOOD -> {
                RiftFx.send(world, RiftFx.Kind.IMPACT, RiftFx.GOLD, at, 0.55f, 8);
                world.playSound(null, forge, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.2f);
            }
            case MISS -> world.playSound(null, forge, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 0.6f);
        }
    }

    /** Everything the screen needs to draw the game, and the banner of the last finished one, once. */
    public static CompoundTag view(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        long now = player.serverLevel().getGameTime();
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && now - session.start <= ForgeTiming.SESSION_TICKS) {
            CompoundTag s = new CompoundTag();
            s.putLong("start", session.start);
            s.putLong("now", now);
            s.putBoolean("enriched", session.options.enriched());
            s.putBoolean("protection", session.options.protection());
            s.putBoolean("blessing", session.options.blessing());
            s.putBoolean("certificate", session.options.certificate());
            ListTag centers = new ListTag();
            for (double c : session.centers) {
                centers.add(DoubleTag.valueOf(c));
            }
            s.put("centers", centers);
            s.put("grades", grades(session.grades));
            s.putDouble("bonus", ForgeTiming.total(session.grades));
            tag.put("session", s);
        }
        Finished finished = FINISHED.remove(player.getUUID());
        if (finished != null && now - finished.at() < 200) {
            CompoundTag f = new CompoundTag();
            f.putString("result", finished.outcome().result().name());
            f.putInt("level", finished.outcome().level());
            f.put("grades", grades(finished.grades()));
            tag.put("finished", f);
        }
        return tag;
    }

    private static ListTag grades(List<ForgeTiming.Grade> grades) {
        ListTag list = new ListTag();
        for (ForgeTiming.Grade grade : grades) {
            list.add(IntTag.valueOf(grade.ordinal()));
        }
        return list;
    }
}
