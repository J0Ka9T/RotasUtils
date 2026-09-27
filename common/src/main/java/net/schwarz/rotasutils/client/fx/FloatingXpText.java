package net.schwarz.rotasutils.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Floating XP numbers that appear when XP is gained. Different colors per source.
 */
@Environment(EnvType.CLIENT)
public final class FloatingXpText {
    private static final Deque<FloatingXp> floatingXpList = new ArrayDeque<>();
    private static final int MAX_VISIBLE = 12;

    private FloatingXpText() {
    }

    /**
     * Add a floating XP number. sourceKey determines color:
     * mob_kill=green, boss_kill=red, quest=blue, crafting=orange, default=gold.
     */
    public static void add(long amount, String sourceKey, int screenX, int screenY) {
        if (amount <= 0 || floatingXpList.size() >= MAX_VISIBLE) {
            return;
        }
        int color = switch (sourceKey) {
            case "mob" -> 0xFF67D9A5;
            case "boss" -> 0xFFFF7181;
            case "quest" -> 0xFF7FC6D8;
            case "craft" -> 0xFFFFC76D;
            case "mine" -> 0xFF8DCAEE;
            case "smelt" -> 0xFFFF9F44;
            default -> 0xFFFFE066;
        };
        floatingXpList.add(new FloatingXp(amount, color, screenX, screenY));
    }

    public static void render(GuiGraphics graphics) {
        floatingXpList.removeIf(FloatingXp::isDone);
        for (FloatingXp fx : floatingXpList) {
            fx.render(graphics);
        }
    }

    public static void tick() {
        floatingXpList.removeIf(FloatingXp::isDone);
    }

    private static class FloatingXp {
        private static final int DURATION = 60; // 3 seconds
        private final long amount;
        private final int color;
        private final int startX;
        private final int startY;
        private int age;

        FloatingXp(long amount, int color, int startX, int startY) {
            this.amount = amount;
            this.color = color;
            this.startX = startX;
            this.startY = startY;
            this.age = 0;
        }

        boolean isDone() {
            return age >= DURATION;
        }

        void render(GuiGraphics graphics) {
            float progress = age / (float) DURATION;
            float alpha = 1f - progress;
            float yOffset = -progress * 40;

            int currentX = startX;
            int currentY = (int) (startY + yOffset);
            int textAlpha = (int) (255 * alpha);

            String text = "+" + amount + " XP";
            // Subtle glow
            graphics.drawString(Minecraft.getInstance().font, text,
                    currentX - 1, currentY, (textAlpha / 4 << 24) | (color & 0x00FFFFFF), false);
            graphics.drawString(Minecraft.getInstance().font, text,
                    currentX + 1, currentY, (textAlpha / 4 << 24) | (color & 0x00FFFFFF), false);
            // Main text
            graphics.drawString(Minecraft.getInstance().font, text,
                    currentX, currentY, (textAlpha << 24) | (color & 0x00FFFFFF), false);

            age++;
        }
    }
}
