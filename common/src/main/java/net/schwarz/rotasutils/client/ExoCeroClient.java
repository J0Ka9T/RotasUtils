package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.schwarz.rotasutils.item.ExoDisintegratorItem;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;

/**
 * The Cero Metralleta's input edge: shift + left-click with the Disintegrator in the main hand.
 *
 * <p>The click is only a request. It is filtered locally so a burst is never sent while the gun is
 * cooling or the wrong thing is held, and the server re-validates everything before it fires. A
 * consumed click also never reaches the vanilla attack, so sneaking over a block cannot start
 * mining instead of shooting.</p>
 */
@Environment(EnvType.CLIENT)
public final class ExoCeroClient {
    private ExoCeroClient() {
    }

    /** Sends the burst request; true when the click should be swallowed. */
    public static boolean tryUnleash() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.gameMode == null) {
            return false;
        }
        if (!minecraft.player.isShiftKeyDown()
                || minecraft.player.isUsingItem()
                || !(minecraft.player.getMainHandItem().getItem() instanceof ExoDisintegratorItem)
                || minecraft.player.getCooldowns().isOnCooldown(RotasRegistry.EXO_DISINTEGRATOR.get())) {
            return false;
        }
        // Swing for feel; the server's own gun hand never needs to know.
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        RotasNetwork.sendAction("exo_cero");
        return true;
    }
}
