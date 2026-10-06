package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.RiftConvergenceEntity;
import net.schwarz.rotasutils.entity.TetrarchEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ConvergenceSigilItem extends Item {
    private static final double AHEAD = RiftConvergenceEntity.RADIUS + 3;
    private static final int COOLDOWN_TICKS = RiftConvergenceEntity.END;

    public ConvergenceSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(held, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(held);
        }
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.admin_only"), true);
            return InteractionResultHolder.fail(held);
        }
        Vec3 look = serverPlayer.getLookAngle().multiply(1, 0, 1).normalize();
        BlockPos centre = floor(serverLevel, BlockPos.containing(serverPlayer.position().add(look.scale(AHEAD))), 4);
        if (centre == null || !serverLevel.noCollision(new AABB(centre).inflate(1.5, 0, 1.5).expandTowards(0, 4, 0))) {
            serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.convergence.no_room"), true);
            return InteractionResultHolder.fail(held);
        }
        int[] heights = new int[4];
        for (int i = 0; i < 4; i++) {
            BlockPos spot = floor(serverLevel,
                    BlockPos.containing(Vec3.atBottomCenterOf(centre).add(RiftConvergenceEntity.riftOffset(i))), 3);
            if (spot == null) {
                serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.convergence.no_room"), true);
                return InteractionResultHolder.fail(held);
            }
            heights[i] = spot.getY() - centre.getY();
        }
        Vec3 toPlayer = serverPlayer.position().subtract(Vec3.atBottomCenterOf(centre));
        float yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.x, toPlayer.z));
        RiftConvergenceEntity.begin(serverLevel, Vec3.atBottomCenterOf(centre), yaw, heights);
        serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(held);
    }

    private static BlockPos floor(ServerLevel level, BlockPos near, int range) {
        for (int dy = 0; dy <= range; dy++) {
            for (int sign : new int[]{-1, 1}) {
                BlockPos pos = near.offset(0, dy * sign, 0);
                if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                    return pos;
                }
                if (dy == 0) {
                    break;
                }
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.rotasutils.convergence_sigil.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.rotasutils.convergence_sigil.warning", TetrarchEntity.LEVEL)
                .withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
