package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.Rotasutils;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
final class SkyGlowBuffer {
    private static final ResourceLocation WHITE = Rotasutils.id("textures/environment/white.png");
    private static boolean entityPath;

    private SkyGlowBuffer() {
    }

    static BufferBuilder begin() {
        entityPath = ShaderPackCompat.active();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        if (entityPath) {
            RenderSystem.setShader(GameRenderer::getRendertypeEyesShader);
            RenderSystem.setShaderTexture(0, WHITE);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }
        return buffer;
    }

    static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                       float r, float g, float b, float alpha) {
        float a = EldritchSkyCelestial.clamp01(alpha);
        if (entityPath) {
            buffer.vertex(matrix, x, y, z).color(r * a, g * a, b * a, 1f).uv(0.5f, 0.5f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(0f, -1f, 0f).endVertex();
        } else {
            buffer.vertex(matrix, x, y, z).color(r, g, b, a).endVertex();
        }
    }
}
