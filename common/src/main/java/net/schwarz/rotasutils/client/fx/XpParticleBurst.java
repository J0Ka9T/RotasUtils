package net.schwarz.rotasutils.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small particle burst that rises from the XP bar when XP is gained.
 * Complements FloatingXpText with visual sparkle.
 */
@Environment(EnvType.CLIENT)
public final class XpParticleBurst {
    private static final Deque<XpParticle> particles = new ArrayDeque<>();
    private static final int MAX_PARTICLES = 60;

    private XpParticleBurst() {
    }

    /**
     * Spawn a burst of particles at the given position.
     * {@code count} particles, {@code spread} horizontal spread, {@code rise} max vertical rise.
     */
    public static void spawn(int centerX, int centerY, int count, int spread, int rise, int color) {
        for (int i = 0; i < count && particles.size() < MAX_PARTICLES; i++) {
            particles.add(new XpParticle(centerX, centerY, spread, rise, color));
        }
    }

    public static void render(GuiGraphics graphics) {
        particles.removeIf(XpParticle::isDone);
        for (XpParticle p : particles) {
            p.render(graphics);
        }
    }

    public static void tick() {
        particles.removeIf(XpParticle::isDone);
    }

    private static class XpParticle {
        private static final int DURATION = 40;
        private final int startX;
        private final int startY;
        private final float dx;
        private final float dy;
        private final int color;
        private final int size;
        private int age;

        XpParticle(int startX, int startY, int spread, int rise, int color) {
            this.startX = startX;
            this.startY = startY;
            this.dx = (float) ((Math.random() - 0.5) * spread);
            this.dy = -rise * (0.5f + (float) Math.random() * 0.5f);
            this.color = color;
            this.size = 1 + (int) (Math.random() * 2);
            this.age = 0;
        }

        boolean isDone() {
            return age >= DURATION;
        }

        void render(GuiGraphics graphics) {
            float progress = age / (float) DURATION;
            float alpha = 1f - progress;
            float easedX = dx * progress;
            float easedY = dy * progress * progress; // accelerate upward

            int px = startX + (int) easedX;
            int py = startY + (int) easedY;
            int textAlpha = (int) (200 * alpha);

            if (textAlpha > 0) {
                graphics.fill(px, py, px + size, py + size,
                        (textAlpha << 24) | (color & 0x00FFFFFF));
            }
            age++;
        }
    }
}
