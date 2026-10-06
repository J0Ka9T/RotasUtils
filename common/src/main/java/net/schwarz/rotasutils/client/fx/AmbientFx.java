package net.schwarz.rotasutils.client.fx;

import com.schwarz.lenlorui.ui.ModernUiRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public final class AmbientFx {
    private static final int[][] noisePattern = new int[16][16];
    private static boolean noiseInitialized;
    private static int noiseOffsetX;
    private static int noiseOffsetY;
    private static long lastNoiseUpdate;

    private AmbientFx() {
    }

    public static void renderGrain(GuiGraphics graphics, int x, int y, int width, int height) {
        if (!noiseInitialized) {
            initNoise();
            noiseInitialized = true;
        }

        long now = System.currentTimeMillis();
        if (now - lastNoiseUpdate > 100) {
            noiseOffsetX = (int) (Math.random() * 16);
            noiseOffsetY = (int) (Math.random() * 16);
            lastNoiseUpdate = now;
        }

        for (int gy = y; gy < y + height; gy += 4) {
            for (int gx = x; gx < x + width; gx += 4) {
                int noiseVal = noisePattern[(gx + noiseOffsetX) & 15][(gy + noiseOffsetY) & 15];
                if (noiseVal > 200) {
                    graphics.fill(gx, gy, gx + 2, gy + 2, 0x0E2A2015);
                }
            }
        }
    }

    public static void renderVignette(GuiGraphics graphics, int x, int y, int width, int height, int alpha) {
        int edgeSize = Math.min(20, Math.min(width, height) / 4);
        for (int i = 0; i < edgeSize; i++) {
            int a = (int) (alpha * (1f - i / (float) edgeSize));
            if (a > 0) {
                graphics.fill(x, y + i, x + width, y + i + 1, (a << 24));
            }
        }
        for (int i = 0; i < edgeSize; i++) {
            int a = (int) (alpha * (1f - i / (float) edgeSize));
            if (a > 0) {
                graphics.fill(x, y + height - i - 1, x + width, y + height - i, (a << 24));
            }
        }
    }

    public static void renderXpGlow(GuiGraphics graphics, int x, int y, int width, int height,
                                     float xpFraction, int accentColor) {
        if (xpFraction < 0.8f || xpFraction >= 1f) {
            return;
        }

        long now = System.currentTimeMillis();
        float pulse = (float) (Math.sin(now * 0.005) * 0.5 + 0.5);
        int glowAlpha = (int) (30 * pulse * ((xpFraction - 0.8f) / 0.2f));

        if (glowAlpha > 0) {
            ModernUiRenderer.softShadow(graphics, x - 2, y - 2, width + 4, height + 4, 6,
                    (glowAlpha << 24) | (accentColor & 0x00FFFFFF));
        }
    }

    private static void initNoise() {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                noisePattern[i][j] = (int) (Math.random() * 256);
            }
        }
    }
}
