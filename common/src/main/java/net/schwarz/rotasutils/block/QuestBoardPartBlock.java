package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.schwarz.rotasutils.registry.RotasRegistry;

public class QuestBoardPartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, QuestBoardGeometry.COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, QuestBoardGeometry.ROWS - 1);

    public QuestBoardPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(COLUMN, 0)
                .setValue(ROW, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return QuestBoardBlock.shapeFor(state.getValue(FACING), state.getValue(COLUMN), state.getValue(ROW));
    }

    public static BlockPos mainPos(BlockPos pos, BlockState state) {
        return pos.subtract(QuestBoardGeometry.cellOffset(state.getValue(FACING), state.getValue(COLUMN), state.getValue(ROW)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos main = mainPos(pos, state);
        BlockState mainState = level.getBlockState(main);
        if (mainState.getBlock() instanceof QuestBoardBlock) {
            return mainState.use(level, player, hand, hit.withPosition(main));
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos main = mainPos(pos, state);
            if (level.getBlockState(main).getBlock() instanceof QuestBoardBlock) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockPos main = mainPos(pos, state);
            if (level.getBlockState(main).getBlock() instanceof QuestBoardBlock) {
                level.destroyBlock(main, true);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(RotasRegistry.QUEST_BOARD_ITEM.get());
    }
}
