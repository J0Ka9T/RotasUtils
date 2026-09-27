package net.schwarz.rotasutils.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.server.horse.HorseService;

/**
 * The horse whistle: use it to open the stable and call a horse, or use it on a SWEM horse you own to put that
 * horse in your stable.
 */
public final class HorseWhistleItem extends Item {
    public HorseWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            HorseService.open(serverPlayer, "", "STABLE", null);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            HorseService.Result result = HorseService.adopt(serverPlayer, target);
            RotasNetwork.feedback(serverPlayer, result.ok(), result.message());
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(result.message()), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
