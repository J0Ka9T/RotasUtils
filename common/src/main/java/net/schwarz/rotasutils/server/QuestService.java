package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.skill.EffectType;

import java.util.ArrayList;
import java.util.List;

/** Accepting, tracking, turning in, abandoning and failing quests. */
public final class QuestService {
    private QuestService() {
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult no(String message) {
            return new ActionResult(false, message);
        }
    }

    // Availability ---------------------------------------------------------

    /** Full requirement report for the quest detail screen and the accept check. */
    public static List<CheckResult> report(ServerPlayer player, RotasData data, QuestDef quest) {
        List<CheckResult> results = new ArrayList<>();
        PlayerProgress progress = data.progress(player.getUUID());

        if (quest.requiredLevel() > 0) {
            results.add(progress.level() >= quest.requiredLevel()
                    ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.level", quest.requiredLevel()))
                    : CheckResult.fail(ThaiText.t("rotasutils.msg.req.level", quest.requiredLevel()),
                    ThaiText.t("rotasutils.msg.req.your_level", progress.level())));
        }
        if (data.levelConfig().rankRequirementsEnabled()) {
            DangerRank rank = quest.rank();
            results.add(progress.hasClearance(rank)
                    ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.clearance", rank.display()))
                    : CheckResult.fail(ThaiText.t("rotasutils.msg.req.clearance", rank.display()),
                    ThaiText.t("rotasutils.msg.req.your_clearance", progress.highestClearance().display())));
        }
        if (quest.recommendedLevel() > 0) {
            results.add(CheckResult.advisory(progress.level() >= quest.recommendedLevel(),
                    ThaiText.t("rotasutils.msg.req.recommended_level", quest.recommendedLevel()),
                    ThaiText.t("rotasutils.msg.req.your_level", progress.level())));
        }
        results.addAll(RequirementChecker.checkAll(player, data, quest.requirements()));
        return results;
    }

    public static boolean canSee(ServerPlayer player, RotasData data, QuestDef quest) {
        if (!quest.published()) {
            return false;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if (!quest.hidden()) {
            return true;
        }
        if (progress.unlockedQuests().contains(quest.id())) {
            return true;
        }
        return SkillService.hasFlag(data, progress, EffectType.REVEAL_HIDDEN_QUESTS);
    }

    /** Reason the quest cannot be accepted right now, or null. */
    public static String blockedReason(ServerPlayer player, RotasData data, QuestDef quest) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.active(quest.id()) != null) {
            return ThaiText.t("rotasutils.msg.quest.already_accepted");
        }
        if (progress.activeQuests().size() >= data.serverSettings().maxActiveQuests()) {
            return ThaiText.t("rotasutils.msg.quest.max_active", data.serverSettings().maxActiveQuests());
        }
        int completions = progress.completionCount(quest.id());
        if (completions > 0 && quest.repeat() == QuestDef.Repeat.NEVER) {
            return ThaiText.t("rotasutils.msg.quest.once");
        }
        long now = nowSeconds();
        long cooldownUntil = progress.cooldownUntil(quest.id());
        if (cooldownUntil > now) {
            long remaining = cooldownUntil - now;
            return ThaiText.t("rotasutils.msg.quest.cooldown", formatDuration(remaining));
        }
        List<CheckResult> results = report(player, data, quest);
        for (CheckResult result : results) {
            if (result.blocking() && !result.pass()) {
                return ThaiText.t("rotasutils.msg.quest.required", result.label())
                        + (result.detail().isEmpty() ? "" : " " + result.detail());
            }
        }
        return null;
    }

    // Accept ---------------------------------------------------------------

    public static ActionResult accept(ServerPlayer player, RotasData data, String questId, String boardId) {
        return accept(player, data, questId, boardId, false);
    }

    /**
     * Accepts a quest.
     *
     * @param questGiverAuthorised set when a configured NPC is handing the quest over. An NPC
     *                             is a quest source in its own right, so it satisfies the
     *                             "quests must come from a board" server rule the way a board
     *                             does; every other check still applies.
     */
    public static ActionResult accept(ServerPlayer player, RotasData data, String questId, String boardId,
                                      boolean questGiverAuthorised) {
        QuestDef quest = data.quest(questId);
        if (quest == null || !quest.published()) {
            return ActionResult.no(ThaiText.t("rotasutils.msg.quest.unavailable"));
        }
        if (!canSee(player, data, quest)) {
            return ActionResult.no(ThaiText.t("rotasutils.msg.quest.unavailable"));
        }
        BoardConfig board = boardId == null || boardId.isEmpty() ? null : data.board(boardId);
        // The browser only opens when the board's own gates pass (level, clearance, dimension,
        // schedule, availability), but the accept packet can name any board id.
        if (board != null && !questGiverAuthorised) {
            String closed = BoardService.openBlockedReason(player, data, board);
            if (closed != null) {
                return ActionResult.no(closed);
            }
        }
        if (data.serverSettings().requireBoardForAccept() && !questGiverAuthorised) {
            if (board == null) {
                return ActionResult.no(ThaiText.t("rotasutils.msg.quest.board_only"));
            }
            if (!board.allowAccept()) {
                return ActionResult.no(ThaiText.t("rotasutils.msg.quest.board_no_accept"));
            }
            if (!BoardService.offers(data, board, quest, player)) {
                return ActionResult.no(ThaiText.t("rotasutils.msg.quest.board_not_offered"));
            }
        }
        String blocked = blockedReason(player, data, quest);
        if (blocked != null) {
            return ActionResult.no(blocked);
        }
        // Entry costs are consumed only after every other check passed.
        for (Requirement requirement : quest.requirements()) {
            if (requirement.type() == RequirementType.HAS_ITEM && requirement.params().getBool("consume", false)) {
                ResourceLocation itemId = requirement.params().getId("item");
                Item item = itemId == null ? null : BuiltInRegistries.ITEM.get(itemId);
                int amount = Math.max(1, requirement.params().getInt("amount", 1));
                if (item == null || RequirementChecker.consumeItem(player, item, amount) < amount) {
                    return ActionResult.no(ThaiText.t("rotasutils.msg.quest.entry_cost"));
                }
            }
        }

        PlayerProgress progress = data.progress(player.getUUID());
        ActiveQuest active = new ActiveQuest(quest.id(), quest.version(), quest.objectives().size());
        active.setStartedAt(nowSeconds());
        if (quest.timeLimitSeconds() > 0) {
            active.setDeadline(nowSeconds() + quest.timeLimitSeconds());
        }
        active.setPartyId(progress.partyId());
        progress.putActive(active);
        data.audit(player.getGameProfile().getName() + " accepted " + quest.id());
        data.setDirty();

        player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.accepted_named", quest.name())
                .withStyle(quest.rank().color()));
        ObjectiveEngine.refreshInventory(player, data);
        // A quest with no objectives is immediately ready to hand in.
        if (allRequiredComplete(quest, active) && !active.turnInReady()) {
            active.setTurnInReady(true);
            notifyReadyForTurnIn(player, quest);
        }
        net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
        return ActionResult.ok(ThaiText.t("rotasutils.msg.quest.accepted"));
    }

    // Abandon --------------------------------------------------------------

    public static ActionResult abandon(ServerPlayer player, RotasData data, String questId) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.active(questId) == null) {
            return ActionResult.no(ThaiText.t("rotasutils.msg.quest.not_accepted"));
        }
        progress.removeActive(questId);
        QuestDef quest = data.quest(questId);
        if (quest != null && quest.cooldownSeconds() > 0) {
            progress.setCooldown(questId, nowSeconds() + quest.cooldownSeconds());
        }
        data.setDirty();
        net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
        return ActionResult.ok(ThaiText.t("rotasutils.msg.quest.abandoned"));
    }

    // Turn in --------------------------------------------------------------

    public static ActionResult turnIn(ServerPlayer player, RotasData data, String questId, String boardId) {
        QuestDef quest = data.quest(questId);
        if (quest == null) {
            return ActionResult.no(ThaiText.t("rotasutils.msg.quest.gone"));
        }
        PlayerProgress progress = data.progress(player.getUUID());
        ActiveQuest active = progress.active(questId);
        if (active == null) {
            return ActionResult.no(ThaiText.t("rotasutils.msg.quest.not_accepted"));
        }
        ObjectiveEngine.migrate(active, quest);

        ObjectiveEngine.refreshInventory(player, data);
        if (!allRequiredComplete(quest, active)) {
            return ActionResult.no(ThaiText.t("rotasutils.msg.quest.not_finished"));
        }
        if (boardId != null && !boardId.isEmpty()) {
            BoardConfig board = data.board(boardId);
            if (board != null && !board.allowTurnIn()) {
                return ActionResult.no(ThaiText.t("rotasutils.msg.quest.board_no_turnin"));
            }
        }

        if (!QuestInventory.consume(quest, active, ObjectiveEngine.carriedStacks(player))) {
            return ActionResult.no(ThaiText.t("rotasutils.msg.quest.items_missing"));
        }
        player.getInventory().setChanged();
        int completionIndex = progress.completionCount(questId);
        boolean first = completionIndex == 0;
        int optionalDone = 0;
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (quest.objectives().get(i).optional() && active.isComplete(i)) {
                optionalDone++;
            }
        }
        double contributionShare = 1.0;

        progress.removeActive(questId);
        progress.recordCompletion(questId, nowSeconds());
        DailyService.onQuestCompleted(player, data, quest);
        TitleService.onProgress(player, data);
        progress.noteRankCompleted(quest.rank());
        applyRepeatCooldown(data, progress, quest);

        SeasonService.QuestType seasonType = SeasonService.typeOf(quest);
        double xpScale = SeasonService.questXpMultiplier(data, progress, seasonType);
        if (seasonType == SeasonService.QuestType.REPEATABLE && SeasonService.active(data)) {
            SeasonService.recordRepeatableRun(progress);
        }
        RewardService.Context context = RewardService.Context.quest(quest, completionIndex, first,
                contributionShare, optionalDone).withXpScale(xpScale);
        List<Reward> rewards = new ArrayList<>(quest.rewards());
        RewardService.Granted granted = RewardService.grant(player, data, rewards, context);
        SeasonService.grantRankPoints(player, data, seasonType);

        ProgressService.refreshClearance(player, data, progress);
        data.audit(player.getGameProfile().getName() + " completed " + quest.id());
        data.setDirty();

        if (!"false".equals(progress.questVariables().get("pref.pref_announce"))) {
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.complete_named", quest.name())
                    .withStyle(quest.rank().color()));
        }
        for (Component line : granted.lines()) {
            player.sendSystemMessage(line);
        }
        playCompletionSound(player, quest);

        // Feed the completion back in so "complete another quest" objectives advance.
        ObjectiveEngine.handle(player, data,
                new QuestEvent(net.schwarz.rotasutils.quest.objective.EventKind.QUEST_COMPLETE)
                        .customId(questId));
        net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
        return ActionResult.ok(ThaiText.t("rotasutils.msg.quest.complete"));
    }

    private static void applyRepeatCooldown(RotasData data, PlayerProgress progress, QuestDef quest) {
        double reduction = SkillService.multiplier(data, progress, EffectType.QUEST_COOLDOWN_REDUCTION);
        long base = switch (quest.repeat()) {
            case NEVER, UNLIMITED -> 0;
            case DAILY -> 86_400L;
            case WEEKLY -> 604_800L;
            case COOLDOWN -> quest.cooldownSeconds();
        };
        if (base <= 0) {
            return;
        }
        long adjusted = Math.max(0, Math.round(base * Math.max(0.0, 1.0 - reduction)));
        progress.setCooldown(quest.id(), nowSeconds() + adjusted);
    }

    private static void playCompletionSound(ServerPlayer player, QuestDef quest) {
        ResourceLocation soundId = ResourceLocation.tryParse(quest.completionSound());
        if (soundId == null) {
            return;
        }
        var sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (sound != null) {
            player.level().playSound(null, player.blockPosition(), sound,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    // Failure --------------------------------------------------------------

    public static void fail(ServerPlayer player, RotasData data, String questId, String reason) {
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.active(questId) == null) {
            return;
        }
        progress.removeActive(questId);
        progress.failedQuests().add(questId);
        QuestDef quest = data.quest(questId);
        if (quest != null && quest.cooldownSeconds() > 0) {
            progress.setCooldown(questId, nowSeconds() + quest.cooldownSeconds());
        }
        data.setDirty();
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.failed",
                quest == null ? questId : quest.name(), reason).withStyle(ChatFormatting.RED));
        net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
    }

    /** How long before a deadline the one-time expiry warning fires. */
    private static final long EXPIRY_WARNING_SECONDS = 60L;

    /**
     * Expires timed quests, and warns once per quest when the deadline is a minute away.
     * Called from the batched server tick, not per player per tick.
     */
    public static void checkTimeLimits(ServerPlayer player, RotasData data) {
        PlayerProgress progress = data.peek(player.getUUID());
        if (progress == null || progress.activeQuests().isEmpty()) {
            return;
        }
        long now = nowSeconds();
        List<String> expired = new ArrayList<>();
        for (ActiveQuest active : progress.activeQuests().values()) {
            if (active.deadline() <= 0) {
                continue;
            }
            long remaining = active.deadline() - now;
            if (remaining < 0) {
                expired.add(active.questId());
                continue;
            }
            if (remaining <= EXPIRY_WARNING_SECONDS && !active.deadlineWarned()) {
                active.setDeadlineWarned(true);
                QuestDef quest = data.quest(active.questId());
                player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.expires",
                        quest == null ? active.questId() : quest.name(), formatDuration(remaining))
                        .withStyle(ChatFormatting.YELLOW));
                progress.markDirty();
            }
        }
        for (String questId : expired) {
            fail(player, data, questId, ThaiText.t("rotasutils.msg.quest.time_ran_out"));
        }
    }

    // Helpers --------------------------------------------------------------

    public static boolean allRequiredComplete(QuestDef quest, ActiveQuest active) {
        List<Objective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!objectives.get(i).optional() && !active.isComplete(i)) {
                return false;
            }
        }
        return true;
    }

    static void notifyObjectiveComplete(ServerPlayer player, QuestDef quest, Objective objective) {
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.objective_done", objective.displayText())
                .withStyle(ChatFormatting.GREEN));
    }

    static void notifyReadyForTurnIn(ServerPlayer player, QuestDef quest) {
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.ready", quest.name())
                .withStyle(ChatFormatting.YELLOW));
    }

    /** Mirrors progress onto party members according to the quest's party mode. */
    static void shareWithParty(ServerPlayer player, RotasData data, QuestDef quest, ActiveQuest source) {
        if (quest.partyProgress() == QuestDef.PartyProgress.INDIVIDUAL
                || !data.serverSettings().partySystemEnabled()) {
            return;
        }
        PlayerProgress owner = data.progress(player.getUUID());
        if (owner.partyId() == null) {
            return;
        }
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(player.getUUID())) {
                continue;
            }
            PlayerProgress otherProgress = data.peek(other.getUUID());
            if (otherProgress == null || !owner.partyId().equals(otherProgress.partyId())) {
                continue;
            }
            if (quest.partyProgress() == QuestDef.PartyProgress.NEARBY
                    && (other.level() != player.level()
                    || other.distanceTo(player) > data.serverSettings().partyNearbyRadius())) {
                continue;
            }
            if (quest.partyProgress() == QuestDef.PartyProgress.SAME_DIMENSION
                    && other.level() != player.level()) {
                continue;
            }
            ActiveQuest mirror = otherProgress.active(quest.id());
            if (mirror == null) {
                continue;
            }
            ObjectiveEngine.migrate(mirror, quest);
            boolean changed = false;
            for (int i = 0; i < quest.objectives().size(); i++) {
                if (!quest.objectives().get(i).partyShared()) {
                    continue;
                }
                if (mirror.progress(i) < source.progress(i)) {
                    mirror.setProgress(i, source.progress(i));
                    mirror.setComplete(i, source.isComplete(i));
                    changed = true;
                }
            }
            if (changed) {
                otherProgress.markDirty();
                if (allRequiredComplete(quest, mirror) && !mirror.turnInReady()) {
                    mirror.setTurnInReady(true);
                    notifyReadyForTurnIn(other, quest);
                }
                net.schwarz.rotasutils.network.RotasNetwork.syncProgress(other);
            }
        }
    }

    public static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Human-readable duration using at most the two largest units ("2d 3h", "1h 5m"),
     * so deadline text stays short in list rows. Seconds only appear when nothing
     * larger is left, which keeps the sub-minute expiry warning exact.
     */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.time.seconds", 0);
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder builder = new StringBuilder();
        int units = 0;
        if (days > 0) {
            builder.append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.time.days", days)).append(' ');
            units++;
        }
        if (hours > 0 && units < 2) {
            builder.append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.time.hours", hours)).append(' ');
            units++;
        }
        if (minutes > 0 && units < 2) {
            builder.append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.time.minutes", minutes)).append(' ');
            units++;
        }
        if (units == 0 && secs > 0) {
            builder.append(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.time.seconds", secs));
        }
        return builder.toString().trim();
    }
}

