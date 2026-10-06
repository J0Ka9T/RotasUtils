package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiExperienceMixin {
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void rotasutils$hideVanillaExperienceBar(GuiGraphics graphics, int x, CallbackInfo ci) {
        ci.cancel();
    }
}
