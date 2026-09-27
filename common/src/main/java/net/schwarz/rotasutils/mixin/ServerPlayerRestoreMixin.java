package net.schwarz.rotasutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.server.ZoneRuleService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies a kept inventory and experience onto the respawned player, the same way vanilla does for the
 * keepInventory game rule, when the player died in a zone that keeps inventory.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRestoreMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void rotasutils$restoreKeptInventory(ServerPlayer old, boolean keepEverything, CallbackInfo ci) {
        if (keepEverything || !ZoneRuleService.consumeKept(old.getUUID())) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.getInventory().replaceWith(old.getInventory());
        self.experienceLevel = old.experienceLevel;
        self.totalExperience = old.totalExperience;
        self.experienceProgress = old.experienceProgress;
        self.setScore(old.getScore());
    }
}
