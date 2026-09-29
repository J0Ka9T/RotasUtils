package net.schwarz.rotasutils.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.client.cinematic.CastPoseApplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lays the Red Reversal pose over a humanoid model once the game has posed it. Runs inside the base
 * model's animation so a player model's sleeves, jacket and trousers copy the finished pose afterwards.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelCastMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void rotasutils$castPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                     float netHeadYaw, float headPitch, CallbackInfo ci) {
        // ageInTicks is tickCount + partialTick, so the partial tick is what is left over.
        CastPoseApplier.apply((HumanoidModel<?>) (Object) this, entity, ageInTicks - entity.tickCount);
    }
}
