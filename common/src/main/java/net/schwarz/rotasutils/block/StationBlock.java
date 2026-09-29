package net.schwarz.rotasutils.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
public class StationBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    /** What the station looks and sounds like when alive. */
    public enum Kind { FORGE, ALTAR }

    private final Kind kind;
    private final VoxelShape shape;
    private final Consumer<ServerPlayer> open;

    public StationBlock(Properties properties, Kind kind, VoxelShape shape, Consumer<ServerPlayer> open) {
        super(properties);
        this.kind = kind;
        this.shape = shape;
        this.open = open;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Only the altar carries a renderer (its floating crystal). */
    @org.jetbrains.annotations.Nullable
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.ALTAR ? new RuneAltarBlockEntity(pos, state) : null;
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
            level.playSound(null, pos, kind == Kind.FORGE ? SoundEvents.ANVIL_USE : SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.BLOCKS, kind == Kind.FORGE ? 0.35f : 0.8f, kind == Kind.FORGE ? 1.4f : 1.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Embers and a crackle at the forge's mouth; enchanting glyphs rising off the altar. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind == Kind.FORGE) {
            Direction face = state.getValue(FACING);
            double x = pos.getX() + 0.5 + face.getStepX() * 0.52, z = pos.getZ() + 0.5 + face.getStepZ() * 0.52;
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.35 + random.nextDouble() * 0.25,
                        z + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
            }
            if (random.nextInt(8) == 0) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.03, 0);
            }
            if (random.nextInt(24) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT,
                        SoundSource.BLOCKS, 0.5f, 0.8f + random.nextFloat() * 0.3f, false);
            }
        } else if (random.nextInt(3) == 0) {
            // Glyphs drift up from the ring in the top plate, as over an enchanting table.
            double ox = (random.nextDouble() - 0.5) * 1.2, oz = (random.nextDouble() - 0.5) * 1.2;
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, ox, 0.6, oz);
        }
    }
}
