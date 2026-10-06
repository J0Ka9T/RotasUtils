package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.schwarz.rotasutils.client.render.VfxRenderTypes;

final class SwordSprite {
    static final ResourceLocation SWORD =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/sword/spirit_sword.png");
    static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/sword/spirit_sword_glow.png");
    private static final float GUARD_V = .75f, TIP_V = 4f / 256f;
    private static final float ASPECT = 64f / 256f;

    private SwordSprite() {
    }

    static void draw(PoseStack pose, MultiBufferSource output, double blade, int tint, float alpha, int glow,
                     float glowAlpha, boolean crossed) {
        if (alpha <= .01f) return;
        double length = blade / (GUARD_V - TIP_V);
        double top = blade + TIP_V * length, bottom = -(1 - GUARD_V) * length;
        double half = length * ASPECT * .5;
        VertexConsumer steel = output.getBuffer(VfxRenderTypes.sprite(SWORD));
        VertexConsumer light = output.getBuffer(VfxRenderTypes.lightTextured(GLOW));
        plane(pose, light, half * 1.15, bottom, top + length * .02, 0, glow, alpha * glowAlpha);
        plane(pose, steel, half, bottom, top, 0, tint, alpha);
        if (crossed) {
            plane(pose, light, half * 1.15, bottom, top + length * .02, 1, glow, alpha * glowAlpha * .7f);
            plane(pose, steel, half, bottom, top, 1, shade(tint, .82f), alpha);
        }
    }

    private static void plane(PoseStack p, VertexConsumer v, double half, double bottom, double top, int axis, int c,
                              float a) {
        if (a <= .01f) return;
        double x = axis == 0 ? half : 0, z = axis == 0 ? 0 : half;
        vertex(p, v, -x, bottom, -z, 0, 1, c, a);
        vertex(p, v, x, bottom, z, 1, 1, c, a);
        vertex(p, v, x, top, z, 1, 0, c, a);
        vertex(p, v, -x, top, -z, 0, 0, c, a);
    }

    private static int shade(int c, float k) {
        return ((int) (((c >> 16) & 255) * k) << 16) | ((int) (((c >> 8) & 255) * k) << 8) | (int) ((c & 255) * k);
    }

    private static void vertex(PoseStack p, VertexConsumer v, double x, double y, double z, float u, float vv, int c,
                               float a) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z).uv(u, vv)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }
}
