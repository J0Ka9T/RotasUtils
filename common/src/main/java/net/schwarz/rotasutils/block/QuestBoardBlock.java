package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.server.BoardHandlers;
import org.jetbrains.annotations.Nullable;

public class QuestBoardBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape[][][] SHAPES = buildShapes();

    public QuestBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    private static VoxelShape[][][] buildShapes() {
        VoxelShape[][][] shapes = new VoxelShape[4][QuestBoardGeometry.COLUMNS][QuestBoardGeometry.ROWS];
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape board = Shapes.empty();
            for (double[] b : QuestBoardGeometry.boxes(facing)) {
                board = Shapes.or(board, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            board = board.optimize();
            for (int column = 0; column < QuestBoardGeometry.COLUMNS; column++) {
                for (int row = 0; row < QuestBoardGeometry.ROWS; row++) {
                    BlockPos offset = QuestBoardGeometry.cellOffset(facing, column, row);
                    shapes[facing.get2DDataValue()][column][row] =
                            board.move(-offset.getX(), -offset.getY(), -offset.getZ());
                }
            }
        }
        return shapes;
    }

    public static VoxelShape shapeFor(Direction facing, int column, int row) {
        return SHAPES[facing.get2DDataValue()][column][row];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING), QuestBoardGeometry.MAIN_COLUMN, QuestBoardGeometry.MAIN_ROW);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (int column = 0; column < QuestBoardGeometry.COLUMNS; column++) {
            for (int row = 0; row < QuestBoardGeometry.ROWS; row++) {
                if (QuestBoardGeometry.isMain(column, row)) continue;
                BlockPos cell = pos.offset(QuestBoardGeometry.cellOffset(facing, column, row));
                if (level.isOutsideBuildHeight(cell) || !level.getBlockState(cell).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, waterlogged);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            placeParts(level, pos, state);
        }
    }

    static void placeParts(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < QuestBoardGeometry.COLUMNS; column++) {
            for (int row = 0; row < QuestBoardGeometry.ROWS; row++) {
                if (QuestBoardGeometry.isMain(column, row)) continue;
                BlockPos cell = pos.offset(QuestBoardGeometry.cellOffset(facing, column, row));
                BlockState current = level.getBlockState(cell);
                BlockState part = RotasRegistry.QUEST_BOARD_PART.get().defaultBlockState()
                        .setValue(QuestBoardPartBlock.FACING, facing)
                        .setValue(QuestBoardPartBlock.COLUMN, column)
                        .setValue(QuestBoardPartBlock.ROW, row);
                if (current.equals(part) || level.isOutsideBuildHeight(cell) || !current.canBeReplaced()) continue;
                level.setBlock(cell, part, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuestBoardBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof QuestBoardBlockEntity boardEntity)) {
            return InteractionResult.PASS;
        }
        placeParts(level, pos, state);
        BoardHandlers.onInteract(serverPlayer, boardEntity);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof QuestBoardBlockEntity boardEntity) {
                BoardHandlers.onBroken(level, boardEntity);
            }
            removeParts(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private static void removeParts(Level level, BlockPos pos, Direction facing) {
        for (int column = 0; column < QuestBoardGeometry.COLUMNS; column++) {
            for (int row = 0; row < QuestBoardGeometry.ROWS; row++) {
                if (QuestBoardGeometry.isMain(column, row)) continue;
                BlockPos cell = pos.offset(QuestBoardGeometry.cellOffset(facing, column, row));
                BlockState current = level.getBlockState(cell);
                if (current.getBlock() instanceof QuestBoardPartBlock
                        && QuestBoardPartBlock.mainPos(cell, current).equals(pos)) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }
}
