package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.client.cinematic.Projection;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityTumbleMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("HEAD"))
    private void rotasutils$tumble(LivingEntity entity, PoseStack pose, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci) {
        Quaternionf turn = Projection.tumble(entity, partialTicks);
        if (turn != null) {
            float middle = entity.getBbHeight() / 2f;
            pose.translate(0f, middle, 0f);
            pose.mulPose(turn);
            pose.translate(0f, -middle, 0f);
        }
    }
}
