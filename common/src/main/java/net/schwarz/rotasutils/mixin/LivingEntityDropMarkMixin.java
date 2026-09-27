package net.schwarz.rotasutils.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.schwarz.rotasutils.server.DropRollContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/**
 * Marks the window in which a mob is spilling its death drops.
 *
 * <p>{@code dropFromLootTable} hands every rolled stack to {@code spawnAtLocation}, and that method is
 * used for a dozen other things as well. The mark is what lets {@link EntityDropFilterMixin} tell a
 * death drop from an ordinary one without guessing.</p>
 *
 * <p>Drops happen on the server thread, but the mark is per thread anyway: an integrated server and its
 * client share a process, and a modded thread that spilled drops of its own must not see this one.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDropMarkMixin {
    @Redirect(
            method = "dropFromLootTable",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"))
    private void rotasutils$markDeathDrops(LootTable lootTable, LootParams params, long seed,
                                            Consumer<ItemStack> output) {
        DropRollContext.begin((LivingEntity) (Object) this);
        try {
            lootTable.getRandomItems(params, seed, output);
        } finally {
            DropRollContext.end();
        }
    }
}
