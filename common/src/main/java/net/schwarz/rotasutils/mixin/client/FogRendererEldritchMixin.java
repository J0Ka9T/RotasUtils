package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.schwarz.rotasutils.client.render.EldritchSkyClientTint;
import net.schwarz.rotasutils.client.render.EldritchSkyEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererEldritchMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void rotasutils$eldritchFogColor(Camera camera, float partialTick, ClientLevel level,
                                                    int renderDistance, float darkenWorld, CallbackInfo ci) {
        float amount = EldritchSkyClientTint.fogStrength(partialTick);
        if (amount <= 0.002f) {
            return;
        }
        float[] target = EldritchSkyClientTint.fogTarget();
        fogRed = EldritchSkyEnvironment.mix(fogRed, target[0], amount);
        fogGreen = EldritchSkyEnvironment.mix(fogGreen, target[1], amount);
        fogBlue = EldritchSkyEnvironment.mix(fogBlue, target[2], amount);
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
    }
}
