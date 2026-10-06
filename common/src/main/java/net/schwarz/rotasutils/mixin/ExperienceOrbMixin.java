package net.schwarz.rotasutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void rotasutils$removeVanillaOrb(Player player, CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (player instanceof ServerPlayer && self.getType() == EntityType.EXPERIENCE_ORB) {
            self.discard();
            ci.cancel();
        }
    }
}
