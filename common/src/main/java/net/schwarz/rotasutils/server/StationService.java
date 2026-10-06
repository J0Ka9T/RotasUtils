package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

public final class StationService {
    public static final int REACH = 4;

    private StationService() {
    }

    public static boolean near(ServerPlayer player, Block station) {
        return find(player, station) != null;
    }

    @org.jetbrains.annotations.Nullable
    public static BlockPos find(ServerPlayer player, Block station) {
        BlockPos feet = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-REACH, -REACH, -REACH),
                feet.offset(REACH, REACH + 1, REACH))) {
            if (player.level().getBlockState(pos).is(station)) {
                double d = pos.distSqr(feet);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    public static String tradeForKind(net.schwarz.rotasutils.block.StationBlock.Kind kind) {
        return switch (kind) {
            case STOVE -> "chef";
            case SMELTER -> "miner";
            case BENCH -> "blacksmith";
            case TABLE -> "alchemy";
            case TANNERY -> "rancher";
            case FISH -> "fisher";
            case MILL -> "farmer";
            default -> "";
        };
    }

    public static boolean hasActiveQueueNearby(net.minecraft.world.level.Level level, BlockPos pos,
                                              net.schwarz.rotasutils.block.StationBlock.Kind kind) {
        String trade = tradeForKind(kind);
        if (trade.isEmpty() || level.isClientSide()) return false;
        long now = System.currentTimeMillis();
        for (net.minecraft.world.entity.player.Player player : level.players()) {
            if (player instanceof ServerPlayer serverPlayer && player.blockPosition().closerThan(pos, 10.0)) {
                net.schwarz.rotasutils.progress.PlayerProgress progress =
                        net.schwarz.rotasutils.data.RotasData.get(serverPlayer.server).progress(serverPlayer.getUUID());
                String queueStr = progress.questVariables().get("rpg.trade." + trade + ".queue");
                if (queueStr != null && !queueStr.isBlank()) {
                    for (net.schwarz.rotasutils.core.TradeKit.Cooking c : net.schwarz.rotasutils.core.TradeKit.parseQueue(queueStr)) {
                        if (!c.done(now)) return true;
                    }
                }
            }
        }
        return false;
    }
}
