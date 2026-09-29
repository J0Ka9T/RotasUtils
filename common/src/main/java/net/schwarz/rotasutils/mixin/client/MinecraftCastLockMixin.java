package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.schwarz.rotasutils.client.cinematic.CinematicDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No attacking, mining or using items while the player's own cutscene plays. */
@Mixin(Minecraft.class)
public abstract class MinecraftCastLockMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void rotasutils$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CinematicDirector.locksInput()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void rotasutils$noUse(CallbackInfo ci) {
        if (CinematicDirector.locksInput()) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void rotasutils$noMine(boolean leftClick, CallbackInfo ci) {
        if (CinematicDirector.locksInput()) {
            ci.cancel();
        }
    }
}
