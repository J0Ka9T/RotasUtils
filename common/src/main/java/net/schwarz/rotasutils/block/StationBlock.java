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

public class StationBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

    public enum Kind { FORGE, ALTAR, STOVE, SMELTER, BENCH, TABLE, TANNERY, FISH, MILL }

    private final Kind kind;
    private final VoxelShape shape;
    private final Consumer<ServerPlayer> open;

    public Kind kind() {
        return kind;
    }

    public StationBlock(Properties properties, Kind kind, VoxelShape shape, Consumer<ServerPlayer> open) {
        super(properties);
        this.kind = kind;
        this.shape = shape;
        this.open = open;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.ALTAR ? new RuneAltarBlockEntity(pos, state) : null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, false);
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
            level.playSound(null, pos, openSound(), SoundSource.BLOCKS, kind == Kind.FORGE ? 0.35f : 0.8f,
                    kind == Kind.FORGE ? 1.4f : 1.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        boolean active = net.schwarz.rotasutils.server.StationService.hasActiveQueueNearby(level, pos, kind);
        if (state.getValue(LIT) != active) {
            level.setBlock(pos, state.setValue(LIT, active), Block.UPDATE_ALL);
        }
        if (active) {
            level.scheduleTick(pos, this, 30);
        }
    }

    public static void lightUp(Level level, BlockPos pos, int durationTicks) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StationBlock && !state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, state.getBlock(), Math.max(20, durationTicks));
    }

    private net.minecraft.sounds.SoundEvent openSound() {
        return switch (kind) {
            case FORGE -> SoundEvents.ANVIL_USE;
            case STOVE -> SoundEvents.BARREL_OPEN;
            case SMELTER -> SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
            case BENCH -> SoundEvents.SMITHING_TABLE_USE;
            case TABLE -> SoundEvents.BREWING_STAND_BREW;
            case TANNERY -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case FISH -> SoundEvents.FISHING_BOBBER_SPLASH;
            case MILL -> SoundEvents.GRINDSTONE_USE;
            case ALTAR -> SoundEvents.ENCHANTMENT_TABLE_USE;
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean lit = state.hasProperty(LIT) && state.getValue(LIT);
        if (!lit) {
            if (kind == Kind.FORGE && random.nextInt(20) == 0) {
                Direction face = state.getValue(FACING);
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + face.getStepX() * 0.52,
                        pos.getY() + 0.35, pos.getZ() + 0.5 + face.getStepZ() * 0.52, 0, 0.01, 0);
            } else if (kind == Kind.ALTAR && random.nextInt(10) == 0) {
                double ox = (random.nextDouble() - 0.5) * 0.8, oz = (random.nextDouble() - 0.5) * 0.8;
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, ox, 0.3, oz);
            }
            return;
        }

        double px = pos.getX() + 0.5;
        double py = pos.getY() + 1.02;
        double pz = pos.getZ() + 0.5;

        switch (kind) {
            case STOVE -> {
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            px + (random.nextDouble() - 0.5) * 0.3, py, pz + (random.nextDouble() - 0.5) * 0.3, 0, 0.04, 0);
                }
                if (random.nextInt(3) == 0) {
                    Direction face = state.getValue(FACING);
                    double fx = px + face.getStepX() * 0.45;
                    double fz = pz + face.getStepZ() * 0.45;
                    level.addParticle(ParticleTypes.FLAME, fx, pos.getY() + 0.35, fz, 0, 0.01, 0);
                }
                if (random.nextInt(12) == 0) {
                    level.playLocalSound(px, py, pz, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.45f, 0.9f + random.nextFloat() * 0.3f, false);
                }
            }
            case SMELTER -> {
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.LAVA, px + (random.nextDouble() - 0.5) * 0.4, py, pz + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
                }
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.FLAME, px + (random.nextDouble() - 0.5) * 0.3, py, pz + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
                }
                if (random.nextInt(12) == 0) {
                    level.playLocalSound(px, py, pz, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.5f, 0.9f + random.nextFloat() * 0.2f, false);
                }
            }
            case BENCH -> {
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.CRIT, px + (random.nextDouble() - 0.5) * 0.4, py + 0.1, pz + (random.nextDouble() - 0.5) * 0.4,
                            (random.nextDouble() - 0.5) * 0.1, 0.06, (random.nextDouble() - 0.5) * 0.1);
                }
                if (random.nextInt(16) == 0) {
                    level.playLocalSound(px, py, pz, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.5f, 1.2f + random.nextFloat() * 0.2f, false);
                }
            }
            case TABLE -> {
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.WITCH, px + (random.nextDouble() - 0.5) * 0.3, py, pz + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
                }
                if (random.nextInt(16) == 0) {
                    level.playLocalSound(px, py, pz, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.45f, 1.1f + random.nextFloat() * 0.2f, false);
                }
            }
            case TANNERY -> {
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.DRIPPING_WATER, px + (random.nextDouble() - 0.5) * 0.4, py - 0.1, pz + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
                }
                if (random.nextInt(18) == 0) {
                    level.playLocalSound(px, py, pz, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.BLOCKS, 0.45f, 0.9f + random.nextFloat() * 0.2f, false);
                }
            }
            case FISH -> {
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE, px + (random.nextDouble() - 0.5) * 0.3, py, pz + (random.nextDouble() - 0.5) * 0.3, 0, 0.03, 0);
                }
                if (random.nextInt(18) == 0) {
                    level.playLocalSound(px, py, pz, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.35f, 1.3f + random.nextFloat() * 0.2f, false);
                }
            }
            case MILL -> {
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.COMPOSTER, px + (random.nextDouble() - 0.5) * 0.3, py, pz + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
                }
                if (random.nextInt(16) == 0) {
                    level.playLocalSound(px, py, pz, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.45f, 0.8f + random.nextFloat() * 0.2f, false);
                }
            }
            case FORGE -> {
                Direction face = state.getValue(FACING);
                double fx = px + face.getStepX() * 0.52;
                double fz = pz + face.getStepZ() * 0.52;
                level.addParticle(ParticleTypes.FLAME, fx + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.35 + random.nextDouble() * 0.25,
                        fz + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
                if (random.nextInt(6) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.03, 0);
                }
                if (random.nextInt(20) == 0) {
                    level.playLocalSound(px, pos.getY() + 0.5, pz, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5f, 0.8f + random.nextFloat() * 0.3f, false);
                }
            }
            case ALTAR -> {
                double ox = (random.nextDouble() - 0.5) * 1.0;
                double oz = (random.nextDouble() - 0.5) * 1.0;
                level.addParticle(ParticleTypes.ENCHANT, px, pos.getY() + 1.0, pz, ox, 0.5, oz);
            }
        }
    }
}
