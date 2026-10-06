package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.RiftWandererEntity;

@Environment(EnvType.CLIENT)
public class RiftWandererRenderer extends LivingEntityRenderer<RiftWandererEntity, PlayerModel<RiftWandererEntity>> {
    private static final ResourceLocation SKIN = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_wanderer.png");
    private static final ResourceLocation EYES = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_wanderer_eyes.png");

    public RiftWandererRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new Arrival(this));
        addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(RiftWandererEntity entity) {
        return SKIN;
    }

    @Override
    protected void scale(RiftWandererEntity entity, PoseStack pose, float partialTick) {
        pose.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    protected RenderType getRenderType(RiftWandererEntity entity, boolean visible, boolean translucent, boolean glowing) {
        if (entity.emergence(0f) < 1f && entity.arriving()) {
            return null;
        }
        return super.getRenderType(entity, visible, translucent, glowing);
    }

    @Override
    protected boolean shouldShowName(RiftWandererEntity entity) {
        return entity.hasCustomName() && super.shouldShowName(entity);
    }

    private static final class Arrival extends RenderLayer<RiftWandererEntity, PlayerModel<RiftWandererEntity>> {
        Arrival(RenderLayerParent<RiftWandererEntity, PlayerModel<RiftWandererEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RiftWandererEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!entity.arriving()) {
                return;
            }
            float e = entity.emergence(partialTick);
            if (e <= 0.01f) {
                return;
            }
            float r = 0.55f + 0.45f * e;
            float g = 0.35f + 0.65f * e;
            float b = 1f;
            int lit = e < 0.7f ? LightTexture.FULL_BRIGHT : light;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SKIN));
            getParentModel().renderToBuffer(pose, vc, lit, LivingEntityRenderer.getOverlayCoords(entity, 0f),
                    r, g, b, e * e);
        }
    }

    private static final class Eyes extends RenderLayer<RiftWandererEntity, PlayerModel<RiftWandererEntity>> {
        Eyes(RenderLayerParent<RiftWandererEntity, PlayerModel<RiftWandererEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, RiftWandererEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            float e = entity.emergence(partialTick);
            if (e <= 0.05f) {
                return;
            }
            float pulse = 0.85f + 0.15f * (float) Math.sin(ageInTicks * 0.15f);
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(EYES));
            getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, e * pulse, e * pulse, e * pulse, 1f);
        }
    }
}
