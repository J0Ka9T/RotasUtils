package net.schwarz.rotasutils.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.server.ProductionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractFurnaceBlockEntity.class, BrewingStandBlockEntity.class})
public abstract class ProductionStationMixin {
    @Inject(method = "canTakeItemThroughFace", at = @At("RETURN"), cancellable = true)
    private void rotasutils$sealedAutomation(int index, ItemStack stack, Direction direction,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        BlockEntity self = (BlockEntity) (Object) this;
        JobDef.ProductionEntry.Activity activity = self instanceof AbstractFurnaceBlockEntity
                ? (index == 2 ? JobDef.ProductionEntry.Activity.SMELT : null)
                : (index < 3 ? JobDef.ProductionEntry.Activity.BREW : null);
        if (activity != null
                && !ProductionService.automationMayTake(self.getLevel(), self.getBlockPos(), activity, stack)) {
            cir.setReturnValue(false);
        }
    }
}
