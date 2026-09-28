package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
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
import net.schwarz.rotasutils.sky.SkySunder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Operator-only: binds the open eldritch rift in a golden seal and shatters it, closing the sky - the
 * {@link SkySunder}. Works on any sigil's rift, the four skies included.
 */
public final class SunderingSigilItem extends Item {
    public SunderingSigilItem(Properties properties) {
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
        switch (SkySunder.begin(serverLevel)) {
            case NO_RIFT -> {
                serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.sundering_sigil.no_rift"), true);
                return InteractionResultHolder.fail(held);
            }
            case RUNNING -> {
                serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.sundering_sigil.running"), true);
                return InteractionResultHolder.fail(held);
            }
            default -> {
            }
        }
        serverPlayer.getCooldowns().addCooldown(this, SkySunder.END);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.rotasutils.sundering_sigil.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
