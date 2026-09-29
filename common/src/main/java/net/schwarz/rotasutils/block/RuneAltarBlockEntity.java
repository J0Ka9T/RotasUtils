package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.registry.RotasRegistry;

/** Present only to carry the altar's floating-crystal renderer; stores nothing. */
public class RuneAltarBlockEntity extends BlockEntity {
    public RuneAltarBlockEntity(BlockPos pos, BlockState state) {
        super(RotasRegistry.RUNE_ALTAR_BLOCK_ENTITY.get(), pos, state);
    }
}
