package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.server.TradeService;

public final class RecipeScrollItem extends Item {
    public static final String TAG = "RotasRecipe";
    public static final String TRADE_TAG = "RotasTrade";

    public RecipeScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Item item, String trade, String recipeId) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG, recipeId == null ? "" : recipeId);
        tag.putString(TRADE_TAG, trade == null ? "" : trade);
        return stack;
    }

    public static String recipeOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TAG);
    }

    public static String tradeOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TRADE_TAG);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = recipeOf(stack);
        return id.isBlank() ? super.getName(stack)
                : Component.translatable("item.rotasutils.recipe_scroll.of", TradeService.recipeName(id));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer server
                && TradeService.learn(server, tradeOf(stack), recipeOf(stack))) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
