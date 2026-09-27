package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.QuestDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.ProgressionMath;
import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.List;

/**
 * Server side of the season rules: overflow currency, repeatable-quest diminishing, rank points and
 * rank perks.
 *
 * <p>Daily counters live in the player's server-only {@code rpg.} variables, so they persist with the
 * record, never reach the client snapshot, and reset on the first award after the server's midnight.</p>
 */
public final class SeasonService {
    public static final List<String> RANK_ORDER = List.of("F", "E", "D", "C", "B", "A", "S", "SS");

    private static final String DAY = "rpg.season.day";
    private static final String SUB_HELD = "rpg.season.subheld";
    private static final String REPEAT_RUNS = "rpg.season.rep";
    private static final String REPEAT_RANK = "rpg.season.reprank";

    public enum QuestType { MAIN, SIDE, DAILY, WEEKLY, REPEATABLE }

    private SeasonService() {
    }

    public static SeasonRules rules(RotasData data) {
        return data.levelConfig().season();
    }

    public static boolean active(RotasData data) {
        return data != null && rules(data).enabled;
    }

    public static long today() {
        return java.time.LocalDate.now().toEpochDay();
    }

    private static void rollDay(PlayerProgress progress) {
        String day = Long.toString(today());
        if (!day.equals(progress.questVariables().get(DAY))) {
            progress.questVariables().put(DAY, day);
            progress.questVariables().remove(REPEAT_RUNS);
            progress.questVariables().remove(REPEAT_RANK);
            progress.markDirty();
        }
    }

    private static long number(PlayerProgress progress, String key) {
        try {
            return Math.max(0, Long.parseLong(progress.questVariables().getOrDefault(key, "0")));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    private static void put(PlayerProgress progress, String key, long value) {
        progress.questVariables().put(key, Long.toString(Math.max(0, value)));
        progress.markDirty();
    }

    /** EXP a maxed sub job could not use turns into overflow currency. */
    private static long overflow(SeasonRules rules, PlayerProgress progress, String key, long withheld) {
        if (withheld <= 0) {
            return 0;
        }
        long pool = ProgressionMath.add(number(progress, key), withheld);
        long tokens = SeasonMath.overflowTokens(pool, rules.overflowXpPerToken);
        put(progress, key, pool - tokens * rules.overflowXpPerToken);
        if (tokens > 0) {
            try {
                progress.rpg().currency(rules.overflowCurrency, tokens);
            } catch (RuntimeException full) {
                // A full wallet or section keeps the pool; nothing else depends on the conversion.
            }
        }
        return tokens;
    }

    public static long overflowSubJob(RotasData data, PlayerProgress progress, long withheld) {
        return active(data) && rules(data).subCapToOverflow ? overflow(rules(data), progress, SUB_HELD, withheld) : 0;
    }

    /** Main quests, side quests, dailies, weeklies and repeatables, read from the quest's own settings. */
    public static QuestType typeOf(QuestDef quest) {
        if (quest.mainQuest()) {
            return QuestType.MAIN;
        }
        return switch (quest.repeat()) {
            case DAILY -> QuestType.DAILY;
            case WEEKLY -> QuestType.WEEKLY;
            case COOLDOWN, UNLIMITED -> QuestType.REPEATABLE;
            default -> QuestType.SIDE;
        };
    }

    public static QuestType typeOf(QuestDefinitions.Quest quest) {
        if (!quest.type().isEmpty()) {
            try {
                return QuestType.valueOf(quest.type());
            } catch (IllegalArgumentException unknown) {
                // Parsing already rejects unknown types; fall through to the reset mapping.
            }
        }
        return switch (quest.reset()) {
            case DAILY -> QuestType.DAILY;
            case WEEKLY -> QuestType.WEEKLY;
            case COOLDOWN -> QuestType.REPEATABLE;
            default -> quest.repeatable() ? QuestType.REPEATABLE : QuestType.SIDE;
        };
    }

    /** EXP multiplier for this completion: repeatables slow down after the daily full-rate runs. */
    public static double questXpMultiplier(RotasData data, PlayerProgress progress, QuestType type) {
        if (!active(data) || type != QuestType.REPEATABLE) {
            return 1.0;
        }
        rollDay(progress);
        SeasonRules rules = rules(data);
        return SeasonMath.repeatableMultiplier((int) Math.min(Integer.MAX_VALUE, number(progress, REPEAT_RUNS)),
                rules.repeatableFullRuns, rules.repeatableHalfRuns, rules.repeatableHalfRate, rules.repeatableLowRate);
    }

    public static void recordRepeatableRun(PlayerProgress progress) {
        rollDay(progress);
        put(progress, REPEAT_RUNS, number(progress, REPEAT_RUNS) + 1);
    }

    /** Grants the rank points one finished quest is worth, then refreshes the rank. */
    public static long grantRankPoints(ServerPlayer player, RotasData data, QuestType type) {
        if (!active(data)) {
            return 0;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        SeasonRules rules = rules(data);
        long amount = rules.rankExpFor(type.name());
        if (type == QuestType.REPEATABLE) {
            rollDay(progress);
            long already = number(progress, REPEAT_RANK);
            amount = Math.max(0, Math.min(amount, rules.repeatableRankCapPerDay - already));
            put(progress, REPEAT_RANK, already + amount);
        }
        if (amount <= 0) {
            return 0;
        }
        progress.addRankPoints(amount);
        player.sendSystemMessage(ThaiText.c("rotasutils.msg.season.rank_points", amount, progress.rankPoints())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        ProgressService.refreshClearance(player, data, progress);
        return amount;
    }

    public static String rankName(PlayerProgress progress, SeasonRules rules) {
        return SeasonMath.rankFor(progress.rankPoints(), rules.seasonRankTotal, rules.rankThresholds, RANK_ORDER);
    }

    public static DangerRank rank(PlayerProgress progress, SeasonRules rules) {
        return DangerRank.byName(rankName(progress, rules), DangerRank.F);
    }

    /** The next rank to reach and its points, or null at the top. */
    public static String nextRank(PlayerProgress progress, SeasonRules rules) {
        int index = RANK_ORDER.indexOf(rankName(progress, rules));
        for (int i = index + 1; i < RANK_ORDER.size(); i++) {
            if (rules.rankThresholds.containsKey(RANK_ORDER.get(i))) {
                return RANK_ORDER.get(i);
            }
        }
        return null;
    }

    /** Perks of the highest rank reached. Each rank lists its full perks, so they do not stack. */
    public static SeasonRules.RankPerk perk(RotasData data, PlayerProgress progress) {
        if (!active(data)) {
            return new SeasonRules.RankPerk();
        }
        SeasonRules rules = rules(data);
        return rules.perk(rankName(progress, rules));
    }


    /** True once per player and recipe: the first craft of an item pays the first-craft bonus. */
    public static boolean claimFirstCraft(PlayerProgress progress, String itemId) {
        return progress.claimOnce("season_first_craft|" + itemId);
    }
}
