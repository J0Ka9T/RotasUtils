package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.registry.RotasRegistry;

public class WaystoneBlockEntity extends BlockEntity {
    public WaystoneBlockEntity(BlockPos pos, BlockState state) {
        super(RotasRegistry.WAYSTONE_BLOCK_ENTITY.get(), pos, state);
    }
}
