package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.schwarz.rotasutils.client.render.SkyClashCinematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Clash of Heavens film's lens (its zoom, and no held item in shot), plus eruption lens punches. */
@Mixin(GameRenderer.class)
public abstract class GameRendererCinematicMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void rotasutils$cinematicFov(Camera camera, float partialTick, boolean useFovSetting,
                                         CallbackInfoReturnable<Double> cir) {
        double k = SkyClashCinematic.fov(partialTick)
                * net.schwarz.rotasutils.client.render.CameraQuake.lens(partialTick);
        if (k != 1.0) {
            cir.setReturnValue(cir.getReturnValue() * k);
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void rotasutils$cinematicHand(PoseStack pose, Camera camera, float partialTick, CallbackInfo ci) {
        if (SkyClashCinematic.hidesHand(partialTick)) {
            ci.cancel();
        }
    }
}
