package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.DailyTrack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;

public final class DailyService {
    private static final String DAY = "rpg.daily.day";
    private static final String DONE = "rpg.daily.done";
    private static final String CLAIMED = "rpg.daily.claimed";
    private static final String ANNOUNCED = "rpg.daily.announced";

    private DailyService() {
    }

    public record Result(boolean success, String message) {
    }

    private static SeasonRules.DailyRules rules(RotasData data) {
        return SeasonService.rules(data).daily;
    }

    public static void roll(PlayerProgress progress) {
        String today = Long.toString(SeasonService.today());
        if (!today.equals(progress.questVariables().get(DAY))) {
            progress.questVariables().put(DAY, today);
            progress.questVariables().remove(DONE);
            progress.questVariables().remove(CLAIMED);
            progress.questVariables().remove(ANNOUNCED);
            progress.markDirty();
        }
    }

    public static int done(PlayerProgress progress) {
        roll(progress);
        return (int) Math.min(Integer.MAX_VALUE, read(progress, DONE));
    }

    public static int claimedMask(PlayerProgress progress) {
        roll(progress);
        return (int) read(progress, CLAIMED);
    }

    private static long read(PlayerProgress progress, String key) {
        try {
            return Math.max(0, Long.parseLong(progress.questVariables().getOrDefault(key, "0")));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    public static boolean counts(ServerPlayer player, RotasData data, QuestDef quest) {
        SeasonRules.DailyRules daily = rules(data);
        if (daily == null || !daily.enabled || quest == null) {
            return false;
        }
        if (daily.countDailyRepeat && quest.repeat() == QuestDef.Repeat.DAILY) {
            return true;
        }
        var board = daily.boardId.isBlank() ? null : data.board(daily.boardId);
        return board != null && BoardService.visibleQuests(player, data, board).contains(quest.id());
    }

    public static void onQuestCompleted(ServerPlayer player, RotasData data, QuestDef quest) {
        if (!counts(player, data, quest)) {
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        int before = done(progress);
        int after = before + 1;
        progress.questVariables().put(DONE, Integer.toString(after));
        progress.markDirty();
        data.setDirty();

        long[] thresholds = rules(data).thresholds();
        for (int tier = 0; tier < thresholds.length; tier++) {
            if (before < thresholds[tier] && after >= thresholds[tier]) {
                player.sendSystemMessage(ThaiText.c("rotasutils.msg.daily.tier_reached", tier + 1, thresholds[tier])
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        RotasNetwork.syncProgress(player);
    }

    public static Result claim(ServerPlayer player, RotasData data, int tier) {
        SeasonRules.DailyRules daily = rules(data);
        if (daily == null || !daily.enabled) {
            return new Result(false, ThaiText.t("rotasutils.msg.daily.disabled"));
        }
        if (tier < 0 || tier >= daily.tiers.length) {
            return new Result(false, ThaiText.t("rotasutils.msg.daily.no_tier"));
        }
        PlayerProgress progress = data.progress(player.getUUID());
        int done = done(progress);
        int mask = claimedMask(progress);
        long[] thresholds = daily.thresholds();
        if (DailyTrack.claimed(mask, tier)) {
            return new Result(false, ThaiText.t("rotasutils.msg.daily.already"));
        }
        if (!DailyTrack.reached(thresholds, tier, done)) {
            return new Result(false, ThaiText.t("rotasutils.msg.daily.not_yet", thresholds[tier] - done));
        }
        progress.questVariables().put(CLAIMED, Integer.toString(DailyTrack.claim(mask, tier)));
        progress.markDirty();
        TrackRewards.Paid paid = TrackRewards.pay(player, data, daily.tiers[tier].reward,
                "daily|" + SeasonService.today() + "|" + tier);
        data.audit(player.getGameProfile().getName() + " claimed daily tier " + (tier + 1));
        EventService.fire(player, data, net.schwarz.rotasutils.event.EventType.QUEST_COMPLETE, "rotas:daily_track");
        RotasNetwork.syncProgress(player);
        return new Result(true, ThaiText.t("rotasutils.msg.daily.claimed", tier + 1, TrackRewards.describe(paid)));
    }

    public static List<String> missionsToday(ServerPlayer player, RotasData data) {
        SeasonRules.DailyRules daily = rules(data);
        if (daily == null || !daily.enabled) {
            return List.of();
        }
        var board = daily.boardId.isBlank() ? null : data.board(daily.boardId);
        if (board != null) {
            return BoardService.visibleQuests(player, data, board);
        }
        List<String> quests = new ArrayList<>();
        for (QuestDef quest : data.quests().values()) {
            if (quest.published() && quest.repeat() == QuestDef.Repeat.DAILY && QuestService.canSee(player, data, quest)) {
                quests.add(quest.id());
            }
        }
        return quests.size() > 12 ? quests.subList(0, 12) : quests;
    }

    public static void onJoin(ServerPlayer player, RotasData data) {
        SeasonRules.DailyRules daily = rules(data);
        if (daily == null || !daily.enabled || !daily.announceOnJoin) {
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        roll(progress);
        if (progress.questVariables().containsKey(ANNOUNCED)) {
            return;
        }
        progress.questVariables().put(ANNOUNCED, "1");
        progress.markDirty();
        int missions = missionsToday(player, data).size();
        if (missions > 0) {
            player.sendSystemMessage(ThaiText.c("rotasutils.msg.daily.welcome", missions)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    public static void reset(PlayerProgress progress) {
        progress.questVariables().remove(DAY);
        progress.questVariables().remove(DONE);
        progress.questVariables().remove(CLAIMED);
        progress.questVariables().remove(ANNOUNCED);
        progress.markDirty();
    }
}
