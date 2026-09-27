package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Same as {@link EditBoxFlatTextMixin} for the multi-line editors used by the admin studios. */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxFlatTextMixin {
    @Redirect(method = "renderContents", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int rotasutils$flatString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        return graphics.drawString(font, text, x, y, color, !rotasutils$flat());
    }

    /** The character counter under the editor is drawn from renderDecorations, not renderContents. */
    @Redirect(method = "renderDecorations", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int rotasutils$flatComponent(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        return graphics.drawString(font, text, x, y, color, !rotasutils$flat());
    }

    @Unique
    private static boolean rotasutils$flat() {
        return Minecraft.getInstance().screen instanceof RotasScreen;
    }
}
