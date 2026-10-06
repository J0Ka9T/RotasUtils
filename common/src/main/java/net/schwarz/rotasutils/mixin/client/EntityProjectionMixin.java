package net.schwarz.rotasutils.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.cinematic.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityProjectionMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rotasutils$nowhere(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack pose,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (Projection.hidden(entity, partialTick)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double rotasutils$stagedX(double x, Entity entity, double x0, double y0, double z0, float yaw, float partialTick) {
        Vec3 offset = Projection.renderOffset(entity, partialTick);
        return offset == null ? x : x + offset.x;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double rotasutils$stagedY(double y, Entity entity, double x0, double y0, double z0, float yaw, float partialTick) {
        Vec3 offset = Projection.renderOffset(entity, partialTick);
        return offset == null ? y : y + offset.y;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double rotasutils$stagedZ(double z, Entity entity, double x0, double y0, double z0, float yaw, float partialTick) {
        Vec3 offset = Projection.renderOffset(entity, partialTick);
        return offset == null ? z : z + offset.z;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float rotasutils$frozenFrame(float partialTick, Entity entity) {
        return Projection.partialTick(entity, partialTick);
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void rotasutils$staged(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (Projection.staged(entity)) {
            cir.setReturnValue(true);
        }
    }
}
