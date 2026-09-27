package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScreenScale;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the Rotas screens and the Character Hub the same size on every monitor. While one of them
 * is open the GUI scale is lowered (never raised) so the canvas reaches {@link ScreenScale}'s design
 * size; every other screen and the in-game view get the player's own scale back. Vanilla's
 * projection, mouse mapping and scissor all read the window scale, so input stays aligned.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftScreenScaleMixin {
    @Shadow @Final private Window window;
    @Shadow @Final public Options options;
    @Shadow public Screen screen;
    @Shadow public MultiPlayerGameMode gameMode;

    @Shadow public abstract boolean isEnforceUnicode();

    /** Before the new screen's init, so it lays out against the adjusted canvas. */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void rotasutils$scaleForScreen(Screen next, CallbackInfo ci) {
        rotasutils$applyScale(next);
    }

    /** A window resize resets the scale from options; reapply before the open screen re-inits. */
    @Inject(method = "resizeDisplay", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;setGuiScale(D)V", shift = At.Shift.AFTER))
    private void rotasutils$scaleAfterResize(CallbackInfo ci) {
        rotasutils$applyScale(screen);
    }

    @Unique
    private void rotasutils$applyScale(Screen target) {
        int playerScale = window.calculateScale(options.guiScale().get(), isEnforceUnicode());
        int scale;
        if (target instanceof RotasScreen) {
            scale = ScreenScale.effectiveScale(playerScale, window.getWidth(), window.getHeight());
        } else if (rotasutils$managed(target)) {
            // The Character Hub adapts to a smaller canvas, so small monitors keep scale 2 for it.
            scale = ScreenScale.hubScale(playerScale, window.getWidth(), window.getHeight());
        } else {
            scale = playerScale;
        }
        if (window.getGuiScale() != scale) {
            window.setGuiScale(scale);
        }
    }

    @Unique
    private boolean rotasutils$managed(Screen target) {
        if (target instanceof RotasScreen) {
            return true;
        }
        // Only the survival inventory is the Character Hub; creative keeps vanilla sizing.
        return target instanceof InventoryScreen && gameMode != null && !gameMode.hasInfiniteItems();
    }
}
