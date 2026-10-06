package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.schwarz.rotasutils.client.cinematic.Stargun;
import net.schwarz.rotasutils.client.cinematic.StargunFade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityStargunMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rotasutils$gone(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (Stargun.dissolveProgress(entity, partialTick) >= 0.985) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource rotasutils$fade(MultiBufferSource buffers, Entity entity, double x, double y, double z, float yaw, float partialTick) {
        double progress = Stargun.dissolveProgress(entity, partialTick);
        return progress <= 0.001 ? buffers : StargunFade.wrap(buffers, progress);
    }
}
