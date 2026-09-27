package net.schwarz.rotasutils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Whether a player is standing at a work station. Refinement needs a Refine Forge and inscribing
 * needs a Rune Altar within {@link #REACH} blocks; every attempt asks again, so the answer is always
 * about where the player is now, not where they opened the screen.
 */
public final class StationService {
    /** Blocks from the player's feet to the station, in every direction. */
    public static final int REACH = 4;

    private StationService() {
    }

    public static boolean near(ServerPlayer player, Block station) {
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-REACH, -REACH, -REACH),
                feet.offset(REACH, REACH + 1, REACH))) {
            if (player.level().getBlockState(pos).is(station)) {
                return true;
            }
        }
        return false;
    }
}
