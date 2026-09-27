package net.schwarz.rotasutils.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.render.CameraQuake;
import net.schwarz.rotasutils.entity.ClientFx;
import net.schwarz.rotasutils.entity.ExoBeamEntity;
import net.schwarz.rotasutils.item.ExoDisintegratorItem;

/**
 * The Disintegrator's crosshair readout: a thin segmented bar pinned under the crosshair for as
 * long as the local player's own ray exists.
 *
 * <p>Before the shot it fills as the muzzle charges. Once the ray is loose it drains with the
 * gun's heat, running from gold to red as the vent closes in and pulsing in the last fifth, so
 * "how much burn is left" is readable without looking away from the aim. It carries no text on
 * purpose - the colour is the message - so it needs no translation and never fights the rest of
 * the HUD for words.</p>
 *
 * <p>The state arrives through {@link ClientFx#beamState}, pushed by the beam each client tick;
 * a beam that dies simply stops reporting, so the bar fades itself out on a short timer. The
 * eruption also arms {@link CameraQuake}'s lens punch here, keyed on the firing edge, so the
 * punch fires exactly once per discharge and only for the shooter.</p>
 */
@Environment(EnvType.CLIENT)
public final class ExoBeamHud {
    /** Ten 5px segments and nine 1px gaps, so the bar lands exactly on the vanilla crosshair width. */
    private static final int BAR_W = 59;
    private static final int BAR_H = 3;
    private static final int SEGMENTS = 10;
    private static final int SEGMENT_GAP = 1;
    /** Below the crosshair and clear of vanilla's attack indicator, which spans +9 to +25 there. */
    private static final int OFFSET_Y = 26;
    private static final int TRACK = 0x66000000;
    /** Colour while charging: cold white-cyan, distinct from every heat colour. */
    private static final int CHARGE = 0xFF9FF3FF;
    /** The lance's own ready colour, matching its gold-white beam. */
    private static final int LANCE_READY = 0xFFFFE08A;
    /** Ticks without a report before the bar is gone (the beam was released or discarded). */
    private static final long FADE_TICKS = 4;

    private static ExoBeamEntity.Mode mode = ExoBeamEntity.Mode.BEAM;
    private static float charge;
    private static float heat;
    private static boolean firing;
    private static long lastTick = Long.MIN_VALUE;
    private static boolean wasFiring;

    private ExoBeamHud() {
    }

    /** {@link ClientFx.BeamState} entry point: keeps this player's own ray in the readout. */
    public static void tick(int ownerId, int mode, float charge, float heat, boolean firing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || ownerId != minecraft.player.getId()) {
            return;
        }
        ExoBeamHud.mode = mode == ExoBeamEntity.Mode.LANCE.ordinal()
                ? ExoBeamEntity.Mode.LANCE : ExoBeamEntity.Mode.BEAM;
        ExoBeamHud.charge = Mth.clamp(charge, 0f, 1f);
        ExoBeamHud.heat = Mth.clamp(heat, 0f, 1f);
        ExoBeamHud.firing = firing;
        ExoBeamHud.lastTick = minecraft.level.getGameTime();
        if (firing && !wasFiring) {
            // The discharge edge: the lance gets the full lens punch, the beam a lighter one.
            CameraQuake.lensPunch(ExoBeamHud.mode == ExoBeamEntity.Mode.LANCE ? 1f : 0.5f);
        }
        wasFiring = firing;
    }

    /** Draws the readout under the crosshair; a no-op unless the local player's ray was just reported. */
    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui || !holds(minecraft)) {
            return;
        }
        long since = minecraft.level.getGameTime() - lastTick;
        // A negative gap means the world clock restarted (join/rejoin): the report is from another
        // world's timeline, so fail safe and hide rather than paint a stale bar at full opacity.
        if (lastTick == Long.MIN_VALUE || since < 0 || since > FADE_TICKS) {
            return;
        }
        float fade = 1f - since / (float) FADE_TICKS;
        int x = graphics.guiWidth() / 2 - BAR_W / 2;
        int y = graphics.guiHeight() / 2 + OFFSET_Y;
        graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, withFade(TRACK, fade));

        float filled = fillFraction(charge, heat, firing);
        int color = barColor(mode, firing, heat);
        // A last-fifth pulse, so a shot about to cut out is impossible to miss.
        if (firing && heat > 0.8f) {
            color = withFade(color, 0.62f + 0.38f * Mth.sin(minecraft.level.getGameTime() * 1.4f));
        }
        int segW = (BAR_W - (SEGMENTS - 1) * SEGMENT_GAP) / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float lo = i / (float) SEGMENTS;
            if (lo >= filled) {
                break;
            }
            float part = Math.min(1f, (filled - lo) * SEGMENTS);
            int width = Math.max(1, Math.round(segW * part));
            int segX = x + i * (segW + SEGMENT_GAP);
            graphics.fill(segX, y, segX + width, y + BAR_H, withFade(color, fade));
        }
    }

    /** Drops the readout when the world goes away, so a leftover bar cannot appear on the next join. */
    public static void reset() {
        charge = 0f;
        heat = 0f;
        firing = false;
        wasFiring = false;
        lastTick = Long.MIN_VALUE;
    }

    /** True while the Disintegrator is in either hand, so a stale bar never floats over another item. */
    private static boolean holds(Minecraft minecraft) {
        ItemStack main = minecraft.player.getMainHandItem();
        ItemStack off = minecraft.player.getOffhandItem();
        return main.getItem() instanceof ExoDisintegratorItem || off.getItem() instanceof ExoDisintegratorItem;
    }

    /** Charge progress before the shot; remaining burn while it is firing. */
    static float fillFraction(float charge, float heat, boolean firing) {
        return firing ? 1f - Mth.clamp(heat, 0f, 1f) : Mth.clamp(charge, 0f, 1f);
    }

    /** The readout's colour: charge cyan, lance gold, and the beam's heat ramp gold to red. */
    static int barColor(ExoBeamEntity.Mode mode, boolean firing, float heat) {
        if (!firing) {
            return CHARGE;
        }
        return mode == ExoBeamEntity.Mode.LANCE ? LANCE_READY : heatColor(heat);
    }

    /** Heat ramp from gold through orange to red; pure so the stops can be tested. */
    static int heatColor(float heat) {
        heat = Mth.clamp(heat, 0f, 1f);
        return heat < 0.5f
                ? lerpColor(0xFFFFE9B0, 0xFFFFA030, heat / 0.5f)
                : lerpColor(0xFFFFA030, 0xFFFF3326, (heat - 0.5f) / 0.5f);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private static int withFade(int argb, float fade) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(fade, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
