package net.schwarz.rotasutils.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lifts vanilla's ceiling on max health (1024) and attack damage (2048). Monster setups scale these by
 * level, and a level-999 boss was silently flattened to 1024 health however high it was set. Armor,
 * toughness and knockback resistance keep their caps: past them the combat formulas give nothing more.
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeLimitMixin {
    /** Room for any level curve, while staying well inside float precision for health. */
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
