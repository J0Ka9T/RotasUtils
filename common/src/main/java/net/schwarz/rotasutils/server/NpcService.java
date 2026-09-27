package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.npc.NpcState;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the NPC layer: who is bound to what, what they will say, and what a
 * conversation is allowed to do.
 *
 * <p>The client is never trusted with any of it. A dialogue screen is a view of the state
 * computed here, and every action it offers is re-checked on arrival, so a crafted packet
 * can only ask for something the player could have asked for by clicking.</p>
 */
public final class NpcService {
    private NpcService() {
    }

    /** The NPC bound to this entity, or null when the entity is just an entity. */
    public static NpcDef bound(RotasData data, Entity entity) {
        String uuid = entity.getUUID().toString();
        for (NpcDef npc : data.npcs().values()) {
            if (npc.enabled() && uuid.equals(npc.entityUuid())) {
                return npc;
            }
        }
        return null;
    }

    public static NpcDef byId(RotasData data, String npcId) {
        return data.npc(npcId);
    }

    /**
     * Handles a right-click on a bound entity.
     *
     * @return true when the NPC took the interaction, so the entity's own menu is suppressed
     */
    public static boolean onInteract(ServerPlayer player, RotasData data, Entity entity) {
        NpcDef npc = bound(data, entity);
        if (npc == null) {
            return false;
        }
        // Reach is re-checked here because the interaction packet only proves the client
        // believed it was close enough.
        double reach = npc.interactionDistance();
        if (player.distanceToSqr(entity) > reach * reach) {
            return false;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if (npc.requiredLevel() > 0 && progress.level() < npc.requiredLevel()) {
            player.sendSystemMessage(Component.literal(npc.name() + ": " + npc.blockedLine()
                            + " " + net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc.requires_level",
                            npc.requiredLevel(), progress.level()))
                    .withStyle(ChatFormatting.GRAY));
            return npc.captureInteraction();
        }
        // The role decides what a click opens: a board keeper and a shopkeeper go straight to their
        // board or shop, everyone else starts a conversation.
        if (npc.role() == NpcDef.Role.BOARD_KEEPER && !npc.boardId().isBlank()) {
            openBoard(player, data, npc);
        } else if (npc.role() == NpcDef.Role.MERCHANT && npc.hasShop()) {
            openShop(player, data, npc);
        } else if (npc.role() == NpcDef.Role.STABLE && npc.interactions() == null) {
            net.schwarz.rotasutils.server.horse.HorseService.open(player, npc.id(), "STABLE", null);
        } else if (npc.role() == NpcDef.Role.CRAFTER && npc.interactions() == null) {
            CrafterService.open(player, data, npc);
        } else {
            openDialogue(player, data, npc);
        }
        return npc.captureInteraction();
    }

    /** Opens the NPC's own trades, or the content-pack merchant it points at. */
    public static void openShop(ServerPlayer player, RotasData data, NpcDef npc) {
        // The shop screen shows the NPC's own trades and its content-pack merchant together.
        if (npc.hasShop()) {
            ShopService.open(player, data, npc);
            return;
        }
        RotasNetwork.feedback(player, false, net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc.nothing_to_sell", npc.name()));
    }

    /** The loaded entity an NPC is bound to, in any dimension, or null. */
    public static Entity findEntity(net.minecraft.server.MinecraftServer server, NpcDef npc) {
        if (!npc.bound()) {
            return null;
        }
        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(npc.entityUuid());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Writes the NPC's mob options onto the entity. They are stored in the entity's own save data, so
     * they stay after restarts: the mob never despawns, and stand still, can't be hurt and the name tag
     * follow the NPC settings. Easy NPC movement from the branching conversation's behavior is applied too.
     */
    public static void applyEntityOptions(NpcDef npc, Entity entity) {
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setPersistenceRequired();
            mob.setNoAi(npc.standStill());
        }
        entity.setInvulnerable(npc.invulnerable());
        if (npc.showName() && !npc.name().isBlank()) {
            entity.setCustomName(net.minecraft.network.chat.Component.literal(npc.name()));
            entity.setCustomNameVisible(true);
        } else if (!npc.showName() && entity.isCustomNameVisible()) {
            entity.setCustomNameVisible(false);
        }
        var definition = npc.interactions();
        if (definition != null && definition.behavior().enabled() && !npc.standStill()) {
            java.util.UUID owner = null;
            try {
                owner = definition.behavior().owner().isBlank() ? null : java.util.UUID.fromString(definition.behavior().owner());
            } catch (IllegalArgumentException ignored) {
                // Parsing already rejected malformed owners; keep the mob ownerless if one slips through.
            }
            net.schwarz.rotasutils.compat.EasyNpcCompat.configureBehavior(entity,
                    definition.behavior().movement(), definition.behavior().combat(), owner);
        }
    }

    /** Pushes the dialogue screen with everything it may show, and nothing it may not. */
    public static void openDialogue(ServerPlayer player, RotasData data, NpcDef npc) {
        if (npc.interactions() != null) { NpcConversations.open(player, data, npc); return; }
        net.minecraft.nbt.CompoundTag payload = new net.minecraft.nbt.CompoundTag();
        payload.putString("npc_id", npc.id());
        payload.put("npc", npc.save());
        payload.putString("state", stateFor(player, data, npc).name());
        payload.put("offers", net.schwarz.rotasutils.util.Nbt.saveStrings(offers(player, data, npc)));
        RotasNetwork.openScreen(player, "npc_dialogue", payload);
    }

    /**
     * The quests this NPC may show this player: published, visible, and either already
     * accepted or currently offerable. Everything else is simply absent from the dialogue.
     */
    public static List<String> offers(ServerPlayer player, RotasData data, NpcDef npc) {
        List<String> offers = new ArrayList<>();
        // "Just talks" means just that; quests stay listed only for the quest-giving roles.
        if (npc.role() == NpcDef.Role.DIALOGUE) {
            return offers;
        }
        PlayerProgress progress = data.progress(player.getUUID());
        for (String questId : npc.questIds()) {
            QuestDef quest = data.quest(questId);
            if (quest == null || !quest.published()) {
                continue;
            }
            if (progress.active(questId) != null || QuestService.canSee(player, data, quest)) {
                offers.add(questId);
            }
        }
        return offers;
    }

    /** The most advanced thing this NPC has for this player. */
    public static NpcState stateFor(ServerPlayer player, RotasData data, NpcDef npc) {
        PlayerProgress progress = data.progress(player.getUUID());
        NpcState best = NpcState.NO_QUEST;
        if (npc.requiredLevel() > 0 && progress.level() < npc.requiredLevel()) {
            return NpcState.REQUIREMENTS_NOT_MET;
        }
        for (String questId : npc.questIds()) {
            QuestDef quest = data.quest(questId);
            if (quest == null || !quest.published()) {
                continue;
            }
            best = best.best(stateOf(player, data, quest, progress));
        }
        return best;
    }

    private static NpcState stateOf(ServerPlayer player, RotasData data, QuestDef quest,
                                    PlayerProgress progress) {
        ActiveQuest active = progress.active(quest.id());
        if (active != null) {
            if (active.turnInReady()) {
                return NpcState.READY_TO_TURN_IN;
            }
            return QuestService.allRequiredComplete(quest, active)
                    ? NpcState.OBJECTIVE_COMPLETE : NpcState.QUEST_ACTIVE;
        }
        if (progress.failedQuests().contains(quest.id())) {
            return NpcState.QUEST_FAILED;
        }
        if (progress.cooldownUntil(quest.id()) > QuestService.nowSeconds()) {
            return NpcState.QUEST_ON_COOLDOWN;
        }
        if (progress.completionCount(quest.id()) > 0 && quest.repeat() == QuestDef.Repeat.NEVER) {
            return NpcState.QUEST_COMPLETE;
        }
        return QuestService.blockedReason(player, data, quest) == null
                ? NpcState.QUEST_AVAILABLE : NpcState.REQUIREMENTS_NOT_MET;
    }

    /** Opens whatever this NPC's role points at: its board or its shop. */
    public static void openRoleTarget(ServerPlayer player, RotasData data, NpcDef npc) {
        switch (npc.role()) {
            case BOARD_KEEPER -> openBoard(player, data, npc);
            case MERCHANT -> openShop(player, data, npc);
            case STABLE -> net.schwarz.rotasutils.server.horse.HorseService.open(player, npc.id(), "STABLE", null);
            case CRAFTER -> CrafterService.open(player, data, npc);
            case JOB_MASTER -> {
                boolean offered = false;
                for (net.schwarz.rotasutils.job.JobSlot slot : net.schwarz.rotasutils.job.JobSlot.values()) {
                    if (JobService.offeredJob(npc, slot).isBlank()) continue;
                    offered = true;
                    JobService.Result result = JobService.acceptNpcOffer(player, data, npc, slot);
                    RotasNetwork.feedback(player, result.success(), result.message());
                }
                if (!offered) RotasNetwork.feedback(player, false, "This job master has no configured offers.");
                RotasNetwork.syncProgress(player);
            }
            default -> openDialogue(player, data, npc);
        }
    }

    public static void openBoard(ServerPlayer player, RotasData data, NpcDef npc) {
        var board = data.board(npc.boardId());
        if (board == null) {
            RotasNetwork.feedback(player, false, net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc.board_gone"));
            return;
        }
        String blocked = BoardService.openBlockedReason(player, data, board);
        if (blocked != null) {
            RotasNetwork.feedback(player, false, blocked);
            return;
        }
        RotasNetwork.openBoardBrowser(player, board, npc.id());
    }

    /** Records the entity an admin picked, so the admin never handles a uuid. */
    public static void bind(RotasData data, NpcDef npc, Entity entity) {
        String uuid = entity.getUUID().toString();
        // One entity, one NPC: the newer binding wins and the older one is released.
        for (NpcDef other : data.npcs().values()) {
            if (!other.id().equals(npc.id()) && uuid.equals(other.entityUuid())) {
                other.setEntityUuid("");
                other.setEntityType("");
                other.setDimension("");
            }
        }
        npc.setEntityUuid(uuid);
        npc.setEntityType(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
        npc.setDimension(entity.level().dimension().location().toString());
        data.putNpc(npc);
        applyEntityOptions(npc, entity);
    }

    /** True when this quest is handed out or taken back by any NPC, used by validation. */
    public static boolean isQuestLinked(RotasData data, String questId) {
        for (NpcDef npc : data.npcs().values()) {
            if (npc.questIds().contains(questId)) {
                return true;
            }
        }
        return false;
    }
}
