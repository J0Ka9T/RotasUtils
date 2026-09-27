package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class HouseWandItem extends Item {
    private static final String TAG = "RotasHouseWand";
    public HouseWandItem(Properties properties) { super(properties); }
    public static BlockPos first(ItemStack stack) { return point(stack, "first"); }
    public static BlockPos second(ItemStack stack) { return point(stack, "second"); }
    public static String dimension(ItemStack stack) { CompoundTag tag=stack.getTagElement(TAG); return tag == null ? "" : tag.getString("dimension"); }
    public static void select(ItemStack stack, String key, String dimension, BlockPos pos) { CompoundTag tag=stack.getOrCreateTagElement(TAG); tag.putString("dimension",dimension); tag.putLong(key,pos.asLong()); }
    public static void clear(ItemStack stack) { stack.removeTagKey(TAG); }
    private static BlockPos point(ItemStack stack, String key) { CompoundTag tag=stack.getTagElement(TAG); return tag != null && tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null; }

    /** Right-click a block: second corner, or with sneak and a full selection, move the nearest face there. */
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            ItemStack wand = context.getItemInHand();
            if (player.isShiftKeyDown() && first(wand) != null && second(wand) != null) {
                net.schwarz.rotasutils.house.HouseWandService.pushFace(player, wand, context.getClickedPos());
            } else {
                net.schwarz.rotasutils.house.HouseWandService.selectSecond(player, wand, context.getClickedPos());
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /**
     * Right-click the air: with both corners picked, open the create form on that selection; with no
     * selection inside a house, open that house's screen and settings. Sneak + right-click the air:
     * clear the selection, or with none inside a house, load that house's area to reshape it.
     */
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean selected = first(stack) != null && second(stack) != null;
        boolean anything = first(stack) != null || second(stack) != null;
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!player.isShiftKeyDown()) {
            if (selected) {
                net.schwarz.rotasutils.house.HouseWandService.openCreate(serverPlayer);
            } else if (!net.schwarz.rotasutils.house.HouseWandService.openHouseHere(serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (anything || !net.schwarz.rotasutils.house.HouseWandService.loadHouse(serverPlayer, stack)) {
            net.schwarz.rotasutils.house.HouseWandService.clear(serverPlayer, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("rotasutils.tooltip.house_wand.left").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("rotasutils.tooltip.house_wand.right").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("rotasutils.tooltip.house_wand.create").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("rotasutils.tooltip.house_wand.push").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("rotasutils.tooltip.house_wand.open").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("rotasutils.tooltip.house_wand.clear").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("rotasutils.tooltip.house_wand.view").withStyle(ChatFormatting.DARK_AQUA));
        BlockPos first = first(stack);
        BlockPos second = second(stack);
        if (first != null && second != null) {
            tooltip.add(Component.literal((Math.abs(first.getX() - second.getX()) + 1) + "x" + (Math.abs(first.getY() - second.getY()) + 1)
                    + "x" + (Math.abs(first.getZ() - second.getZ()) + 1)).withStyle(ChatFormatting.YELLOW));
        }
    }
}
