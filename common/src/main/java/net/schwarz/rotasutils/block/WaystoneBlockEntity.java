package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.registry.RotasRegistry;

/**
 * Present only so the lower half of a waystone can carry a renderer for its floating core.
 *
 * <p>The pillar's own record - its name and who has discovered it - lives in the world store keyed
 * by position, so nothing here needs saving and a broken pillar leaves no orphaned data.
 */
public class WaystoneBlockEntity extends BlockEntity {
    public WaystoneBlockEntity(BlockPos pos, BlockState state) {
        super(RotasRegistry.WAYSTONE_BLOCK_ENTITY.get(), pos, state);
    }
}
