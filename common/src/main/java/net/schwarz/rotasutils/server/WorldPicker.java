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

public final class WorldPicker {
    public enum Kind {
        POSITION,
        ENTITY,
        NPC,
        NPC_BIND,
        BOARD
    }

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
        String value = switch (request.kind()) {
            case NPC -> target.getUUID().toString();
            case NPC_BIND -> target.getUUID() + "|"
                    + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + "|"
                    + target.level().dimension().location();
            default -> String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
        };
        if (request.kind() == Kind.NPC_BIND && !request.npcId().isEmpty()) {
            RotasData data = RotasData.get(player.server);
            NpcDef npc = data.npc(request.npcId());
            if (npc != null) {
                NpcService.bind(data, npc, target);
                data.audit(player.getGameProfile().getName() + " bound NPC " + npc.id()
                        + " to " + target.getUUID() + " ("
                        + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + ")");
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

    public static void clear() {
        PENDING.clear();
    }
}
