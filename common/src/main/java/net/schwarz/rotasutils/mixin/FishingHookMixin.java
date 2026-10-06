package net.schwarz.rotasutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.server.ProductionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @ModifyArg(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 0))
    private Entity rotasutils$caught(Entity entity) {
        FishingHook self = (FishingHook) (Object) this;
        if (entity instanceof ItemEntity item && self.getPlayerOwner() instanceof ServerPlayer player) {
            ItemStack caught = net.schwarz.rotasutils.server.TradeService.starCatch(player,
                    ProductionService.thinCatch(player, item.getItem()));
            if (caught != item.getItem()) {
                item.setItem(caught);
            }
            if (!caught.isEmpty()) {
                ProductionService.produced(player, JobDef.ProductionEntry.Activity.FISH,
                        ProductionService.item(caught), caught.getCount());
            }
        }
        return entity;
    }
}
