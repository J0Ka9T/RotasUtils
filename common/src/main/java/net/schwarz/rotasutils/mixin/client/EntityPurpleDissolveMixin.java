package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.schwarz.rotasutils.client.cinematic.PurpleDissolve;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityPurpleDissolveMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rotasutils$replaceErasedCorpse(Entity entity, double x, double y, double z, float yaw,
                                               float partialTick, PoseStack pose, MultiBufferSource buffers,
                                               int light, CallbackInfo ci) {
        if (PurpleDissolve.hides(entity)) {
            ci.cancel();
        }
    }
}
