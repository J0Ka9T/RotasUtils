package net.schwarz.rotasutils.server;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.requirement.Requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class BoardService {
    private static final Map<UUID, Map<String, List<String>>> RANDOM_PICKS = new HashMap<>();

    private BoardService() {
    }

    public static void reroll(ServerPlayer player, BoardConfig board) {
        Map<String, List<String>> picks = RANDOM_PICKS.get(player.getUUID());
        if (picks != null) {
            picks.remove(board.id());
        }
    }

    public static void forget(UUID playerId) {
        RANDOM_PICKS.remove(playerId);
    }

    public static void clear() {
        RANDOM_PICKS.clear();
    }

    public static String openBlockedReason(ServerPlayer player, RotasData data, BoardConfig board) {
        if (!board.visible() && !isAdmin(player, data)) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.hidden");
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if (board.minPlayerLevel() > 0 && progress.level() < board.minPlayerLevel()) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.common.requires_level", board.minPlayerLevel());
        }
        if (board.maxPlayerLevel() > 0 && progress.level() > board.maxPlayerLevel()) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.level_above");
        }
        if (board.requiredClearance() != null && !progress.hasClearance(board.requiredClearance())) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.clearance", board.requiredClearance().display());
        }
        if (!board.requiredDimension().isEmpty()
                && !player.level().dimension().location().toString().equals(board.requiredDimension())) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.dimension");
        }
        if (board.scheduleStartHour() != board.scheduleEndHour()) {
            long dayTime = player.level().getDayTime() % 24000L;
            int hour = (int) ((dayTime / 1000L + 6) % 24);
            boolean open = board.scheduleStartHour() < board.scheduleEndHour()
                    ? hour >= board.scheduleStartHour() && hour < board.scheduleEndHour()
                    : hour >= board.scheduleStartHour() || hour < board.scheduleEndHour();
            if (!open) {
                return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.closed");
            }
        }
        for (Requirement requirement : board.availability()) {
            CheckResult result = RequirementChecker.check(player, data, requirement);
            if (result.blocking() && !result.pass()) {
                return result.detail().isEmpty() ? result.label() + " required." : result.detail();
            }
        }
        return null;
    }

    public static boolean isAdmin(ServerPlayer player, RotasData data) {
        return RotasPermissions.allowed(player.createCommandSourceStack(), RotasPermissions.Capability.EDIT);
    }

    public static boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(4);
    }

    public static List<String> pool(RotasData data, BoardConfig board) {
        Set<String> ids = new LinkedHashSet<>(board.questIds());
        for (QuestDef quest : data.quests().values()) {
            if (!quest.published()) {
                continue;
            }
            boolean matchesFilter = board.categoryFilters().contains(quest.category())
                    || board.rankFilters().contains(quest.rank())
                    || (!quest.organization().isEmpty() && board.factionFilters().contains(quest.organization()));
            if (matchesFilter) {
                ids.add(quest.id());
            }
        }
        ids.addAll(board.dailyPool());
        ids.addAll(board.eventPool());
        ids.addAll(board.emergencyPool());
        return new ArrayList<>(ids);
    }

    public static boolean offers(RotasData data, BoardConfig board, QuestDef quest, ServerPlayer player) {
        return visibleQuests(player, data, board).contains(quest.id());
    }

    public static List<String> visibleQuests(ServerPlayer player, RotasData data, BoardConfig board) {
        List<String> rotated = resolveRotation(player, data, board);
        PlayerProgress progress = data.progress(player.getUUID());
        List<String> result = new ArrayList<>(rotated.size());

        for (String questId : rotated) {
            QuestDef quest = data.quest(questId);
            if (quest == null || !QuestService.canSee(player, data, quest)) {
                continue;
            }
            boolean locked = QuestService.blockedReason(player, data, quest) != null;
            boolean alreadyActive = progress.active(questId) != null;
            if (locked && !alreadyActive) {
                if (board.hideLocked() || !board.showUnavailable()) {
                    continue;
                }
            }
            result.add(questId);
        }
        String featured = board.featuredQuestId();
        if (!featured.isEmpty() && result.remove(featured)) {
            result.add(0, featured);
        }
        return result;
    }

    private static List<String> resolveRotation(ServerPlayer player, RotasData data, BoardConfig board) {
        List<String> pool = pool(data, board);
        BoardConfig.Rotation rotation = board.rotation();
        if (rotation == BoardConfig.Rotation.NEVER || pool.isEmpty()) {
            return pool;
        }
        int slots = Math.min(board.rotationSlots(), pool.size());

        if (rotation == BoardConfig.Rotation.RANDOM) {
            Map<String, List<String>> picks = RANDOM_PICKS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
            List<String> kept = new ArrayList<>(picks.getOrDefault(board.id(), List.of()));
            kept.retainAll(pool);
            if (!kept.isEmpty()) {
                return kept;
            }
            List<String> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            List<String> picked = List.copyOf(shuffled.subList(0, slots));
            picks.put(board.id(), picked);
            return new ArrayList<>(picked);
        }
        if (rotation == BoardConfig.Rotation.BY_PLAYER_LEVEL) {
            PlayerProgress progress = data.progress(player.getUUID());
            List<String> matched = new ArrayList<>();
            for (String questId : pool) {
                QuestDef quest = data.quest(questId);
                if (quest == null) {
                    continue;
                }
                int recommended = quest.recommendedLevel() > 0 ? quest.recommendedLevel() : 1;
                if (Math.abs(recommended - progress.level()) <= 15) {
                    matched.add(questId);
                }
            }
            List<String> source = matched.isEmpty() ? pool : matched;
            return source.subList(0, Math.min(slots, source.size()));
        }

        int period = rotation.periodSeconds();
        if (period <= 0) {
            return pool;
        }
        long now = QuestService.nowSeconds();
        if (board.rotatedSelection().isEmpty() || now - board.lastRotation() >= period) {
            board.setLastRotation(now);
            board.setRotationSeed(now / period);
            List<String> shuffled = new ArrayList<>(pool);
            if (rotation == BoardConfig.Rotation.BY_CATEGORY) {
                shuffled.sort((a, b) -> categoryOf(data, a).compareTo(categoryOf(data, b)));
            } else if (rotation == BoardConfig.Rotation.BY_RANK) {
                shuffled.sort((a, b) -> Integer.compare(rankOf(data, a).ordinal(), rankOf(data, b).ordinal()));
            } else {
                Collections.shuffle(shuffled, new Random(board.rotationSeed()));
            }
            board.rotatedSelection().clear();
            board.rotatedSelection().addAll(shuffled.subList(0, Math.min(slots, shuffled.size())));
            data.setDirty();
        }
        board.rotatedSelection().removeIf(id -> data.quest(id) == null);
        return new ArrayList<>(board.rotatedSelection());
    }

    public static void forceRotation(RotasData data, BoardConfig board) {
        board.rotatedSelection().clear();
        board.setLastRotation(0);
        data.setDirty();
    }

    private static String categoryOf(RotasData data, String questId) {
        QuestDef quest = data.quest(questId);
        return quest == null ? "" : quest.category();
    }

    private static DangerRank rankOf(RotasData data, String questId) {
        QuestDef quest = data.quest(questId);
        return quest == null ? DangerRank.F : quest.rank();
    }
}
