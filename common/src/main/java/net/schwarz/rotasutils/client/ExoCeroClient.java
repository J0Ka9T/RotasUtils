package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.schwarz.rotasutils.item.ExoDisintegratorItem;
import net.schwarz.rotasutils.network.RotasNetwork;
import net.schwarz.rotasutils.registry.RotasRegistry;

@Environment(EnvType.CLIENT)
public final class ExoCeroClient {
    private ExoCeroClient() {
    }

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
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        RotasNetwork.sendAction("exo_cero");
        return true;
    }
}
