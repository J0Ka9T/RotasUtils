package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.schwarz.rotasutils.client.cinematic.CinematicOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the cutscene's letterbox and flashes over the finished frame, after the game's own interface. */
@Mixin(GameRenderer.class)
public abstract class GameRendererOverlayMixin {
    @Inject(method = "render(FJZ)V", at = @At("TAIL"))
    private void rotasutils$cinematicOverlay(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || net.schwarz.rotasutils.client.cinematic.ClientCasts.local() == null) {
            return;
        }
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        CinematicOverlay.render(graphics, partialTick);
    }
}
