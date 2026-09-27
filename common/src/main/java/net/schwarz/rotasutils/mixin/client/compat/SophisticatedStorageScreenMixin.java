package net.schwarz.rotasutils.mixin.client.compat;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.schwarz.rotasutils.client.theme.SophisticatedTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class SophisticatedStorageScreenMixin extends AbstractContainerScreen<AbstractContainerMenu> {
    protected SophisticatedStorageScreenMixin(AbstractContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @ModifyConstant(
            method = {"renderStorageTitle", "renderLabels", "m_280003_"},
            constant = @Constant(intValue = SophisticatedTheme.DEFAULT_DARK_TEXT),
            require = 0,
            remap = false
    )
    private int rotasutils$themeDefaultText(int color) {
        return SophisticatedTheme.remapDefaultTextColor(color);
    }
}
