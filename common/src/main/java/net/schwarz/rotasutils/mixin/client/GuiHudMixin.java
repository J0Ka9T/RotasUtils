package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.schwarz.rotasutils.client.hud.RotasHudRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiHudMixin {
    @Shadow @Final protected Minecraft minecraft;

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void rotasutils$renderModernHotbar(float partialTick, GuiGraphics graphics, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        RotasHudRenderer.renderHotbar(graphics, minecraft, partialTick);
        ci.cancel();
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void rotasutils$renderModernVitals(GuiGraphics graphics, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        RotasHudRenderer.renderVitals(graphics, minecraft);
        ci.cancel();
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void rotasutils$hideVanillaVehicleHealth(GuiGraphics graphics, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V",
            at = @At("HEAD"), cancellable = true)
    private void rotasutils$hideVanillaJumpMeter(PlayerRideableJumping vehicle, GuiGraphics graphics,
                                                 int x, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true)
    private void rotasutils$hideVanillaItemName(GuiGraphics graphics, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }
        ci.cancel();
    }
}