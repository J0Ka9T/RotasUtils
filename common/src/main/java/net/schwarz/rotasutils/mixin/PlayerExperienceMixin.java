package net.schwarz.rotasutils.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerExperienceMixin {
    @Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void rotasutils$blockVanillaExperiencePoints(int amount, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "giveExperienceLevels", at = @At("HEAD"), cancellable = true)
    private void rotasutils$blockVanillaExperienceLevels(int levels, CallbackInfo ci) {
        ci.cancel();
    }
}
