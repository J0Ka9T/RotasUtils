package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.schwarz.rotasutils.client.cinematic.CinematicDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class CastInputLockMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void rotasutils$lock(boolean sneaking, float sneakSpeed, CallbackInfo ci) {
        if (CinematicDirector.locksInput()) {
            Input input = (Input) (Object) this;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
            input.forwardImpulse = 0f;
            input.leftImpulse = 0f;
        }
    }
}
