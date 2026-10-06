package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

@Environment(EnvType.CLIENT)
public final class VfxRenderTypes extends RenderType {
    public static final RenderType ADDITIVE = create("rotasutils_vfx_additive", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    public static final RenderType TRANSLUCENT = create("rotasutils_vfx_translucent", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private static final Function<ResourceLocation, RenderType> GLOW_TEXTURED = Util.memoize(texture ->
            create("rotasutils_vfx_textured", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1 << 16,
                    false, true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setOverlayState(OVERLAY)
                            .setOutputState(WEATHER_TARGET)
                            .createCompositeState(false)));

    public static RenderType glowTextured(ResourceLocation texture) {
        return GLOW_TEXTURED.apply(texture);
    }

    private static final Function<ResourceLocation, RenderType> LIGHT_TEXTURED = Util.memoize(texture ->
            create("rotasutils_vfx_light_textured", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                    1 << 16, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(texture, true, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setOutputState(WEATHER_TARGET)
                            .createCompositeState(false)));

    public static RenderType lightTextured(ResourceLocation texture) {
        return LIGHT_TEXTURED.apply(texture);
    }

    private static final Function<ResourceLocation, RenderType> SPRITE = Util.memoize(texture ->
            create("rotasutils_vfx_sprite", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                    1 << 16, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setOutputState(WEATHER_TARGET)
                            .createCompositeState(false)));

    public static RenderType sprite(ResourceLocation texture) {
        return SPRITE.apply(texture);
    }

    private VfxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                           boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
