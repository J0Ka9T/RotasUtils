package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.block.QuestBoardBlockEntity;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.npc.NpcDef;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-selection mode.
 *
 * <p>An admin screen asks for a position, NPC or board; the screen closes, the player
 * clicks the thing in the world with the admin tool, and the picked value is sent back
 * to the client which reopens the editor with the field filled in. This is what keeps
 * UUIDs and coordinates out of the admin's hands.
 */
public final class WorldPicker {
    public enum Kind {
        POSITION,
        ENTITY,
        NPC,
        /** Like NPC, but the value carries type and dimension so a binding is self-describing. */
        NPC_BIND,
        BOARD
    }

    /**
     * @param kind      what the screen asked for
     * @param screenKey identifies the screen to reopen
     * @param fieldKey  identifies the field to fill
     */
    public record Request(Kind kind, String screenKey, String fieldKey, String npcId) {
    }

    private static final Map<UUID, Request> PENDING = new HashMap<>();

    private WorldPicker() {
    }

    public static void begin(ServerPlayer player, Kind kind, String screenKey, String fieldKey,
                             String npcId) {
        PENDING.put(player.getUUID(), new Request(kind, screenKey, fieldKey, npcId));
        player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c(switch (kind) {
            case POSITION -> "rotasutils.msg.pick.position";
            case ENTITY -> "rotasutils.msg.pick.entity";
            case NPC -> "rotasutils.msg.pick.npc";
            case NPC_BIND -> "rotasutils.msg.pick.npc_bind";
            case BOARD -> "rotasutils.msg.pick.board";
        }).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.pick.cancel_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean cancel(ServerPlayer player) {
        return PENDING.remove(player.getUUID()) != null;
    }

    public static boolean isPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    public static boolean resolveBlock(ServerPlayer player, BlockPos pos) {
        if (!authorized(player)) return false;
        Request request = PENDING.get(player.getUUID());
        if (request == null) {
            return false;
        }
        if (request.kind() == Kind.BOARD) {
            if (player.level().getBlockEntity(pos) instanceof QuestBoardBlockEntity boardEntity) {
                if (boardEntity.boardId().isEmpty()) {
                    player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.pick.board_unconfigured")
                            .withStyle(ChatFormatting.RED));
                    return true;
                }
                finish(player, request, boardEntity.boardId(),
                        net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.pick.selected_board", boardEntity.displayName()));
                return true;
            }
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.pick.not_board")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        if (request.kind() != Kind.POSITION) {
            return false;
        }
        finish(player, request, RewardService.formatPos(pos), net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.pick.selected_position", RewardService.formatPos(pos)));
        return true;
    }

    public static boolean resolveEntity(ServerPlayer player, LivingEntity target) {
        if (!authorized(player)) return false;
        Request request = PENDING.get(player.getUUID());
        if (request == null || (request.kind() != Kind.ENTITY && request.kind() != Kind.NPC
                && request.kind() != Kind.NPC_BIND)) {
            return false;
        }
        // A binding needs three facts about the entity, and an admin should never type any
        // of them, so they travel together in one pipe-separated value.
        String value = switch (request.kind()) {
            case NPC -> target.getUUID().toString();
            case NPC_BIND -> target.getUUID() + "|"
                    + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + "|"
                    + target.level().dimension().location();
            default -> String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
        };
        // A binding is identity, not content: apply it to the live character as soon as
        // the entity is clicked, so it survives a closed editor, a restart, or an admin
        // who never reaches the Save/review flow.
        if (request.kind() == Kind.NPC_BIND && !request.npcId().isEmpty()) {
            RotasData data = RotasData.get(player.server);
            NpcDef npc = data.npc(request.npcId());
            if (npc != null) {
                NpcService.bind(data, npc, target);
                data.audit(player.getGameProfile().getName() + " bound NPC " + npc.id()
                        + " to " + target.getUUID() + " ("
                        + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + ")");
                // The binding is live immediately: drop any staged draft for this character
                // (its frozen baseline can no longer apply) and refresh every editor so
                // their baselines match the new live state instead of failing Save.
                data.configHistory().discard(player.getUUID().toString(), "npc/" + npc.id());
                for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                    RotasNetwork.syncContent(online);
                }
                finish(player, request, value, net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.pick.selected_bound",
                        target.getName().getString(), npc.name()));
                return true;
            }
        }
        finish(player, request, value, net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.pick.selected", target.getName().getString()));
        return true;
    }

    private static void finish(ServerPlayer player, Request request, String value, String message) {
        PENDING.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
        RotasNetwork.sendPickResult(player, request.screenKey(), request.fieldKey(), value);
    }

    private static boolean authorized(ServerPlayer player) {
        if (BoardService.isAdmin(player, RotasData.get(player.server))) return true;
        if (PENDING.remove(player.getUUID()) != null)
            RotasNetwork.feedback(player, false, net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.pick.perm_removed"));
        return false;
    }

    public static void clear(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    /** Drops every outstanding world pick when the server stops. */
    public static void clear() {
        PENDING.clear();
    }
}
