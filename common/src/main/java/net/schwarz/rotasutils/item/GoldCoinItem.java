package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Gold coins. One stack holds any amount ({@link GoldCoins}); dropping one coin stack onto another in an
 * inventory merges them, and using a stack puts all of it in the wallet.
 */
public final class GoldCoinItem extends Item {
    public GoldCoinItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack coins = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            net.schwarz.rotasutils.server.GoldCoinService.depositHeld(serverPlayer, coins);
        }
        return InteractionResultHolder.sidedSuccess(coins, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        long amount = GoldCoins.amount(stack);
        Component base = super.getName(stack);
        return amount <= 1 || stack.getCount() > 1 ? base
                : Component.empty().append(base).append(" ×" + GoldCoins.format(amount, false));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("rotasutils.coin.worth", GoldCoins.format(GoldCoins.amount(stack), false))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("rotasutils.coin.hint").withStyle(ChatFormatting.GRAY));
    }

    /** Coins on the cursor clicked onto coins in a slot join that stack. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.PRIMARY || !GoldCoins.is(other) || !slot.allowModification(player)) {
            return false;
        }
        if (GoldCoins.merge(stack, other)) {
            slot.setChanged();
            access.set(other.isEmpty() ? ItemStack.EMPTY : other);
            return true;
        }
        return false;
    }
}
