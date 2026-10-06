package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.schwarz.rotasutils.client.render.EldritchSkyCinema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererEldritchShakeMixin {
    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void rotasutils$eldritchShake(PoseStack pose, float partialTick, CallbackInfo ci) {
        EldritchSkyCinema.shake(pose, partialTick);
        net.schwarz.rotasutils.client.render.CameraQuake.apply(pose, partialTick);
        net.schwarz.rotasutils.client.cinematic.CinematicDirector.applyRoll(pose, partialTick);
    }
}
