package net.schwarz.rotasutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.schwarz.rotasutils.server.ZoneRuleService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keep inventory for players who die in a zone that keeps it. Vanilla calls {@code dropEquipment} before
 * {@code dropExperience} while dropping death loot, so the inventory decision is made first and the
 * experience orbs follow it. {@link ServerPlayerRestoreMixin} copies both onto the respawned player.
 */
@Mixin(Player.class)
public abstract class PlayerKeepInventoryMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void rotasutils$keepZoneInventory(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && ZoneRuleService.keepInventoryAtDeath(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "getExperienceReward", at = @At("HEAD"), cancellable = true)
    private void rotasutils$keepZoneExperience(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ServerPlayer player && ZoneRuleService.kept(player.getUUID())) {
            cir.setReturnValue(0);
        }
    }
}
