package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.cinematic.CastPoseApplier;
import net.schwarz.rotasutils.client.cinematic.HandCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a cast plays, the caster's held item is not drawn (the hand is the ability's own) - but the layer is
 * where the game has the arm's matrix ready, so it is recorded here first: the Red core is anchored to the
 * hand the model really drew.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandCastMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void rotasutils$captureAndHide(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                           PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!CastPoseApplier.hidesHeldItem(entity)) {
            return;
        }
        Object model = ((RenderLayer<?, ?>) (Object) this).getParentModel();
        if (model instanceof ArmedModel armed) {
            pose.pushPose();
            armed.translateToHand(arm, pose);
            HandCapture.record(entity.getId(), pose.last().pose());
            pose.popPose();
        }
        ci.cancel();
    }
}
