package net.schwarz.rotasutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.schwarz.rotasutils.server.ZonePresenceService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void rotasutils$zonePearl(HitResult result, CallbackInfo ci) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        if (self.level().isClientSide || !(self.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        if (ZonePresenceService.pearlBlocked(owner, self)) {
            self.discard();
            ci.cancel();
        }
    }
}
