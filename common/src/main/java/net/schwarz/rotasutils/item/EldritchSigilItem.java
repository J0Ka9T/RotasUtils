package net.schwarz.rotasutils.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.sky.EldritchSkyService;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;

/** Operator-only toggle for the eldritch sky rupture in the player's current dimension. */
public final class EldritchSigilItem extends Item {
    private final int variant;

    public EldritchSigilItem(Properties properties) {
        this(properties, net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_SKY);
    }

    /** A sigil that opens a particular kind of sky, e.g. the herald variant. */
    public EldritchSigilItem(Properties properties, int variant) {
        super(properties);
        this.variant = variant;
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
        EldritchSkyTransition.Snapshot next = EldritchSkyService.toggle(serverLevel, variant);
        boolean opening = next.state == EldritchSkyTransition.State.OPENING
                || next.state == EldritchSkyTransition.State.ACTIVE;
        // An opened sky is an invasion: waves, a champion and rewards. Closing it by hand ends the invasion.
        if (opening) {
            net.schwarz.rotasutils.sky.IncursionService.start(serverLevel, serverPlayer.position(), variant);
        } else {
            net.schwarz.rotasutils.sky.IncursionService.stop(serverLevel, false);
        }
        // No text: the sky, the letterbox and the sound tell the story.
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                opening ? SoundEvents.PORTAL_TRIGGER : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.AMBIENT, 1.0f, opening ? 0.5f : 0.7f);
        return InteractionResultHolder.consume(held);
    }
}
