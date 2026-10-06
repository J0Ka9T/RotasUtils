package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.schwarz.rotasutils.client.hud.MobLevelName;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityNameplateMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void rotasutils$useHeadPlate(Entity entity, Component component, PoseStack poseStack,
                                         MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (net.schwarz.rotasutils.client.cinematic.PurpleDissolve.capturing() || MobLevelName.replacedByPlate(entity, component)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component rotasutils$levelColour(Component value, Entity entity, Component component,
                                             PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        return net.schwarz.rotasutils.client.PlayerTitles.decorate(entity, MobLevelName.color(value, entity));
    }
}
