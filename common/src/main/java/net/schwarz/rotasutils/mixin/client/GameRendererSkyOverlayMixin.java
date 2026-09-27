package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.schwarz.rotasutils.client.render.SkyShaderOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after the world is drawn, and after a shader pack has composited it, but before the hand:
 * where the eldritch sky is drawn while a pack is on, so the pack cannot recolour, fog or cover it.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererSkyOverlayMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void rotasutils$deferredSky(float partialTick, long finishNanos, PoseStack pose, CallbackInfo ci) {
        SkyShaderOverlay.render();
        net.schwarz.rotasutils.client.render.WorldVfxOverlay.render();
    }
}
