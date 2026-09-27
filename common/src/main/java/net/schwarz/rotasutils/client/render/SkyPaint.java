package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.Rotasutils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Painted light on the sky dome: the textures from {@code scripts/gen_sunder_textures.py} laid around
 * any direction of the sky, curved with it, blended additively. For the sky passes, which draw with
 * their own GL state rather than through a buffer source. Call inside a pass that has blending on and
 * depth writes off; {@link #begin} and {@link #end} bracket each texture.
 */
@Environment(EnvType.CLIENT)
final class SkyPaint {
    static final ResourceLocation SEAL = tex("seal");
    static final ResourceLocation RUNES = tex("rune_ring");
    static final ResourceLocation FLARE = tex("flare");
    static final ResourceLocation GLOW = tex("glow");
    static final ResourceLocation RAYS = tex("rays");
    static final ResourceLocation SHOCK = tex("shock");
    static final ResourceLocation MOTE = tex("mote");
    static final ResourceLocation CRACKS = tex("cracks");

    /** The centre direction and a tangent basis around it (right, up). */
    private static final float[] D = {0f, 1f, 0f}, R = {1f, 0f, 0f}, U = {0f, 0f, 1f};
    private static float tr = 1f, tg = 1f, tb = 1f;
    private static final float[] P = new float[3];

    private SkyPaint() {
    }

    private static ResourceLocation tex(String name) {
        return Rotasutils.id("textures/environment/sunder/" + name + ".png");
    }

    static BufferBuilder begin(ResourceLocation location) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        texture.setFilter(true, false);
        GlStateManager._bindTexture(texture.getId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return buffer;
    }

    static void end(BufferBuilder buffer) {
        BufferUploader.drawWithShader(buffer.end());
    }

    static void tint(float[] c) {
        tr = c[0];
        tg = c[1];
        tb = c[2];
    }

    /** Centre later primitives on a unit direction of the sky. */
    static void centre(float[] direction) {
        float len = (float) Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
        D[0] = direction[0] / len;
        D[1] = direction[1] / len;
        D[2] = direction[2] / len;
        // Right is horizontal; straight overhead, where that is undefined, take east.
        float rx = D[2], rz = -D[0];
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0e-3f) {
            rx = 1f;
            rz = 0f;
            rl = 1f;
        }
        R[0] = rx / rl;
        R[1] = 0f;
        R[2] = rz / rl;
        // up = right x direction, so it points towards the zenith.
        U[0] = R[1] * D[2] - R[2] * D[1];
        U[1] = R[2] * D[0] - R[0] * D[2];
        U[2] = R[0] * D[1] - R[1] * D[0];
    }

    /** A texture as a disc of {@code radiusDeg} around the centre, turned by {@code rollDeg}, curved on the dome. */
    static void disc(BufferBuilder b, Matrix4f m, float radiusDeg, float rollDeg, float alpha, float dome) {
        if (alpha <= 0.003f || radiusDeg <= 0.01f) {
            return;
        }
        int angular = 48, radial = 5;
        double roll = Math.toRadians(rollDeg);
        for (int s = 0; s < angular; s++) {
            double a0 = s * Math.PI * 2 / angular, a1 = (s + 1) * Math.PI * 2 / angular;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float p0 = (float) Math.cos(a0 + roll), q0 = (float) Math.sin(a0 + roll);
            float p1 = (float) Math.cos(a1 + roll), q1 = (float) Math.sin(a1 + roll);
            for (int k = 0; k < radial; k++) {
                float r0 = k / (float) radial, r1 = (k + 1) / (float) radial;
                point(b, m, p0 * r0 * radiusDeg, q0 * r0 * radiusDeg, 0.5f + 0.5f * c0 * r0, 0.5f - 0.5f * s0 * r0, alpha, dome);
                point(b, m, p0 * r1 * radiusDeg, q0 * r1 * radiusDeg, 0.5f + 0.5f * c0 * r1, 0.5f - 0.5f * s0 * r1, alpha, dome);
                point(b, m, p1 * r1 * radiusDeg, q1 * r1 * radiusDeg, 0.5f + 0.5f * c1 * r1, 0.5f - 0.5f * s1 * r1, alpha, dome);
                point(b, m, p1 * r0 * radiusDeg, q1 * r0 * radiusDeg, 0.5f + 0.5f * c1 * r0, 0.5f - 0.5f * s1 * r0, alpha, dome);
            }
        }
    }

    /** A small texture at an offset (degrees) from the centre, on a 2x2 grid. */
    static void sprite(BufferBuilder b, Matrix4f m, float x, float y, float halfDeg, float rollDeg, float alpha,
                       float dome) {
        if (alpha <= 0.003f || halfDeg <= 0.01f) {
            return;
        }
        double roll = Math.toRadians(rollDeg);
        float cs = (float) Math.cos(roll), sn = (float) Math.sin(roll);
        int grid = 2;
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                corner(b, m, x, y, halfDeg, cs, sn, i / (float) grid, j / (float) grid, alpha, dome);
                corner(b, m, x, y, halfDeg, cs, sn, (i + 1) / (float) grid, j / (float) grid, alpha, dome);
                corner(b, m, x, y, halfDeg, cs, sn, (i + 1) / (float) grid, (j + 1) / (float) grid, alpha, dome);
                corner(b, m, x, y, halfDeg, cs, sn, i / (float) grid, (j + 1) / (float) grid, alpha, dome);
            }
        }
    }

    private static void corner(BufferBuilder b, Matrix4f m, float x, float y, float half, float cs, float sn, float fx,
                               float fy, float alpha, float dome) {
        float lx = (fx * 2f - 1f) * half, ly = (1f - fy * 2f) * half;
        point(b, m, x + lx * cs - ly * sn, y + lx * sn + ly * cs, fx, fy, alpha, dome);
    }

    private static void point(BufferBuilder b, Matrix4f m, float x, float y, float u, float v, float alpha, float dome) {
        // Rotate the centre by the offset's angle, towards the offset's direction in the tangent plane.
        double angle = Math.toRadians(Math.sqrt(x * x + y * y));
        if (angle < 1.0e-8) {
            P[0] = D[0];
            P[1] = D[1];
            P[2] = D[2];
        } else {
            double inv = 1.0 / Math.sqrt(x * x + y * y);
            double tx = (R[0] * x + U[0] * y) * inv, ty = (R[1] * x + U[1] * y) * inv, tz = (R[2] * x + U[2] * y) * inv;
            double c = Math.cos(angle), sn = Math.sin(angle);
            P[0] = (float) (D[0] * c + tx * sn);
            P[1] = (float) (D[1] * c + ty * sn);
            P[2] = (float) (D[2] * c + tz * sn);
        }
        b.vertex(m, P[0] * dome, P[1] * dome, P[2] * dome).uv(u, v)
                .color(tr, tg, tb, Math.max(0f, Math.min(1f, alpha))).endVertex();
    }
}
