package net.schwarz.rotasutils.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the modern inventory screen to move vanilla slots without replacing menu logic. */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void rotasutils$setX(int x);

    @Mutable
    @Accessor("y")
    void rotasutils$setY(int y);
}
