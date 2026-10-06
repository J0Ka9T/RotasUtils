package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.schwarz.rotasutils.client.inventory.RotasInventoryRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void rotasutils$scrollHubLists(long window, double scrollX, double scrollY, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof InventoryScreen inventory)
                || minecraft.gameMode == null || minecraft.gameMode.hasInfiniteItems()) {
            return;
        }
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) inventory;
        int rawWidth = Math.max(1, minecraft.getWindow().getScreenWidth());
        int rawHeight = Math.max(1, minecraft.getWindow().getScreenHeight());
        double mouseX = minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth() / rawWidth;
        double mouseY = minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight() / rawHeight;
        if (RotasInventoryRenderer.scrollList(
                screen.rotasutils$getLeftPos(), screen.rotasutils$getTopPos(),
                screen.rotasutils$getImageWidth(), screen.rotasutils$getImageHeight(),
                mouseX, mouseY, scrollY)) {
            ci.cancel();
        }
    }
}
