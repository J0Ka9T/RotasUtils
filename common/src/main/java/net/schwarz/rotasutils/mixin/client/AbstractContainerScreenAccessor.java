package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Loader-neutral access to container layout coordinates used by the inventory skin. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int rotasutils$getLeftPos();

    @Accessor("topPos")
    int rotasutils$getTopPos();

    @Accessor("imageWidth")
    int rotasutils$getImageWidth();

    @Accessor("imageWidth")
    void rotasutils$setImageWidth(int value);

    @Accessor("imageHeight")
    int rotasutils$getImageHeight();

    @Accessor("imageHeight")
    void rotasutils$setImageHeight(int value);
}
