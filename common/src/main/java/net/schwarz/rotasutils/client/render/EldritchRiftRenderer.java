package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.schwarz.rotasutils.Rotasutils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The focal tear, painted rather than built from flat quads.
 *
 * <p>Textures from {@code scripts/gen_rift_textures.py} are layered on meshes laid over the celestial
 * sphere around the rift's world-fixed direction:</p>
 * <ol>
 *   <li>a veil over nearly the whole sky, laid out around the tear, that turns around it and drains
 *       into it, so the sky itself changes and not only the patch around the rift;</li>
 *   <li>a nebula disc, slowly turning, plus a pair of copies that zoom inward and cross-fade, so space
 *       reads as being dragged into the tear;</li>
 *   <li>a near-black backing that hides the sky behind the opening;</li>
 *   <li>a spiral vortex spun <em>inside</em> the eye-shaped opening: the mesh is the opening and only the
 *       texture coordinates rotate, so the swirl turns while the outline stays put. An inner copy turns
 *       faster and another zooming pair pulls the pattern toward the centre;</li>
 *   <li>a soft black abyss with sparse stars;</li>
 *   <li>the torn, burning lips, plus a hot filament layer whose brightness travels along the edge.</li>
 * </ol>
 *
 * <p>Vertex directions are computed once per mesh per frame into preallocated arrays and reused by every
 * pass; the nebula mesh does not scale with the tear's breathing and is cached until the rift moves.</p>
 */
@Environment(EnvType.CLIENT)
public final class EldritchRiftRenderer {
    static final float HALF_WIDTH = EldritchSkyArt.APERTURE_HALF_WIDTH_DEG;
    static final float HALF_HEIGHT = EldritchSkyArt.APERTURE_HALF_HEIGHT_DEG;
    /** Half extents of the rim/abyss quad; must match BOX_W / BOX_H in the texture script. */
    static final float BOX_HALF_WIDTH = 30.0f;
    static final float BOX_HALF_HEIGHT = 34.0f;
    static final float NEBULA_RADIUS = 52f;

    private static final ResourceLocation NEBULA = Rotasutils.id("textures/environment/rift_nebula.png");
    private static final ResourceLocation VORTEX = Rotasutils.id("textures/environment/rift_vortex.png");
    private static final ResourceLocation RIM = Rotasutils.id("textures/environment/rift_rim.png");
    private static final ResourceLocation RIM_HOT = Rotasutils.id("textures/environment/rift_rim_hot.png");
    private static final ResourceLocation ABYSS = Rotasutils.id("textures/environment/rift_abyss.png");
    private static final ResourceLocation SPECKS = Rotasutils.id("textures/environment/rift_specks.png");
    private static final ResourceLocation VEIL = Rotasutils.id("textures/environment/sky_veil.png");
    private static final ResourceLocation GLOW = Rotasutils.id("textures/environment/rift_glow.png");
    private static final ResourceLocation RING = Rotasutils.id("textures/environment/rift_ring.png");
    private static final ResourceLocation CRACK = Rotasutils.id("textures/environment/sky_crack.png");
    private static final ResourceLocation RIM_B = Rotasutils.id("textures/environment/rift_rim_b.png");
    private static final ResourceLocation BLOOM = Rotasutils.id("textures/environment/rift_bloom.png");
    private static final ResourceLocation RAYS = Rotasutils.id("textures/environment/rift_rays.png");
    private static final ResourceLocation SIGIL = Rotasutils.id("textures/environment/rift_sigil.png");
    private static final ResourceLocation FLARE = Rotasutils.id("textures/environment/rift_flare.png");

    /** The seal: outer copy framing the tear, inner copy counter-turning inside it. Radii in degrees. */
    private static final float SIGIL_OUTER_DEG = 64f;
    private static final float SIGIL_INNER_DEG = 42f;
    private static final float SIGIL_DEPTH = EldritchSkyArt.CORONA_RADIUS - 0.2f;
    private static final int SIGIL_RINGS = 10;
    private static final int SIGIL_SECTORS = 96;
    private static final float[] SIGIL_DIR = new float[(SIGIL_RINGS + 1) * (SIGIL_SECTORS + 1) * 3];
    /** The flare: a 4:1 strip across the tear, curved over the sphere, so it needs more than a quad. */
    private static final float FLARE_HALF_W_DEG = 84f;
    private static final float FLARE_HALF_H_DEG = 21f;
    private static final int FLARE_COLUMNS = 24;
    private static final int FLARE_ROWS = 4;
    private static final float[] FLARE_DIR = new float[(FLARE_ROWS + 1) * (FLARE_COLUMNS + 1) * 3];
    private static double sigilPhase;

    private static final float VEIL_DEPTH = EldritchSkyArt.DOME_RADIUS - 0.3f;
    private static final float NEBULA_DEPTH = EldritchSkyArt.CORONA_RADIUS + 0.4f;
    private static final float BACKING_DEPTH = EldritchSkyArt.VOID_RADIUS + 0.2f;
    private static final float VORTEX_DEPTH = EldritchSkyArt.VOID_RADIUS;
    private static final float ABYSS_DEPTH = EldritchSkyArt.VOID_DEEP_RADIUS;
    private static final float RIM_DEPTH = EldritchSkyArt.DEBRIS_RADIUS - 0.2f;
    private static final float RING_DEPTH = EldritchSkyArt.CORONA_RADIUS;
    private static final float GLOW_DEPTH = EldritchSkyArt.DEBRIS_RADIUS - 0.5f;

    private static final int VEIL_RINGS = 26;
    private static final int VEIL_SECTORS = 64;
    private static final float VEIL_INNER_DEG = 4f;
    private static final float VEIL_OUTER_DEG = 172f;
    private static final float FOUR_SKIES_VEIL_DEG = 64f;
    private static final int NEB_RINGS = 14;
    private static final int NEB_SECTORS = 48;
    private static final int EYE_ROWS = 40;
    private static final int EYE_COLUMNS = 16;
    private static final int BOX_ROWS = 18;
    private static final int BOX_COLUMNS = 16;
    private static final int RING_RINGS = 8;
    private static final int RING_SECTORS = 72;
    private static final int GLOW_CELLS = 4;

    /** Vertex directions (x, y, z per vertex) and their local, unscaled tangent offsets in degrees. */
    private static final float[] VEIL_DIR = new float[(VEIL_RINGS + 1) * (VEIL_SECTORS + 1) * 3];
    private static final float[] NEB_DIR = new float[(NEB_RINGS + 1) * (NEB_SECTORS + 1) * 3];
    private static final float[] NEB_LOCAL = new float[(NEB_RINGS + 1) * (NEB_SECTORS + 1) * 2];
    private static final float[] EYE_DIR = new float[(EYE_ROWS + 1) * (EYE_COLUMNS + 1) * 3];
    private static final float[] EYE_LOCAL = new float[(EYE_ROWS + 1) * (EYE_COLUMNS + 1) * 2];
    private static final float[] EYE_FEATHER = new float[(EYE_ROWS + 1) * (EYE_COLUMNS + 1)];
    private static final float[] BOX_DIR = new float[(BOX_ROWS + 1) * (BOX_COLUMNS + 1) * 3];
    private static final float[] BOX_LOCAL = new float[(BOX_ROWS + 1) * (BOX_COLUMNS + 1) * 2];
    private static final float[] RING_DIR = new float[(RING_RINGS + 1) * (RING_SECTORS + 1) * 3];
    private static final float[] GLOW_DIR = new float[(GLOW_CELLS + 1) * (GLOW_CELLS + 1) * 3];
    private static final float[] OUT = new float[3];
    private static final float[] WHITE = {1f, 1f, 1f};
    private static float nebulaYaw = Float.NaN;
    private static float nebulaElevation = Float.NaN;
    private static float nebulaVeilRadius = Float.NaN;

    /*
     * Motion phases, integrated frame by frame. Speeds change during the opening and the collapse; a
     * phase computed as rate * time would jump every time a rate changed, so each one accumulates.
     */
    private static float lastSeconds = Float.NaN;
    private static double spinPhase;
    private static double innerSpinPhase;
    private static double vortexDriftPhase;
    private static double veilTurnPhase;
    private static double veilDrainPhase;
    private static double veilUnderPhase;
    private static double nebulaTurnPhase;
    private static double nebulaDriftPhase;
    private static double heartbeatPhase;

    private EldritchRiftRenderer() { }

    /** Half the width of the opening at {@code t = y / halfHeight}; the texture script uses the same curve. */
    static float halfWidth(float t) {
        float a = Math.min(1f, Math.abs(t));
        return HALF_WIDTH * (float) (Math.sqrt(1.0 - a * a) * Math.pow(1.0 - a, 0.55));
    }

    /**
     * One frame. Every stage of the opening is a window on the single openness value, so the collapse
     * is the same film run backwards - and read backwards it is an implosion: the swirl speeds up, the
     * lips close, the shockwave rushes back in, a last flash, and the seed of light gutters out.
     *
     * <pre>
     * openness  0.02-0.14  omen: a point of light with a quickening heartbeat
     *           0.14-0.34  crack: the point stretches into a flickering hairline, tip to tip
     *           0.28-0.40  strain: the hairline trembles and brightens
     *           0.40-0.68  split: a flash, the lips tear apart with overshoot, a shockwave leaves
     *           0.50-0.82  the vortex fades in spinning fast and settles to its steady turn
     * </pre>
     */
    public static void render(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        render(matrix, geometry, env, true);
    }

    static void render(Matrix4f matrix, EldritchSkyGeometry geometry, EldritchSkyEnvironment env,
                       boolean wholeSkyVeil) {
        float yaw = geometry.focalYawDeg();
        float elevation = geometry.focalElevationDeg();
        float time = env.seconds();
        float o = env.openness();
        float dt = advance(time);
        boolean shaders = ShaderPackCompat.active();
        entityPath = shaders;

        float seedLight = EldritchSkyCelestial.smoothstep(0.02f, 0.14f, o) * (1f - EldritchSkyCelestial.smoothstep(0.40f, 0.52f, o));
        float crack = EldritchSkyCelestial.smoothstep(0.14f, 0.34f, o);
        float split = EldritchSkyCelestial.smoothstep(0.40f, 0.68f, o);
        float strain = EldritchSkyCelestial.smoothstep(0.28f, 0.40f, o) * (1f - split);
        float spread = easeOutBack(split);
        float flash = (float) Math.exp(-Math.pow((o - 0.42f) / 0.03f, 2));
        float body = EldritchSkyCelestial.smoothstep(0.50f, 0.82f, o);
        float surge = Math.max(env.haloPulse(), env.lightningPulse());
        // Fast while it is tearing open (and while it is collapsing), settling to the steady turn.
        float boost = 1f + 4f * (1f - EldritchSkyCelestial.smoothstep(0.55f, 0.98f, o)) * (env.closing() ? 1.5f : 1f);
        float rush = 1f + 3f * (float) Math.exp(-Math.pow((o - 0.52f) / 0.15f, 2));

        spinPhase += dt * 360.0 / 38.0 * boost;
        innerSpinPhase += dt * 360.0 / 20.0 * boost;
        vortexDriftPhase += dt / 6.0 * (0.6 + 0.4 * boost);
        veilTurnPhase += dt / 360.0 * rush;
        veilDrainPhase += dt / 45.0 * rush;
        veilUnderPhase += dt / 70.0;
        nebulaTurnPhase += dt * 360.0 / 240.0 * (0.7 + 0.3 * boost);
        nebulaDriftPhase += dt / 14.0 * rush;
        heartbeatPhase += dt * (0.5 + 2.5 * EldritchSkyCelestial.smoothstep(0.04f, 0.38f, o));
        sigilPhase += dt * 360.0 / 240.0 * (0.5 + 0.5 * boost);
        sigilPhase %= 360.0 * 64;
        wrapPhases();

        // Whole sky: two veils at different speeds and scales, so the streams have depth.
        float veil = env.influence() * env.retreat();
        float veilRadius = wholeSkyVeil ? VEIL_OUTER_DEG : FOUR_SKIES_VEIL_DEG;
        if (veil > 0.004f) {
            buildMeshesAround(yaw, elevation, veilRadius);
            additive();
            texture(VEIL, true);
            drawVeil(matrix, (float) veilTurnPhase, (float) veilDrainPhase, 2f, 0.35f,
                    veil * (1f + 0.25f * surge), veilRadius);
            drawVeil(matrix, (float) -veilUnderPhase * 0.6f, (float) veilUnderPhase, 3f, -0.2f,
                    veil * 0.35f, veilRadius);
        }

        float nebulaVisibility = Math.max(env.contamination(), env.revelation() * 0.78f) * env.retreat();
        if (nebulaVisibility > 0.004f) {
            buildMeshesAround(yaw, elevation, veilRadius);
            additive();
            texture(NEBULA, false);
            // Arrives diffuse and far, then gathers in around the tear.
            float gather = 1f + 0.9f * (1f - EldritchSkyCelestial.smoothstep(0.30f, 0.80f, o));
            // Flow: two copies whose twist and inward pull build over a cycle, cross-faded so the distortion
            // never runs away. The clouds wind into the tear like liquid instead of turning like a disc.
            for (int copy = 0; copy < 2; copy++) {
                float phase = fraction((float) nebulaDriftPhase + copy * 0.5f);
                float weight = 1f - Math.abs(2f * phase - 1f);
                drawNebula(matrix, (float) nebulaTurnPhase + copy * 90f, gather, 0.9f * phase, 0.35f * phase,
                        nebulaVisibility * (0.95f + 0.2f * surge) * weight);
            }
        }

        // God rays: slow shafts of light turning out of the tear, one set each way.
        float rayLight = EldritchSkyCelestial.smoothstep(0.45f, 0.85f, o) * (0.22f + 0.1f * (float) Math.sin(time * 0.7f))
                + surge * 0.25f + (float) Math.exp(-Math.pow((o - 0.42f) / 0.04f, 2)) * 0.8f;
        if (rayLight > 0.004f) {
            buildMeshesAround(yaw, elevation, veilRadius);
            additive();
            texture(RAYS, false);
            drawNebula(matrix, time * 6f, 0.95f, 0f, 0f, rayLight);
            drawNebula(matrix, -time * 4.2f + 40f, 1.15f, 0f, 0f, rayLight * 0.6f);
        }

        // The seal: a colossal rune circle blooms open behind the tear as it splits (overshooting, then
        // settling), turns slowly, and flares on every surge. An inner copy counter-turns at twice the pace,
        // so the frame has depth instead of reading as one flat decal.
        float seal = EldritchSkyCelestial.smoothstep(0.44f, 0.80f, o);
        if (wholeSkyVeil && seal > 0.004f) {
            float bloomOpen = 0.55f + 0.45f * easeOutBack(seal);
            float sealLight = seal * (0.42f + 0.35f * surge + 0.08f * (float) Math.sin(time * 1.3f)) * env.retreat();
            additive();
            texture(SIGIL, false);
            drawSigil(matrix, yaw, elevation, SIGIL_OUTER_DEG * bloomOpen, (float) sigilPhase, sealLight);
            drawSigil(matrix, yaw, elevation, SIGIL_INNER_DEG * bloomOpen, (float) -sigilPhase * 2f + 15f, sealLight * 0.45f);
        }

        // The sky cracks outward from the tear before it splits, and heals as it closes.
        if (wholeSkyVeil) {
            additive();
            texture(CRACK, true);
            EldritchSkyCracks.render(matrix, geometry, o, time, surge, body);
        }

        float shock = EldritchSkyCelestial.smoothstep(0.41f, 0.74f, o);
        if (wholeSkyVeil && shock > 0.001f && shock < 0.999f) {
            float travelled = 1f - (1f - shock) * (1f - shock) * (1f - shock);
            additive();
            texture(RING, false);
            drawRing(matrix, yaw, elevation, 6f + 100f * travelled,
                    (float) Math.pow(Math.sin(Math.PI * shock), 1.2) * 0.95f);
        }

        float beat = (float) Math.pow(0.5 + 0.5 * Math.sin(heartbeatPhase * Math.PI * 2.0), 10.0);
        if (crack > 0.003f) {
            float scale = Math.max(0.2f, env.apertureScale()) * env.breath();
            float tremble = strain * (0.5f + 0.5f * (float) Math.sin(time * Math.PI * 2.0 * 9.0)) * 0.03f;
            float heightScale = scale * (0.1f + 0.9f * crack) * (1f + 0.02f * strain * (float) Math.sin(time * Math.PI * 2.0 * 7.0));
            float widthScale = scale * Math.max(0.025f + tremble, spread);
            float sway = body * (1.2f * (float) Math.sin(time * Math.PI * 2.0 / 13.0)
                    + 0.4f * (float) Math.sin(time * Math.PI * 2.0 / 5.3));
            buildEye(yaw, elevation, widthScale, heightScale, sway);
            buildBox(yaw, elevation, widthScale, heightScale, sway);

            if (body > 0.005f) {
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                drawBacking(matrix, body);

                additive();
                texture(VORTEX, false);
                float vortexGlow = body * (1f + 0.25f * surge);
                // Differential flow: the middle turns faster than the rim and everything is pulled inward,
                // so the swirl winds up like water going down a drain. Each pair cross-fades its reset.
                for (int copy = 0; copy < 2; copy++) {
                    float phase = fraction((float) vortexDriftPhase + copy * 0.5f);
                    float weight = 1f - Math.abs(2f * phase - 1f);
                    drawVortex(matrix, (float) -spinPhase + copy * 97f, 1.0f, -1.6f * phase, 0.5f * phase,
                            vortexGlow * weight);
                    float inner = fraction((float) vortexDriftPhase * 1.6f + copy * 0.5f + 0.25f);
                    float innerWeight = 1f - Math.abs(2f * inner - 1f);
                    drawVortex(matrix, (float) -innerSpinPhase + 40f + copy * 131f, 0.62f, -2.2f * inner, 0.6f * inner,
                            vortexGlow * 0.75f * innerWeight);
                }

                RenderSystem.defaultBlendFunc();
                texture(ABYSS, false);
                drawBox(matrix, ABYSS_DEPTH,
                        body * (0.9f + 0.1f * EldritchSkyCelestial.clamp01(env.voidDepth())), 0f, 0f, 0f);
                additive();
                texture(SPECKS, false);
                drawBox(matrix, ABYSS_DEPTH - 0.1f, body * (0.65f + 0.35f * (float) Math.sin(time * 1.7f)), 0f, 0f, 0f);
            }

            additive();
            // While it is only a crack it flickers like a live wire; once open it breathes.
            float flicker = 1f - (1f - split) * 0.35f * (0.5f + 0.5f
                    * (float) (Math.sin(time * 37.0) * Math.sin(time * 23.0 + 1.3)));
            float glow = crack * flicker * (0.72f + 0.28f * EldritchSkyCelestial.clamp01(env.edgeGlow()))
                    * (0.93f + 0.07f * (float) Math.sin(time * Math.PI * 2.0 / 6.0));
            float rimLight = glow * (1f + 0.5f * surge) + (strain + flash) * 1.3f;
            // Bloom: bright light bleeding past the lips, as a camera sees it.
            texture(BLOOM, false);
            drawBox(matrix, RIM_DEPTH + 0.1f, crack * (0.3f + 0.25f * surge) * (0.9f + 0.1f * beat) + flash * 0.9f + strain * 0.35f,
                    time, 0f, 0.6f);
            // The plasma boils: the texture wanders a little and two different bakes trade places.
            float trade = 0.5f + 0.5f * (float) Math.sin(time * Math.PI * 2.0 / 11.0);
            texture(RIM, false);
            drawBox(matrix, RIM_DEPTH, rimLight * (1f - trade * 0.85f), time, 0f, 1f);
            texture(RIM_B, false);
            drawBox(matrix, RIM_DEPTH - 0.05f, rimLight * (0.15f + trade * 0.85f), time + 17f, 0f, 1f);
            texture(RIM_HOT, false);
            drawBox(matrix, RIM_DEPTH - 0.1f, crack * flicker * (0.55f + 0.45f * EldritchSkyCelestial.clamp01(env.lightningPulse()))
                    + surge * 0.6f + (strain + flash) * 1.2f, time, 1f, 0.7f);

            // Lightning arcs between the lips and the cracks: constant while it strains, on surges after.
            float charge = strain * 1.5f + flash * 2f + crack * (1f - split) * 0.4f
                    + env.lightningPulse() * body * 0.9f + (env.closing() ? (1f - body) * crack * 0.8f : 0f);
            texture(CRACK, true);
            if (wholeSkyVeil) {
                EldritchSkyCracks.renderBolts(matrix, geometry, charge, time, widthScale, heightScale);
            }
        }

        additive();
        texture(GLOW, false);
        if (body > 0.005f) {
            // Bloom: a very wide wash, a soft halo in the rift's colour and a tighter hot one, swelling on
            // surges. Stronger over a shader pack, whose own bloom never reaches this sky.
            float bloom = ShaderPackCompat.overlay() ? 1f : 0.6f;
            drawGlow(matrix, yaw, elevation, 55f, body * (0.28f + 0.15f * surge) * bloom);
            drawGlow(matrix, yaw, elevation, 30f, body * (0.55f + 0.25f * surge) * bloom);
            drawGlow(matrix, yaw, elevation, 15f, body * (0.80f + 0.35f * surge + 0.15f * beat) * bloom);
        }
        if (seedLight > 0.003f) {
            drawGlow(matrix, yaw, elevation, 3.5f + 5f * seedLight + 2.5f * beat, seedLight * (0.55f + 0.9f * beat));
        }
        if (flash > 0.01f) {
            drawGlow(matrix, yaw, elevation, 34f, flash * 1.4f);
        }
        // The one white-hot focal point: a lens flare whose anamorphic streak spans the sky. It is born with
        // the omen's point of light, blinds on the split, and breathes with the heartbeat once open.
        float flareLight = seedLight * (0.35f + 0.5f * beat) + flash * 1.3f
                + body * (0.45f + 0.3f * surge + 0.12f * beat);
        if (flareLight > 0.004f) {
            texture(FLARE, false);
            drawFlare(matrix, yaw, elevation, 0.6f + 0.4f * Math.max(split, seedLight), flareLight);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /**
     * How hard the world shakes, in degrees of camera roll: a shiver while it cracks, a real tremble while it strains, a
     * jolt when it splits, a rumble on lightning, and all of it harder while it collapses.
     */
    static float trembleDeg(EldritchSkyEnvironment env) {
        float o = env.openness();
        float crack = EldritchSkyCelestial.smoothstep(0.14f, 0.34f, o) * (1f - EldritchSkyCelestial.smoothstep(0.34f, 0.45f, o));
        float split = EldritchSkyCelestial.smoothstep(0.40f, 0.68f, o);
        float strain = EldritchSkyCelestial.smoothstep(0.28f, 0.40f, o) * (1f - split);
        float flash = (float) Math.exp(-Math.pow((o - 0.42f) / 0.04f, 2));
        float body = EldritchSkyCelestial.smoothstep(0.50f, 0.82f, o);
        float amplitude = 0.08f * crack + 0.32f * strain + 0.6f * flash + 0.12f * env.lightningPulse() * body;
        return env.closing() ? amplitude * 1.4f : amplitude;
    }

    /** The omen heartbeat's phase; each whole number is one beat, for the sound to follow. */
    static double heartbeat() {
        return heartbeatPhase;
    }

    /** Seconds since the last frame; zero after a pause, a rewind or a long hitch, so nothing lurches. */
    private static float advance(float time) {
        float dt = Float.isNaN(lastSeconds) || time < lastSeconds || time - lastSeconds > 0.5f ? 0f : time - lastSeconds;
        lastSeconds = time;
        return dt;
    }

    private static void wrapPhases() {
        spinPhase %= 360.0 * 64;
        innerSpinPhase %= 360.0 * 64;
        nebulaTurnPhase %= 360.0 * 64;
        vortexDriftPhase %= 1024.0;
        veilTurnPhase %= 1024.0;
        veilDrainPhase %= 1024.0;
        veilUnderPhase %= 1024.0;
        nebulaDriftPhase %= 1024.0;
        heartbeatPhase %= 1024.0;
    }

    /** Overshoots a little past one before settling, so the lips snap open instead of sliding. */
    static float easeOutBack(float x) {
        float c1 = 1.2f;
        float c3 = c1 + 1f;
        float t = x - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }

    // Meshes ---------------------------------------------------------------------------------------

    /** The veil and nebula meshes do not breathe, so they are rebuilt only when the rift moves. */
    private static void buildMeshesAround(float yaw, float elevation, float veilRadius) {
        if (yaw == nebulaYaw && elevation == nebulaElevation && veilRadius == nebulaVeilRadius) return;
        nebulaYaw = yaw;
        nebulaElevation = elevation;
        nebulaVeilRadius = veilRadius;
        for (int ring = 0; ring <= VEIL_RINGS; ring++) {
            float rho = veilRho(ring, veilRadius);
            for (int sector = 0; sector <= VEIL_SECTORS; sector++) {
                double phi = Math.PI * 2.0 * sector / VEIL_SECTORS;
                EldritchSkyCelestial.around(yaw, elevation, (float) Math.cos(phi) * rho, (float) Math.sin(phi) * rho, 0f, OUT);
                store(VEIL_DIR, ring * (VEIL_SECTORS + 1) + sector, OUT);
            }
        }
        for (int ring = 0; ring <= NEB_RINGS; ring++) {
            float rho = ring / (float) NEB_RINGS;
            for (int sector = 0; sector <= NEB_SECTORS; sector++) {
                double phi = Math.PI * 2.0 * sector / NEB_SECTORS;
                float lx = (float) Math.cos(phi) * rho;
                float ly = (float) Math.sin(phi) * rho;
                int index = ring * (NEB_SECTORS + 1) + sector;
                NEB_LOCAL[index * 2] = lx;
                NEB_LOCAL[index * 2 + 1] = ly;
                EldritchSkyCelestial.around(yaw, elevation, lx * NEBULA_RADIUS, ly * NEBULA_RADIUS * 1.1f, 0f, OUT);
                store(NEB_DIR, index, OUT);
            }
        }
    }

    /** Rings bunch up near the tear, where the streams are brightest and curve the most. */
    private static float veilRho(int ring, float outerRadius) {
        float t = ring / (float) VEIL_RINGS;
        return VEIL_INNER_DEG + (outerRadius - VEIL_INNER_DEG) * t * (0.35f + 0.65f * t);
    }

    private static void buildEye(float yaw, float elevation, float widthScale, float heightScale, float roll) {
        for (int row = 0; row <= EYE_ROWS; row++) {
            float t = -1f + 2f * row / EYE_ROWS;
            float width = halfWidth(t);
            for (int column = 0; column <= EYE_COLUMNS; column++) {
                float s = -1f + 2f * column / EYE_COLUMNS;
                float x = s * width;
                float y = t * HALF_HEIGHT;
                int index = row * (EYE_COLUMNS + 1) + column;
                EYE_LOCAL[index * 2] = x / (HALF_WIDTH * 1.02f);
                EYE_LOCAL[index * 2 + 1] = y / (HALF_HEIGHT * 1.02f);
                EYE_FEATHER[index] = (1f - EldritchSkyCelestial.smoothstep(0.72f, 1f, Math.abs(s)))
                        * (1f - EldritchSkyCelestial.smoothstep(0.8f, 1f, Math.abs(t)));
                EldritchSkyCelestial.around(yaw, elevation, x * widthScale, y * heightScale, roll, OUT);
                store(EYE_DIR, index, OUT);
            }
        }
    }

    private static void buildBox(float yaw, float elevation, float widthScale, float heightScale, float roll) {
        for (int row = 0; row <= BOX_ROWS; row++) {
            float ty = -1f + 2f * row / BOX_ROWS;
            for (int column = 0; column <= BOX_COLUMNS; column++) {
                float tx = -1f + 2f * column / BOX_COLUMNS;
                int index = row * (BOX_COLUMNS + 1) + column;
                BOX_LOCAL[index * 2] = tx;
                BOX_LOCAL[index * 2 + 1] = ty;
                EldritchSkyCelestial.around(yaw, elevation, tx * BOX_HALF_WIDTH * widthScale,
                        ty * BOX_HALF_HEIGHT * heightScale, roll, OUT);
                store(BOX_DIR, index, OUT);
            }
        }
    }

    // Passes ---------------------------------------------------------------------------------------

    /**
     * The veil: u runs around the tear (tiled twice, with a twist so streams spiral), v runs away from
     * it. Scrolling u turns the sky around the tear once every six minutes; scrolling v drains the
     * streams into it. The fade by distance is per vertex so the tiling texture stays uniform.
     */
    private static void drawVeil(Matrix4f matrix, float turn, float drain, float tiles, float twist, float alpha,
                                 float outerRadius) {
        BufferBuilder buffer = begin();
        for (int ring = 0; ring < VEIL_RINGS; ring++) {
            for (int sector = 0; sector < VEIL_SECTORS; sector++) {
                int a = ring * (VEIL_SECTORS + 1) + sector;
                veilVertex(buffer, matrix, a, ring, sector, turn, drain, tiles, twist, alpha, outerRadius);
                veilVertex(buffer, matrix, a + 1, ring, sector + 1, turn, drain, tiles, twist, alpha, outerRadius);
                veilVertex(buffer, matrix, a + VEIL_SECTORS + 2, ring + 1, sector + 1, turn, drain, tiles, twist, alpha, outerRadius);
                veilVertex(buffer, matrix, a + VEIL_SECTORS + 1, ring + 1, sector, turn, drain, tiles, twist, alpha, outerRadius);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void veilVertex(BufferBuilder buffer, Matrix4f matrix, int index, int ring, int sector,
                                   float turn, float drain, float tiles, float twist, float alpha, float outerRadius) {
        float rho = veilRho(ring, outerRadius);
        float distance = rho / 180f;
        float u = tiles * sector / VEIL_SECTORS + twist * distance + turn;
        float v = 1.5f * distance + drain;
        float fade = EldritchSkyCelestial.smoothstep(VEIL_INNER_DEG, 26f, rho)
                * (outerRadius == VEIL_OUTER_DEG
                ? 1f - 0.55f * EldritchSkyCelestial.smoothstep(60f, 165f, rho)
                : 1f - EldritchSkyCelestial.smoothstep(42f, outerRadius, rho));
        vertex(buffer, matrix, VEIL_DIR, index, VEIL_DEPTH, u, v, alpha * fade);
    }

    /** The shockwave: a disc whose painted ring sits at 90 % of its radius, laid on the sphere. */
    private static void drawRing(Matrix4f matrix, float yaw, float elevation, float radiusDeg, float alpha) {
        if (alpha <= 0.003f) return;
        float disc = radiusDeg / 0.9f;
        for (int ring = 0; ring <= RING_RINGS; ring++) {
            float rho = 0.55f + 0.45f * ring / RING_RINGS;
            for (int sector = 0; sector <= RING_SECTORS; sector++) {
                double phi = Math.PI * 2.0 * sector / RING_SECTORS;
                EldritchSkyCelestial.around(yaw, elevation, (float) Math.cos(phi) * rho * disc,
                        (float) Math.sin(phi) * rho * disc * 1.08f, 0f, OUT);
                store(RING_DIR, ring * (RING_SECTORS + 1) + sector, OUT);
            }
        }
        BufferBuilder buffer = begin();
        for (int ring = 0; ring < RING_RINGS; ring++) {
            for (int sector = 0; sector < RING_SECTORS; sector++) {
                int a = ring * (RING_SECTORS + 1) + sector;
                ringVertex(buffer, matrix, a, ring, sector, alpha);
                ringVertex(buffer, matrix, a + 1, ring, sector + 1, alpha);
                ringVertex(buffer, matrix, a + RING_SECTORS + 2, ring + 1, sector + 1, alpha);
                ringVertex(buffer, matrix, a + RING_SECTORS + 1, ring + 1, sector, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void ringVertex(BufferBuilder buffer, Matrix4f matrix, int index, int ring, int sector, float alpha) {
        float rho = 0.55f + 0.45f * ring / RING_RINGS;
        double phi = Math.PI * 2.0 * sector / RING_SECTORS;
        vertex(buffer, matrix, RING_DIR, index, RING_DEPTH, 0.5f + 0.5f * rho * (float) Math.cos(phi),
                0.5f - 0.5f * rho * (float) Math.sin(phi), alpha);
    }

    /** The rune seal: a disc on the sphere around the tear whose texture turns while the mesh stays put. */
    private static void drawSigil(Matrix4f matrix, float yaw, float elevation, float radiusDeg, float angleDeg, float alpha) {
        if (alpha <= 0.003f) return;
        for (int ring = 0; ring <= SIGIL_RINGS; ring++) {
            float rho = 0.3f + 0.7f * ring / SIGIL_RINGS;
            for (int sector = 0; sector <= SIGIL_SECTORS; sector++) {
                double phi = Math.PI * 2.0 * sector / SIGIL_SECTORS;
                EldritchSkyCelestial.around(yaw, elevation, (float) Math.cos(phi) * rho * radiusDeg,
                        (float) Math.sin(phi) * rho * radiusDeg, 0f, OUT);
                store(SIGIL_DIR, ring * (SIGIL_SECTORS + 1) + sector, OUT);
            }
        }
        double turn = Math.toRadians(angleDeg);
        BufferBuilder buffer = begin();
        for (int ring = 0; ring < SIGIL_RINGS; ring++) {
            for (int sector = 0; sector < SIGIL_SECTORS; sector++) {
                int a = ring * (SIGIL_SECTORS + 1) + sector;
                sigilVertex(buffer, matrix, a, ring, sector, turn, alpha);
                sigilVertex(buffer, matrix, a + 1, ring, sector + 1, turn, alpha);
                sigilVertex(buffer, matrix, a + SIGIL_SECTORS + 2, ring + 1, sector + 1, turn, alpha);
                sigilVertex(buffer, matrix, a + SIGIL_SECTORS + 1, ring + 1, sector, turn, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void sigilVertex(BufferBuilder buffer, Matrix4f matrix, int index, int ring, int sector,
                                    double turn, float alpha) {
        float rho = 0.3f + 0.7f * ring / SIGIL_RINGS;
        double phi = Math.PI * 2.0 * sector / SIGIL_SECTORS + turn;
        vertex(buffer, matrix, SIGIL_DIR, index, SIGIL_DEPTH, 0.5f + 0.5f * rho * (float) Math.cos(phi),
                0.5f - 0.5f * rho * (float) Math.sin(phi), alpha);
    }

    /** The lens flare strip; {@code stretch} widens the streak as the tear splits. */
    private static void drawFlare(Matrix4f matrix, float yaw, float elevation, float stretch, float alpha) {
        for (int row = 0; row <= FLARE_ROWS; row++) {
            float ty = -1f + 2f * row / FLARE_ROWS;
            for (int column = 0; column <= FLARE_COLUMNS; column++) {
                float tx = -1f + 2f * column / FLARE_COLUMNS;
                EldritchSkyCelestial.around(yaw, elevation, tx * FLARE_HALF_W_DEG * stretch, ty * FLARE_HALF_H_DEG, 0f, OUT);
                store(FLARE_DIR, row * (FLARE_COLUMNS + 1) + column, OUT);
            }
        }
        BufferBuilder buffer = begin();
        for (int row = 0; row < FLARE_ROWS; row++) {
            for (int column = 0; column < FLARE_COLUMNS; column++) {
                int a = row * (FLARE_COLUMNS + 1) + column;
                flareVertex(buffer, matrix, a, alpha);
                flareVertex(buffer, matrix, a + 1, alpha);
                flareVertex(buffer, matrix, a + FLARE_COLUMNS + 2, alpha);
                flareVertex(buffer, matrix, a + FLARE_COLUMNS + 1, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void flareVertex(BufferBuilder buffer, Matrix4f matrix, int index, float alpha) {
        int row = index / (FLARE_COLUMNS + 1);
        int column = index % (FLARE_COLUMNS + 1);
        vertex(buffer, matrix, FLARE_DIR, index, GLOW_DEPTH - 0.1f, column / (float) FLARE_COLUMNS,
                1f - row / (float) FLARE_ROWS, alpha);
    }

    /** A flare centred on the tear: the omen's point of light, and the flash when it splits. */
    private static void drawGlow(Matrix4f matrix, float yaw, float elevation, float halfSizeDeg, float alpha) {
        if (alpha <= 0.003f) return;
        for (int row = 0; row <= GLOW_CELLS; row++) {
            float ty = -1f + 2f * row / GLOW_CELLS;
            for (int column = 0; column <= GLOW_CELLS; column++) {
                float tx = -1f + 2f * column / GLOW_CELLS;
                EldritchSkyCelestial.around(yaw, elevation, tx * halfSizeDeg, ty * halfSizeDeg, 0f, OUT);
                store(GLOW_DIR, row * (GLOW_CELLS + 1) + column, OUT);
            }
        }
        BufferBuilder buffer = begin();
        for (int row = 0; row < GLOW_CELLS; row++) {
            for (int column = 0; column < GLOW_CELLS; column++) {
                int a = row * (GLOW_CELLS + 1) + column;
                glowVertex(buffer, matrix, a, alpha);
                glowVertex(buffer, matrix, a + 1, alpha);
                glowVertex(buffer, matrix, a + GLOW_CELLS + 2, alpha);
                glowVertex(buffer, matrix, a + GLOW_CELLS + 1, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void glowVertex(BufferBuilder buffer, Matrix4f matrix, int index, float alpha) {
        int row = index / (GLOW_CELLS + 1);
        int column = index % (GLOW_CELLS + 1);
        vertex(buffer, matrix, GLOW_DIR, index, GLOW_DEPTH, column / (float) GLOW_CELLS,
                1f - row / (float) GLOW_CELLS, alpha);
    }

    /**
     * A textured disc around the tear. {@code twist} (radians) turns the middle further than the edge and
     * {@code pull} draws the pattern inward; both are zero for a plain rotating disc.
     */
    private static void drawNebula(Matrix4f matrix, float angleDeg, float zoom, float twist, float pull, float alpha) {
        if (alpha <= 0.003f) return;
        flowAngle = (float) Math.toRadians(angleDeg);
        flowZoom = zoom;
        flowTwist = twist;
        flowPull = pull;
        float cos = 0f;
        float sin = 0f;
        BufferBuilder buffer = begin();
        for (int ring = 0; ring < NEB_RINGS; ring++) {
            for (int sector = 0; sector < NEB_SECTORS; sector++) {
                int a = ring * (NEB_SECTORS + 1) + sector;
                int b = a + 1;
                int c = a + NEB_SECTORS + 2;
                int d = a + NEB_SECTORS + 1;
                nebulaVertex(buffer, matrix, a, cos, sin, alpha);
                nebulaVertex(buffer, matrix, b, cos, sin, alpha);
                nebulaVertex(buffer, matrix, c, cos, sin, alpha);
                nebulaVertex(buffer, matrix, d, cos, sin, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void nebulaVertex(BufferBuilder buffer, Matrix4f matrix, int index, float cos, float sin, float alpha) {
        float lx = NEB_LOCAL[index * 2];
        float ly = NEB_LOCAL[index * 2 + 1];
        float r = (float) Math.sqrt(lx * lx + ly * ly);
        flow(lx, ly, r, 1f - r, 0.5f);
        float edge = 1f - EldritchSkyCelestial.smoothstep(0.82f, 1f, r);
        vertex(buffer, matrix, NEB_DIR, index, NEBULA_DEPTH, FLOW_UV[0], FLOW_UV[1], alpha * edge);
    }

    /** Current flow parameters, set per pass so the per-vertex work allocates nothing. */
    private static float flowAngle;
    private static float flowZoom = 1f;
    private static float flowTwist;
    private static float flowPull;
    private static final float[] FLOW_UV = new float[2];

    /**
     * Texture coordinate for a local point under the current flow: rotated by the pass angle plus the
     * twist weighted toward the middle, and scaled outward by the pull so the pattern slides inward.
     */
    private static void flow(float lx, float ly, float r, float inwardness, float extent) {
        float angle = flowAngle + flowTwist * inwardness * inwardness;
        float scale = (1f + flowPull * (1f - 0.6f * r)) / flowZoom;
        float cos = (float) Math.cos(angle) * scale;
        float sin = (float) Math.sin(angle) * scale;
        FLOW_UV[0] = 0.5f + extent * (lx * cos - ly * sin);
        FLOW_UV[1] = 0.5f - extent * (lx * sin + ly * cos);
    }

    private static void drawBacking(Matrix4f matrix, float open) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int row = 0; row < EYE_ROWS; row++) {
            for (int column = 0; column < EYE_COLUMNS; column++) {
                int a = row * (EYE_COLUMNS + 1) + column;
                backingVertex(buffer, matrix, a, open);
                backingVertex(buffer, matrix, a + 1, open);
                backingVertex(buffer, matrix, a + EYE_COLUMNS + 2, open);
                backingVertex(buffer, matrix, a + EYE_COLUMNS + 1, open);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void backingVertex(BufferBuilder buffer, Matrix4f matrix, int index, float open) {
        float feather = EldritchSkyCelestial.smoothstep(0f, 0.35f, EYE_FEATHER[index]);
        buffer.vertex(matrix, EYE_DIR[index * 3] * BACKING_DEPTH, EYE_DIR[index * 3 + 1] * BACKING_DEPTH,
                        EYE_DIR[index * 3 + 2] * BACKING_DEPTH)
                .color(EldritchSkyPalette.r(0.008f, 0.010f, 0.035f), EldritchSkyPalette.g(0.008f, 0.010f, 0.035f), EldritchSkyPalette.b(0.008f, 0.010f, 0.035f), open * (0.55f + 0.42f * feather)).endVertex();
    }

    private static void drawVortex(Matrix4f matrix, float angleDeg, float zoom, float twist, float pull, float alpha) {
        if (alpha <= 0.003f) return;
        flowAngle = (float) Math.toRadians(angleDeg);
        flowZoom = zoom;
        flowTwist = twist;
        flowPull = pull;
        float cos = 0f;
        float sin = 0f;
        BufferBuilder buffer = begin();
        for (int row = 0; row < EYE_ROWS; row++) {
            for (int column = 0; column < EYE_COLUMNS; column++) {
                int a = row * (EYE_COLUMNS + 1) + column;
                eyeVertex(buffer, matrix, a, cos, sin, alpha);
                eyeVertex(buffer, matrix, a + 1, cos, sin, alpha);
                eyeVertex(buffer, matrix, a + EYE_COLUMNS + 2, cos, sin, alpha);
                eyeVertex(buffer, matrix, a + EYE_COLUMNS + 1, cos, sin, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void eyeVertex(BufferBuilder buffer, Matrix4f matrix, int index, float cos, float sin, float alpha) {
        float lx = EYE_LOCAL[index * 2];
        float ly = EYE_LOCAL[index * 2 + 1];
        float r = Math.min(1f, (float) Math.sqrt(lx * lx + ly * ly));
        flow(lx, ly, r, 1f - r, 0.49f);
        vertex(buffer, matrix, EYE_DIR, index, VORTEX_DEPTH, FLOW_UV[0], FLOW_UV[1], alpha * EYE_FEATHER[index]);
    }

    /**
     * Draws the rim/abyss box. With {@code travel} above zero the brightness of each row follows pulses
     * that run from the middle of the tear out to its tips, so the hot filaments crawl along the edge.
     */
    private static void drawBox(Matrix4f matrix, float depth, float alpha, float time, float travel, float wander) {
        if (alpha <= 0.003f) return;
        BufferBuilder buffer = begin();
        for (int row = 0; row < BOX_ROWS; row++) {
            for (int column = 0; column < BOX_COLUMNS; column++) {
                int a = row * (BOX_COLUMNS + 1) + column;
                boxVertex(buffer, matrix, a, depth, alpha, time, travel, wander);
                boxVertex(buffer, matrix, a + 1, depth, alpha, time, travel, wander);
                boxVertex(buffer, matrix, a + BOX_COLUMNS + 2, depth, alpha, time, travel, wander);
                boxVertex(buffer, matrix, a + BOX_COLUMNS + 1, depth, alpha, time, travel, wander);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void boxVertex(BufferBuilder buffer, Matrix4f matrix, int index, float depth, float alpha,
                                  float time, float travel, float wander) {
        float tx = BOX_LOCAL[index * 2];
        float ty = BOX_LOCAL[index * 2 + 1];
        float shade = alpha;
        if (travel > 0f) {
            float along = Math.abs(ty) * 1.3f + (tx < 0f ? 0.37f : 0f);
            float wave = (float) Math.sin((along - time / 5.5f) * Math.PI * 2.0);
            float pulse = (float) Math.pow(Math.max(0f, wave), 6.0);
            shade *= 0.22f + 1.1f * pulse;
        }
        float u = (tx + 1f) * 0.5f;
        float v = 1f - (ty + 1f) * 0.5f;
        if (wander > 0f) {
            // Slow, smooth wandering of the texture under a fixed mesh: the plasma seems to boil.
            u += wander * 0.006f * (float) Math.sin(time * 0.83 + ty * 5.1 + tx * 2.3);
            v += wander * 0.006f * (float) Math.cos(time * 0.61 + tx * 4.7 - ty * 1.9);
        }
        vertex(buffer, matrix, BOX_DIR, index, depth, u, v, shade);
    }

    // Helpers --------------------------------------------------------------------------------------

    /**
     * With a shader pack on, textured layers go through the entity "eyes" shader instead of
     * position_tex_color. Packs swap position_tex_color for their own sky program during the sky pass,
     * which throws away the palette colours; the eyes program keeps texture colour and blends additively.
     */
    private static boolean entityPath;

    private static BufferBuilder begin() {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, entityPath ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR);
        return buffer;
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float[] directions, int index, float radius,
                               float u, float v, float alpha) {
        float x = directions[index * 3];
        float y = directions[index * 3 + 1];
        float z = directions[index * 3 + 2];
        // The rainbow sky's textures carry no hue; the colour comes from the direction, so every layer
        // stacked at one place agrees on it instead of summing back to white.
        float[] tint = EldritchSkyPalette.rainbow() ? EldritchSkyPalette.tint(x, y, z) : WHITE;
        if (entityPath) {
            // Eyes blend additively on colour only, so fade by darkening instead of by alpha.
            float a = EldritchSkyCelestial.clamp01(alpha);
            buffer.vertex(matrix, x * radius, y * radius, z * radius)
                    .color(a * tint[0], a * tint[1], a * tint[2], 1f).uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(-x, -y, -z).endVertex();
            return;
        }
        buffer.vertex(matrix, x * radius, y * radius, z * radius)
                .uv(u, v).color(tint[0], tint[1], tint[2], EldritchSkyCelestial.clamp01(alpha)).endVertex();
    }

    private static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    /** Binds a rift texture smoothly filtered: clamped so spun and zoomed layers never wrap, unless it tiles. */
    private static void texture(ResourceLocation location, boolean tile) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(EldritchSkyPalette.texture(location));
        texture.setFilter(true, false);
        int wrap = tile ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
        GlStateManager._bindTexture(texture.getId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        RenderSystem.setShader(entityPath ? GameRenderer::getRendertypeEyesShader : GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture.getId());
    }

    private static void store(float[] target, int index, float[] direction) {
        target[index * 3] = direction[0];
        target[index * 3 + 1] = direction[1];
        target[index * 3 + 2] = direction[2];
    }

    private static float fraction(float value) {
        float result = value % 1f;
        return result < 0f ? result + 1f : result;
    }
}
