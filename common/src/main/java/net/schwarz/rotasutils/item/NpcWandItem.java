package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.BoardService;
import net.schwarz.rotasutils.server.WorldPicker;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The NPC Wand: point at a mob and click to make it an NPC or edit it.
 *
 * <p>Clicks on mobs are handled in {@code RotasEvents.onInteractEntity}, which runs before the mob's
 * own interaction, so a villager's trade menu or an Easy NPC dialog never opens on top of the
 * editor. This class only handles clicking the air, which opens the NPC list.</p>
 */
public class NpcWandItem extends Item {
    public NpcWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!BoardService.isAdmin(serverPlayer, RotasData.get(serverPlayer.server))) {
                serverPlayer.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.item.npc_admin_only")
                        .withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            if (WorldPicker.cancel(serverPlayer)) {
                serverPlayer.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.item.placing_cancelled"));
            } else {
                RotasNetwork.openAdminMenu(serverPlayer, "NPCS");
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.npc_wand.mob").withStyle(ChatFormatting.GRAY));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.npc_wand.test").withStyle(ChatFormatting.GRAY));
        tooltip.add(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.tooltip.npc_wand.list").withStyle(ChatFormatting.DARK_GRAY));
    }
}
