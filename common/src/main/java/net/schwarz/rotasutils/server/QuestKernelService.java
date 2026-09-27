package net.schwarz.rotasutils.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.KernelContext;
import net.schwarz.rotasutils.core.QuestDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kernel quests. Every mutation is one player-record transaction, so accepting, advancing, claiming
 * and abandoning cannot half-apply, and a conflicting write is rejected rather than merged.
 *
 * <p>Quest state lives in the existing bounded player variable namespace, which means it inherits the
 * transaction, receipt and persistence guarantees the progression phase already proved.
 */
public final class QuestKernelService {
    public enum Result { ACCEPTED, ADVANCED, COMPLETED, CLAIMED, UNAVAILABLE, ALREADY_ACTIVE, NOT_ACTIVE, LIMIT_REACHED }

    /** Active quests per player are bounded so the variable namespace cannot be flooded. */
    public static final int MAX_ACTIVE = 32;
    private final MinecraftServer server;
    private final RpgKernel kernel;
    private long accepted, advanced, completed, claimed, rejected;

    public QuestKernelService(MinecraftServer server, RpgKernel kernel) { this.server = server; this.kernel = kernel; }

    private QuestDefinitions.Quest quest(ContentId id) {
        var quest = kernel.content().quests().get(id);
        if (quest == null) { throw new IllegalArgumentException("Unknown or disabled quest: " + id); }
        return quest;
    }

    public int stage(ServerPlayer player, ContentId id) {
        String value = RotasData.get(server).progress(player.getUUID()).questVariables().get(QuestDefinitions.key(id, "stage"));
        return value == null ? -1 : parse(value, -1);
    }

    public long completedAt(ServerPlayer player, ContentId id) {
        String value = RotasData.get(server).progress(player.getUUID()).questVariables().get(QuestDefinitions.key(id, "done"));
        return value == null ? 0 : parse(value, 0);
    }

    public int progress(ServerPlayer player, ContentId id, String objective) {
        String value = RotasData.get(server).progress(player.getUUID())
                .questVariables().get(QuestDefinitions.key(id, "p." + objective));
        return value == null ? 0 : parse(value, 0);
    }

    /** Quests the player may accept right now. */
    public List<ContentId> available(ServerPlayer player) {
        long now = now();
        var context = new KernelPlayerContext(player, Map.of());
        List<ContentId> result = new ArrayList<>();
        kernel.content().quests().forEach((id, quest) -> {
            if (stage(player, id) >= 0 || !quest.available(completedAt(player, id), now)) { return; }
            try { if (quest.requirement().test(context)) { result.add(id); } }
            catch (RuntimeException failure) { error("quest-requirement:" + id, failure); }
        });
        return List.copyOf(result);
    }

    public Result accept(ServerPlayer player, ContentId id) {
        var quest = quest(id);
        if (stage(player, id) >= 0) { return Result.ALREADY_ACTIVE; }
        long now = now();
        if (!quest.available(completedAt(player, id), now)) { return Result.UNAVAILABLE; }
        var context = new KernelPlayerContext(player, Map.of());
        if (!quest.requirement().test(context)) { return Result.UNAVAILABLE; }
        if (active(player).size() >= MAX_ACTIVE) { return Result.LIMIT_REACHED; }
        try (KernelContext.Transaction transaction = context.begin()) {
            transaction.seed("quest|" + id + "|" + now);
            transaction.variable(QuestDefinitions.key(id, "stage"), "0");
            clearProgress(transaction, quest, 0);
            transaction.commit();
        }
        accepted++;
        return Result.ACCEPTED;
    }

    public Result abandon(ServerPlayer player, ContentId id) {
        var quest = quest(id);
        int stage = stage(player, id);
        if (stage < 0) { return Result.NOT_ACTIVE; }
        var context = new KernelPlayerContext(player, Map.of());
        try (KernelContext.Transaction transaction = context.begin()) {
            transaction.variable(QuestDefinitions.key(id, "stage"), "-1");
            clearProgress(transaction, quest, stage);
            transaction.commit();
        }
        return Result.NOT_ACTIVE;
    }

    /**
     * Applies one kernel event to every active quest of a player. Objectives complete, stage rewards
     * are granted, branches choose the next stage, and the whole step is one transaction per quest.
     */
    public List<Result> handle(ServerPlayer player, ContentId event, String occurrence, Map<String, String> facts) {
        List<Result> results = new ArrayList<>();
        for (var entry : active(player).entrySet()) {
            try { results.add(apply(player, entry.getKey(), entry.getValue(), event, occurrence, facts)); }
            catch (RuntimeException failure) { error("quest:" + entry.getKey(), failure); }
        }
        return List.copyOf(results);
    }

    private Result apply(ServerPlayer player, ContentId id, int stageIndex, ContentId event, String occurrence, Map<String, String> facts) {
        var quest = quest(id);
        if (stageIndex < 0 || stageIndex >= quest.stages().size()) { return Result.NOT_ACTIVE; }
        var stage = quest.stages().get(stageIndex);
        var context = new KernelPlayerContext(player, facts);
        Map<String, Integer> updates = new LinkedHashMap<>();
        boolean touched = false;
        for (var objective : stage.objectives()) {
            int current = progress(player, id, objective.key());
            if (current >= objective.count()) { updates.put(objective.key(), current); continue; }
            if (objective.matches(event, facts) && objective.condition().test(context)) {
                updates.put(objective.key(), Math.min(objective.count(), current + 1));
                touched = true;
            } else {
                updates.put(objective.key(), current);
            }
        }
        if (!touched) { return Result.NOT_ACTIVE; }
        boolean stageDone = stage.objectives().stream().allMatch(objective -> updates.get(objective.key()) >= objective.count());
        int next = stageDone ? stage.next(context, quest.stages().size()) : stageIndex;
        try (KernelContext.Transaction transaction = context.begin()) {
            transaction.seed("quest|" + id + "|" + stageIndex + "|" + occurrence);
            updates.forEach((key, value) -> transaction.variable(QuestDefinitions.key(id, "p." + key), Integer.toString(value)));
            if (stageDone) {
                transaction.variable(QuestDefinitions.key(id, "stage"), Integer.toString(next));
                if (next >= 0) { clearProgress(transaction, quest, next); }
                else { transaction.variable(QuestDefinitions.key(id, "done"), Long.toString(now())); }
            }
            transaction.commit();
        }
        if (!stageDone) { advanced++; return Result.ADVANCED; }
        // Rewards are granted after the state write so a rejected write never pays out.
        if (stage.reward() != null) {
            try { kernel.grant(player, stage.reward(), "quest:" + id + ":" + stageIndex + ":" + windowKey(quest)); }
            catch (RuntimeException failure) { error("quest-stage-reward:" + id, failure); }
        }
        if (next >= 0) { advanced++; return Result.ADVANCED; }
        completed++;
        return Result.COMPLETED;
    }

    /**
     * Claims the completion reward once per reset window. A bounty quest also consumes one of its
     * world-wide slots for the window; a full bounty pays nobody and leaves the claim unclaimed.
     */
    public Result claim(ServerPlayer player, ContentId id) {
        var quest = quest(id);
        if (stage(player, id) != -1 || completedAt(player, id) <= 0) { return Result.NOT_ACTIVE; }
        var data = RotasData.get(server);
        String window = windowKey(quest);
        String receipt = "kernel|quest|" + id + "|" + window;
        var context = new KernelPlayerContext(player, Map.of());
        try (KernelContext.Transaction transaction = context.begin()) {
            transaction.seed(receipt);
            if (transaction.claimed(receipt)) { return Result.UNAVAILABLE; }
            if (quest.bountyLimit() > 0
                    && !data.addCounter("bounty|" + id, quest.window(now()), quest.bountyLimit(), 1)) {
                return Result.LIMIT_REACHED;
            }
            transaction.claim(receipt);
            transaction.commit();
        }
        if (quest.reward() != null) {
            try { kernel.grant(player, quest.reward(), "quest:" + id + ":" + window); }
            catch (RuntimeException failure) { error("quest-reward:" + id, failure); }
        }
        try { SeasonService.grantRankPoints(player, data, SeasonService.typeOf(quest)); }
        catch (RuntimeException failure) { error("quest-rank:" + id, failure); }
        claimed++;
        return Result.CLAIMED;
    }

    /** Active quests of a player, keyed by quest with the current stage index. */
    public Map<ContentId, Integer> active(ServerPlayer player) {
        Map<ContentId, Integer> result = new LinkedHashMap<>();
        kernel.content().quests().keySet().forEach(id -> {
            int stage = stage(player, id);
            if (stage >= 0) { result.put(id, stage); }
        });
        return Map.copyOf(result);
    }

    private void clearProgress(KernelContext.Transaction transaction, QuestDefinitions.Quest quest, int stage) {
        if (stage < 0 || stage >= quest.stages().size()) { return; }
        quest.stages().get(stage).objectives()
                .forEach(objective -> transaction.variable(QuestDefinitions.key(quest.id(), "p." + objective.key()), "0"));
    }

    private String windowKey(QuestDefinitions.Quest quest) {
        return quest.reset() == QuestDefinitions.Reset.NONE ? "once" : Long.toString(quest.window(now()));
    }

    private long now() { return System.currentTimeMillis() / 1000L; }

    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException malformed) { return fallback; }
    }

    private static long parse(String value, long fallback) {
        try { return Long.parseLong(value); } catch (NumberFormatException malformed) { return fallback; }
    }

    public String diagnostics() {
        return "Quests accepted=" + accepted + " advanced=" + advanced + " completed=" + completed
                + " claimed=" + claimed + " rejected=" + rejected;
    }

    private void error(String id, RuntimeException failure) {
        rejected++;
        Rotasutils.LOG.error("RPG {}: {}", id, failure.getMessage(), failure);
    }
}
