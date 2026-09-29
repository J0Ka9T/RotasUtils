package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.cinematic.CastPoseApplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The caster's held item is not drawn while the sequence plays: the hand is the ability's own. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandCastMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void rotasutils$hideHeld(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                     PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (CastPoseApplier.hidesHeldItem(entity)) {
            ci.cancel();
        }
    }
}
