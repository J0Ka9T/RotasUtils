package net.schwarz.rotasutils.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.quest.reward.RewardType;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillConnection;
import net.schwarz.rotasutils.skill.SkillEffect;
import net.schwarz.rotasutils.skill.SkillNode;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Validation {
    private Validation() {
    }

    public enum Severity {
        ERROR, WARNING, INFO
    }

    public record Issue(Severity severity, String targetKind, String targetId, String field, String message) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("severity", severity.name());
            tag.putString("kind", targetKind);
            tag.putString("id", targetId);
            tag.putString("field", field);
            tag.putString("message", message);
            return tag;
        }

        public static Issue load(CompoundTag tag) {
            return new Issue(Nbt.readEnum(tag, "severity", Severity.class, Severity.ERROR),
                    tag.getString("kind"), tag.getString("id"), tag.getString("field"), tag.getString("message"));
        }
    }

    public static List<Issue> validateAll(RotasData data) {
        List<Issue> issues = new ArrayList<>();
        Set<String> seenQuestIds = new HashSet<>();
        for (QuestDef quest : data.quests().values()) {
            if (!seenQuestIds.add(quest.id())) {
                issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "id", "Duplicate quest id."));
            }
            issues.addAll(validateQuest(data, quest));
        }
        for (BoardConfig board : data.boards().values()) {
            issues.addAll(validateBoard(data, board));
        }
        Set<String> boundEntities = new HashSet<>();
        for (net.schwarz.rotasutils.npc.NpcDef npc : data.npcs().values()) {
            issues.addAll(validateNpc(data, npc));
            if (npc.bound() && !boundEntities.add(npc.entityUuid())) {
                issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "entity",
                        "Two NPCs are bound to the same entity; only one can answer a click."));
            }
        }
        Set<String> seenNodeIds = new HashSet<>();
        for (SkillCategory category : data.categories().values()) {
            issues.addAll(validateCategory(data, category, seenNodeIds));
        }
        issues.addAll(validateQuestChains(data));
        return issues;
    }

public static List<Issue> validateQuest(RotasData data, QuestDef quest) {
        List<Issue> issues = new ArrayList<>();
        if (quest.id().isBlank()) {
            issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "id", "Quest has no id."));
        }
        if (quest.name().isBlank()) {
            issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "name", "Quest has no name."));
        }
        if (quest.objectives().isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "objectives",
                    "Quest has no objectives, so it can never be completed."));
        }
        boolean hasRequired = false;
        for (Objective objective : quest.objectives()) {
            if (!objective.optional()) {
                hasRequired = true;
            }
        }
        if (!quest.objectives().isEmpty() && !hasRequired) {
            issues.add(new Issue(Severity.WARNING, "quest", quest.id(), "objectives",
                    "Every objective is optional; the quest completes immediately."));
        }
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            issues.addAll(validateParams(data, "quest", quest.id(), "objective." + i,
                    objective.params(), objective.type().specs()));
            if (objective.requiredAmount() <= 0) {
                issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "objective." + i + ".amount",
                        "Objective " + (i + 1) + " requires zero of something."));
            }
            if (objective.type() == ObjectiveType.REACH_LOCATION
                    && RewardService.parsePos(objective.params().getString("pos", "")) == null) {
                issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "objective." + i + ".pos",
                        "Objective " + (i + 1) + " has no target position, so it is unreachable."));
            }
            if (objective.type() == ObjectiveType.COMPLETE_QUEST) {
                String other = objective.params().getString("quest", "");
                if (other.equals(quest.id())) {
                    issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "objective." + i + ".quest",
                            "Objective " + (i + 1) + " requires this quest itself."));
                } else if (!other.isEmpty() && data.quest(other) == null) {
                    issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "objective." + i + ".quest",
                            "Objective " + (i + 1) + " references a quest that does not exist."));
                }
            }
        }
        for (int i = 0; i < quest.requirements().size(); i++) {
            Requirement requirement = quest.requirements().get(i);
            issues.addAll(validateParams(data, "quest", quest.id(), "requirement." + i,
                    requirement.params(), requirement.type().specs()));
            issues.addAll(validateReference(data, "quest", quest.id(), "requirement." + i,
                    requirement.type(), requirement.params()));
        }
        for (int i = 0; i < quest.rewards().size(); i++) {
            Reward reward = quest.rewards().get(i);
            issues.addAll(validateParams(data, "quest", quest.id(), "reward." + i,
                    reward.params(), reward.type().specs()));
            if (reward.type() == RewardType.UNLOCK_QUEST) {
                String other = reward.params().getString("quest", "");
                if (!other.isEmpty() && data.quest(other) == null) {
                    issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "reward." + i + ".quest",
                            "Reward " + (i + 1) + " unlocks a quest that does not exist."));
                }
            }
            if (reward.type() == RewardType.UNLOCK_SKILL) {
                String skill = reward.params().getString("skill", "");
                if (!skill.isEmpty() && data.findNode(skill) == null) {
                    issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "reward." + i + ".skill",
                            "Reward " + (i + 1) + " unlocks a skill that does not exist."));
                }
            }
        }
        Set<String> paths = new HashSet<>();
        quest.objectives().forEach(objective -> { if (!objective.opens().isEmpty()) paths.add(objective.opens()); });
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            if (!objective.path().isEmpty() && !paths.contains(objective.path())) {
                issues.add(new Issue(Severity.WARNING, "quest", quest.id(), "objective." + i + ".path",
                        "Objective " + (i + 1) + " is on path '" + objective.path() + "', but no choice picks that path."));
            }
            if (!objective.opens().isEmpty() && !objective.path().isEmpty()) {
                issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "objective." + i + ".path",
                        "Objective " + (i + 1) + " is a choice; choices cannot also belong to a path."));
            }
        }
        for (int i = 0; i < quest.rewards().size(); i++) {
            String path = quest.rewards().get(i).path();
            if (!path.isEmpty() && !paths.contains(path)) {
                issues.add(new Issue(Severity.WARNING, "quest", quest.id(), "reward." + i + ".path",
                        "Reward " + (i + 1) + " is for path '" + path + "', but no choice picks that path."));
            }
        }
        if (quest.followUp() && data.quests().values().stream().noneMatch(other -> other.rewards().stream()
                .anyMatch(r -> r.type() == RewardType.UNLOCK_QUEST && quest.id().equals(r.params().getString("quest", ""))))) {
            issues.add(new Issue(Severity.WARNING, "quest", quest.id(), "follow_up",
                    "Follow-up quest, but no quest has an Unlock Quest reward for it, so nobody will receive it."));
        }
        if (quest.published() && quest.boardIds().isEmpty()) {
            boolean onAnyBoard = false;
            for (BoardConfig board : data.boards().values()) {
                if (board.questIds().contains(quest.id())
                        || board.categoryFilters().contains(quest.category())
                        || board.rankFilters().contains(quest.rank())) {
                    onAnyBoard = true;
                    break;
                }
            }
            boolean fromNpc = data.npcs().values().stream().anyMatch(npc -> npc.questIds().contains(quest.id()));
            if (!onAnyBoard && !fromNpc && !quest.followUp()) {
                issues.add(new Issue(Severity.WARNING, "quest", quest.id(), "boards",
                        "Quest is published but no board or NPC hands it out, so players cannot find it."));
            }
        }
        if (quest.requiredLevel() > quest.recommendedLevel() && quest.recommendedLevel() > 0) {
            issues.add(new Issue(Severity.WARNING, "quest", quest.id(), "rec_level",
                    "Required level is higher than the recommended level."));
        }
        if (quest.requiredLevel() > data.levelConfig().curve().maxLevel()) {
            issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "req_level",
                    "Required level is above the server maximum level; this quest is impossible."));
        }
        return issues;
    }

public static List<Issue> validateNpc(RotasData data, net.schwarz.rotasutils.npc.NpcDef npc) {
        List<Issue> issues = new ArrayList<>();
        if (npc.id().isBlank()) {
            issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "id", "NPC has no id."));
        }
        if (npc.name().isBlank()) {
            issues.add(new Issue(Severity.WARNING, "npc", npc.id(), "name", "NPC has no name."));
        }
        if (!npc.bound()) {
            issues.add(new Issue(Severity.WARNING, "npc", npc.id(), "entity",
                    "NPC is not bound to an entity yet, so nothing in the world opens it."));
        }
        for (String questId : npc.questIds()) {
            QuestDef quest = data.quest(questId);
            if (quest == null) {
                issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "quests",
                        "NPC offers a quest that does not exist: " + questId));
            } else if (!quest.published()) {
                issues.add(new Issue(Severity.WARNING, "npc", npc.id(), "quests",
                        "NPC offers an unpublished quest, which players cannot see: " + questId));
            }
        }
        if (!npc.boardId().isBlank() && data.board(npc.boardId()) == null) {
            issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "board",
                    "NPC opens a board that does not exist: " + npc.boardId()));
        }
        if (npc.role() == net.schwarz.rotasutils.npc.NpcDef.Role.QUEST_GIVER && npc.questIds().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "npc", npc.id(), "quests",
                    "Quest giver has no quests assigned."));
        }
        if (npc.role() == net.schwarz.rotasutils.npc.NpcDef.Role.BOARD_KEEPER && npc.boardId().isBlank()) {
            issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "board",
                    "Board keeper has no board to open."));
        }
        if (npc.role() == net.schwarz.rotasutils.npc.NpcDef.Role.MERCHANT && npc.merchantId().isBlank()) {
            issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "merchant",
                    "Merchant has no merchant id to trade from."));
        }
        if (!npc.merchantId().isBlank()) {
            var id = ResourceLocation.tryParse(npc.merchantId());
            if (id == null || data.kernel() == null || !data.kernel().content().merchants().containsKey(new net.schwarz.rotasutils.core.ContentId(id.toString())))
                issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "merchant", "Merchant does not exist: " + npc.merchantId()));
        }
        for (var service : npc.services()) {
            if (!service.jobId().isBlank() && data.job(service.jobId()) == null) {
                issues.add(new Issue(Severity.ERROR,"npc",npc.id(),"services","Service references a missing job: "+service.jobId()));
            }
            if (service.type() == net.schwarz.rotasutils.npc.NpcServiceDef.Type.TRAINER
                    && !service.targetId().isBlank() && data.findNode(service.targetId()) == null) {
                issues.add(new Issue(Severity.ERROR,"npc",npc.id(),"services","Trainer references a missing skill: "+service.targetId()));
            }
        }
        if (npc.role() == net.schwarz.rotasutils.npc.NpcDef.Role.CRAFTER) {
            var crafter = npc.crafterService();
            var craftJob = crafter == null ? null : data.job(crafter.jobId());
            if (crafter == null) {
                issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "services", "Artisan has no job to craft for."));
            } else if (craftJob != null && craftJob.production().stream().noneMatch(row -> !row.selector().startsWith("#")
                    && (row.activity() == net.schwarz.rotasutils.job.JobDef.ProductionEntry.Activity.CRAFT
                    || row.activity() == net.schwarz.rotasutils.job.JobDef.ProductionEntry.Activity.SMELT))) {
                issues.add(new Issue(Severity.WARNING, "npc", npc.id(), "services",
                        "Artisan's job has no crafting or smelting unlocks to offer: " + craftJob.id()));
            }
        }
        if (npc.build() != null) {
            npc.build().statAllocations().forEach((id,points)->{ if(net.schwarz.rotasutils.stat.CoreStat.byId(id)==null) issues.add(new Issue(Severity.ERROR,"npc",npc.id(),"build.stats","Combat build references a missing stat: "+id)); });
            npc.build().skillRanks().forEach((id,rank)->{
                SkillNode node=data.findNode(id);
                if(node==null) issues.add(new Issue(Severity.ERROR,"npc",npc.id(),"build.skills","Combat build references a missing skill: "+id));
                else if(rank>node.maxRank()) issues.add(new Issue(Severity.ERROR,"npc",npc.id(),"build.skills","Skill rank exceeds maximum: "+id));
            });
        }
        var conversation = npc.interactions();
        if (conversation != null) {
            for (var node : conversation.nodes().values()) for (var choice : node.choices()) {
                String field = "dialogue." + node.id() + "." + choice.id();
                if (!choice.when().quest().isBlank() && data.quest(choice.when().quest()) == null)
                    issues.add(new Issue(Severity.ERROR, "npc", npc.id(), field, "Condition quest does not exist: " + choice.when().quest()));
                var action = choice.action();
                if (Set.of("start_quest", "turn_in").contains(action.type()) && !npc.questIds().contains(action.target()))
                    issues.add(new Issue(Severity.ERROR, "npc", npc.id(), field, "Assign the action's quest to this NPC: " + action.target()));
                validateNpcGrants(data, npc.id(), field, action.rewards(), issues);
            }
            for (var gift : conversation.gifts()) {
                for (String selector : gift.items()) {
                    var id = ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector);
                    boolean valid = id != null && (selector.startsWith("#")
                            ? BuiltInRegistries.ITEM.getTagNames().anyMatch(tag -> tag.location().equals(id))
                            : BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR);
                    if (!valid) issues.add(new Issue(Severity.ERROR, "npc", npc.id(), "gift." + gift.id(), "Gift item or tag does not exist: " + selector));
                }
                validateNpcGrants(data, npc.id(), "gift." + gift.id(), gift.rewards(), issues);
            }
        }
        return issues;
    }

    private static void validateNpcGrants(RotasData data, String npc, String field,
                                         List<net.schwarz.rotasutils.core.NpcInteractions.Grant> grants, List<Issue> issues) {
        for (var grant : grants) {
            if (grant.type().equals("item")) {
                var id = ResourceLocation.tryParse(grant.id());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == net.minecraft.world.item.Items.AIR)
                    issues.add(new Issue(Severity.ERROR, "npc", npc, field, "Reward item does not exist: " + grant.id()));
            } else if (grant.type().equals("quest_unlock") && data.quest(grant.id()) == null) {
                issues.add(new Issue(Severity.ERROR, "npc", npc, field, "Unlock quest does not exist: " + grant.id()));
            }
        }
    }

public static List<Issue> validateBoard(RotasData data, BoardConfig board) {
        List<Issue> issues = new ArrayList<>();
        if (board.name().isBlank()) {
            issues.add(new Issue(Severity.WARNING, "board", board.id(), "name", "Board has no name."));
        }
        for (String questId : board.questIds()) {
            if (data.quest(questId) == null) {
                issues.add(new Issue(Severity.ERROR, "board", board.id(), "quests",
                        "Board lists a quest that does not exist: " + questId));
            }
        }
        if (!board.featuredQuestId().isEmpty() && data.quest(board.featuredQuestId()) == null) {
            issues.add(new Issue(Severity.ERROR, "board", board.id(), "featured",
                    "Featured quest does not exist."));
        }
        if (board.questIds().isEmpty() && board.categoryFilters().isEmpty()
                && board.rankFilters().isEmpty() && board.factionFilters().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "board", board.id(), "quests", "Board has an empty quest pool."));
        }
        if (board.maxPlayerLevel() > 0 && board.minPlayerLevel() > board.maxPlayerLevel()) {
            issues.add(new Issue(Severity.ERROR, "board", board.id(), "min_level",
                    "Minimum level is above the maximum level; no player can use this board."));
        }
        return issues;
    }

public static List<Issue> validateCategory(RotasData data, SkillCategory category, Set<String> seenNodeIds) {
        List<Issue> issues = new ArrayList<>();
        if (category.nodes().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "category", category.id(), "nodes",
                    "Category has no skill nodes."));
            return issues;
        }
        boolean hasRoot = false;
        for (SkillNode node : category.nodes().values()) {
            if (node.root() || node.connections().isEmpty()) {
                hasRoot = true;
            }
            if (!seenNodeIds.add(node.id())) {
                issues.add(new Issue(Severity.ERROR, "node", node.id(), "id",
                        "Duplicate skill node id: " + node.id()));
            }
            if (node.costPerRank() <= 0 && node.type() != SkillNode.NodeType.UNLOCK) {
                issues.add(new Issue(Severity.WARNING, "node", node.id(), "cost",
                        node.name() + " costs zero points."));
            }
            for (SkillConnection connection : node.connections()) {
                SkillNode source = data.findNode(connection.fromId());
                if (source == null) {
                    issues.add(new Issue(Severity.ERROR, "node", node.id(), "connections",
                            node.name() + " links to a node that does not exist: " + connection.fromId()));
                } else if (connection.requiredRank() > source.maxRank()) {
                    issues.add(new Issue(Severity.ERROR, "node", node.id(), "connections",
                            node.name() + " requires " + source.name() + " rank " + connection.requiredRank()
                                    + " but its maximum rank is " + source.maxRank() + "."));
                }
            }
            Set<Integer> seenModifierSlots = new HashSet<>();
            for (int i = 0; i < node.effects().size(); i++) {
                SkillEffect effect = node.effects().get(i);
                issues.addAll(validateParams(data, "node", node.id(), "effect." + i,
                        effect.params(), effect.type().specs()));
                if (!seenModifierSlots.add(i)) {
                    issues.add(new Issue(Severity.ERROR, "node", node.id(), "effect." + i,
                            "Duplicate attribute modifier identifier."));
                }
            }
            for (String exclusive : node.exclusiveWith()) {
                if (data.findNode(exclusive) == null) {
                    issues.add(new Issue(Severity.ERROR, "node", node.id(), "exclusive",
                            node.name() + " excludes a node that does not exist: " + exclusive));
                }
            }
        }
        if (!hasRoot) {
            issues.add(new Issue(Severity.ERROR, "category", category.id(), "nodes",
                    "Category has no root node, so nothing can be unlocked."));
        }
        issues.addAll(findSkillCycles(data, category));
        issues.addAll(findUnreachableNodes(data, category));
        return issues;
    }

    private static List<Issue> findSkillCycles(RotasData data, SkillCategory category) {
        List<Issue> issues = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> done = new HashSet<>();
        for (SkillNode node : category.nodes().values()) {
            if (hasCycle(data, node.id(), visiting, done)) {
                issues.add(new Issue(Severity.ERROR, "node", node.id(), "connections",
                        "Circular skill requirement involving " + node.name() + "."));
            }
        }
        return issues;
    }

    private static boolean hasCycle(RotasData data, String nodeId, Set<String> visiting, Set<String> done) {
        if (done.contains(nodeId)) {
            return false;
        }
        if (!visiting.add(nodeId)) {
            return true;
        }
        SkillNode node = data.findNode(nodeId);
        if (node != null) {
            for (SkillConnection connection : node.connections()) {
                if (connection.type() == SkillConnection.Type.VISUAL_ONLY) {
                    continue;
                }
                if (hasCycle(data, connection.fromId(), visiting, done)) {
                    return true;
                }
            }
        }
        visiting.remove(nodeId);
        done.add(nodeId);
        return false;
    }

    private static List<Issue> findUnreachableNodes(RotasData data, SkillCategory category) {
        List<Issue> issues = new ArrayList<>();
        Set<String> reachable = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (SkillNode node : category.nodes().values()) {
            if (node.root() || node.connections().isEmpty()) {
                reachable.add(node.id());
                queue.add(node.id());
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (SkillNode node : category.nodes().values()) {
                if (reachable.contains(node.id())) {
                    continue;
                }
                for (SkillConnection connection : node.connections()) {
                    if (connection.fromId().equals(current)
                            && connection.type() != SkillConnection.Type.EXCLUSIVE) {
                        reachable.add(node.id());
                        queue.add(node.id());
                        break;
                    }
                }
            }
        }
        for (SkillNode node : category.nodes().values()) {
            if (!reachable.contains(node.id())) {
                issues.add(new Issue(Severity.ERROR, "node", node.id(), "connections",
                        node.name() + " cannot be reached from any root node."));
            }
        }
        return issues;
    }

private static List<Issue> validateQuestChains(RotasData data) {
        List<Issue> issues = new ArrayList<>();
        for (QuestDef quest : data.quests().values()) {
            Set<String> seen = new HashSet<>();
            if (chainCycle(data, quest.id(), seen)) {
                issues.add(new Issue(Severity.ERROR, "quest", quest.id(), "requirements",
                        "Circular quest chain involving " + quest.name() + "."));
            }
        }
        return issues;
    }

    private static boolean chainCycle(RotasData data, String questId, Set<String> seen) {
        if (!seen.add(questId)) {
            return true;
        }
        QuestDef quest = data.quest(questId);
        if (quest != null) {
            for (Requirement requirement : quest.requirements()) {
                if (requirement.type() == RequirementType.QUEST_COMPLETED) {
                    String other = requirement.params().getString("quest", "");
                    if (!other.isEmpty() && chainCycle(data, other, seen)) {
                        return true;
                    }
                }
            }
        }
        seen.remove(questId);
        return false;
    }

private static List<Issue> validateParams(RotasData data, String kind, String targetId, String prefix,
                                              Params params, List<ParamSpec> specs) {
        List<Issue> issues = new ArrayList<>();
        for (ParamSpec spec : specs) {
            String raw = params.getString(spec.key(), "");
            if (raw.isBlank()) {
                continue;
            }
            switch (spec.kind()) {
                case ITEM -> {
                    ResourceLocation id = ResourceLocation.tryParse(raw);
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Invalid item: " + raw));
                    }
                }
                case ENTITY -> {
                    ResourceLocation id = ResourceLocation.tryParse(raw);
                    if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Invalid entity: " + raw));
                    }
                }
                case BLOCK -> {
                    ResourceLocation id = ResourceLocation.tryParse(raw);
                    if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Invalid block: " + raw));
                    }
                }
                case EFFECT -> {
                    ResourceLocation id = ResourceLocation.tryParse(raw);
                    if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Invalid mob effect: " + raw));
                    }
                }
                case ATTRIBUTE -> {
                    ResourceLocation id = ResourceLocation.tryParse(raw);
                    if (id == null || !BuiltInRegistries.ATTRIBUTE.containsKey(id)) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Invalid attribute: " + raw));
                    }
                }
                case QUEST -> {
                    if (data.quest(raw) == null) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Quest does not exist: " + raw));
                    }
                }
                case SKILL -> {
                    if (data.findNode(raw) == null) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Skill does not exist: " + raw));
                    }
                }
                case CATEGORY -> {
                    if (data.category(raw) == null) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Skill category does not exist: " + raw));
                    }
                }
                case BOARD -> {
                    if (data.board(raw) == null) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Quest board does not exist: " + raw));
                    }
                }
                case NPC -> {
                    try { java.util.UUID.fromString(raw); }
                    catch (IllegalArgumentException error) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Select an NPC in the world; invalid UUID: " + raw));
                    }
                }
                case POS -> {
                    if (RewardService.parsePos(raw) == null) {
                        issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + "." + spec.key(),
                                "Invalid position: " + raw));
                    }
                }
                default -> {
                }
            }
        }
        return issues;
    }

    private static List<Issue> validateReference(RotasData data, String kind, String targetId, String prefix,
                                                 RequirementType type, Params params) {
        List<Issue> issues = new ArrayList<>();
        if (type == RequirementType.QUEST_COMPLETED) {
            String quest = params.getString("quest", "");
            if (!quest.isEmpty() && data.quest(quest) == null) {
                issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + ".quest",
                        "Requirement references a quest that does not exist."));
            }
        }
        if (type == RequirementType.MIN_LEVEL
                && params.getInt("level", 1) > data.levelConfig().curve().maxLevel()) {
            issues.add(new Issue(Severity.ERROR, kind, targetId, prefix + ".level",
                    "Required level is above the server maximum; this requirement is impossible."));
        }
        return issues;
    }
}
