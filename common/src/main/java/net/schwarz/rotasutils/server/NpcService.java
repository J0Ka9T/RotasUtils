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

public final class NpcService {
    private NpcService() {
    }

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

    public static boolean onInteract(ServerPlayer player, RotasData data, Entity entity) {
        NpcDef npc = bound(data, entity);
        if (npc == null) {
            return false;
        }
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
            speak(player, npc, "blocked");
            return npc.captureInteraction();
        }
        NpcSocial.onTalk(player, data, npc);
        openDialogue(player, data, npc);
        return npc.captureInteraction();
    }

    public static void speak(ServerPlayer player, NpcDef npc, String key) {
        var sound = npc.voiceSound(key);
        if (sound == null) return;
        Entity at = findEntity(player.server, npc);
        double x = at != null ? at.getX() : player.getX(), y = at != null ? at.getEyeY() : player.getEyeY(),
                z = at != null ? at.getZ() : player.getZ();
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(sound), net.minecraft.sounds.SoundSource.NEUTRAL, x, y, z, 1.0f,
                npc.voicePitch() / 100f, player.getRandom().nextLong()));
    }

    public static void openShop(ServerPlayer player, RotasData data, NpcDef npc) {
        if (npc.hasShop()) {
            ShopService.open(player, data, npc);
            return;
        }
        RotasNetwork.feedback(player, false, net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.npc.nothing_to_sell", npc.name()));
    }

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
            }
            net.schwarz.rotasutils.compat.EasyNpcCompat.configureBehavior(entity,
                    definition.behavior().movement(), definition.behavior().combat(), owner);
        }
    }

    public static void openDialogue(ServerPlayer player, RotasData data, NpcDef npc) {
        if (npc.interactions() != null) { NpcConversations.open(player, data, npc); return; }
        net.minecraft.nbt.CompoundTag payload = new net.minecraft.nbt.CompoundTag();
        payload.putString("npc_id", npc.id());
        NpcDef shown = npc;
        String flavour = npc.role().hasScreen() ? NpcHub.greeting(npc) : "";
        if (!flavour.isEmpty()) {
            shown = NpcDef.load(npc.save());
            shown.setGreeting(flavour);
        }
        payload.put("npc", shown.save());
        NpcState state = stateFor(player, data, npc);
        speak(player, npc, NpcDef.voiceKey(state));
        payload.putString("state", state.name());
        payload.put("offers", net.schwarz.rotasutils.util.Nbt.saveStrings(offers(player, data, npc)));
        RotasNetwork.openScreen(player, "npc_dialogue", payload);
    }

    public static List<String> offers(ServerPlayer player, RotasData data, NpcDef npc) {
        List<String> offers = new ArrayList<>();
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

    public static void openRoleTarget(ServerPlayer player, RotasData data, NpcDef npc) {
        switch (npc.role()) {
            case BOARD_KEEPER -> openBoard(player, data, npc);
            case MERCHANT -> openShop(player, data, npc);
            case STABLE -> net.schwarz.rotasutils.server.horse.HorseService.open(player, npc.id(), "STABLE", null);
            case CRAFTER -> CrafterService.open(player, data, npc);
            case AUCTIONEER -> AuctionService.open(player, data, npc, "");
            case BLACKSMITH, ENCHANTER, ALCHEMIST, INNKEEPER, PRIEST, FORTUNE_TELLER, BANKER, BOUNTY_MASTER, GUARD,
                 TRAINER, CARTOGRAPHER, COLLECTOR -> NpcHub.open(player, data, npc, false);
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

    public static void bind(RotasData data, NpcDef npc, Entity entity) {
        String uuid = entity.getUUID().toString();
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
        npc.setHome(entity.blockPosition());
        data.putNpc(npc);
        applyEntityOptions(npc, entity);
    }

    public static boolean isQuestLinked(RotasData data, String questId) {
        for (NpcDef npc : data.npcs().values()) {
            if (npc.questIds().contains(questId)) {
                return true;
            }
        }
        return false;
    }
}
