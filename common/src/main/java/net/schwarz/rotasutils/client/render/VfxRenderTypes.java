package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Render types for layered light effects. Vanilla's {@code lightning()} writes depth and culls
 * back faces, so overlapping glow quads hide each other: layers cut off with hard edges, coplanar
 * halos z-fight into jagged patterns, and a tube's far side vanishes. These types depth-test
 * against the world but never write depth and never cull, so every layer simply adds up.
 */
@Environment(EnvType.CLIENT)
public final class VfxRenderTypes extends RenderType {
    /** Additive light (alpha scales brightness): glows, cores, rings, sparkles, lightning. */
    public static final RenderType ADDITIVE = create("rotasutils_vfx_additive", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    /** Ordinary alpha blending, coloured, no depth write or culling: solid-looking gems that read against a bright sky. */
    public static final RenderType TRANSLUCENT = create("rotasutils_vfx_translucent", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 16, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    /** Alpha-blended, fullbright, textured, no depth write: plasma that can hold dark streaks. */
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

    /**
     * Additive light from a painted texture (rgb = colour, alpha = intensity), smoothly filtered, with no
     * alpha cut-off, so soft flares and seals keep their faint fringes. Vertex format POSITION_TEX_COLOR.
     */
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

    private VfxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                           boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
