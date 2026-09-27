package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Consumer;

/**
 * A work station the player stands at: the Refine Forge opens the refinement bench, the Rune Altar
 * opens rune inscribing. The block itself stores nothing. The server re-checks that one is still
 * within reach on every attempt (see {@link net.schwarz.rotasutils.server.StationService}), so a
 * screen kept open after walking away cannot be used from across the map.
 */
public class StationBlock extends HorizontalDirectionalBlock {
    private final VoxelShape shape;
    private final Consumer<ServerPlayer> open;

    public StationBlock(Properties properties, VoxelShape shape, Consumer<ServerPlayer> open) {
        super(properties);
        this.shape = shape;
        this.open = open;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            open.accept(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
