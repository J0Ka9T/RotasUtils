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
import net.schwarz.rotasutils.ability.AbilityDefinition;
import net.schwarz.rotasutils.ability.AbilityManager;
import net.schwarz.rotasutils.ability.RedReversalAbility;

import java.util.List;

public class RedReversalItem extends Item {
    private final AbilityDefinition ability;
    private final String desc;

    public RedReversalItem(Properties properties) {
        this(properties, RedReversalAbility.INSTANCE, "item.rotasutils.red_reversal.desc");
    }

    public RedReversalItem(Properties properties, AbilityDefinition ability, String desc) {
        super(properties);
        this.ability = ability;
        this.desc = desc;
    }

    public AbilityDefinition ability() {
        return ability;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer server
                && AbilityManager.start(server, ability) == AbilityManager.Result.STARTED
                && ability.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, ability.cooldownTicks());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(desc).withStyle(ChatFormatting.RED));
    }
}
