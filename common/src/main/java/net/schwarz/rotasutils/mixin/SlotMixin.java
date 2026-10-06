package net.schwarz.rotasutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.server.ProductionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void rotasutils$lockSealedResult(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Slot self = (Slot) (Object) this;
        JobDef.ProductionEntry.Activity activity = self instanceof ResultSlot ? JobDef.ProductionEntry.Activity.CRAFT
                : self instanceof FurnaceResultSlot ? JobDef.ProductionEntry.Activity.SMELT
                : self.container instanceof BrewingStandBlockEntity && self.getContainerSlot() < 3
                ? JobDef.ProductionEntry.Activity.BREW : null;
        if (activity != null && ProductionService.takeLocked(serverPlayer, activity, self.getItem())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void rotasutils$brewedPotion(Player player, ItemStack stack, CallbackInfo ci) {
        Slot self = (Slot) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && self.container instanceof BrewingStandBlockEntity
                && self.getContainerSlot() < 3 && !stack.isEmpty()) {
            ProductionService.produced(serverPlayer, JobDef.ProductionEntry.Activity.BREW,
                    ProductionService.item(stack), stack.getCount());
        }
    }
}
