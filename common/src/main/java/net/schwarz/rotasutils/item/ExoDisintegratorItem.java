package net.schwarz.rotasutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.entity.ExoBeamEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ExoElectric Disintegrator: a channelled beam gun. Holding use gathers charge at the muzzle
 * for {@link ExoBeamEntity#CHARGE} ticks, then looses a continuous exo-electric beam that pierces
 * every creature along it and unmakes whatever it kills. Sneaking while it is used fires the
 * Annihilation Lance instead: a single overcharged discharge, twice as wide and far heavier, that
 * runs its own length whether the trigger is held or not and leaves the gun cooling. Sneaking and
 * left-clicking instead looses the Cero Metralleta, a chained torrent of azure aim-tracking bolts
 * that saturates an area, which the client asks for and the server starts in {@link ExoBeamEntity}
 * and {@link net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity}. The rays themselves (charge,
 * damage, overheat, all visuals) live in those entities; the item only starts them.
 */
public class ExoDisintegratorItem extends Item {
    public ExoDisintegratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)
                || net.schwarz.rotasutils.entity.ExoCeroMuzzleEntity.activeFor(player)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (level instanceof ServerLevel server) {
            ExoBeamEntity.channel(server, player,
                    player.isShiftKeyDown() ? ExoBeamEntity.Mode.LANCE : ExoBeamEntity.Mode.BEAM);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rotasutils.exo_disintegrator.desc")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.rotasutils.exo_disintegrator.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rotasutils.exo_disintegrator.lance").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.rotasutils.exo_disintegrator.cero").withStyle(ChatFormatting.RED));
    }
}
