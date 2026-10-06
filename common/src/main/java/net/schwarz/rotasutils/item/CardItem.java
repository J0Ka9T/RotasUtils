package net.schwarz.rotasutils.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CardItem extends Item {
    public static final String TAG = "RotasCard";

    public CardItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Item item, String cardId, int count) {
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        CompoundTag card = new CompoundTag();
        card.putInt("schema", 1);
        card.putString("id", cardId == null ? "" : cardId);
        stack.getOrCreateTag().put(TAG, card);
        return stack;
    }

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_COMPOUND)) {
            return "";
        }
        return tag.getCompound(TAG).getString("id");
    }

    public static boolean isCard(ItemStack stack) {
        return stack != null && stack.getItem() instanceof CardItem;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = idOf(stack);
        if (id.isBlank()) {
            return super.getName(stack);
        }
        var entry = net.schwarz.rotasutils.core.CardIndex.get(id);
        return Component.literal(entry == null ? id : entry.name())
                .withStyle(style -> style.withColor(net.schwarz.rotasutils.core.CardIndex.color(id) & 0xFFFFFF));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            net.schwarz.rotasutils.network.RotasNetwork.openSockets(server);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
