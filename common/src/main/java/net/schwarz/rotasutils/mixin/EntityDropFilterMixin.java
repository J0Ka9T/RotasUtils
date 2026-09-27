package net.schwarz.rotasutils.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.DropFilterService;
import net.schwarz.rotasutils.server.DropRollContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a blocked item being dropped by a dying mob.
 *
 * <p>Every drop a mob makes goes through {@code spawnAtLocation}, whether it came from a vanilla loot
 * table, a data pack or another mod, so this is the one place that catches all of them. It only looks
 * while {@link DropRollContext} says the entity is in the middle of its death drop, so a
 * mob handing an item over for any other reason - a trade, a shear, a dropped weapon - is untouched.</p>
 */
@Mixin(Entity.class)
public abstract class EntityDropFilterMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void rotasutils$filterDrop(ItemStack stack, float offsetY, CallbackInfoReturnable<ItemEntity> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide || !DropRollContext.isDropping(self)) {
            return;
        }
        RotasData data = RotasData.instance();
        if (data != null && DropFilterService.blocked(data, self, stack)) {
            // Returning null is what vanilla itself returns for an empty stack, so no caller is surprised.
            cir.setReturnValue(null);
        }
    }
}
