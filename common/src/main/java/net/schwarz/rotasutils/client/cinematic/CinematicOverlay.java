package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.schwarz.rotasutils.ability.PurpleTimings;
import net.schwarz.rotasutils.ability.RedTimings;
import net.schwarz.rotasutils.ability.StargunTimings;

@Environment(EnvType.CLIENT)
public final class CinematicOverlay {
    private CinematicOverlay() {
    }

    public static void render(GuiGraphics g, float partialTick) {
        ClientCast cast = ClientCasts.local();
        if (cast == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double t = cast.time(partialTick);

        boolean purple = cast.purple();
        double flash = 0;
        double release = cast.releaseSeconds();
        if (t >= release) {
            flash = Math.max(flash, (purple ? 0.9 : 0.75) * Math.exp(-(t - release) / (purple ? 0.14 : 0.09)));
        }
        if (purple && t >= PurpleTimings.BORN) {
            flash = Math.max(flash, 0.55 * Math.exp(-(t - PurpleTimings.BORN) / 0.10));
        }
        double di = cast.sinceImpact(t);
        if (di >= 0) {
            flash = Math.max(flash, (purple ? 0.9 : 0.6) * Math.exp(-di / (purple ? 0.2 : 0.12)));
        }
        if (flash > 0.01) {
            flash *= mc.options.screenEffectScale().get();
            int a = (int) Math.min(255, flash * 255);
            g.fill(0, 0, w, h, (a << 24) | (purple ? 0xF0E0FF : 0xFFDCC8));
        }

        if (cast.stargun()) {
            double f = 0;
            if (t >= StargunTimings.FIRE - 0.04) {
                f = Math.max(f, 0.85 * Math.exp(-(t - StargunTimings.FIRE + 0.04) / 0.22));
            }
            if (t >= StargunTimings.IMPACT) {
                f = Math.max(f, 1.0 * Math.exp(-(t - StargunTimings.IMPACT) / 0.35));
            }
            if (f > 0.01) {
                f *= mc.options.screenEffectScale().get();
                g.fill(0, 0, w, h, ((int) Math.min(255, f * 255) << 24) | 0xE8F0FF);
            }
        }

        if (cast.projection() && Projection.black(cast, t)) {
            g.fill(0, 0, w, h, 0xFF000000);
        }

        double end = cast.endSeconds();
        double bars = Curves.smoothstep(Curves.window(t, RedTimings.CAMERA_DETACH, 0.8))
                * (1 - Curves.smoothstep(purple || cast.projection() || cast.stargun() ? Curves.window(t, end - 1.3, end - 0.1)
                : Curves.window(t, RedTimings.CAMERA_RETURN + 0.2, RedTimings.END - 0.1)));
        int bar = (int) Math.round(h * 0.115 * bars);
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        g.flush();
    }
}
