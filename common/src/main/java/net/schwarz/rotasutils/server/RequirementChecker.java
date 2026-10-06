package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import dev.architectury.platform.Platform;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillNode;

import java.util.ArrayList;
import java.util.List;

public final class RequirementChecker {
    private RequirementChecker() {
    }

    public static List<CheckResult> checkAll(ServerPlayer player, RotasData data, List<Requirement> requirements) {
        List<CheckResult> results = new ArrayList<>(requirements.size());
        for (Requirement requirement : requirements) {
            results.add(check(player, data, requirement));
        }
        return results;
    }

    public static boolean allPass(List<CheckResult> results) {
        for (CheckResult result : results) {
            if (result.blocking() && !result.pass()) {
                return false;
            }
        }
        return true;
    }

    public static String firstBlockedLabel(ServerPlayer player, RotasData data, List<Requirement> requirements) {
        for (Requirement requirement : requirements) {
            CheckResult result = check(player, data, requirement);
            if (result.blocking() && !result.pass()) {
                return result.label();
            }
        }
        return null;
    }

    public static CheckResult check(ServerPlayer player, RotasData data, Requirement requirement) {
        CheckResult result = evaluate(player, data, requirement);
        if (requirement.recommendationOnly()) {
            return CheckResult.advisory(result.pass(), result.label(), result.detail());
        }
        return result;
    }

    private static CheckResult evaluate(ServerPlayer player, RotasData data, Requirement requirement) {
        PlayerProgress progress = data.progress(player.getUUID());
        Params params = requirement.params();
        return switch (requirement.type()) {
            case MIN_LEVEL -> {
                int need = params.getInt("level", 1);
                yield progress.level() >= need
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.level", need))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.level", need),
                        ThaiText.t("rotasutils.msg.req.your_level", progress.level()));
            }
            case MAX_LEVEL -> {
                int cap = params.getInt("level", 100);
                yield progress.level() <= cap
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.level_max", cap))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.level_max", cap),
                        ThaiText.t("rotasutils.msg.req.your_level", progress.level()));
            }
            case RANK_CLEARANCE -> {
                DangerRank rank = DangerRank.byName(params.getString("rank", "F"), DangerRank.F);
                yield progress.hasClearance(rank)
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.clearance", rank.display()))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.clearance", rank.display()),
                        ThaiText.t("rotasutils.msg.req.your_clearance", progress.highestClearance().display()));
            }
            case QUEST_COMPLETED -> {
                String questId = params.getString("quest", "");
                int times = Math.max(1, params.getInt("times", 1));
                QuestDef quest = data.quest(questId);
                String name = quest == null ? questId : quest.name();
                int have = progress.completionCount(questId);
                yield have >= times
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.completed", name))
                        : CheckResult.fail(times > 1 ? ThaiText.t("rotasutils.msg.req.completed_times", name, times)
                                : ThaiText.t("rotasutils.msg.req.completed", name),
                        ThaiText.t("rotasutils.msg.req.completed_of", have, times));
            }
            case RANK_QUESTS_COMPLETED -> {
                DangerRank rank = DangerRank.byName(params.getString("rank", "C"), DangerRank.C);
                int times = Math.max(1, params.getInt("times", 1));
                int have = progress.completionsOfRank(rank, id -> {
                    QuestDef quest = data.quest(id);
                    return quest == null ? null : quest.rank();
                });
                yield have >= times
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.rank_quests", times, rank.display()))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.rank_quests", times, rank.display()),
                        ThaiText.t("rotasutils.msg.req.you_completed", have));
            }
            case SKILL_UNLOCKED -> {
                String skillId = params.getString("skill", "");
                SkillNode node = data.findNode(skillId);
                String name = node == null ? skillId : node.name();
                yield progress.skillRank(skillId) > 0
                        ? CheckResult.pass(name)
                        : CheckResult.fail(name, ThaiText.t("rotasutils.msg.req.not_unlocked", name));
            }
            case SKILL_RANK -> {
                String skillId = params.getString("skill", "");
                int need = Math.max(1, params.getInt("rank", 1));
                SkillNode node = data.findNode(skillId);
                String name = node == null ? skillId : node.name();
                int have = progress.skillRank(skillId);
                yield have >= need
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.skill_rank", name, need))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.skill_rank", name, need),
                        ThaiText.t("rotasutils.msg.req.your_skill_rank", name, have));
            }
            case POINTS_SPENT -> {
                int need = params.getInt("points", 0);
                yield progress.totalPointsSpent() >= need
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.points_spent", need))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.points_spent", need),
                        ThaiText.t("rotasutils.msg.req.you_spent", progress.totalPointsSpent()));
            }
            case CATEGORY_POINTS_SPENT -> {
                String categoryId = params.getString("category", "");
                int need = params.getInt("points", 0);
                SkillCategory category = data.category(categoryId);
                String name = category == null ? categoryId : category.name();
                int have = progress.categoryPointsSpent(categoryId);
                yield have >= need
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.category_spent", need, name))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.category_spent", need, name),
                        ThaiText.t("rotasutils.msg.req.you_spent", have));
            }
            case CONNECTED_SKILLS -> {
                yield CheckResult.pass(ThaiText.t("rotasutils.msg.req.connected"));
            }
            case HAS_ITEM -> {
                ResourceLocation itemId = params.getId("item");
                int amount = Math.max(1, params.getInt("amount", 1));
                Item item = itemId == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    yield CheckResult.fail(ThaiText.t("rotasutils.msg.req.item"),
                            ThaiText.t("rotasutils.msg.req.item_missing", params.getString("item", "")));
                }
                int have = countItem(player, item);
                String name = new ItemStack(item).getHoverName().getString();
                yield have >= amount
                        ? CheckResult.pass(amount + "x " + name)
                        : CheckResult.fail(amount + "x " + name, ThaiText.t("rotasutils.msg.req.carrying", have));
            }
            case FACTION -> {
                String faction = params.getString("faction", "");
                String have = progress.questVariables().getOrDefault("faction", "");
                yield have.equalsIgnoreCase(faction)
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.faction", faction))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.faction", faction),
                        have.isEmpty() ? ThaiText.t("rotasutils.msg.req.no_faction")
                                : ThaiText.t("rotasutils.msg.req.your_faction", have));
            }
            case REPUTATION -> {
                String faction = params.getString("faction", "");
                int need = params.getInt("amount", 0);
                long have = progress.reputation(faction);
                yield have >= need
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.reputation", faction, need))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.reputation", faction, need),
                        ThaiText.t("rotasutils.msg.req.your_reputation", have));
            }
            case PERMISSION -> {
                int opLevel = params.getInt("op_level", 2);
                yield player.hasPermissions(opLevel)
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.permission"))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.permission"),
                        ThaiText.t("rotasutils.msg.req.no_permission"));
            }
            case ADVANCEMENT -> {
                ResourceLocation advancementId = params.getId("advancement");
                Advancement advancement = advancementId == null ? null
                        : player.server.getAdvancements().getAdvancement(advancementId);
                boolean done = advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
                yield done
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.advancement"))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.advancement"),
                        ThaiText.t("rotasutils.msg.req.no_advancement"));
            }
            case SCOREBOARD -> {
                String objectiveName = params.getString("objective", "");
                int need = params.getInt("amount", 0);
                int have = readScore(player, objectiveName);
                yield have >= need
                        ? CheckResult.pass(objectiveName + " " + need)
                        : CheckResult.fail(objectiveName + " " + need, ThaiText.t("rotasutils.msg.req.your_value", have));
            }
            case DIMENSION -> {
                String want = params.getString("dimension", "");
                String here = player.level().dimension().location().toString();
                yield want.isEmpty() || here.equals(want)
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.in", want))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.in", want),
                        ThaiText.t("rotasutils.msg.req.you_are_in", here));
            }
            case MOD_LOADED -> {
                String modId = params.getString("mod", "");
                yield modId.isEmpty() || Platform.isModLoaded(modId)
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.mod", modId))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.mod", modId),
                        ThaiText.t("rotasutils.msg.req.mod_missing", modId));
            }
            case PRESTIGE -> {
                int need = params.getInt("amount", 1);
                yield progress.prestige() >= need
                        ? CheckResult.pass(ThaiText.t("rotasutils.msg.req.prestige", need))
                        : CheckResult.fail(ThaiText.t("rotasutils.msg.req.prestige", need),
                        ThaiText.t("rotasutils.msg.req.your_prestige", progress.prestige()));
            }
        };
    }

    public static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int consumeItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.getInventory().setChanged();
        return amount - remaining;
    }

    public static int readScore(ServerPlayer player, String objectiveName) {
        if (objectiveName.isEmpty()) {
            return 0;
        }
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) {
            return 0;
        }
        if (!scoreboard.hasPlayerScore(player.getScoreboardName(), objective)) {
            return 0;
        }
        return scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore();
    }

    public static void addScore(ServerPlayer player, String objectiveName, int amount) {
        if (objectiveName.isEmpty()) {
            return;
        }
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) {
            return;
        }
        scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).add(amount);
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
