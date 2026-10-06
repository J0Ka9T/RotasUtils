package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EditBox.class)
public abstract class EditBoxFlatTextMixin {
    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int rotasutils$flatSequence(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        return graphics.drawString(font, text, x, y, color, !rotasutils$flat());
    }

    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int rotasutils$flatString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        return graphics.drawString(font, text, x, y, color, !rotasutils$flat());
    }

    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int rotasutils$flatComponent(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        return graphics.drawString(font, text, x, y, color, !rotasutils$flat());
    }

    @Unique
    private static boolean rotasutils$flat() {
        return Minecraft.getInstance().screen instanceof RotasScreen;
    }
}
