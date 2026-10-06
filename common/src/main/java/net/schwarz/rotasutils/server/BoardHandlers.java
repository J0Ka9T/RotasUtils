package net.schwarz.rotasutils.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.block.QuestBoardBlockEntity;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.util.Ids;

public final class BoardHandlers {
    private BoardHandlers() {
    }

    public static void onInteract(ServerPlayer player, QuestBoardBlockEntity boardEntity) {
        RotasData data = RotasData.get(player.server);
        boolean admin = BoardService.isAdmin(player, data);

        if (boardEntity.boardId().isEmpty()) {
            if (!admin) {
                player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.board.not_set_up")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
            BoardConfig board = new BoardConfig(Ids.unique("board", data.boards().keySet()));
            board.setName(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.default_name"));
            data.putBoard(board);
            boardEntity.setBoardId(board.id());
            data.audit(player.getGameProfile().getName() + " created board " + board.id());
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.board.created")
                    .withStyle(ChatFormatting.GREEN));
            refreshDisplay(player, data, boardEntity);
            RotasNetwork.openBoardConfig(player, board);
            return;
        }

        BoardConfig board = data.board(boardEntity.boardId());
        if (board == null) {
            if (!admin) {
                player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.board.not_configured")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
            boardEntity.setBoardId("");
            boardEntity.refreshDisplay(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.board.default_name"),
                    net.schwarz.rotasutils.board.BoardStyle.WOODEN_VILLAGE, 0, 0xFF3BE8FF, false, false, false);
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.board.deleted_unbound")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (player.isShiftKeyDown() && admin) {
            RotasNetwork.openBoardConfig(player, board);
            return;
        }
        String blocked = BoardService.openBlockedReason(player, data, board);
        if (blocked != null) {
            player.sendSystemMessage(Component.literal(blocked).withStyle(ChatFormatting.RED));
            return;
        }
        refreshDisplay(player, data, boardEntity);
        RotasNetwork.openBoardBrowser(player, board);
    }

    public static void onBroken(Level level, QuestBoardBlockEntity boardEntity) {
    }

    public static void refreshDisplay(ServerPlayer player, RotasData data, QuestBoardBlockEntity boardEntity) {
        BoardConfig board = data.board(boardEntity.boardId());
        if (board == null) {
            return;
        }
        int count = BoardService.visibleQuests(player, data, board).size();
        boolean emergency = !board.emergencyPool().isEmpty();
        boardEntity.refreshDisplay(board.name(), board.style(), count, board.screenColor(),
                emergency, board.glow(), board.particles());
    }
}
