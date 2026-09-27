package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.schwarz.rotasutils.client.inventory.RotasInventoryRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Matches InventoryScreen mouse targeting and tooltip presentation to the Character Hub. */
@Mixin(AbstractContainerScreen.class)
public abstract class InventorySlotHitboxMixin {
    /** Forge adds a per-slot color argument to the vanilla hover overlay. */
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"
            ),
            index = 4,
            require = 0
    )
    private int rotasutils$hideForgeVanillaSlotHighlight(int color) {
        if ((Object) this instanceof InventoryScreen) {
            return 0x00000000;
        }
        return color;
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;III)V"
            ),
            require = 0
    )
    private void rotasutils$hideFabricVanillaSlotHighlight(GuiGraphics graphics, int x, int y, int blitOffset) {
        if ((Object) this instanceof InventoryScreen) {
            return;
        }
        AbstractContainerScreen.renderSlotHighlight(graphics, x, y, blitOffset);
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void rotasutils$expandInventoryHitbox(Slot slot, double mouseX, double mouseY,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) {
            return;
        }
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        // The hub draws sockets, crafting cells and bag cells at different sizes around the item;
        // the whole drawn cell is the target, centred on the 16px item.
        int size = RotasInventoryRenderer.slotHitSize(
                screen.rotasutils$getImageWidth(), screen.rotasutils$getImageHeight(), slot.index);
        double x = screen.rotasutils$getLeftPos() + slot.x + 8 - size / 2;
        double y = screen.rotasutils$getTopPos() + slot.y + 8 - size / 2;
        if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Replaces the vanilla container tooltip with the hub's inspection panel. AbstractContainerScreen
     * owns renderTooltip, so the injection lives here rather than on InventoryScreen itself.
     */
    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), cancellable = true)
    private void rotasutils$renderHubInspection(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.gameMode.hasInfiniteItems()) return;
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        RotasInventoryRenderer.renderOverlay(graphics,
                screen.rotasutils$getLeftPos(), screen.rotasutils$getTopPos(),
                screen.rotasutils$getImageWidth(), screen.rotasutils$getImageHeight(), mouseX, mouseY);
        ci.cancel();
    }
}
