package net.schwarz.rotasutils.mixin.client.compat;

import net.schwarz.rotasutils.client.theme.SophisticatedTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.SettingsScreen", remap = false)
public abstract class SophisticatedSettingsScreenMixin {
    @ModifyConstant(
            method = {"renderSettingsTitle", "renderLabels", "m_280003_"},
            constant = @Constant(intValue = SophisticatedTheme.DEFAULT_DARK_TEXT),
            require = 0
    )
    private int rotasutils$themeDefaultText(int color) {
        return SophisticatedTheme.remapDefaultTextColor(color);
    }
}
