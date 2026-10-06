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
            cir.setReturnValue(null);
        }
    }
}
