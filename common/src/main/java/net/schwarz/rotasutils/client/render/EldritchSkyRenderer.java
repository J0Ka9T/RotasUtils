package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class EldritchSkyRenderer {
    private static final float[] A = new float[3];
    private static final float[] B = new float[3];
    private static final float[] C = new float[3];
    private static final float[] D = new float[3];
    static final boolean LEGACY_RIFT_LAYERS = false;
    private static long cachedSeed = Long.MIN_VALUE;
    private static EldritchSkyGeometry cachedGeometry;
    private static long fourSkiesSeed = Long.MIN_VALUE;
    private static final EldritchSkyGeometry[] FOUR_SKIES = new EldritchSkyGeometry[4];
    private static final float[] FOCAL_DIRECTION = new float[3];
    private static final float[] FOUR_YAWS = {180f, 270f, 0f, 90f};
    private static final int[] FOUR_PALETTES = {
            net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_BLUE,
            net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_GOLD,
            net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_RED,
            net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_VOID
    };

    private EldritchSkyRenderer() { }

    public static void render(PoseStack pose, float partialTick, boolean isFoggy, boolean blockedByFluid) {
        EldritchSkyEnvironment env = EldritchSkyClientState.environment(partialTick);
        EldritchSkyPalette.beginFrame();
        var snapshot = EldritchSkyClientState.current();
        if (!ShaderPackCompat.overlay()) EldritchSkyCinema.tickSounds(env);
        if (isFoggy || blockedByFluid) return;
        if (!env.active() || env.openness() <= 0f) return;
        if (ShaderPackCompat.active()) {
            SkyShaderOverlay.defer(pose, partialTick);
            return;
        }
        boolean fourSkies = snapshot != null && snapshot.variant
                == net.schwarz.rotasutils.sky.EldritchSkyTransition.VARIANT_FOUR_SKIES;
        EldritchSkyGeometry geometry = fourSkies ? fourSkiesGeometry(env.seed(), 0) : geometry(env.seed());
        float originalYaw = env.focalYawDeg();
        float originalElevation = env.focalElevationDeg();
        float originalApertureScale = env.apertureScale();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        boolean overlay = ShaderPackCompat.overlay();
        RenderSystem.depthMask(false);
        if (overlay) {
            pose.pushPose();
            float blocks = net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
            float stretch = Math.max(1f, blocks * 1.6f / 100f);
            pose.scale(stretch, stretch, stretch);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        }
        try {
            EldritchSkyAtmosphereRenderer.render(pose, geometry, env, fourSkies);
            if (fourSkies) {
                env.apertureScale(originalApertureScale * 0.70f);
                EldritchSkyMajestyRenderer.render(pose.last().pose(), env, true);
                for (int i = 0; i < FOUR_SKIES.length; i++) {
                    EldritchSkyGeometry focal = fourSkiesGeometry(env.seed(), i);
                    if (!visible(focal)) {
                        continue;
                    }
                    env.focalDirection(focal.focalYawDeg(), focal.focalElevationDeg());
                    EldritchSkyPalette.usePalette(FOUR_PALETTES[i]);
                    EldritchRiftRenderer.render(pose.last().pose(), focal, env, false);
                }
                FourSkiesCrownRenderer.render(pose.last().pose(), env);
                for (int i = 0; i < FOUR_SKIES.length; i++) {
                    EldritchSkyGeometry focal = fourSkiesGeometry(env.seed(), i);
                    if (!visible(focal)) {
                        continue;
                    }
                    env.focalDirection(focal.focalYawDeg(), focal.focalElevationDeg());
                    EldritchSkyPalette.usePalette(FOUR_PALETTES[i]);
                    if (i == 3) {
                        EldritchSkyTentacleRenderer.render(pose, env);
                    } else {
                        EldritchSkyHeraldRenderer.render(pose, env, FOUR_PALETTES[i], 0.70f, i * 2.3f);
                    }
                }
                return;
            }
            boolean herald = snapshot != null
                    && net.schwarz.rotasutils.sky.EldritchSkyTransition.herald(snapshot.variant);
            boolean reaching = snapshot != null
                    && net.schwarz.rotasutils.sky.EldritchSkyTransition.tentacles(snapshot.variant);
            if (!herald && !reaching) {
                EldritchSkyPresenceRenderer.render(pose, geometry, env);
            }
            EldritchSkyMajestyRenderer.render(pose.last().pose(), env);
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            if (!LEGACY_RIFT_LAYERS) {
                EldritchRiftRenderer.render(pose.last().pose(), geometry, env);
                if (herald) {
                    EldritchSkyHeraldRenderer.render(pose, env,
                            net.schwarz.rotasutils.sky.EldritchSkyTransition.palette(snapshot.variant));
                }
                if (snapshot != null && net.schwarz.rotasutils.sky.EldritchSkyTransition.tentacles(snapshot.variant)) {
                    EldritchSkyTentacleRenderer.render(pose, env);
                }
                return;
            }
            int frontCrown = Math.max(0, geometry.crowns().length - 1);
            drawCrowns(pose.last().pose(), geometry, env, 0, frontCrown);
            drawNebulaPull(pose.last().pose(), geometry, env);
            EldritchSkyAtmosphereRenderer.renderShockwaves(pose, geometry, env);
            EldritchSkyAtmosphereRenderer.renderFractures(pose, geometry, env);
            drawCorona(pose.last().pose(), geometry, env);
            drawVoidBackdrop(pose.last().pose(), geometry, env);
            drawVortexBands(pose.last().pose(), geometry, env);
            drawDeepVoid(pose.last().pose(), geometry, env);
            drawTornEdges(pose.last().pose(), geometry, env);
            drawCrowns(pose.last().pose(), geometry, env, frontCrown, geometry.crowns().length);
            drawRimPlates(pose.last().pose(), geometry, env);
            drawDebris(pose.last().pose(), geometry, env);
        } finally {
            if (overlay) {
                pose.popPose();
            }
            env.focalDirection(originalYaw, originalElevation);
            env.apertureScale(originalApertureScale);
            EldritchSkyPalette.beginFrame();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    static EldritchSkyGeometry geometry(long seed) {
        if (cachedGeometry == null || cachedSeed != seed) {
            cachedSeed = seed;
            cachedGeometry = EldritchSkyGeometry.create(seed);
        }
        return cachedGeometry;
    }

    private static EldritchSkyGeometry fourSkiesGeometry(long seed, int index) {
        if (fourSkiesSeed != seed) {
            fourSkiesSeed = seed;
            for (int i = 0; i < FOUR_SKIES.length; i++) {
                FOUR_SKIES[i] = EldritchSkyGeometry.create(seed + i * 0x4F1BBCDCL, FOUR_YAWS[i], 34f);
            }
        }
        return FOUR_SKIES[index];
    }

    private static boolean visible(EldritchSkyGeometry geometry) {
        EldritchSkyCelestial.direction(geometry.focalYawDeg(), geometry.focalElevationDeg(), FOCAL_DIRECTION);
        var look = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        return look.x() * FOCAL_DIRECTION[0] + look.y() * FOCAL_DIRECTION[1]
                + look.z() * FOCAL_DIRECTION[2] > -0.62f;
    }

    private static void drawCrowns(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env,
                                   int firstCrown, int endCrown) {
        float reveal = env.revelation() * env.retreat();
        if (reveal <= 0.003f || firstCrown >= endCrown) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        EldritchSkyGeometry.Crown[] crowns = geometry.crowns();
        for (int crownIndex = firstCrown; crownIndex < endCrown; crownIndex++) {
            EldritchSkyGeometry.Crown crown = crowns[crownIndex];
            float rotation = crown.phaseDeg() + crown.direction() * env.seconds() * 360f / crown.periodSeconds();
            float surge = 1f + env.haloPulse() * (0.025f + crownIndex * 0.008f);
            float thickness = 0.8f + crownIndex * 0.34f + env.haloPulse() * 0.75f;
            for (int segment = 0; segment < EldritchSkyArt.RING_SEGMENTS; segment++) {
                if ((crown.segmentMask() & (1L << segment)) == 0L) continue;
                float angle0 = (float) (Math.PI * 2.0 * segment / EldritchSkyArt.RING_SEGMENTS
                        + Math.toRadians(rotation));
                float angle1 = (float) (Math.PI * 2.0 * (segment + 0.78f) / EldritchSkyArt.RING_SEGMENTS
                        + Math.toRadians(rotation));
                crownPoint(geometry, crown, angle0, -thickness, surge, A);
                crownPoint(geometry, crown, angle0, thickness, surge, B);
                crownPoint(geometry, crown, angle1, thickness, surge, C);
                crownPoint(geometry, crown, angle1, -thickness, surge, D);
                float hot = env.haloPulse() * (((segment + crownIndex * 7) % 13) == 0 ? 1f : 0f);
                quad(buffer, matrix, A, B, C, D,
                        EldritchSkyCelestial.lerp(EldritchSkyArt.RIM_RED * 0.46f, EldritchSkyArt.HOT_RED, hot),
                        EldritchSkyCelestial.lerp(EldritchSkyArt.RIM_GREEN * 0.42f, EldritchSkyArt.HOT_GREEN, hot),
                        EldritchSkyCelestial.lerp(EldritchSkyArt.RIM_BLUE * 0.56f, EldritchSkyArt.HOT_BLUE, hot),
                        reveal * crown.alpha() * (0.82f + 0.36f * hot),
                        EldritchSkyArt.CROWN_RADIUS - crown.depthOffset());
            }
            drawCrownRunes(buffer, matrix, geometry, crown, crownIndex, rotation, reveal, env.haloPulse());
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void crownPoint(EldritchSkyGeometry geometry, EldritchSkyGeometry.Crown crown,
                                   float angle, float radialOffset, float surge, float[] out) {
        float horizontal = (crown.radiusDeg() + radialOffset) * surge;
        float vertical = horizontal * crown.aspect();
        EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                horizontal, vertical, angle, crown.axisDeg(), out);
    }

    private static void drawCrownRunes(BufferBuilder buffer, Matrix4f matrix, EldritchSkyGeometry geometry,
                                       EldritchSkyGeometry.Crown crown, int crownIndex, float rotation,
                                       float reveal, float surge) {
        for (int rune = 0; rune < crown.runeCount(); rune++) {
            float angle = (float) (Math.PI * 2.0 * (rune + 0.37f * crownIndex) / crown.runeCount()
                    + Math.toRadians(rotation));
            float halfAngle = 0.018f + rune % 3 * 0.006f;
            float inner = crown.radiusDeg() - 1.5f;
            float outer = crown.radiusDeg() + 3.4f + (rune % 2) * 2.3f;
            EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                    inner, inner * crown.aspect(), angle - halfAngle, crown.axisDeg(), A);
            EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                    inner, inner * crown.aspect(), angle + halfAngle, crown.axisDeg(), B);
            EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                    outer, outer * crown.aspect(), angle + halfAngle, crown.axisDeg(), C);
            EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                    outer, outer * crown.aspect(), angle - halfAngle, crown.axisDeg(), D);
            quad(buffer, matrix, A, B, C, D, EldritchSkyArt.RIM_RED * 0.72f,
                    EldritchSkyArt.RIM_GREEN * 0.68f, EldritchSkyArt.RIM_BLUE * 0.76f,
                    reveal * crown.alpha() * (0.34f + surge * 0.38f),
                    EldritchSkyArt.CROWN_RADIUS - crown.depthOffset() - 0.05f);
        }
    }

    private static void drawCorona(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        float open = env.apertureOpen();
        if (open <= 0.01f) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float scale = env.apertureScale() * env.breath();
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < geometry.segments(); i++) {
                if (geometry.gap(i)) continue;
                float turbulence = 5.2f + 2.9f * (0.5f + 0.5f
                        * (float) Math.sin(geometry.flickerPhase(i) + i * 0.73f));
                edgePoint(geometry, i, side, scale, 0f, A);
                edgePoint(geometry, i, side, scale, turbulence, B);
                edgePoint(geometry, i + 1, side, scale, turbulence * 0.82f, C);
                edgePoint(geometry, i + 1, side, scale, 0f, D);
                float fade = Math.min(tipFade(geometry, i), tipFade(geometry, i + 1));
                float violet = 0.18f + 0.34f * (0.5f + 0.5f
                        * (float) Math.sin(geometry.flickerPhase(i) + side));
                gradientQuad(buffer, matrix, A, B, C, D,
                        EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_RED, EldritchSkyArt.VIOLET_RED, violet),
                        EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_GREEN, EldritchSkyArt.VIOLET_GREEN, violet),
                        EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_BLUE, EldritchSkyArt.VIOLET_BLUE, violet),
                        open * env.edgeGlow() * fade * 0.20f, EldritchSkyArt.CORONA_RADIUS);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawDeepVoid(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        float open = env.apertureOpen();
        if (open <= 0.005f) return;
        float scale = env.apertureScale() * env.breath();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        apertureInterior(buffer, matrix, geometry, scale * 0.42f, scale * 0.60f, 0f,
                EldritchSkyArt.VOID_DEEP_RADIUS, EldritchSkyArt.DEEP_RED, EldritchSkyArt.DEEP_GREEN,
                EldritchSkyArt.DEEP_BLUE, open * (0.985f + env.voidDepth() * 0.014f));
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawVoidBackdrop(Matrix4f matrix, EldritchSkyGeometry geometry,
                                         EldritchSkyEnvironment env) {
        float open = env.apertureOpen();
        if (open <= 0.005f) return;
        float scale = env.apertureScale() * env.breath();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        apertureInterior(buffer, matrix, geometry, scale, scale, 0f,
                EldritchSkyArt.VOID_RADIUS, EldritchSkyArt.VOID_RED, EldritchSkyArt.VOID_GREEN,
                EldritchSkyArt.VOID_BLUE, open * 0.96f);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawVortexBands(Matrix4f matrix, EldritchSkyGeometry geometry,
                                        EldritchSkyEnvironment env) {
        float open = env.apertureOpen() * env.retreat();
        if (open <= 0.005f) return;
        float scale = env.apertureScale() * env.breath();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        EldritchSkyGeometry.FlowBand[] bands = geometry.flowBands();
        for (int bandIndex = 0; bandIndex < bands.length; bandIndex++) {
            EldritchSkyGeometry.FlowBand band = bands[bandIndex];
            float rotation = band.phaseDeg() + band.direction() * env.seconds() * 360f / band.periodSeconds();
            float cycle = positiveFraction(band.phaseDeg() / 360f
                    + band.direction() * env.seconds() / band.periodSeconds());
            float inward = 0.5f - 0.5f * (float) Math.cos(cycle * Math.PI * 2.0);
            for (int segment = 0; segment < EldritchSkyArt.RING_SEGMENTS; segment++) {
                if ((band.segmentMask() & (1L << segment)) == 0L) continue;
                float angle0 = (float) (Math.PI * 2.0 * segment / EldritchSkyArt.RING_SEGMENTS
                        + Math.toRadians(rotation));
                float angle1 = (float) (Math.PI * 2.0 * (segment + 0.72f) / EldritchSkyArt.RING_SEGMENTS
                        + Math.toRadians(rotation));
                vortexPoint(geometry, band, angle0, -band.thicknessDeg(), scale, inward, A);
                vortexPoint(geometry, band, angle0, band.thicknessDeg(), scale, inward, B);
                vortexPoint(geometry, band, angle1, band.thicknessDeg(), scale, inward, C);
                vortexPoint(geometry, band, angle1, -band.thicknessDeg(), scale, inward, D);
                float hot = EldritchSkyPulseClock.travelingPulse(segment, EldritchSkyArt.RING_SEGMENTS,
                        env.seconds(), 6.5f + bandIndex * 0.9f,
                        band.phaseDeg() / 360f, band.direction());
                float ice = hot * hot * hot;
                float violet = 0.18f + 0.42f * ((bandIndex & 1) == 0 ? 0f : 1f);
                float red = EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_RED,
                        EldritchSkyArt.VIOLET_RED, violet);
                float green = EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_GREEN,
                        EldritchSkyArt.VIOLET_GREEN, violet);
                float blue = EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_BLUE,
                        EldritchSkyArt.VIOLET_BLUE, violet);
                red = EldritchSkyCelestial.lerp(red, EldritchSkyArt.ELECTRIC_CYAN_RED, hot * 0.72f);
                green = EldritchSkyCelestial.lerp(green, EldritchSkyArt.ELECTRIC_CYAN_GREEN, hot * 0.72f);
                blue = EldritchSkyCelestial.lerp(blue, EldritchSkyArt.ELECTRIC_CYAN_BLUE, hot * 0.72f);
                red = EldritchSkyCelestial.lerp(red, EldritchSkyArt.ICE_BLUE_RED, ice * 0.74f);
                green = EldritchSkyCelestial.lerp(green, EldritchSkyArt.ICE_BLUE_GREEN, ice * 0.74f);
                blue = EldritchSkyCelestial.lerp(blue, EldritchSkyArt.ICE_BLUE_BLUE, ice * 0.74f);
                quad(buffer, matrix, A, B, C, D, red, green, blue,
                        open * band.alpha() * (0.74f + hot * 0.44f),
                        EldritchSkyArt.EDGE_RADIUS - 0.18f - band.depthOffset());
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void vortexPoint(EldritchSkyGeometry geometry, EldritchSkyGeometry.FlowBand band,
                                    float angle, float radialOffset, float scale, float cycleInward,
                                    float[] out) {
        float curl = 0.5f + 0.5f * (float) Math.sin(angle * 2.0f + band.curlPhase());
        float inward = band.inwardDriftDeg() * (0.28f * curl + 0.72f * cycleInward);
        float horizontal = (band.horizontalRadiusDeg() - inward + radialOffset) * scale;
        float vertical = (band.verticalRadiusDeg() - inward * 1.35f
                + radialOffset * 1.6f) * scale;
        EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                horizontal, vertical, angle, band.axisDeg(), out);
    }

    private static void drawNebulaPull(Matrix4f matrix, EldritchSkyGeometry geometry,
                                       EldritchSkyEnvironment env) {
        float visibility = Math.max(env.contamination(), env.revelation() * 0.78f) * env.retreat();
        if (visibility <= 0.004f) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        EldritchSkyGeometry.NebulaFilament[] filaments = geometry.nebulaFilaments();
        int segments = 28;
        for (int filamentIndex = 0; filamentIndex < filaments.length; filamentIndex++) {
            EldritchSkyGeometry.NebulaFilament filament = filaments[filamentIndex];
            float rotation = filament.phaseDeg() + filament.direction() * env.seconds() * 360f
                    / filament.periodSeconds();
            for (int segment = 0; segment < segments; segment++) {
                if ((segment + filamentIndex * 3) % 8 <= 1) continue;
                float t0 = segment / (float) segments;
                float t1 = (segment + 0.78f) / segments;
                nebulaPoint(geometry, filament, rotation, t0, -1f, A);
                nebulaPoint(geometry, filament, rotation, t0, 1f, B);
                nebulaPoint(geometry, filament, rotation, t1, 1f, C);
                nebulaPoint(geometry, filament, rotation, t1, -1f, D);
                float fade = Math.min(arcFade(t0), arcFade(t1));
                float gradient = EldritchSkyCelestial.clamp01(filament.colorPhase() * 0.72f + t0 * 0.28f);
                float cyan = Math.max(0f, 1f - Math.abs(gradient - 0.52f) * 4f) * 0.42f;
                float red = EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_RED,
                        EldritchSkyArt.VIOLET_RED, gradient * 0.58f);
                float green = EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_GREEN,
                        EldritchSkyArt.VIOLET_GREEN, gradient * 0.58f);
                float blue = EldritchSkyCelestial.lerp(EldritchSkyArt.ROYAL_BLUE_BLUE,
                        EldritchSkyArt.VIOLET_BLUE, gradient * 0.58f);
                red = EldritchSkyCelestial.lerp(red, EldritchSkyArt.ELECTRIC_CYAN_RED, cyan);
                green = EldritchSkyCelestial.lerp(green, EldritchSkyArt.ELECTRIC_CYAN_GREEN, cyan);
                blue = EldritchSkyCelestial.lerp(blue, EldritchSkyArt.ELECTRIC_CYAN_BLUE, cyan);
                quad(buffer, matrix, A, B, C, D, red, green, blue,
                        visibility * filament.alpha() * fade,
                        EldritchSkyArt.CORONA_RADIUS + 0.42f - filament.depthOffset());
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void nebulaPoint(EldritchSkyGeometry geometry,
                                    EldritchSkyGeometry.NebulaFilament filament,
                                    float rotationDeg, float progress, float edge, float[] out) {
        float angleDeg = rotationDeg + filament.direction() * (progress - 0.5f) * filament.arcDeg();
        float angle = (float) Math.toRadians(angleDeg);
        float radius = EldritchSkyCelestial.lerp(filament.outerRadiusDeg(), filament.innerRadiusDeg(), progress)
                + (float) Math.sin(progress * Math.PI * 3.0 + Math.toRadians(filament.phaseDeg()))
                * filament.curlDeg();
        radius += edge * filament.thicknessDeg() * 0.5f;
        EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                (float) Math.cos(angle) * radius,
                (float) Math.sin(angle) * radius * 0.78f,
                filament.axisDeg(), out);
    }

    private static void apertureInterior(BufferBuilder buffer, Matrix4f matrix, EldritchSkyGeometry geometry,
                                         float horizontalScale, float verticalScale, float rotationDeg,
                                         float radius, float red, float green, float blue, float alpha) {
        for (int i = 0; i < geometry.segments(); i++) {
            aperturePoint(geometry, i, -1f, horizontalScale, verticalScale, rotationDeg, A);
            aperturePoint(geometry, i, 1f, horizontalScale, verticalScale, rotationDeg, B);
            aperturePoint(geometry, i + 1, 1f, horizontalScale, verticalScale, rotationDeg, C);
            aperturePoint(geometry, i + 1, -1f, horizontalScale, verticalScale, rotationDeg, D);
            float fade = Math.min(tipFade(geometry, i), tipFade(geometry, i + 1));
            quad(buffer, matrix, A, B, C, D, red, green, blue, alpha * (0.72f + 0.28f * fade), radius);
        }
    }

    private static void aperturePoint(EldritchSkyGeometry geometry, int index, float side,
                                      float horizontalScale, float verticalScale, float rotationDeg,
                                      float[] out) {
        float vertical = geometry.verticalDeg(index) * verticalScale;
        float width = geometry.halfWidthDeg(geometry.verticalDeg(index) / EldritchSkyGeometry.APERTURE_HALF_HEIGHT_DEG,
                side < 0f) * horizontalScale * side * sharpTipFactor(geometry, index);
        EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                width, vertical, rotationDeg, out);
    }

    private static void drawTornEdges(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        float open = env.apertureOpen();
        if (open <= 0.01f) return;
        float scale = env.apertureScale() * env.breath();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < geometry.segments(); i++) {
                if (geometry.gap(i)) continue;
                edgePoint(geometry, i, side, scale, 0f, A);
                edgePoint(geometry, i, side, scale, 1.25f, B);
                edgePoint(geometry, i + 1, side, scale, 1.25f, C);
                edgePoint(geometry, i + 1, side, scale, 0f, D);
                boolean selected = ((i * 3 + side * 5 + (int) geometry.seed()) & 7) <= 1;
                float phase = geometry.flickerPhase(i) / (float) (Math.PI * 2.0);
                float cyanPulse = selected ? EldritchSkyPulseClock.travelingPulse(i, geometry.segments(),
                        env.seconds(), 7.5f, phase, side) : 0f;
                float icePulse = selected ? EldritchSkyPulseClock.travelingPulse(i, geometry.segments(),
                        env.seconds(), 7.5f, phase - side * 0.040f, side) : 0f;
                float violetTrail = selected ? EldritchSkyPulseClock.travelingPulse(i, geometry.segments(),
                        env.seconds(), 7.5f, phase - side * 0.095f, side) : 0f;
                float iceCore = icePulse * icePulse;
                float red = EldritchSkyCelestial.lerp(EldritchSkyArt.INDIGO_RED,
                        EldritchSkyArt.VIOLET_RED, violetTrail * 0.78f);
                float green = EldritchSkyCelestial.lerp(EldritchSkyArt.INDIGO_GREEN,
                        EldritchSkyArt.VIOLET_GREEN, violetTrail * 0.78f);
                float blue = EldritchSkyCelestial.lerp(EldritchSkyArt.INDIGO_BLUE,
                        EldritchSkyArt.VIOLET_BLUE, violetTrail * 0.78f);
                red = EldritchSkyCelestial.lerp(red, EldritchSkyArt.ELECTRIC_CYAN_RED, cyanPulse);
                green = EldritchSkyCelestial.lerp(green, EldritchSkyArt.ELECTRIC_CYAN_GREEN, cyanPulse);
                blue = EldritchSkyCelestial.lerp(blue, EldritchSkyArt.ELECTRIC_CYAN_BLUE, cyanPulse);
                red = EldritchSkyCelestial.lerp(red, EldritchSkyArt.ICE_BLUE_RED, iceCore);
                green = EldritchSkyCelestial.lerp(green, EldritchSkyArt.ICE_BLUE_GREEN, iceCore);
                blue = EldritchSkyCelestial.lerp(blue, EldritchSkyArt.ICE_BLUE_BLUE, iceCore);
                float alpha = open * env.edgeGlow() * (0.25f + cyanPulse * 0.62f + iceCore * 0.18f)
                        * Math.min(tipFade(geometry, i), tipFade(geometry, i + 1));
                gradientQuad(buffer, matrix, A, B, C, D, red, green, blue, alpha, EldritchSkyArt.EDGE_RADIUS);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void edgePoint(EldritchSkyGeometry geometry, int index, int side,
                                  float scale, float outward, float[] out) {
        float vertical = geometry.verticalDeg(index) * scale;
        float tip = sharpTipFactor(geometry, index);
        float width = geometry.halfWidthDeg(geometry.verticalDeg(index) / EldritchSkyGeometry.APERTURE_HALF_HEIGHT_DEG,
                side < 0) * scale * tip + outward * tip;
        EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                side * width, vertical, 0f, out);
    }

    private static void drawRimPlates(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        float open = env.apertureOpen();
        if (open <= 0.02f) return;
        float scale = env.apertureScale() * env.breath();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (EldritchSkyGeometry.RimPlate plate : geometry.plates()) {
            boolean left = plate.side() < 0f;
            float vertical = plate.vertical() * EldritchSkyGeometry.APERTURE_HALF_HEIGHT_DEG * scale;
            float edge = geometry.halfWidthDeg(plate.vertical(), left) * scale * plate.side();
            float settle = (float) Math.sin(env.seconds() * Math.PI * 2.0 / plate.settlePeriodSeconds() + plate.phase());
            float peel = plate.outwardDeg() * scale * (0.72f + 0.28f * open)
                    + settle * 0.85f + env.fracturePulse() * 0.65f;
            float tipX = edge + plate.side() * (peel + plate.lengthDeg() * scale);
            float tipY = vertical + plate.leanDeg() * scale * 0.42f;
            float half = plate.baseWidthDeg() * scale * (0.55f + 0.45f * open);
            EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(), edge, vertical + half, 0f, A);
            EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(), edge, vertical - half, 0f, B);
            EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(), tipX, tipY, 0f, C);
            float alpha = open * plate.peel() * 0.48f;
            vertex(buffer, matrix, A, EldritchSkyArt.PLATE_RADIUS,
                    EldritchSkyArt.BRUISE_RED, EldritchSkyArt.BRUISE_GREEN, EldritchSkyArt.BRUISE_BLUE, alpha);
            vertex(buffer, matrix, B, EldritchSkyArt.PLATE_RADIUS,
                    EldritchSkyArt.DEEP_RED, EldritchSkyArt.DEEP_GREEN, EldritchSkyArt.DEEP_BLUE, alpha * 0.76f);
            vertex(buffer, matrix, C, EldritchSkyArt.PLATE_RADIUS,
                    EldritchSkyArt.RIM_RED, EldritchSkyArt.RIM_GREEN, EldritchSkyArt.RIM_BLUE, alpha * 0.46f);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawDebris(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        float pull = env.debrisPull() * env.retreat();
        if (pull <= 0.02f) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (EldritchSkyGeometry.Star star : geometry.stars()) {
            float progress = positiveModulo(env.seconds() * (0.018f + star.pull() * 0.018f) + star.phase(), 1f);
            float inward = 1f - EldritchSkyCelestial.smoothstep(0f, 1f, progress);
            float spiral = star.spiral() * progress * 18f;
            float x = star.horizontal() * inward + spiral;
            float y = star.vertical() * inward;
            float size = star.size() * (0.35f + 0.65f * inward);
            EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(), x - size, y, 0f, A);
            EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(), x, y + size, 0f, B);
            EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(), x + size, y, 0f, C);
            EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(), x, y - size, 0f, D);
            float alpha = pull * (float) Math.sin(Math.PI * progress) * 0.34f;
            quad(buffer, matrix, A, B, C, D, EldritchSkyArt.RIM_RED, EldritchSkyArt.RIM_GREEN,
                    EldritchSkyArt.RIM_BLUE, alpha, EldritchSkyArt.DEBRIS_RADIUS);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static float tipFade(EldritchSkyGeometry geometry, int index) {
        float normalized = Math.abs(geometry.verticalDeg(index) / EldritchSkyGeometry.APERTURE_HALF_HEIGHT_DEG);
        return 1f - EldritchSkyCelestial.smoothstep(0.76f, 1.03f, normalized);
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0f ? result + divisor : result;
    }

    private static float arcFade(float t) {
        return EldritchSkyCelestial.smoothstep(0f, 0.13f, t)
                * (1f - EldritchSkyCelestial.smoothstep(0.86f, 1f, t));
    }

    private static float sharpTipFactor(EldritchSkyGeometry geometry, int index) {
        float normalized = Math.abs(geometry.verticalDeg(index) / EldritchSkyGeometry.APERTURE_HALF_HEIGHT_DEG);
        return EldritchSkyCelestial.smoothstep(0f, 0.14f,
                1f - EldritchSkyCelestial.clamp01(normalized));
    }

    private static float positiveFraction(float value) {
        float result = value % 1f;
        return result < 0f ? result + 1f : result;
    }

    private static void gradientQuad(BufferBuilder buffer, Matrix4f matrix, float[] a, float[] b,
                                     float[] c, float[] d, float red, float green, float blue,
                                     float alpha, float radius) {
        vertex(buffer, matrix, a, radius, red, green, blue, alpha);
        vertex(buffer, matrix, b, radius, red * 0.35f, green * 0.35f, blue * 0.42f, alpha * 0.22f);
        vertex(buffer, matrix, c, radius, red * 0.35f, green * 0.35f, blue * 0.42f, alpha * 0.22f);
        vertex(buffer, matrix, d, radius, red, green, blue, alpha);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, float[] a, float[] b, float[] c, float[] d,
                             float red, float green, float blue, float alpha, float radius) {
        vertex(buffer, matrix, a, radius, red, green, blue, alpha);
        vertex(buffer, matrix, b, radius, red, green, blue, alpha);
        vertex(buffer, matrix, c, radius, red, green, blue, alpha);
        vertex(buffer, matrix, d, radius, red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float[] direction, float radius,
                               float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, direction[0] * radius, direction[1] * radius, direction[2] * radius)
                .color(EldritchSkyPalette.r(red, green, blue), EldritchSkyPalette.g(red, green, blue), EldritchSkyPalette.b(red, green, blue), EldritchSkyCelestial.clamp01(alpha)).endVertex();
    }
}
