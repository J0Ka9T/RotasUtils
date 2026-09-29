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

/**
 * Draws the selected level zone's outline once the world is rendered.
 *
 * <p>Injected at the end of {@link LevelRenderer#renderLevel} so the outline is drawn in the same
 * camera space as the world, and only while the Zone Wand is held (the renderer makes that check
 * itself, so this hook stays cheap for every other frame).</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void rotasutils$zoneOutline(PoseStack poseStack, float partialTick, long finishNanos, boolean drawBlockOutline,
                                        Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                        Matrix4f projection, CallbackInfo ci) {
        net.schwarz.rotasutils.client.hud.MobPlateRenderer.render(poseStack, camera, partialTick);
        ZoneOutlineRenderer.render(poseStack, camera);
        net.schwarz.rotasutils.client.render.RiftFxRenderer.render(poseStack, camera, partialTick);
        net.schwarz.rotasutils.client.render.CeroFxRenderer.render(poseStack, camera, partialTick);
        net.schwarz.rotasutils.client.cinematic.RedVfxRenderer.render(poseStack, camera, partialTick, projection);
    }
}
