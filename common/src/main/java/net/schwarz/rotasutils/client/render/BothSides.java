package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
final class BothSides {
    private final float[] buf = new float[4 * 7];
    private int count;

    void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        int o = count * 7;
        buf[o] = x;
        buf[o + 1] = y;
        buf[o + 2] = z;
        buf[o + 3] = r;
        buf[o + 4] = g;
        buf[o + 5] = b;
        buf[o + 6] = a < 0f ? 0f : Math.min(1f, a);
        if (++count < 4) {
            return;
        }
        count = 0;
        for (int i = 0; i < 4; i++) {
            emit(vc, m, i);
        }
    }

    private void emit(VertexConsumer vc, Matrix4f m, int i) {
        int o = i * 7;
        vc.vertex(m, buf[o], buf[o + 1], buf[o + 2]).color(buf[o + 3], buf[o + 4], buf[o + 5], buf[o + 6]).endVertex();
    }
}
