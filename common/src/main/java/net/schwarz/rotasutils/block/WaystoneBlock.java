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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.schwarz.rotasutils.registry.RotasRegistry;
import net.schwarz.rotasutils.server.WaystoneService;
import org.jetbrains.annotations.Nullable;

public class WaystoneBlock extends BaseEntityBlock {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 2);
    public static final int SEGMENTS = 3;

    private static final VoxelShape PLINTH_SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape SHAFT_SHAPE = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape CROWN_SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public WaystoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SEGMENT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(SEGMENT)) {
            case 1 -> SHAFT_SHAPE;
            case 2 -> CROWN_SHAPE;
            default -> PLINTH_SHAPE;
        };
    }

    public static BlockPos rootOf(BlockState state, BlockPos pos) {
        return pos.below(state.getValue(SEGMENT));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - (SEGMENTS - 1)) {
            return null;
        }
        for (int segment = 1; segment < SEGMENTS; segment++) {
            if (!level.getBlockState(pos.above(segment)).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        for (int segment = 1; segment < SEGMENTS; segment++) {
            level.setBlock(pos.above(segment), defaultBlockState().setValue(SEGMENT, segment), Block.UPDATE_ALL);
        }
        WaystoneService.onPlaced(level, pos, placer instanceof ServerPlayer player ? player : null);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        int segment = state.getValue(SEGMENT);
        if (direction == Direction.DOWN && segment > 0
                && !(neighbour.is(this) && neighbour.getValue(SEGMENT) == segment - 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == Direction.UP && segment < SEGMENTS - 1
                && !(neighbour.is(this) && neighbour.getValue(SEGMENT) == segment + 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
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
        WaystoneService.onUse(serverPlayer, level, rootOf(state, pos));
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && state.getValue(SEGMENT) == 0) {
            WaystoneService.onBroken(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SEGMENT) == 0 ? new WaystoneBlockEntity(pos, state) : null;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        int segment = state.getValue(SEGMENT);
        if (segment > 0) {
            BlockPos root = pos.below(segment);
            BlockState plinth = level.getBlockState(root);
            if (plinth.is(this) && plinth.getValue(SEGMENT) == 0) {
                level.setBlock(root, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, root, Block.getId(plinth));
                if (!player.isCreative()) {
                    Block.dropResources(plinth, level, root, null, player, player.getMainHandItem());
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(RotasRegistry.WAYSTONE_ITEM.get());
    }
}
