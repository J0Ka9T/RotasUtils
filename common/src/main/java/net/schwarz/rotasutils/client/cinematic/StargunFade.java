package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public final class StargunFade {
    private StargunFade() {
    }

    private static final Pattern TEXTURE = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]*textures/[a-z0-9_./-]+[.]png");
    private static final Map<RenderType, ResourceLocation> TEXTURES = new HashMap<>();
    private static final ResourceLocation NONE = new ResourceLocation("rotasutils", "none");

    private static ResourceLocation textureOf(RenderType type) {
        ResourceLocation found = TEXTURES.get(type);
        if (found == null) {
            found = NONE;
            String text = type.toString();
            boolean body = (text.startsWith("RenderType[entity_") || text.startsWith("RenderType[armor_") || text.startsWith("RenderType[item_entity_"))
                    && !text.contains("glint") && !text.contains("shadow") && !text.contains("outline");
            if (body) {
                Matcher m = TEXTURE.matcher(text);
                if (m.find()) {
                    String s = m.group();
                    found = s.contains(":") ? new ResourceLocation(s) : new ResourceLocation("minecraft", s);
                }
            }
            TEXTURES.put(type, found);
        }
        return found == NONE ? null : found;
    }

    public static MultiBufferSource wrap(MultiBufferSource source, double progress) {
        float alpha = (float) Math.max(0.02, 1 - progress);
        return type -> {
            ResourceLocation texture = textureOf(type);
            return texture == null ? source.getBuffer(type) : new Faded(source.getBuffer(RenderType.entityTranslucent(texture)), alpha);
        };
    }

    private record Faded(VertexConsumer out, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            out.color(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            out.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            out.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            out.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            out.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            out.defaultColor(r, g, b, Math.round(a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }
}
