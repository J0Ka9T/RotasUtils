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

public static ActionResult accept(ServerPlayer player, RotasData data, String questId, String boardId) {
        return accept(player, data, questId, boardId, false);
    }

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
        java.util.Map<Item, Integer> entryCosts = new java.util.HashMap<>();
        for (Requirement requirement : quest.requirements()) {
            if (requirement.type() == RequirementType.HAS_ITEM && requirement.params().getBool("consume", false)) {
                ResourceLocation itemId = requirement.params().getId("item");
                Item item = itemId == null ? null : BuiltInRegistries.ITEM.get(itemId);
                if (item == null) {
                    return ActionResult.no(ThaiText.t("rotasutils.msg.quest.entry_cost"));
                }
                entryCosts.merge(item, Math.max(1, requirement.params().getInt("amount", 1)), Integer::sum);
            }
        }
        for (var cost : entryCosts.entrySet()) {
            int carried = 0;
            for (net.minecraft.world.item.ItemStack stack : ObjectiveEngine.carriedStacks(player)) {
                if (stack.is(cost.getKey())) carried += stack.getCount();
            }
            if (carried < cost.getValue()) {
                return ActionResult.no(ThaiText.t("rotasutils.msg.quest.entry_cost"));
            }
        }
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
        progress.failedQuests().remove(quest.id());
        data.audit(player.getGameProfile().getName() + " accepted " + quest.id());
        data.setDirty();

        player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.accepted_named", quest.name())
                .withStyle(quest.rank().color()));
        ObjectiveEngine.refreshInventory(player, data);
        if (allRequiredComplete(quest, active) && !active.turnInReady()) {
            active.setTurnInReady(true);
            notifyReadyForTurnIn(player, quest);
        }
        net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
        return ActionResult.ok(ThaiText.t("rotasutils.msg.quest.accepted"));
    }

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
        java.util.Set<String> chosenPaths = chosenPaths(quest, active);

        progress.removeActive(questId);
        progress.recordCompletion(questId, nowSeconds());
        String claimPrefix = "quest:" + questId + ":";
        String current = claimPrefix + completionIndex + ":";
        progress.claimedRewards().removeIf(key -> key.startsWith(claimPrefix) && !key.startsWith(current)
                && key.substring(claimPrefix.length()).matches("[0-9]+:.*"));
        if (chosenPaths.isEmpty()) {
            progress.questVariables().remove("quest_paths." + questId);
        } else {
            progress.questVariables().put("quest_paths." + questId, String.join(",", chosenPaths));
        }
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
        rewards.removeIf(reward -> !reward.path().isEmpty() && !chosenPaths.contains(reward.path()));
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

    private static final long EXPIRY_WARNING_SECONDS = 60L;

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

public static boolean allRequiredComplete(QuestDef quest, ActiveQuest active) {
        List<Objective> objectives = quest.objectives();
        java.util.Set<Integer> decisions = new java.util.HashSet<>();
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            if (!objective.opens().isEmpty()) {
                if (!objective.optional()) decisions.add(objective.step());
                continue;
            }
            if (!objective.optional() && onPath(quest, active, i) && !active.isComplete(i)) {
                return false;
            }
        }
        for (int step : decisions) {
            if (!decided(quest, active, step)) return false;
        }
        return true;
    }

    public static boolean decided(QuestDef quest, ActiveQuest active, int step) {
        List<Objective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            if (!objective.opens().isEmpty() && objective.step() == step && active.isComplete(i)) return true;
        }
        return false;
    }

    public static java.util.Set<String> chosenPaths(QuestDef quest, ActiveQuest active) {
        java.util.Set<String> paths = new java.util.HashSet<>();
        List<Objective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!objectives.get(i).opens().isEmpty() && active.isComplete(i)) paths.add(objectives.get(i).opens());
        }
        return paths;
    }

    public static String chosenPath(QuestDef quest, ActiveQuest active) {
        List<Objective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!objectives.get(i).opens().isEmpty() && active.isComplete(i)) {
                return objectives.get(i).opens();
            }
        }
        return "";
    }

    public static boolean onPath(QuestDef quest, ActiveQuest active, int index) {
        Objective objective = quest.objectives().get(index);
        if (!objective.opens().isEmpty()) {
            return active.isComplete(index) || !decided(quest, active, objective.step());
        }
        return objective.path().isEmpty() || chosenPaths(quest, active).contains(objective.path());
    }

    static void notifyObjectiveComplete(ServerPlayer player, QuestDef quest, Objective objective) {
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.objective_done", objective.displayText())
                .withStyle(ChatFormatting.GREEN));
    }

    static void notifyReadyForTurnIn(ServerPlayer player, QuestDef quest) {
        if (quest.autoComplete()) {
            var server = player.server;
            String questId = quest.id();
            server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> {
                if (player.isRemoved() || player.hasDisconnected()) {
                    return;
                }
                RotasData data = RotasData.get(server);
                if (data.progress(player.getUUID()).active(questId) == null) {
                    return;
                }
                ActionResult result = turnIn(player, data, questId, "");
                if (!result.success()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result.message())
                            .withStyle(ChatFormatting.YELLOW));
                }
            }));
            return;
        }
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.quest.ready", quest.name())
                .withStyle(ChatFormatting.YELLOW));
    }

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

