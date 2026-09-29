package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.schwarz.rotasutils.ability.PurpleTimings;
import net.schwarz.rotasutils.ability.RedTimings;

/**
 * What is drawn over the finished frame while the player's own cutscene plays: black letterbox bars that slide in
 * as the camera leaves the player and out as it returns, and the two blinding flashes - one on release, one when the
 * attack lands. The game's own interface is hidden for the duration (see {@link ClientCasts}), so nothing else competes.
 */
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

        // The flashes: white-hot, tinged red, gone in a fraction of a second.
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
            int a = (int) Math.min(255, flash * 255);
            g.fill(0, 0, w, h, (a << 24) | (purple ? 0xF0E0FF : 0xFFDCC8));
        }

        // Letterbox bars.
        double end = cast.endSeconds();
        double bars = Curves.smoothstep(Curves.window(t, RedTimings.CAMERA_DETACH, 0.8))
                * (1 - Curves.smoothstep(purple ? Curves.window(t, end - 2.4, end - 0.1)
                : Curves.window(t, RedTimings.CAMERA_RETURN + 0.2, RedTimings.END - 0.1)));
        int bar = (int) Math.round(h * 0.115 * bars);
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        g.flush();
    }
}
