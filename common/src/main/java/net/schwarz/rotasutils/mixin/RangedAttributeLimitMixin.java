package net.schwarz.rotasutils.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedAttribute.class)
public abstract class RangedAttributeLimitMixin {
    private static final double RAISED_MAX = 1_000_000_000d;

    @Shadow
    @Final
    @Mutable
    private double maxValue;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void rotasutils$raiseLimit(String descriptionId, double defaultValue, double min, double max,
                                       CallbackInfo ci) {
        if ("attribute.name.generic.max_health".equals(descriptionId)
                || "attribute.name.generic.attack_damage".equals(descriptionId)) {
            maxValue = Math.max(maxValue, RAISED_MAX);
        }
    }
}
