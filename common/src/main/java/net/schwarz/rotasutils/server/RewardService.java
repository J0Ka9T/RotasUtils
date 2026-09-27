package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.compat.PuffishSkillsCompat;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.skill.EffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Grants rewards. Every path is guarded against duplicate claims. */
public final class RewardService {
    private static final Random RANDOM = new Random();

    private RewardService() {
    }

    /**
     * What triggered a grant. The claim key derived from this is what makes a
     * reward impossible to claim twice.
     */
    public record Context(String claimKeyPrefix, DangerRank rank, boolean firstCompletion,
                          boolean repeatCompletion, double contributionShare, int optionalObjectivesDone,
                          double xpScale) {

        public Context(String claimKeyPrefix, DangerRank rank, boolean firstCompletion,
                       boolean repeatCompletion, double contributionShare, int optionalObjectivesDone) {
            this(claimKeyPrefix, rank, firstCompletion, repeatCompletion, contributionShare, optionalObjectivesDone, 1.0);
        }

        /** Scales only EXP rewards, used by the season's repeatable-quest diminishing. */
        public Context withXpScale(double scale) {
            return new Context(claimKeyPrefix, rank, firstCompletion, repeatCompletion, contributionShare,
                    optionalObjectivesDone, Double.isFinite(scale) ? Math.max(0, scale) : 1.0);
        }

        public static Context quest(QuestDef quest, int completionIndex, boolean first, double contribution,
                                    int optionalDone) {
            return new Context("quest:" + quest.id() + ":" + completionIndex, quest.rank(), first, !first,
                    contribution, optionalDone);
        }

        public static Context levelUp(int level) {
            return new Context("level:" + level, null, true, false, 1.0, 0);
        }

        public static Context admin(String tag) {
            return new Context("admin:" + tag + ":" + System.nanoTime(), null, false, false, 1.0, 0);
        }
    }

    /** Result of a grant, used to build the "you received" summary. */
    public record Granted(List<Component> lines, List<Reward> pendingChoices) {
    }

    public static Granted grant(ServerPlayer player, RotasData data, List<Reward> rewards, Context context) {
        List<Component> lines = new ArrayList<>();
        List<Reward> choices = new ArrayList<>();
        Map<String, List<Reward>> weightedPools = new LinkedHashMap<>();

        for (Reward reward : rewards) {
            switch (reward.mode()) {
                case GUARANTEED -> applyOne(player, data, reward, context, 1.0, lines);
                case FIRST_COMPLETION -> {
                    if (context.firstCompletion()) {
                        applyOne(player, data, reward, context, 1.0, lines);
                    }
                }
                case REPEAT_ONLY -> {
                    if (context.repeatCompletion()) {
                        applyOne(player, data, reward, context, 1.0, lines);
                    }
                }
                case OPTIONAL_BONUS -> {
                    if (context.optionalObjectivesDone() > 0) {
                        applyOne(player, data, reward, context, 1.0, lines);
                    }
                }
                case PARTY_CONTRIBUTION -> applyOne(player, data, reward, context,
                        Math.max(0.1, context.contributionShare()), lines);
                case RANDOM_WEIGHTED -> weightedPools
                        .computeIfAbsent(reward.pool(), key -> new ArrayList<>()).add(reward);
                case PLAYER_CHOICE -> choices.add(reward);
            }
        }

        for (Map.Entry<String, List<Reward>> entry : weightedPools.entrySet()) {
            Reward picked = pickWeighted(entry.getValue());
            if (picked != null) {
                applyOne(player, data, picked, context, 1.0, lines);
            }
        }
        data.setDirty();
        return new Granted(lines, choices);
    }

    /** Grants a single player-selected reward, refusing a second pick from the same pool. */
    public static boolean grantChoice(ServerPlayer player, RotasData data, Reward reward, Context context) {
        PlayerProgress progress = data.progress(player.getUUID());
        String key = context.claimKeyPrefix() + ":choice:" + reward.pool();
        if (!progress.claimOnce(key)) {
            return false;
        }
        List<Component> lines = new ArrayList<>();
        applyOne(player, data, reward, context, 1.0, lines);
        for (Component line : lines) {
            player.sendSystemMessage(line);
        }
        data.setDirty();
        return true;
    }

    private static Reward pickWeighted(List<Reward> pool) {
        int total = 0;
        for (Reward reward : pool) {
            total += reward.weight();
        }
        if (total <= 0) {
            return null;
        }
        int roll = RANDOM.nextInt(total);
        for (Reward reward : pool) {
            roll -= reward.weight();
            if (roll < 0) {
                return reward;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static void applyOne(ServerPlayer player, RotasData data, Reward reward,
                                 Context context, double scale, List<Component> lines) {
        PlayerProgress progress = data.progress(player.getUUID());
        Params params = reward.params();
        switch (reward.type()) {
            case ROTAS_XP -> {
                long amount = Math.round(params.getInt("amount", 0) * scale * context.xpScale());
                if (params.getBool("scale_with_rank", true) && context.rank() != null) {
                    amount = Math.round(amount * data.levelConfig().rankMultiplier(context.rank()));
                }
                if (params.getBool("scale_with_level", false)) {
                    amount = Math.round(amount * (1.0 + progress.level() / 100.0));
                }
                if (amount > 0) {
                    ProgressService.addExperience(player, data, amount, true);
                    lines.add(ThaiText.c("rotasutils.msg.reward.xp", amount).withStyle(ChatFormatting.GREEN));
                }
            }
            case SKILL_POINT -> {
                int amount = (int) Math.round(params.getInt("amount", 0) * scale);
                if (amount > 0) {
                    int categories = PuffishSkillsCompat.active(data)
                            ? PuffishSkillsCompat.addPointsToUnlockedCategories(player, amount) : 0;
                    if (categories == 0) {
                        progress.addSkillPoints(amount);
                    }
                    lines.add(ThaiText.c(categories > 0 ? "rotasutils.msg.level.puffish_points"
                                    : "rotasutils.msg.level.skill_points", amount)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
            case CATEGORY_POINT -> {
                int amount = (int) Math.round(params.getInt("amount", 0) * scale);
                String categoryId = params.getString("category", "");
                if (amount > 0 && !categoryId.isEmpty()) {
                    if (!PuffishSkillsCompat.active(data)
                            || !PuffishSkillsCompat.addPointsToCategory(player, categoryId, amount)) {
                        progress.addCategoryPoints(categoryId, amount);
                    }
                    lines.add(ThaiText.c("rotasutils.msg.reward.category_points", amount, categoryId)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
            case ITEM -> {
                ResourceLocation itemId = params.getId("item");
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    int amount = Math.max(1, (int) Math.round(params.getInt("amount", 1) * scale));
                    ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), amount);
                    lines.add(Component.literal(amount + "x ").append(stack.getHoverName()));
                    give(player, stack);
                }
            }
            case CURRENCY -> {
                int amount = (int) Math.round(params.getInt("amount", 0)
                        * scale * (1.0 + SkillService.multiplier(data, progress, EffectType.CURRENCY_MULTIPLIER)));
                String objective = params.getString("objective", "coins");
                RequirementChecker.addScore(player, objective, amount);
                lines.add(Component.literal("+" + amount + " " + objective).withStyle(ChatFormatting.GOLD));
            }
            case VANILLA_XP -> {
                // Compatibility alias for old quest data. Vanilla XP no longer exists as a
                // progression currency, so legacy rewards are migrated into Rotas XP.
                long amount = Math.round(params.getInt("amount", 0) * scale * context.xpScale());
                if (amount > 0) {
                    ProgressService.addExperience(player, data, amount, true);
                    lines.add(ThaiText.c("rotasutils.msg.reward.rotas_xp", amount)
                            .withStyle(ChatFormatting.GREEN));
                }
            }
            case SCOREBOARD -> RequirementChecker.addScore(player, params.getString("objective", ""),
                    (int) Math.round(params.getInt("amount", 0) * scale));
            case RANK_CLEARANCE -> {
                DangerRank rank = DangerRank.byName(params.getString("rank", "F"), DangerRank.F);
                if (progress.grantClearance(rank)) {
                    lines.add(ThaiText.c("rotasutils.msg.rank.unlocked", rank.display())
                            .withStyle(rank.color()));
                }
            }
            case UNLOCK_QUEST -> {
                String questId = params.getString("quest", "");
                if (!questId.isEmpty() && progress.unlockedQuests().add(questId)) {
                    QuestDef unlocked = data.quest(questId);
                    lines.add(ThaiText.c("rotasutils.msg.reward.new_quest",
                            unlocked == null ? questId : unlocked.name()).withStyle(ChatFormatting.YELLOW));
                }
            }
            case UNLOCK_SKILL -> {
                String skillId = params.getString("skill", "");
                int rank = Math.max(1, params.getInt("rank", 1));
                if (!skillId.isEmpty() && data.findNode(skillId) != null
                        && progress.skillRank(skillId) < rank) {
                    progress.setSkillRank(skillId, rank);
                    SkillService.recalculate(player, data);
                    lines.add(ThaiText.c("rotasutils.msg.reward.skill_unlocked", data.findNode(skillId).name())
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            case UNLOCK_CATEGORY -> {
                String categoryId = params.getString("category", "");
                if (!categoryId.isEmpty() && progress.unlockedCategories().add(categoryId)) {
                    lines.add(ThaiText.c("rotasutils.msg.reward.category_unlocked", categoryId)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
            case POTION_EFFECT -> {
                ResourceLocation effectId = params.getId("effect");
                MobEffect effect = effectId == null ? null : BuiltInRegistries.MOB_EFFECT.get(effectId);
                if (effect != null) {
                    player.addEffect(new MobEffectInstance(effect,
                            Math.max(1, params.getInt("duration", 30)) * 20,
                            Math.max(0, params.getInt("amplifier", 0))));
                }
            }
            case TELEPORT -> teleport(player, params);
            case REPUTATION -> {
                String faction = params.getString("faction", "");
                int amount = (int) Math.round(params.getInt("amount", 0) * scale);
                String key = "reputation." + faction;
                if (progress.rpg().hasReputation(faction)) {
                    progress.rpg().reputation(faction, amount);
                } else {
                    long current = progress.reputation(faction);
                    progress.questVariables().put(key, Long.toString(Math.addExact(current, amount)));
                }
                lines.add(ThaiText.c("rotasutils.msg.reward.reputation", amount, faction));
            }
            case TITLE -> {
                progress.questVariables().put("title", params.getString("title", ""));
                lines.add(ThaiText.c("rotasutils.msg.reward.title", params.getString("title", "")));
            }
            case PRESTIGE -> {
                progress.setPrestige(progress.prestige() + Math.max(1, params.getInt("amount", 1)));
                lines.add(ThaiText.c("rotasutils.msg.reward.prestige", progress.prestige()).withStyle(ChatFormatting.GOLD));
            }
            case COMMAND -> runCommand(player, params.getString("command", ""));
            case QUEST_VARIABLE -> progress.questVariables()
                    .put(params.getString("key", ""), params.getString("value", ""));
        }
        progress.markDirty();
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Runs a reward command as the server, with {@code @p} replaced by the player. */
    public static void runCommand(ServerPlayer player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        // The server setting "allow quest commands" was saved and shown but never consulted.
        if (!RotasData.get(player.server).serverSettings().allowQuestCommands()) {
            Rotasutils.LOG.info("Skipped reward command because quest commands are disabled: {}", command);
            return;
        }
        String resolved = command
                .replace("@p", player.getGameProfile().getName())
                .replace("%player%", player.getGameProfile().getName())
                .replace("%uuid%", player.getUUID().toString());
        try {
            CommandSourceStack source = player.server.createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput();
            player.server.getCommands().performPrefixedCommand(source, resolved);
        } catch (Exception e) {
            Rotasutils.LOG.warn("Reward command failed: {}", resolved, e);
        }
    }

    private static void teleport(ServerPlayer player, Params params) {
        String rawPos = params.getString("pos", "");
        BlockPos pos = parsePos(rawPos);
        if (pos == null) {
            return;
        }
        String dimension = params.getString("dimension", "");
        ServerLevel level = player.serverLevel();
        if (!dimension.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(dimension);
            if (id != null) {
                ServerLevel target = player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
                if (target != null) {
                    level = target;
                }
            }
        }
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    /** Parses the "x,y,z" strings written by the world position picker. */
    public static BlockPos parsePos(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("[,; ]+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static ResourceKey<Level> dimensionKey(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? null : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
