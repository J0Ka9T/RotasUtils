package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Environment(EnvType.CLIENT)
final class Paint {
    static final ResourceLocation GLOW = tex("glow"), FLARE = tex("flare"), RUNES = tex("sigil_runes"), SEAL = tex("sigil_star"),
            SHOCK = tex("shock"), STREAK = tex("streak"), CLOUD = tex("cloud"), NEBULA = tex("nebula"),
            HULL = gun("hull"), HULL_GLOW = gun("hull_glow"), GOLD = gun("gold"), IVORY = gun("ivory");

    private static ResourceLocation gun(String name) {
        return Rotasutils.id("textures/vfx/stargun/" + name + ".png");
    }

    private static ResourceLocation tex(String name) {
        return Rotasutils.id("textures/vfx/celestial/" + name + ".png");
    }

    private final Matrix4f matrix;
    final Vec3 camera, left, up;
    private final Map<ResourceLocation, FloatArrayList> queued = new LinkedHashMap<>();
    private FloatArrayList into;

    Paint(Matrix4f matrix, Vec3 camera, Vec3 left, Vec3 up) {
        this.matrix = matrix;
        this.camera = camera;
        this.left = left;
        this.up = up;
    }

    Paint on(ResourceLocation texture) {
        into = queued.computeIfAbsent(texture, k -> new FloatArrayList(1024));
        return this;
    }

    private void v(Vec3 p, double u, double v, float[] c, double alpha) {
        into.add((float) (p.x - camera.x));
        into.add((float) (p.y - camera.y));
        into.add((float) (p.z - camera.z));
        into.add((float) u);
        into.add((float) v);
        into.add(c[0]);
        into.add(c[1]);
        into.add(c[2]);
        into.add((float) Math.max(0, Math.min(1, c[3] * alpha)));
    }

    void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double u0, double v0, double u1, double v1, float[] ca, float[] cb, float[] cc, float[] cd) {
        v(a, u0, v0, ca, 1);
        v(b, u1, v0, cb, 1);
        v(c, u1, v1, cc, 1);
        v(d, u0, v1, cd, 1);
    }

    void corner(Vec3 p, double u, double v, float r, float g, float b, float a) {
        into.add((float) (p.x - camera.x));
        into.add((float) (p.y - camera.y));
        into.add((float) (p.z - camera.z));
        into.add((float) u);
        into.add((float) v);
        into.add(r);
        into.add(g);
        into.add(b);
        into.add(Math.max(0f, Math.min(1f, a)));
    }

    void plane(Vec3 centre, Vec3 u, Vec3 w, double radius, double spin, float[] col) {
        plane(centre, u, w, radius, spin, col, 1, null);
    }

    void plane(Vec3 centre, Vec3 u, Vec3 w, double radius, double spin, float[] col, int grid, ToDoubleFunction<Vec3> mask) {
        if (col[3] <= 0.004f || radius <= 0.01) {
            return;
        }
        double cs = Math.cos(spin) * radius, sn = Math.sin(spin) * radius;
        Vec3 ax = u.scale(cs).add(w.scale(sn)), ay = w.scale(cs).subtract(u.scale(sn));
        Vec3[] p = new Vec3[(grid + 1) * (grid + 1)];
        double[] m = new double[p.length];
        for (int j = 0; j <= grid; j++) {
            for (int i = 0; i <= grid; i++) {
                int k = j * (grid + 1) + i;
                p[k] = centre.add(ax.scale(2.0 * i / grid - 1)).add(ay.scale(1 - 2.0 * j / grid));
                m[k] = mask == null ? 1 : mask.applyAsDouble(p[k]);
            }
        }
        for (int j = 0; j < grid; j++) {
            for (int i = 0; i < grid; i++) {
                int a = j * (grid + 1) + i, b = a + 1, d = a + grid + 1, c = d + 1;
                if (m[a] + m[b] + m[c] + m[d] <= 0.004) {
                    continue;
                }
                double u0 = (double) i / grid, u1 = (double) (i + 1) / grid, v0 = (double) j / grid, v1 = (double) (j + 1) / grid;
                v(p[a], u0, v0, col, m[a]);
                v(p[b], u1, v0, col, m[b]);
                v(p[c], u1, v1, col, m[c]);
                v(p[d], u0, v1, col, m[d]);
            }
        }
    }

    void billboard(Vec3 centre, double size, double roll, float[] col) {
        if (col[3] <= 0.004f || size <= 0.001) {
            return;
        }
        plane(centre, left, up, size, roll, col);
    }

    void strip(Vec3 a, Vec3 b, double w0, double w1, double u0, double u1, float[] c0, float[] c1) {
        if (c0[3] <= 0.004f && c1[3] <= 0.004f) {
            return;
        }
        Vec3 side = b.subtract(a).cross(a.add(b).scale(0.5).subtract(camera));
        if (side.lengthSqr() < 1.0e-12) {
            return;
        }
        side = side.normalize();
        v(a.subtract(side.scale(w0)), u0, 0, c0, 1);
        v(b.subtract(side.scale(w1)), u1, 0, c1, 1);
        v(b.add(side.scale(w1)), u1, 1, c1, 1);
        v(a.add(side.scale(w0)), u0, 1, c0, 1);
    }

    void flush() {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (var entry : queued.entrySet()) {
            FloatArrayList data = entry.getValue();
            if (data.isEmpty()) {
                continue;
            }
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(entry.getKey());
            texture.setFilter(true, false);
            GlStateManager._bindTexture(texture.getId());
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            RenderSystem.setShaderTexture(0, texture.getId());
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float[] f = data.elements();
            for (int i = 0, n = data.size(); i < n; i += 9) {
                buffer.vertex(matrix, f[i], f[i + 1], f[i + 2]).uv(f[i + 3], f[i + 4]).color(f[i + 5], f[i + 6], f[i + 7], f[i + 8]).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
            data.clear();
        }
    }
}
