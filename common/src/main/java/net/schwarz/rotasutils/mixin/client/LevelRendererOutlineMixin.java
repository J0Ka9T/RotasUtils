package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.schwarz.rotasutils.client.ZoneOutlineRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void rotasutils$zoneOutline(PoseStack poseStack, float partialTick, long finishNanos, boolean drawBlockOutline,
                                        Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                        Matrix4f projection, CallbackInfo ci) {
        net.schwarz.rotasutils.client.hud.MobPlateRenderer.render(poseStack, camera, partialTick);
        ZoneOutlineRenderer.render(poseStack, camera);
        net.schwarz.rotasutils.client.QuestNavigator.render(poseStack, camera);
        net.schwarz.rotasutils.client.render.RiftFxRenderer.render(poseStack, camera, partialTick);
        net.schwarz.rotasutils.client.render.CeroFxRenderer.render(poseStack, camera, partialTick);
        net.schwarz.rotasutils.client.cinematic.RedVfxRenderer.render(poseStack, camera, partialTick, projection);
        net.schwarz.rotasutils.client.cinematic.PurpleVfxRenderer.render(poseStack, camera, partialTick, projection);
        net.schwarz.rotasutils.client.cinematic.ProjectionVfxRenderer.render(poseStack, camera, partialTick, projection);
        net.schwarz.rotasutils.client.cinematic.StargunVfxRenderer.render(poseStack, camera, partialTick, projection);
        net.schwarz.rotasutils.client.cinematic.PurpleDissolve.render(poseStack, camera, partialTick);
    }
}
