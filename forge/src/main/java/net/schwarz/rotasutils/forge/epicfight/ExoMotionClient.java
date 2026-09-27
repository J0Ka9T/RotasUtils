package net.schwarz.rotasutils.forge.epicfight;

import net.minecraftforge.common.MinecraftForge;
import net.schwarz.rotasutils.item.ExoDisintegratorItem;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.client.forgeevent.UpdatePlayerMotionEvent;

/**
 * Client only. Epic Fight picks the upper-body motion from the item's use animation, and the Disintegrator
 * uses none (so vanilla shows no bow pull). While its beam is channelled, switch the composite layer to AIM
 * so the shouldered exo_aim pose plays and follows the player's look pitch.
 */
final class ExoMotionClient {
    private ExoMotionClient() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(ExoMotionClient::onComposite);
    }

    private static void onComposite(UpdatePlayerMotionEvent.CompositeLayer event) {
        var player = event.getPlayerPatch().getOriginal();
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof ExoDisintegratorItem) {
            event.setMotion(LivingMotions.AIM);
        }
    }
}
