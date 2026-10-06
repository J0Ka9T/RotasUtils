package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.material.FogType;
import net.schwarz.rotasutils.client.render.EldritchSkyRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {
    @Inject(method = "renderSky", at = @At("TAIL"))
    private void rotasutils$eldritchSky(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick,
                                        Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        EldritchSkyRenderer.render(poseStack, partialTick, isFoggy,
                camera.getFluidInCamera() != FogType.NONE);
        net.schwarz.rotasutils.client.render.NightSkyMeteorRenderer.render(poseStack, partialTick, isFoggy,
                camera.getFluidInCamera() != FogType.NONE);
        net.schwarz.rotasutils.client.render.SkyClashRenderer.render(poseStack, partialTick,
                camera.getFluidInCamera() != FogType.NONE);
        net.schwarz.rotasutils.client.render.SkySunderRenderer.render(poseStack, partialTick,
                camera.getFluidInCamera() != FogType.NONE);
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void rotasutils$hideClouds(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick,
                                       double camX, double camY, double camZ, CallbackInfo ci) {
        if (net.schwarz.rotasutils.client.render.EldritchSkyLetterbox.hidesClouds(partialTick)
                || net.schwarz.rotasutils.client.render.SkyClashRenderer.hidesClouds(partialTick)
                || net.schwarz.rotasutils.client.cinematic.Stargun.hidesClouds(partialTick)) {
            ci.cancel();
        }
    }
}
