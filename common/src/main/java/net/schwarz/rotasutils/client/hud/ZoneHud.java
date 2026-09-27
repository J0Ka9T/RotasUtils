package net.schwarz.rotasutils.client.hud;

import com.schwarz.lenlorui.ui.UiCanvas;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.schwarz.rotasutils.client.ClientZoneView;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.util.ThaiText;

/**
 * Where the player is, at a glance.
 *
 * <p><b>Zone chip</b>: a slim panel on the right edge, just above the quest tracker, naming the zone the
 * player stands in with its level band and danger. A spine down the leading edge carries the danger
 * colour, so Safe/Normal/Dangerous/Deadly read without the words. Nothing is shown in the wilderness or
 * for zones that switched their chip off.</p>
 *
 * <p><b>Entry banner</b>: crossing into a zone fades in its name in the upper third of the screen, flanked
 * by danger-coloured rules, with the band and recommended level beneath; leaving a zone for the wilderness
 * shows a quieter one-line "Leaving" note. Zones with their own admin-written enter title keep that title
 * (sent by the server as a vanilla title) and skip the banner, so the player never sees both.</p>
 *
 * <p>Both draw inside the RPG HUD's locked-scale pose, beside the quest tracker, so they keep the HUD's
 * size at any GUI scale.</p>
 */
@Environment(EnvType.CLIENT)
public final class ZoneHud {
    private static final int MARGIN = 8;
    private static final int CHIP_W = 150;
    private static final int CHIP_H = 26;
    private static final long FADE_IN_MS = 350;
    private static final long HOLD_MS = 2400;
    private static final long FADE_OUT_MS = 700;
    private static final long LEAVE_MS = 1700;

    private static ZoneDef bannerZone;
    private static boolean bannerLeaving;
    private static long bannerStart = Long.MIN_VALUE;

    private ZoneHud() {
    }

    /** Danger colour shared with the approach borders. */
    public static int dangerColor(ZoneDef.Danger danger) {
        return switch (danger) {
            case SAFE -> 0xFF7BD88F;
            case NORMAL -> 0xFFB9D3F0;
            case DANGEROUS -> 0xFFFFA64D;
            case DEADLY -> 0xFFFF5A5A;
        };
    }

    public static final int LOCKED_COLOR = 0xFFFF4D6A;

    /** Called by {@link ClientZoneView} when the player crosses from one zone (or the wilderness) to another. */
    public static void onZoneChanged(ZoneDef previous, ZoneDef current) {
        if (current != null) {
            if (current.features().display().banner() && !current.features().messages().hasEnter()) {
                bannerZone = current;
                bannerLeaving = false;
                bannerStart = System.currentTimeMillis();
            }
        } else if (previous != null && previous.features().display().banner()
                && previous.features().messages().leaveTitle().isEmpty()) {
            bannerZone = previous;
            bannerLeaving = true;
            bannerStart = System.currentTimeMillis();
        }
    }

    public static void clear() {
        bannerZone = null;
        bannerStart = Long.MIN_VALUE;
    }

    /** Draws inside the locked HUD pose; {@code width}/{@code height} are the locked grid size. */
    public static void render(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.HUD)) {
            ZoneDef current = ClientZoneView.current();
            if (current != null && current.features().display().hud()) {
                chip(ui, font, current, width, height);
            }
            banner(ui, font, width, height);
        }
    }

    private static void chip(UiCanvas ui, Font font, ZoneDef zone, int width, int height) {
        int x = width - MARGIN - CHIP_W;
        // Hang just above the quest tracker, which starts a quarter of the way down the right edge.
        int y = Math.max(MARGIN, height / 4 - CHIP_H - 4);
        int color = dangerColor(zone.danger());
        ui.shadow(x, y, CHIP_W, CHIP_H, 4, 0x602A2015);
        ui.borderedRoundedRect(x, y, CHIP_W, CHIP_H, 4, RotasTheme.HUD_PANEL_EDGE, RotasTheme.HUD_PANEL);
        ui.roundedRect(x + 2, y + 4, 2, CHIP_H - 8, 1, color);
        String band = zone.levelLabel();
        String name = trim(font, displayName(zone), CHIP_W - 16 - font.width(band) - 6);
        text(ui, font, name, x + 8, y + 4, RotasTheme.HUD_TEXT);
        text(ui, font, band, x + CHIP_W - 6 - font.width(band), y + 4, RotasTheme.HUD_ACCENT);
        String detail = zone.safe() ? ThaiText.t("rotasutils.hud.zone.safe") : zone.danger().label();
        text(ui, font, trim(font, detail, CHIP_W - 14), x + 8, y + 15, color);
    }

    private static void banner(UiCanvas ui, Font font, int width, int height) {
        ZoneDef zone = bannerZone;
        if (zone == null) {
            return;
        }
        long age = System.currentTimeMillis() - bannerStart;
        long total = bannerLeaving ? LEAVE_MS : FADE_IN_MS + HOLD_MS + FADE_OUT_MS;
        if (age < 0 || age > total) {
            bannerZone = null;
            return;
        }
        float alpha = age < FADE_IN_MS ? age / (float) FADE_IN_MS
                : Math.min(1f, (total - age) / (float) FADE_OUT_MS);
        alpha = Mth.clamp(alpha, 0f, 1f);
        if (alpha < 0.03f) {
            return;
        }
        int cx = width / 2;
        int y = (int) (height * 0.2f) - Math.round((1f - Math.min(1f, age / (float) FADE_IN_MS)) * 6f);
        int color = dangerColor(zone.danger());
        GuiGraphics graphics = ui.graphics();
        if (bannerLeaving) {
            String line = ThaiText.t("rotasutils.hud.zone.leaving", displayName(zone));
            ui.flush();
            graphics.drawCenteredString(font, line, cx, y, fade(RotasTheme.HUD_TEXT_MUTED, alpha));
            return;
        }
        String name = displayName(zone);
        String sub = zone.levelLabel() + "  ·  " + (zone.safe() ? ThaiText.t("rotasutils.hud.zone.safe") : zone.danger().label());
        if (zone.recommendedMin() != zone.levelMin() || zone.recommendedMax() != zone.levelMax()) {
            String rec = zone.recommendedMin() == zone.recommendedMax() ? String.valueOf(zone.recommendedMin())
                    : zone.recommendedMin() + "-" + zone.recommendedMax();
            sub += "  ·  " + ThaiText.t("rotasutils.hud.zone.recommended", rec);
        }
        float scale = 1.6f;
        int nameW = Math.round(font.width(name) * scale);
        int rule = Math.max(24, Math.min(70, nameW / 2));
        int ruleY = y + Math.round(4 * scale);
        int ruleColor = fade(color, alpha * 0.9f);
        ui.rect(cx - nameW / 2 - 8 - rule, ruleY, rule, 1, ruleColor);
        ui.rect(cx + nameW / 2 + 8, ruleY, rule, 1, ruleColor);
        ui.diamond(cx - nameW / 2 - 8 - rule - 3, ruleY + 0.5f, 2.5f, 2.5f, ruleColor);
        ui.diamond(cx + nameW / 2 + 8 + rule + 3, ruleY + 0.5f, 2.5f, 2.5f, ruleColor);
        ui.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(cx, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, name, -font.width(name) / 2, 0, fade(RotasTheme.HUD_TEXT, alpha), true);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, sub, cx, y + Math.round(10 * scale) + 3, fade(color, alpha));
    }

    public static String displayName(ZoneDef zone) {
        return zone.name().isEmpty() ? zone.id() : zone.name();
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (Math.max(4, a) << 24) | (argb & 0xFFFFFF);
    }

    private static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static void text(UiCanvas ui, Font font, String text, int x, int y, int color) {
        ui.flush();
        ui.graphics().drawString(font, text, x, y, color, true);
    }
}
