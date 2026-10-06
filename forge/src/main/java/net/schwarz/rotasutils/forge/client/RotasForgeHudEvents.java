package net.schwarz.rotasutils.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.schwarz.rotasutils.client.hud.RotasHudRenderer;

public final class RotasForgeHudEvents {
    private RotasForgeHudEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(RotasForgeHudEvents::beforeOverlay);
        MinecraftForge.EVENT_BUS.addListener(RotasForgeHudEvents::afterOverlay);
    }

    private static void beforeOverlay(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                || id.equals(VanillaGuiOverlay.JUMP_BAR.id())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id())) {
            event.setCanceled(true);
        }
    }

    private static void afterOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        RotasHudRenderer.renderVitals(event.getGuiGraphics(), minecraft);
    }
}