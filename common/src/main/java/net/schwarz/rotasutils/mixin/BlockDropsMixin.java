package net.schwarz.rotasutils.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.server.ProductionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mining and harvesting stay open to everyone, but a block in a sub job's gathering table drops mostly nothing for a
 * player without that role. This is the drop list both loaders build for a player's break before spawning items.
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void rotasutils$thinGathering(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity,
                                                 Entity entity, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (entity instanceof ServerPlayer player) {
            List<ItemStack> thinned = ProductionService.thinBlockDrops(player, state, cir.getReturnValue());
            if (thinned != null) {
                cir.setReturnValue(thinned);
            }
        }
    }
}
