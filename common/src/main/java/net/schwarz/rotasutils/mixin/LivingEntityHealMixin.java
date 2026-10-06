package net.schwarz.rotasutils.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.server.ZoneRuleService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float rotasutils$zoneHealing(float amount) {
        return ZoneRuleService.scaleHealing((LivingEntity) (Object) this, amount);
    }
}
