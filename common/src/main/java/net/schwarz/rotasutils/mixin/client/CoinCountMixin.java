package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.item.GoldCoins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A coin stack's slot shows the gold it holds ("250", "12.5k"), not the item count of 1. */
@Mixin(GuiGraphics.class)
public abstract class CoinCountMixin {
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"), cancellable = true)
    private void rotasutils$coinAmount(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (text != null || !GoldCoins.is(stack) || stack.getCount() != 1) {
            return;
        }
        long amount = GoldCoins.amount(stack);
        if (amount > 1) {
            ci.cancel();
            // The label is not null, so this call draws normally and does not come back here.
            ((GuiGraphics) (Object) this).renderItemDecorations(font, stack, x, y, GoldCoins.format(amount, true));
        }
    }
}
