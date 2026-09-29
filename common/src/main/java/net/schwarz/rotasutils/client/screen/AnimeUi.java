package net.schwarz.rotasutils.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;

/**
 * The look of the forge and altar screens: soft rounded panels, glossy buttons and gauges, hexagonal
 * sockets, glowing rays behind the item. Every shape is a painted, anti-aliased texture (see
 * {@code scripts/gen_ui_textures.py}) drawn nine-slice at its native size, so corners stay round and
 * nothing is a stepped rectangle. The rim, button and fill textures are white and tinted per use.
 */
@Environment(EnvType.CLIENT)
public final class AnimeUi {
    private AnimeUi() {
    }

    private static ResourceLocation tex(String name) {
        return Rotasutils.id("textures/gui/anime/" + name + ".png");
    }

    private static final ResourceLocation PANEL_BG = tex("panel_bg");
    private static final ResourceLocation PANEL_FRAME = tex("panel_frame");
    private static final ResourceLocation PANEL_INLAY = tex("panel_inlay");
    private static final ResourceLocation BUTTON = tex("button_body");
    private static final ResourceLocation BUTTON_TRIM = tex("button_trim");
    private static final ResourceLocation TROUGH = tex("trough");
    private static final ResourceLocation FILL = tex("fill");
    private static final ResourceLocation CARD = tex("card");
    private static final ResourceLocation CARD_RIM = tex("card_rim");
    private static final ResourceLocation SOCKET_BG = tex("socket_bg");
    private static final ResourceLocation SOCKET_RING = tex("socket_ring");
    private static final ResourceLocation SOCKET_INLAY = tex("socket_inlay");
    private static final ResourceLocation GLOW = tex("glow");
    private static final ResourceLocation RAYS = tex("rays");
    private static final ResourceLocation SPARK = tex("spark");
    private static final ResourceLocation RIBBON = tex("ribbon");
    private static final ResourceLocation RIBBON_TRIM = tex("ribbon_trim");

    public static final int INK = 0xFF0A0810;
    public static final int NIGHT = 0xFF14101C;
    public static final int PANEL = 0xFF1E1A2A;
    public static final int PANEL_LIGHT = 0xFF4A4468;
    public static final int GOLD = 0xFFE3B65A;
    public static final int EMBER = 0xFFE0702E;
    public static final int STEEL = 0xFF6FB3D6;
    public static final int LIME = 0xFF88BB6A;
    public static final int WHITE = 0xFFF4EEE0;
    public static final int MUTED = 0xFF9C94B4;
    public static final int RED = 0xFFD9584A;

    public static float time() {
        return (Util.getMillis() % 100000L) / 1000f;
    }

    public static int alpha(int color, float a) {
        int v = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * a)));
        return (v << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // Texture drawing --------------------------------------------------------------------------------

    private static void tint(int argb) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f);
    }

    private static void untint() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Draws {@code texture} into the box with fixed-size corners and stretched edges and middle. */
    private static void slice(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int corner, int tw, int th) {
        int cx = Math.min(corner, w / 2), cy = Math.min(corner, h / 2);
        int mw = w - 2 * cx, mh = h - 2 * cy;
        int smw = tw - 2 * corner, smh = th - 2 * corner;
        g.blit(texture, x, y, cx, cy, 0, 0, corner, corner, tw, th);
        g.blit(texture, x + w - cx, y, cx, cy, tw - corner, 0, corner, corner, tw, th);
        g.blit(texture, x, y + h - cy, cx, cy, 0, th - corner, corner, corner, tw, th);
        g.blit(texture, x + w - cx, y + h - cy, cx, cy, tw - corner, th - corner, corner, corner, tw, th);
        if (mw > 0) {
            g.blit(texture, x + cx, y, mw, cy, corner, 0, smw, corner, tw, th);
            g.blit(texture, x + cx, y + h - cy, mw, cy, corner, th - corner, smw, corner, tw, th);
        }
        if (mh > 0) {
            g.blit(texture, x, y + cy, cx, mh, 0, corner, corner, smh, tw, th);
            g.blit(texture, x + w - cx, y + cy, cx, mh, tw - corner, corner, corner, smh, tw, th);
        }
        if (mw > 0 && mh > 0) {
            g.blit(texture, x + cx, y + cy, mw, mh, corner, corner, smw, smh, tw, th);
        }
    }

    private static void tinted(GuiGraphics g, ResourceLocation texture, int color, int x, int y, int w, int h, int corner, int tw, int th) {
        tint(color);
        slice(g, texture, x, y, w, h, corner, tw, th);
        untint();
    }

    /** A soft shape-less light: a radial glow of {@code color}, centred, {@code size} across. */
    public static void glow(GuiGraphics g, int cx, int cy, int size, int color) {
        tint(color);
        g.blit(GLOW, cx - size / 2, cy - size / 2, size, size, 0, 0, 128, 128, 128, 128);
        untint();
    }

    public static void spark(GuiGraphics g, int cx, int cy, int size, int color) {
        tint(color);
        g.blit(SPARK, cx - size / 2, cy - size / 2, size, size, 0, 0, 32, 32, 32, 32);
        untint();
    }

    // Components -------------------------------------------------------------------------------------

    /** A dark stone slab in a worked gold frame, with an inlaid line in the accent colour. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        tinted(g, PANEL_BG, 0x88000000, x - 2, y + 3, w + 4, h + 4, 16, 64, 64);
        slice(g, PANEL_BG, x, y, w, h, 16, 64, 64);
        tinted(g, PANEL_INLAY, alpha(accent, 0.75f), x, y, w, h, 16, 64, 64);
        slice(g, PANEL_FRAME, x, y, w, h, 16, 64, 64);
    }

    /** A smaller inset plate with a thin bronze edge, lit in the accent colour when selected. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int accent, boolean lit) {
        if (lit) {
            glow(g, x + w / 2, y + h / 2, Math.max(w, h) + 30, alpha(accent, 0.35f));
        }
        slice(g, CARD, x, y, w, h, 8, 48, 48);
        tinted(g, CARD_RIM, lit ? accent : alpha(accent, 0.4f), x, y, w, h, 8, 48, 48);
    }

    /** Light rays turning slowly behind an item, over a soft glow. */
    public static void burst(GuiGraphics g, int cx, int cy, int radius, int color, float spin) {
        glow(g, cx, cy, radius * 2, alpha(color, 0.4f));
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(time() * spin));
        tint(alpha(color, 0.4f));
        g.blit(RAYS, -radius, -radius, radius * 2, radius * 2, 0, 0, 256, 256, 256, 256);
        untint();
        g.pose().popPose();
    }

    /** An item drawn large, bobbing a little. */
    public static void bigItem(GuiGraphics g, ItemStack stack, int cx, int cy, float scale, boolean bob) {
        float dy = bob ? (float) Math.sin(time() * 2.2f) * 3f : 0f;
        g.pose().pushPose();
        g.pose().translate(cx, cy + dy, 100);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    /** Text with a soft drop shadow. */
    public static void outlined(GuiGraphics g, String text, int x, int y, float scale, int color) {
        Ui.scaledLabel(g, text, x + 1, y + 1, scale, 0xCC000000);
        Ui.scaledLabel(g, text, x, y, scale, color);
    }

    public static void outlinedCentered(GuiGraphics g, String text, int cx, int y, float scale, int color) {
        outlined(g, text, cx - (int) (Ui.scaledWidth(text, scale) / 2), y, scale, color);
    }

    /** A glossy pill gauge: dark trough, a tinted fill with a highlight along its top. */
    public static void gauge(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        slice(g, TROUGH, x, y, w, h, 6, 32, 16);
        int fill = (int) (w * Math.max(0f, Math.min(1f, frac)));
        if (fill > 0) {
            tinted(g, FILL, color, x, y, Math.max(fill, Math.min(w, 12)), h, 6, 32, 16);
        }
    }

    /** A glossy coloured band inside a gauge, for zones and bonus segments. */
    public static void band(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w > 1) {
            tinted(g, FILL, color, x, y, w, h, 6, 32, 16);
        }
    }

    /** A hexagonal socket: a dark well, a glow of {@code lit} behind it when filled, and a tinted bevel ring. */
    public static void socket(GuiGraphics g, int cx, int cy, int size, int lit, int ring) {
        int x = cx - size / 2, y = cy - size / 2;
        g.blit(SOCKET_BG, x, y, size, size, 0, 0, 64, 64, 64, 64);
        if ((lit >>> 24) != 0) {
            glow(g, cx, cy, (int) (size * 0.9f), lit);
        }
        g.blit(SOCKET_RING, x, y, size, size, 0, 0, 64, 64, 64, 64);
        tint(ring);
        g.blit(SOCKET_INLAY, x, y, size, size, 0, 0, 64, 64, 64, 64);
        untint();
    }

    /** A thin soft line between two points. */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int thickness, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.fill(0, -thickness / 2, (int) len, thickness - thickness / 2, color);
        g.pose().popPose();
    }

    /** A slanted ribbon behind big banner text. */
    public static void ribbon(GuiGraphics g, int x, int y, int w, int h, int color) {
        tinted(g, RIBBON, color, x, y, w, h, 14, 96, 32);
        slice(g, RIBBON_TRIM, x, y, w, h, 14, 96, 32);
    }

    /** A glossy rounded button: lifts and brightens on hover, greys out when inactive. */
    public static final class Btn extends Button {
        private final int color;
        private final float textScale;

        public Btn(int x, int y, int w, int h, String label, int color, float textScale, OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.color = color;
            this.textScale = textScale;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            int lift = hot ? -1 : 0;
            int base = active ? (hot ? mix(color, WHITE, 0.22f) : color) : 0xFF4A4658;
            tinted(g, BUTTON, 0x88000000, getX(), getY() + 3, width, height, 8, 48, 32);
            tinted(g, BUTTON, base, getX(), getY() + lift, width, height, 8, 48, 32);
            g.pose().pushPose();
            g.pose().translate(0, lift, 0);
            slice(g, BUTTON_TRIM, getX(), getY(), width, height, 8, 48, 32);
            g.pose().popPose();
            String label = getMessage().getString();
            int textColor = active ? (isDark(base) ? WHITE : 0xFF241808) : 0xFF8A849C;
            float ws = Ui.scaledWidth(label, textScale);
            int tx = getX() + (int) ((width - ws) / 2);
            int ty = getY() + lift + (int) ((height - 8 * Math.max(1f, (int) textScale)) / 2) + 1;
            if (active && !isDark(base)) {
                Ui.scaledLabel(g, label, tx, ty + 1, textScale, alpha(WHITE, 0.55f));
            }
            Ui.scaledLabel(g, label, tx, ty, textScale, textColor);
        }

        private static boolean isDark(int c) {
            return ((c >> 16) & 0xFF) * 0.3 + ((c >> 8) & 0xFF) * 0.59 + (c & 0xFF) * 0.11 < 140;
        }
    }
}
