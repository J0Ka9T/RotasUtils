package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.MouseHandler;
import net.schwarz.rotasutils.client.ExoCeroClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneak + left-click is the Disintegrator's Cero Metralleta. The click is cancelled when the burst
 * takes it, so the same press cannot also swing at, or start mining, whatever is in front.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerClickMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void rotasutils$ceroClick(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS
                && ExoCeroClient.tryUnleash()) {
            ci.cancel();
        }
    }
}
