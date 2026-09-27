package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.EventKind;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes gameplay events into objective counters.
 *
 * <p>Only the quests a player currently has accepted are visited, and inside each,
 * only the objectives whose {@link EventKind} matches. When a candidate objective
 * rejects an event the reason is recorded so the quest tester can explain it.
 */
public final class ObjectiveEngine {
    /** Last rejection reason per player, consumed by the admin quest tester. */
    private static final java.util.Map<java.util.UUID, String> LAST_REJECTION = new java.util.HashMap<>();

    private ObjectiveEngine() {
    }

    public static String lastRejection(ServerPlayer player) {
        return LAST_REJECTION.getOrDefault(player.getUUID(), "");
    }

    private static void reject(ServerPlayer player, String reason) {
        LAST_REJECTION.put(player.getUUID(), reason);
    }

    public static void forget(java.util.UUID playerId) {
        LAST_REJECTION.remove(playerId);
    }

    /** Drops every remembered rejection message when the server stops. */
    public static void clear() {
        LAST_REJECTION.clear();
    }

    /** Tells the event catalogue what happened, with the entity, item or block it happened to. */
    private static void reportToCatalogue(ServerPlayer player, RotasData data, QuestEvent event) {
        var type = net.schwarz.rotasutils.event.EventType.of(event.kind());
        if (type == null) {
            return;
        }
        try {
            net.schwarz.rotasutils.server.EventService.fire(player, data, type, subjectOf(event, type));
            if (type == net.schwarz.rotasutils.event.EventType.KILL_ENTITY && event.boss()) {
                net.schwarz.rotasutils.server.EventService.fire(player, data,
                        net.schwarz.rotasutils.event.EventType.KILL_BOSS, subjectOf(event, type));
            }
        } catch (RuntimeException failure) {
            net.schwarz.rotasutils.Rotasutils.LOG.error("Event catalogue failed for {}: {}",
                    event.kind(), failure.getMessage());
        }
    }

    /** The id a rule's filter is matched against for this event. */
    private static String subjectOf(QuestEvent event, net.schwarz.rotasutils.event.EventType type) {
        return switch (type.subject()) {
            case ENTITY -> event.entityType() == null ? "" : event.entityType().toString();
            case ITEM -> event.stack() == null || event.stack().isEmpty() ? ""
                    : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.stack().getItem()));
            case BLOCK -> event.blockId() == null ? "" : event.blockId().toString();
            case NONE -> "";
        };
    }

    public static void handle(ServerPlayer player, RotasData data, QuestEvent event) {
        RpgKernel.objectiveEvent(player, event);
        // One bridge to the event catalogue. Every objective event already passes through here with the
        // thing it happened to, so the catalogue needs no second set of call sites to stay complete.
        reportToCatalogue(player, data, event);
        // Pickup events may fire before insertion; the coarse inventory refresh observes
        // the actual carried stacks, including grants, crafting and dropped items.
        if (event.kind() == EventKind.COLLECT_ITEM) {
            return;
        }
        PlayerProgress progress = data.peek(player.getUUID());
        if (progress == null || progress.activeQuests().isEmpty()) {
            return;
        }
        boolean changed = false;
        List<String> completedNow = new ArrayList<>();

        for (ActiveQuest active : new ArrayList<>(progress.activeQuests().values())) {
            QuestDef quest = data.quest(active.questId());
            if (quest == null) {
                continue;
            }
            migrate(active, quest);
            boolean questChanged = false;
            List<Objective> objectives = quest.objectives();
            boolean[] unlocked = new boolean[objectives.size()];
            for (int i = 0; i < objectives.size(); i++) {
                unlocked[i] = stepUnlocked(quest, active, objectives.get(i), i);
            }

            for (int index = 0; index < objectives.size(); index++) {
                Objective objective = objectives.get(index);
                if (objective.type().eventKind() != event.kind() || active.isComplete(index)) {
                    continue;
                }
                if (!unlocked[index]) {
                    continue;
                }
                String rejection = matches(player, data, objective, event);
                if (rejection != null) {
                    reject(player, rejection);
                    continue;
                }
                int required = objective.requiredAmount();
                int previous = active.progress(index);
                int updated = (int) Math.min(required, (long) previous + event.amount());
                if (updated == previous) {
                    continue;
                }
                active.setProgress(index, updated);
                // Credit only the ticks the objective actually consumed: the counter caps at
                // the required amount, so the raw event amount would over-credit
                // contribution-based rewards on the completing blow.
                active.addContribution(updated - previous);
                questChanged = true;
                if (updated >= required) {
                    active.setComplete(index, true);
                    completeAlternatives(quest, active, objective);
                    QuestService.notifyObjectiveComplete(player, quest, objective);
                }
            }

            if (questChanged) {
                changed = true;
                if (QuestService.allRequiredComplete(quest, active) && !active.turnInReady()) {
                    active.setTurnInReady(true);
                    completedNow.add(active.questId());
                }
                QuestService.shareWithParty(player, data, quest, active);
            }
        }

        if (changed) {
            progress.markDirty();
            data.setDirty();
            net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
        }
        for (String questId : completedNow) {
            QuestDef quest = data.quest(questId);
            if (quest != null) {
                QuestService.notifyReadyForTurnIn(player, quest);
            }
        }
    }

    public static void refreshInventory(ServerPlayer player, RotasData data) {
        PlayerProgress progress = data.peek(player.getUUID());
        if (progress == null || progress.activeQuests().isEmpty()) return;
        List<ItemStack> stacks = carriedStacks(player);
        boolean changed = false;
        for (ActiveQuest active : progress.activeQuests().values()) {
            QuestDef quest = data.quest(active.questId());
            if (quest == null) continue;
            boolean wasReady = active.turnInReady();
            if (QuestInventory.refresh(quest, active, stacks)) {
                changed = true;
                if (!wasReady && active.turnInReady()) QuestService.notifyReadyForTurnIn(player, quest);
            }
        }
        if (changed) {
            progress.markDirty();
            data.setDirty();
            net.schwarz.rotasutils.network.RotasNetwork.syncProgress(player);
        }
    }

    /**
     * Hands carried items in against a DELIVER_ITEM objective. Stacks are matched with the same
     * rules the objective applies to events (item or tag, name, model data, durability), and the
     * delivery honours sequential steps, alternative groups and party sharing like an event does.
     */
    public static QuestService.ActionResult deliver(ServerPlayer player, RotasData data, String questId, int index) {
        QuestDef quest = data.quest(questId);
        PlayerProgress progress = data.progress(player.getUUID());
        ActiveQuest active = progress.active(questId);
        if (quest == null || active == null) {
            return QuestService.ActionResult.no(ThaiText.t("rotasutils.msg.quest.not_accepted"));
        }
        migrate(active, quest);
        if (index < 0 || index >= quest.objectives().size()) {
            return QuestService.ActionResult.no(ThaiText.t("rotasutils.msg.obj.no_objective"));
        }
        Objective objective = quest.objectives().get(index);
        if (objective.type() != ObjectiveType.DELIVER_ITEM || active.isComplete(index)) {
            return QuestService.ActionResult.no(ThaiText.t("rotasutils.msg.obj.nothing"));
        }
        if (!stepUnlocked(quest, active, objective, index)) {
            return QuestService.ActionResult.no(ThaiText.t("rotasutils.msg.obj.earlier"));
        }
        Params params = objective.params();
        // Without an item or a tag the matcher would accept any stack at all.
        if (params.getId("item") == null && params.getString("item_tag", "").isEmpty()) {
            return QuestService.ActionResult.no(ThaiText.t("rotasutils.msg.obj.misconfigured"));
        }
        int missing = objective.requiredAmount() - active.progress(index);
        List<ItemStack> matching = new ArrayList<>();
        int have = 0;
        for (ItemStack stack : carriedStacks(player)) {
            if (!stack.isEmpty()
                    && matches(player, data, objective, new QuestEvent(EventKind.DELIVER_ITEM).stack(stack)) == null) {
                matching.add(stack);
                have += stack.getCount();
            }
        }
        if (have <= 0) {
            return QuestService.ActionResult.no(ThaiText.t("rotasutils.msg.obj.not_carrying"));
        }
        if (!params.getBool("allow_partial", true) && have < missing) {
            return QuestService.ActionResult.no(ThaiText.t("rotasutils.msg.obj.all_at_once"));
        }
        int delivering = Math.max(0, Math.min(missing, have));
        if (params.getBool("consume", true)) {
            int left = delivering;
            for (ItemStack stack : matching) {
                if (left <= 0) {
                    break;
                }
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
            player.getInventory().setChanged();
        }
        active.setProgress(index, active.progress(index) + delivering);
        active.addContribution(delivering);
        if (active.progress(index) >= objective.requiredAmount()) {
            active.setComplete(index, true);
            completeAlternatives(quest, active, objective);
            QuestService.notifyObjectiveComplete(player, quest, objective);
        }
        if (QuestService.allRequiredComplete(quest, active) && !active.turnInReady()) {
            active.setTurnInReady(true);
            QuestService.notifyReadyForTurnIn(player, quest);
        }
        QuestService.shareWithParty(player, data, quest, active);
        progress.markDirty();
        data.setDirty();
        return QuestService.ActionResult.ok(ThaiText.t("rotasutils.msg.obj.delivered", delivering));
    }

    static List<ItemStack> carriedStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    /** Keeps a running quest usable after its template is republished. */
    public static void migrate(ActiveQuest active, QuestDef quest) {
        if (active.objectiveCount() != quest.objectives().size()) {
            active.resize(quest.objectives().size());
        }
        if (active.questVersion() != quest.version()) {
            active.setQuestVersion(quest.version());
        }
    }

    public static boolean stepUnlocked(QuestDef quest, ActiveQuest active, Objective objective, int index) {
        if (quest.objectiveMode() != QuestDef.ObjectiveMode.SEQUENTIAL) {
            return true;
        }
        int step = objective.step();
        List<Objective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (i == index) {
                continue;
            }
            Objective other = objectives.get(i);
            if (other.optional()) {
                continue;
            }
            if (other.step() < step && !active.isComplete(i)) {
                return false;
            }
        }
        return true;
    }

    /** Alternative-group siblings count as done as soon as one of them completes. */
    private static void completeAlternatives(QuestDef quest, ActiveQuest active, Objective completed) {
        if (completed.alternativeGroup().isBlank()) {
            return;
        }
        List<Objective> objectives = quest.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            Objective other = objectives.get(i);
            if (other != completed && other.alternativeGroup().equals(completed.alternativeGroup())) {
                active.setComplete(i, true);
                active.setProgress(i, other.requiredAmount());
            }
        }
    }

    /** Returns null when the event counts, otherwise a player-readable reason. */
    public static String matches(ServerPlayer player, RotasData data, Objective objective, QuestEvent event) {
        Params params = objective.params();
        return switch (objective.type()) {
            case KILL_MOB, KILL_BOSS -> matchKill(player, objective, params, event);
            case KILL_PLAYER -> matchPlayerKill(player, data, params, event);
            case DELIVER_ITEM -> matchItem(params, event, ThaiText.t("rotasutils.msg.obj.deliver_mismatch"));
            case COLLECT_ITEM -> matchItem(params, event, ThaiText.t("rotasutils.msg.obj.collect_mismatch"));
            case TALK_NPC -> matchNpc(params, event);
            case REACH_LOCATION, STAY_IN_REGION, DEFEND_AREA, ESCORT_NPC -> matchLocation(params, event);
            case BREAK_BLOCK, PLACE_BLOCK, INTERACT_BLOCK -> matchBlock(params, event);
            case INTERACT_ENTITY -> matchEntityType(params, event);
            case CRAFT_ITEM, SMELT_ITEM, USE_ITEM -> matchItem(params, event, ThaiText.t("rotasutils.msg.obj.item_mismatch"));
            case DIALOGUE_CHOICE -> {
                String dialogue = params.getString("dialogue", "");
                String choice = params.getString("choice", "");
                if (!dialogue.isEmpty() && !dialogue.equals(event.dialogueId())) {
                    yield ThaiText.t("rotasutils.msg.obj.wrong_dialogue");
                }
                yield choice.isEmpty() || choice.equals(event.choiceId()) ? null : ThaiText.t("rotasutils.msg.obj.wrong_choice");
            }
            case COMPLETE_QUEST -> {
                String questId = params.getString("quest", "");
                yield questId.isEmpty() || questId.equals(event.customId()) ? null : ThaiText.t("rotasutils.msg.obj.different_quest");
            }
            case CUSTOM, CUSTOM_EVENT -> {
                String eventId = params.getString("event", "");
                yield eventId.isEmpty() || eventId.equals(event.customId()) ? null : ThaiText.t("rotasutils.msg.obj.different_event");
            }
        };
    }

    private static String matchKill(ServerPlayer player, Objective objective, Params params, QuestEvent event) {
        String typeMismatch = matchEntityType(params, event);
        if (typeMismatch != null) {
            return typeMismatch;
        }
        String named = params.getString("named", "");
        if (!named.isEmpty() && !named.equalsIgnoreCase(event.entityName())) {
            return ThaiText.t("rotasutils.msg.obj.named", named);
        }
        if (params.getBool("require_boss", false) && !event.boss()) {
            return ThaiText.t("rotasutils.msg.obj.not_boss");
        }
        if (params.getBool("require_quest_spawned", false) && !event.questSpawned()) {
            return ThaiText.t("rotasutils.msg.obj.not_quest_spawned");
        }
        if (!params.getBool("count_projectile", true) && event.projectile()) {
            return ThaiText.t("rotasutils.msg.obj.no_projectile");
        }
        if (!params.getBool("count_pet", false) && event.petKill()) {
            return ThaiText.t("rotasutils.msg.obj.no_pet");
        }
        if (!params.getBool("count_party", true) && event.partyKill()) {
            return ThaiText.t("rotasutils.msg.obj.no_party");
        }
        String dimensionCheck = matchDimension(params, event);
        if (dimensionCheck != null) {
            return dimensionCheck;
        }
        String biome = params.getString("biome", "");
        if (!biome.isEmpty() && !biome.equals(event.biome())) {
            return ThaiText.t("rotasutils.msg.obj.wrong_biome");
        }
        String weapon = params.getString("weapon", "");
        if (!weapon.isEmpty() && (event.weapon() == null || !weapon.equals(event.weapon().toString()))) {
            return ThaiText.t("rotasutils.msg.obj.wrong_weapon");
        }
        String damageType = params.getString("damage_type", "");
        if (!damageType.isEmpty() && !damageType.equals(event.damageType())) {
            return ThaiText.t("rotasutils.msg.obj.wrong_damage");
        }
        int maxDistance = params.getInt("max_distance", 0);
        BlockPos anchor = RewardService.parsePos(params.getString("pos", ""));
        if (maxDistance > 0 && anchor != null && event.pos() != null
                && !event.pos().closerThan(anchor, maxDistance)) {
            return ThaiText.t("rotasutils.msg.obj.wrong_region");
        }
        return null;
    }

    private static String matchPlayerKill(ServerPlayer player, RotasData data, Params params, QuestEvent event) {
        if (!data.serverSettings().pvpQuestsEnabled()) {
            return ThaiText.t("rotasutils.msg.obj.pvp_disabled");
        }
        ServerPlayer victim = event.victim();
        if (victim == null) {
            return ThaiText.t("rotasutils.msg.obj.no_victim");
        }
        String mode = params.getString("mode", "ANY");
        String target = params.getString("target", "");
        if (mode.equalsIgnoreCase("SPECIFIC") && !target.isEmpty()
                && !target.equalsIgnoreCase(victim.getGameProfile().getName())
                && !target.equalsIgnoreCase(victim.getUUID().toString())) {
            return ThaiText.t("rotasutils.msg.obj.wrong_target");
        }
        if (params.getBool("exclude_party", true)) {
            PlayerProgress killerProgress = data.progress(player.getUUID());
            PlayerProgress victimProgress = data.peek(victim.getUUID());
            if (killerProgress.partyId() != null && victimProgress != null
                    && killerProgress.partyId().equals(victimProgress.partyId())) {
                return ThaiText.t("rotasutils.msg.obj.party_member");
            }
        }
        if (params.getBool("exclude_team", true) && player.getTeam() != null
                && player.getTeam().equals(victim.getTeam())) {
            return ThaiText.t("rotasutils.msg.obj.teammate");
        }
        String dimensionCheck = matchDimension(params, event);
        if (dimensionCheck != null) {
            return dimensionCheck;
        }
        String weapon = params.getString("weapon", "");
        if (!weapon.isEmpty() && (event.weapon() == null || !weapon.equals(event.weapon().toString()))) {
            return ThaiText.t("rotasutils.msg.obj.wrong_weapon");
        }
        if (!AntiFarm.allowPlayerKill(player, victim, data,
                params.getInt("victim_cooldown", 1800),
                params.getInt("min_victim_playtime", 60))) {
            return ThaiText.t("rotasutils.msg.obj.anti_farm");
        }
        return null;
    }

    private static String matchEntityType(Params params, QuestEvent event) {
        if (event.entityType() == null) {
            return ThaiText.t("rotasutils.msg.obj.no_entity");
        }
        String tagId = params.getString("entity_tag", "");
        if (!tagId.isEmpty()) {
            ResourceLocation parsed = ResourceLocation.tryParse(tagId);
            if (parsed != null) {
                TagKey<EntityType<?>> tag = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, parsed);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(event.entityType());
                if (type != null && type.is(tag)) {
                    return null;
                }
                return ThaiText.t("rotasutils.msg.obj.entity_tag", tagId);
            }
        }
        String wanted = params.getString("entity", "");
        if (wanted.isEmpty()) {
            return null;
        }
        return wanted.equals(event.entityType().toString()) ? null : ThaiText.t("rotasutils.msg.obj.different_mob");
    }

    private static String matchItem(Params params, QuestEvent event, String mismatch) {
        ItemStack stack = event.stack();
        if (stack.isEmpty()) {
            return ThaiText.t("rotasutils.msg.obj.no_item");
        }
        String tagId = params.getString("item_tag", "");
        if (!tagId.isEmpty()) {
            ResourceLocation parsed = ResourceLocation.tryParse(tagId);
            if (parsed == null) {
                return ThaiText.t("rotasutils.msg.obj.bad_item_tag");
            }
            if (parsed != null) {
                TagKey<net.minecraft.world.item.Item> tag =
                        TagKey.create(net.minecraft.core.registries.Registries.ITEM, parsed);
                if (stack.is(tag)) {
                    return itemDetailMismatch(params, stack);
                }
                return ThaiText.t("rotasutils.msg.obj.item_tag", tagId);
            }
        }
        ResourceLocation wanted = params.getId("item");
        if (wanted == null) {
            if (!params.getString("item", "").isEmpty()) {
                return ThaiText.t("rotasutils.msg.obj.bad_item");
            }
            return itemDetailMismatch(params, stack);
        }
        ResourceLocation actual = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!wanted.equals(actual)) {
            return mismatch;
        }
        return itemDetailMismatch(params, stack);
    }

    private static String itemDetailMismatch(Params params, ItemStack stack) {
        String name = params.getString("match_name", "");
        if (!name.isEmpty() && !stack.getHoverName().getString().equals(name)) {
            return ThaiText.t("rotasutils.msg.obj.name_mismatch");
        }
        int modelData = params.getInt("match_model_data", -1);
        if (modelData >= 0) {
            int actual = stack.hasTag() && stack.getTag().contains("CustomModelData")
                    ? stack.getTag().getInt("CustomModelData") : -1;
            if (actual != modelData) {
                return ThaiText.t("rotasutils.msg.obj.model_mismatch");
            }
        }
        if (params.getBool("match_enchantments", false) && !stack.isEnchanted()) {
            return ThaiText.t("rotasutils.msg.obj.not_enchanted");
        }
        int minDurability = params.getInt("min_durability", 0);
        if (minDurability > 0 && stack.isDamageableItem()) {
            int percent = (int) (100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
            if (percent < minDurability) {
                return ThaiText.t("rotasutils.msg.obj.durability", minDurability);
            }
        }
        return null;
    }

    private static String matchNpc(Params params, QuestEvent event) {
        String uuid = params.getString("npc_uuid", "");
        if (!uuid.isEmpty()) {
            if (event.entityUuid() == null || !uuid.equals(event.entityUuid().toString())) return ThaiText.t("rotasutils.msg.obj.wrong_npc");
        }
        String name = params.getString("npc_name", "");
        if (uuid.isEmpty() && !name.isEmpty()) {
            if (!name.equalsIgnoreCase(event.entityName())) return ThaiText.t("rotasutils.msg.obj.wrong_npc");
        }
        String type = params.getString("npc_type", "");
        if (uuid.isEmpty() && name.isEmpty() && !type.isEmpty()) {
            if (event.entityType() == null || !type.equals(event.entityType().toString())) return ThaiText.t("rotasutils.msg.obj.wrong_npc_type");
        }
        String dialogue = params.getString("dialogue", ""), choice = params.getString("require_choice", "");
        boolean full = params.getBool("require_full_dialogue", false);
        if (!dialogue.isEmpty() && !dialogue.equals(event.dialogueId())) return ThaiText.t("rotasutils.msg.obj.need_dialogue");
        if (!choice.isEmpty() && !choice.equals(event.choiceId())) return ThaiText.t("rotasutils.msg.obj.need_response");
        if (full && !event.dialogueComplete()) return ThaiText.t("rotasutils.msg.obj.finish_conversation");
        if (dialogue.isEmpty() && choice.isEmpty() && !full && !event.dialogueId().isEmpty())
            return ThaiText.t("rotasutils.msg.obj.initial_only");
        return null;
    }

    private static String matchLocation(Params params, QuestEvent event) {
        BlockPos target = RewardService.parsePos(params.getString("pos", ""));
        if (target == null || event.pos() == null) {
            return ThaiText.t("rotasutils.msg.obj.no_position");
        }
        String dimensionCheck = matchDimension(params, event);
        if (dimensionCheck != null) {
            return dimensionCheck;
        }
        int radius = Math.max(1, params.getInt("radius", 8));
        return event.pos().closerThan(target, radius) ? null : ThaiText.t("rotasutils.msg.obj.outside_area");
    }

    private static String matchBlock(Params params, QuestEvent event) {
        BlockPos required = RewardService.parsePos(params.getString("pos", ""));
        if (required != null && !required.equals(event.pos())) {
            return ThaiText.t("rotasutils.msg.obj.wrong_block_pos");
        }
        String dimensionCheck = matchDimension(params, event);
        if (dimensionCheck != null) {
            return dimensionCheck;
        }
        String wanted = params.getString("block", "");
        if (wanted.isEmpty()) {
            return null;
        }
        return event.blockId() != null && wanted.equals(event.blockId().toString())
                ? null : ThaiText.t("rotasutils.msg.obj.different_block");
    }

    private static String matchDimension(Params params, QuestEvent event) {
        String wanted = params.getString("dimension", "");
        if (wanted.isEmpty() || wanted.equals(event.dimension())) {
            return null;
        }
        return ThaiText.t("rotasutils.msg.obj.wrong_dimension");
    }
}
