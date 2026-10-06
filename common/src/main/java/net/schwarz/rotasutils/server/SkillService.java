package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.schwarz.rotasutils.compat.PuffishSkillsCompat;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.skill.EffectType;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillConnection;
import net.schwarz.rotasutils.skill.SkillEffect;
import net.schwarz.rotasutils.skill.SkillNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkillService {
    private SkillService() {
    }

public record UnlockResult(boolean success, String message) {
        static UnlockResult ok(String message) {
            return new UnlockResult(true, message);
        }

        static UnlockResult no(String message) {
            return new UnlockResult(false, message);
        }
    }

    public static UnlockResult unlock(ServerPlayer player, RotasData data, String nodeId) {
        String blocked = purchaseCheck(player, data, nodeId);
        if (blocked != null) {
            return UnlockResult.no(blocked);
        }
        SkillNode node = data.findNode(nodeId);
        SkillCategory category = data.category(node.categoryId());
        PlayerProgress progress = data.progress(player.getUUID());
        int currentRank = progress.skillRank(nodeId);
        int cost = node.costForRank(currentRank);
        String jobPool=jobPool(progress,category);
        boolean categoryPool = jobPool==null && usesCategoryPool(data, category);
        if(jobPool!=null) {
            if(!progress.rpg().spendJobSkillPoints(jobPool,cost)) return UnlockResult.no(ThaiText.t("rotasutils.msg.skill.not_enough", ThaiText.t("rotasutils.msg.skill.pool_job", jobName(data,jobPool)), cost));
        } else if (categoryPool) {
            if (!progress.spendCategoryPoints(category.id(), cost)) {
                return UnlockResult.no(ThaiText.t("rotasutils.msg.skill.not_enough", ThaiText.t("rotasutils.msg.skill.pool_category", category.name()), cost));
            }
        } else if (!progress.spendSkillPoints(cost)) {
            return UnlockResult.no(ThaiText.t("rotasutils.msg.skill.not_enough", ThaiText.t("rotasutils.msg.skill.pool_skill"), cost));
        }

        for (int r = progress.skillPurchases(nodeId).size(); r < currentRank; r++) {
            progress.recordSkillPurchase(nodeId, category.id(), categoryPool ? category.id() : "", node.costForRank(r));
        }
        progress.recordSkillPurchase(nodeId, category.id(), jobPool!=null?"job:"+jobPool:categoryPool ? category.id() : "", cost);
        progress.setSkillRank(nodeId, currentRank + 1);
        if (node.type() == SkillNode.NodeType.CHOICE) {
            progress.chosenBranches().put(node.id(), node.id());
        }
        applyUnlockEffects(player, data, progress, node, currentRank + 1);
        recalculate(player, data);
        data.setDirty();
        return UnlockResult.ok(ThaiText.t("rotasutils.msg.skill.now_rank", node.name(), currentRank + 1));
    }

    public static String purchaseCheck(ServerPlayer player, RotasData data, String nodeId) {
        SkillNode node = data.findNode(nodeId);
        if (node == null) {
            return ThaiText.t("rotasutils.msg.skill.gone");
        }
        if (node.disabled()) {
            return ThaiText.t("rotasutils.msg.skill.disabled");
        }
        SkillCategory category = data.category(node.categoryId());
        if (category == null) {
            return ThaiText.t("rotasutils.msg.skill.category_gone");
        }
        PlayerProgress progress = data.progress(player.getUUID());

        String audience = audience(player, data, progress, category);
        if (audience != null) {
            return audience;
        }
        if (!categoryUnlocked(player, data, progress, category)) {
            return ThaiText.t("rotasutils.msg.skill.category_locked", category.name());
        }
        int currentRank = progress.skillRank(nodeId);
        if (currentRank >= node.maxRank()) {
            return ThaiText.t("rotasutils.msg.skill.max_rank", node.name());
        }
        if (progress.level() < node.requiredLevel(currentRank)) {
            return ThaiText.t("rotasutils.msg.common.requires_level", node.requiredLevel(currentRank));
        }
        String gate = connectionsSatisfied(data, progress, node);
        if (gate != null) {
            return gate;
        }
        String exclusion = exclusionBlocked(data, progress, node);
        if (exclusion != null) {
            return exclusion;
        }
        for (Requirement requirement : node.requirements()) {
            if (requirement.type() == RequirementType.CONNECTED_SKILLS) {
                int needed = requirement.params().getInt("amount", 1);
                if (countUnlockedConnections(data, progress, node) < needed && !requirement.recommendationOnly()) {
                    return ThaiText.t("rotasutils.msg.skill.connected_needed", needed);
                }
                continue;
            }
            CheckResult result = RequirementChecker.check(player, data, requirement);
            if (result.blocking() && !result.pass()) {
                return result.detail().isEmpty() ? result.label() : result.detail();
            }
        }

        int cost = node.costForRank(currentRank);
        String jobPool=jobPool(progress,category);
        long available = jobPool!=null?progress.rpg().jobSkillPoints(jobPool):usesCategoryPool(data, category) ? progress.categoryPoints(category.id()) : progress.skillPoints();
        if (available < cost) {
            String pool = jobPool != null ? ThaiText.t("rotasutils.msg.skill.pool_job", jobName(data, jobPool))
                    : usesCategoryPool(data, category) ? ThaiText.t("rotasutils.msg.skill.pool_category", category.name())
                    : ThaiText.t("rotasutils.msg.skill.pool_skill");
            return ThaiText.t("rotasutils.msg.skill.not_enough", pool, cost);
        }
        return null;
    }

    private static boolean usesCategoryPool(RotasData data, SkillCategory category) {
        return switch (data.levelConfig().pointMode()) {
            case GLOBAL -> false;
            case CATEGORY -> true;
            case BOTH -> category.usesCategoryPoints();
        };
    }

    public static boolean categoryUnlocked(ServerPlayer player, RotasData data,
                                           PlayerProgress progress, SkillCategory category) {
        if (audience(player, data, progress, category) != null) {
            return false;
        }
        if (progress.unlockedCategories().contains(category.id())) {
            return true;
        }
        if (category.lockedByDefault()) {
            return false;
        }
        if (progress.level() < category.minLevel()) {
            return false;
        }
        for (Requirement requirement : category.unlockRequirements()) {
            CheckResult result = RequirementChecker.check(player, data, requirement);
            if (result.blocking() && !result.pass()) {
                return false;
            }
        }
        return true;
    }

    public static String audience(ServerPlayer player, RotasData data, PlayerProgress progress, SkillCategory category) {
        if (category.jobs().isEmpty() && category.races().isEmpty()) {
            return null;
        }
        return net.schwarz.rotasutils.skill.SkillRules.audienceBlock(category,
                java.util.stream.Stream.of(progress.mainJob(),progress.subJob()).filter(id->!id.isEmpty()).toList(),
                net.schwarz.rotasutils.compat.OriginsCompat.origins(player),
                id -> data.job(id) == null ? id : data.job(id).name(), id -> id);
    }

    private static String jobPool(PlayerProgress progress,SkillCategory category) {
        if(category.jobs().contains(progress.mainJob())) return progress.mainJob();
        if(category.jobs().contains(progress.subJob())) return progress.subJob();
        return null;
    }
    private static String jobName(RotasData data,String id) { return data.job(id)==null?id:data.job(id).name(); }

    public static String connectionsSatisfied(RotasData data, PlayerProgress progress, SkillNode node) {
        return net.schwarz.rotasutils.skill.SkillRules.connectionsSatisfied(data::findNode, progress, node);
    }

    private static String exclusionBlocked(RotasData data, PlayerProgress progress, SkillNode node) {
        for (String otherId : node.exclusiveWith()) {
            if (progress.skillRank(otherId) > 0) {
                SkillNode other = data.findNode(otherId);
                return ThaiText.t("rotasutils.msg.skill.excluded", other == null ? otherId : other.name());
            }
        }
        return null;
    }

    private static int countUnlockedConnections(RotasData data, PlayerProgress progress, SkillNode node) {
        java.util.Set<String> counted = new java.util.HashSet<>();
        for (SkillConnection connection : node.connections()) {
            SkillNode source = data.findNode(connection.fromId());
            if (source != null && !source.disabled() && connection.type() != SkillConnection.Type.VISUAL_ONLY
                    && connection.type() != SkillConnection.Type.EXCLUSIVE
                    && progress.skillRank(source.id()) >= connection.requiredRank()) {
                counted.add(source.id());
            }
        }
        return counted.size();
    }

    public static int refund(PlayerProgress progress, RotasData data, String categoryId) {
        long refunded = 0;
        for (String nodeId : new ArrayList<>(progress.skillRanks().keySet())) {
            SkillNode node = data.findNode(nodeId);
            List<PlayerProgress.SkillPurchase> purchases = progress.skillPurchases(nodeId);
            String owner = purchases.isEmpty() ? (node == null ? null : node.categoryId()) : purchases.get(0).category();
            if (categoryId != null && !categoryId.equals(owner)) {
                continue;
            }
            int rank = progress.skillRank(nodeId);
            for (PlayerProgress.SkillPurchase purchase : purchases) {
                progress.refundSkillPoints(purchase.pool(), purchase.cost());
                refunded += purchase.cost();
            }
            if (purchases.size() < rank) {
                SkillCategory category = node == null ? null : data.category(node.categoryId());
                String pool = category != null && usesCategoryPool(data, category) ? category.id() : "";
                long legacyCost = 0;
                if (node == null) {
                    legacyCost = progress.legacySkills().getOrDefault(nodeId, 0);
                } else {
                    for (int r = purchases.size(); r < Math.min(50, rank); r++) {
                        legacyCost = Math.min(Integer.MAX_VALUE, legacyCost + node.costForRank(r));
                    }
                }
                int cost = (int) Math.min(Integer.MAX_VALUE, Math.max(0, legacyCost));
                progress.refundSkillPoints(pool, cost);
                refunded += cost;
            }
            if (node != null) {
                for (SkillEffect effect : node.effects()) {
                    if (effect.type() == EffectType.PERMISSION) {
                        progress.questVariables().remove("permission." + effect.params().getString("permission", ""));
                    }
                }
            }
            progress.setSkillRank(nodeId, 0);
            progress.clearSkillPurchases(nodeId);
            progress.legacySkills().remove(nodeId);
            progress.chosenBranches().remove(nodeId);
        }
        for (Map.Entry<String, Integer> owned : progress.skillRanks().entrySet()) {
            SkillNode node = owned.getValue() > 0 ? data.findNode(owned.getKey()) : null;
            if (node == null) continue;
            for (SkillEffect effect : node.effects()) {
                if (effect.type() == EffectType.PERMISSION) {
                    progress.questVariables().put("permission." + effect.params().getString("permission", ""), "true");
                }
            }
        }
        progress.markDirty();
        return (int) Math.min(Integer.MAX_VALUE, refunded);
    }

public static int reset(ServerPlayer player, RotasData data, String categoryId) {
        PlayerProgress progress = data.progress(player.getUUID());
        int refunded = refund(progress, data, categoryId);
        recalculate(player, data);
        data.setDirty();
        return refunded;
    }

private static void applyUnlockEffects(ServerPlayer player, RotasData data,
                                           PlayerProgress progress, SkillNode node, int rank) {
        for (SkillEffect effect : node.effects()) {
            switch (effect.type()) {
                case UNLOCK_RANK -> {
                    DangerRank clearance = DangerRank.byName(effect.params().getString("rank", "F"), DangerRank.F);
                    if (progress.grantClearance(clearance)) {
                        player.sendSystemMessage(ThaiText.c("rotasutils.msg.rank.unlocked", clearance.display())
                                .withStyle(clearance.color()));
                    }
                }
                case UNLOCK_CATEGORY -> progress.unlockedCategories().add(effect.params().getString("category", ""));
                case UNLOCK_QUEST -> progress.unlockedQuests().add(effect.params().getString("quest", ""));
                case ITEM_REWARD -> {
                    if (progress.claimOnce("skill_item:" + node.id() + ":" + rank)) {
                        ResourceLocation itemId = effect.params().getId("item");
                        if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                            RewardService.give(player, new net.minecraft.world.item.ItemStack(
                                    BuiltInRegistries.ITEM.get(itemId),
                                    Math.max(1, effect.params().getInt("amount", 1))));
                        }
                    }
                }
                case COMMAND -> {
                    if (progress.claimOnce("skill_cmd:" + node.id() + ":" + rank)) {
                        RewardService.runCommand(player, effect.params().getString("command", ""));
                    }
                }
                case PERMISSION -> progress.questVariables().put(
                        "permission." + effect.params().getString("permission", ""), "true");
                default -> {
                }
            }
        }
    }

    public static void recalculate(ServerPlayer player, RotasData data) {
        double previousMaxHealth = player.getMaxHealth();
        clearModifiers(player, data);
        clearPassivePotions(player);
        if (PuffishSkillsCompat.active(data)) {
            return;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        java.util.Set<String> closedCategories = new java.util.HashSet<>();
        for (SkillCategory category : data.categories().values()) {
            if (audience(player, data, progress, category) != null) {
                closedCategories.add(category.id());
            }
        }

        for (Map.Entry<String, Integer> entry : progress.skillRanks().entrySet()) {
            SkillNode node = data.findNode(entry.getKey());
            if (node == null || node.disabled() || closedCategories.contains(node.categoryId())) {
                continue;
            }
            int rank = entry.getValue();
            List<SkillEffect> effects = node.effects();
            for (int i = 0; i < effects.size(); i++) {
                SkillEffect effect = effects.get(i);
                Attribute attribute = resolveAttribute(effect);
                if (attribute == null) {
                    if (effect.type() == EffectType.POTION_EFFECT) {
                        applyPotion(player, effect, rank);
                    }
                    continue;
                }
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                double value = effect.valueAt(rank, progress.level());
                if (value == 0) {
                    continue;
                }
                UUID id = SkillEffect.modifierId(node.id(), i);
                AttributeModifier.Operation operation = switch (effect.stacking()) {
                    case ADD -> effect.percentage()
                            ? AttributeModifier.Operation.MULTIPLY_BASE
                            : AttributeModifier.Operation.ADDITION;
                    case MULTIPLY_BASE -> AttributeModifier.Operation.MULTIPLY_BASE;
                    case MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_TOTAL;
                };
                double applied = operation == AttributeModifier.Operation.ADDITION ? value : value / 100.0;
                instance.addPermanentModifier(new AttributeModifier(id,
                        "rotasutils.skill." + node.id() + "." + i, applied, operation));
            }
        }
        CharacterStatService.apply(player, data);
        double gained = player.getMaxHealth() - previousMaxHealth;
        if (gained > 0) {
            player.heal((float) gained);
        } else if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static double combatBonus(ServerPlayer player, RotasData data, PlayerProgress progress, EffectType type) {
        String stat = type.combatStat();
        if (stat == null || PuffishSkillsCompat.active(data)) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<String, Integer> entry : progress.skillRanks().entrySet()) {
            SkillNode node = data.findNode(entry.getKey());
            SkillCategory category = node == null ? null : data.category(node.categoryId());
            if (node == null || category == null || node.disabled() || audience(player, data, progress, category) != null) {
                continue;
            }
            for (SkillEffect effect : node.effects()) {
                if (effect.type() == type) {
                    double value = effect.valueAt(entry.getValue(), progress.level());
                    total += type == EffectType.DEFENSE_RATING ? value : value / 100.0;
                }
            }
        }
        return total;
    }

    public static void clearModifiers(ServerPlayer player, RotasData data) {
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (modifier.getName().startsWith("rotasutils.skill.")) {
                    instance.removeModifier(modifier.getId());
                }
            }
        }
    }

    private static void clearPassivePotions(ServerPlayer player) {
        for (MobEffectInstance active : new ArrayList<>(player.getActiveEffects())) {
            if (active.isInfiniteDuration() && active.isAmbient() && !active.isVisible()) {
                player.removeEffect(active.getEffect());
            }
        }
    }

    private static Attribute resolveAttribute(SkillEffect effect) {
        if (effect.type() == EffectType.CUSTOM_ATTRIBUTE) {
            ResourceLocation id = effect.params().getId("attribute");
            return id == null ? null : BuiltInRegistries.ATTRIBUTE.get(id);
        }
        return effect.type().attribute();
    }

    private static void applyPotion(ServerPlayer player, SkillEffect effect, int rank) {
        ResourceLocation effectId = effect.params().getId("effect");
        if (effectId == null) {
            return;
        }
        MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(effectId);
        if (mobEffect == null) {
            return;
        }
        int amplifier = Math.max(0, effect.params().getInt("amplifier", 0) + rank - 1);
        player.addEffect(new MobEffectInstance(mobEffect, MobEffectInstance.INFINITE_DURATION, amplifier, true, false, true));
    }

private static boolean jobOpen(RotasData data, PlayerProgress progress, SkillNode node) {
        SkillCategory category = data.category(node.categoryId());
        return category == null || category.jobs().isEmpty()
                || category.jobs().contains(progress.mainJob()) || category.jobs().contains(progress.subJob());
    }

    public static double experienceMultiplier(RotasData data, PlayerProgress progress, boolean questSource) {
        if (PuffishSkillsCompat.active(data)) {
            return 1.0;
        }
        double total = 1.0;
        for (Map.Entry<String, Integer> entry : progress.skillRanks().entrySet()) {
            SkillNode node = data.findNode(entry.getKey());
            if (node == null || node.disabled() || !jobOpen(data, progress, node)) {
                continue;
            }
            for (SkillEffect effect : node.effects()) {
                boolean applies = effect.type() == EffectType.ROTAS_XP_MULTIPLIER
                        || (questSource && effect.type() == EffectType.QUEST_XP_MULTIPLIER);
                if (applies) {
                    total += effect.valueAt(entry.getValue(), progress.level()) / 100.0;
                }
            }
        }
        return Math.max(0.0, total);
    }

    public static double multiplier(RotasData data, PlayerProgress progress, EffectType type) {
        if (PuffishSkillsCompat.active(data)) {
            return 0.0;
        }
        double total = 0;
        for (Map.Entry<String, Integer> entry : progress.skillRanks().entrySet()) {
            SkillNode node = data.findNode(entry.getKey());
            if (node == null || node.disabled() || !jobOpen(data, progress, node)) {
                continue;
            }
            for (SkillEffect effect : node.effects()) {
                if (effect.type() == type) {
                    total += effect.valueAt(entry.getValue(), progress.level()) / 100.0;
                }
            }
        }
        return total;
    }

    public static boolean hasFlag(RotasData data, PlayerProgress progress, EffectType type) {
        if (PuffishSkillsCompat.active(data)) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : progress.skillRanks().entrySet()) {
            SkillNode node = data.findNode(entry.getKey());
            if (node == null || node.disabled() || !jobOpen(data, progress, node)) {
                continue;
            }
            for (SkillEffect effect : node.effects()) {
                if (effect.type() == type) {
                    return true;
                }
            }
        }
        return false;
    }
}
