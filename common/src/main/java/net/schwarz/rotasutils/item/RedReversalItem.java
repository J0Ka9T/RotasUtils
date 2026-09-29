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
import net.schwarz.rotasutils.ability.AbilityManager;
import net.schwarz.rotasutils.ability.RedReversalAbility;

import java.util.List;

/**
 * The entry point to Red Reversal and nothing more: a right click asks the {@link AbilityManager} to
 * begin the ability. Validation, cooldown, targeting, the timeline and the cutscene all live there.
 */
public class RedReversalItem extends Item {
    public RedReversalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer server
                && AbilityManager.start(server, RedReversalAbility.INSTANCE) == AbilityManager.Result.STARTED
                && RedReversalAbility.INSTANCE.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, RedReversalAbility.INSTANCE.cooldownTicks());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.rotasutils.red_reversal.desc").withStyle(ChatFormatting.RED));
    }
}
