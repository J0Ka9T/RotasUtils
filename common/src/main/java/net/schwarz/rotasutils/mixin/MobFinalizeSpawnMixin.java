package net.schwarz.rotasutils.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.schwarz.rotasutils.server.SpawnReasons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobFinalizeSpawnMixin {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void rotasutils$noteSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                      SpawnGroupData data, CompoundTag tag, CallbackInfoReturnable<SpawnGroupData> cir) {
        SpawnReasons.note((Mob) (Object) this, type);
    }
}
