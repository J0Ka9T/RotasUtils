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
import net.schwarz.rotasutils.sky.SkyClash;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SkyClashSigilItem extends Item {
    public SkyClashSigilItem(Properties properties) {
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
        if (!serverLevel.dimensionType().hasSkyLight()) {
            serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.sky_clash.no_sky"), true);
            return InteractionResultHolder.fail(held);
        }
        if (!SkyClash.begin(serverLevel)) {
            serverPlayer.displayClientMessage(Component.translatable("rotasutils.msg.sky_clash.running"), true);
            return InteractionResultHolder.fail(held);
        }
        serverPlayer.getCooldowns().addCooldown(this, SkyClash.END);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.rotasutils.sky_clash_sigil.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
