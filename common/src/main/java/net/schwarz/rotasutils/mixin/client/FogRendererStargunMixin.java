package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.schwarz.rotasutils.client.cinematic.Stargun;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererStargunMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void rotasutils$stargunFog(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorld, CallbackInfo ci) {
        float amount = (float) Stargun.duskAmount(partialTick) * 0.85f;
        if (amount <= 0.002f) {
            return;
        }
        fogRed += (0.13f - fogRed) * amount;
        fogGreen += (0.08f - fogGreen) * amount;
        fogBlue += (0.26f - fogBlue) * amount;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
    }
}
