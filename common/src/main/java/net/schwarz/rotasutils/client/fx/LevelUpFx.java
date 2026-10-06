package net.schwarz.rotasutils.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class LevelUpFx {
    private static LevelUpEffect activeEffect;

    private LevelUpFx() {
    }

    public static void trigger(int newLevel, int prestige) {
        activeEffect = new LevelUpEffect(newLevel, prestige);
    }

    public static void render(GuiGraphics graphics) {
        if (activeEffect == null || activeEffect.isDone()) {
            activeEffect = null;
            return;
        }
        activeEffect.render(graphics);
    }

    public static void tick() {
        if (activeEffect != null && activeEffect.isDone()) {
            activeEffect = null;
        }
        if (activeEffect != null) {
            activeEffect.tick();
        }
    }

    public static boolean isActive() {
        return activeEffect != null && !activeEffect.isDone();
    }

    private static class LevelUpEffect {
        private static final int TOTAL_DURATION = 120;
        private final int level;
        private final int prestige;
        private int age;

        LevelUpEffect(int level, int prestige) {
            this.level = level;
            this.prestige = prestige;
            this.age = 0;
        }

        void tick() {
            age++;
        }

        boolean isDone() {
            return age >= TOTAL_DURATION;
        }

        void render(GuiGraphics graphics) {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            float progress = Math.min(1f, age / (float) TOTAL_DURATION);

            if (progress < 0.1f) {
                float flashAlpha = 1f - progress / 0.1f;
                graphics.fill(0, 0, w, h, ((int) (flashAlpha * 80) << 24) | 0xFFD4A017);
            }

            if (progress >= 0.1f && progress < 0.4f) {
                float ringProgress = (progress - 0.1f) / 0.3f;
                float radius = ringProgress * Math.max(w, h) * 0.6f;
                int alpha = (int) (200 * (1f - ringProgress));
                drawRing(graphics, w / 2, h / 2, (int) radius, 3, alpha);
            }

            if (progress >= 0.2f && progress < 0.8f) {
                float textProgress = (progress - 0.2f) / 0.6f;
                float slideY = Mth.clamp(textProgress * 1.5f, 0f, 1f);
                if (textProgress > 0.8f) {
                    slideY *= 1f - (textProgress - 0.8f) / 0.2f;
                }
                int textAlpha = (int) (255 * Math.min(1f, textProgress * 3f) * slideY);
                int textY = (int) (h / 2 - 40 + (1f - slideY) * -60);

                for (int i = 3; i >= 1; i--) {
                    int glowAlpha = textAlpha / (i * 2);
                    graphics.drawString(mc.font, net.schwarz.rotasutils.client.screen.L.t("rotasutils.fx.level_up"),
                            w / 2 - mc.font.width(net.schwarz.rotasutils.client.screen.L.t("rotasutils.fx.level_up")) / 2, textY,
                            (glowAlpha << 24) | 0xFFD4A017, false);
                }
                graphics.drawString(mc.font, net.schwarz.rotasutils.client.screen.L.t("rotasutils.fx.level_up"),
                        w / 2 - mc.font.width(net.schwarz.rotasutils.client.screen.L.t("rotasutils.fx.level_up")) / 2, textY,
                        (textAlpha << 24) | 0xFFFFE066, false);

                String levelText = net.schwarz.rotasutils.client.screen.L.t("rotasutils.fx.level", level);
                if (prestige > 0) {
                    levelText += " ★".repeat(Math.min(prestige, 10));
                }
                int levelAlpha = textAlpha;
                int levelY = textY + 22;
                graphics.drawString(mc.font, levelText,
                        w / 2 - mc.font.width(levelText) / 2, levelY,
                        (levelAlpha << 24) | 0xFFE7BE76, false);
            }

            if (progress >= 0.15f && progress < 0.5f) {
                float particleProgress = (progress - 0.15f) / 0.35f;
                int particleAlpha = (int) (180 * (1f - Math.abs(particleProgress - 0.5f) * 2f));
                drawParticles(graphics, w, h, particleAlpha);
            }
        }

        private void drawRing(GuiGraphics graphics, int cx, int cy, int radius, int thickness, int alpha) {
            if (radius <= 0 || alpha <= 0) return;
            int color = (alpha << 24) | 0xFFD4A017;
            for (int i = 0; i < 360; i += 3) {
                double angle = Math.toRadians(i);
                int x = (int) (cx + Math.cos(angle) * radius);
                int y = (int) (cy + Math.sin(angle) * radius);
                graphics.fill(x - thickness / 2, y - thickness / 2,
                        x + thickness / 2, y + thickness / 2, color);
            }
        }

        private void drawParticles(GuiGraphics graphics, int w, int h, int alpha) {
            int count = 40;
            for (int i = 0; i < count; i++) {
                long seed = i * 7919 + age * 13;
                float angle = (seed % 360) * 0.0174533f;
                float dist = 50 + (seed % 200);
                int cx = w / 2;
                int cy = h / 2;
                int px = cx + (int) (Math.cos(angle) * dist);
                int py = cy + (int) (Math.sin(angle) * dist) - 20;
                int size = 1 + (i % 3);
                int particleAlpha = alpha * (60 + (i % 40)) / 100;
                int color = (particleAlpha << 24) | (i % 3 == 0 ? 0xFFFFE066 : 0xFFE7BE76);
                graphics.fill(px, py, px + size, py + size, color);
            }
        }
    }
}
