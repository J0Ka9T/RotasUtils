package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.BoardService;
import net.schwarz.rotasutils.server.WorldPicker;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AdminToolItem extends Item {
    public AdminToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            RotasData data = RotasData.get(serverPlayer.server);
            if (!BoardService.isAdmin(serverPlayer, data)) {
                serverPlayer.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.item.not_admin")
                        .withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            if (WorldPicker.cancel(serverPlayer)) {
                serverPlayer.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.item.selection_cancelled"));
            } else {
                RotasNetwork.openAdminMenu(serverPlayer);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (WorldPicker.resolveBlock(player, context.getClickedPos())) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (WorldPicker.resolveEntity(serverPlayer, target)) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.admin_tool.open")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.admin_tool.select")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
