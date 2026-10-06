package net.schwarz.rotasutils.forge.epicfight;

import net.minecraftforge.common.MinecraftForge;
import net.schwarz.rotasutils.item.ExoDisintegratorItem;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.client.forgeevent.UpdatePlayerMotionEvent;

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
