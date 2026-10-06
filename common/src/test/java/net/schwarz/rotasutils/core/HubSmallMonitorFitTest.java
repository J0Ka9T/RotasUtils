package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.inventory.RotasInventoryRenderer;
import net.schwarz.rotasutils.client.screen.ScreenScale;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HubSmallMonitorFitTest {
    private static final int[][] MONITORS = {{1024, 768}, {1280, 720}, {1280, 800}, {1280, 1024}, {1366, 768},
            {1440, 900}, {1600, 900}, {1680, 1050}, {1920, 1080}};
    private static final int WINDOW_CHROME_W = 16;
    private static final int WINDOW_CHROME_H = 63;

    @Test
    void hubFitsAndStaysReadableOnSmallMonitors() {
        List<String> failures = new ArrayList<>();
        for (int[] monitor : MONITORS) {
            for (boolean windowed : new boolean[]{false, true}) {
                int fw = windowed ? monitor[0] - WINDOW_CHROME_W : monitor[0];
                int fh = windowed ? monitor[1] - WINDOW_CHROME_H : monitor[1];
                for (int option = 0; option <= 4; option++) {
                    int player = vanillaScale(option, fw, fh);
                    int scale = ScreenScale.hubScale(player, fw, fh);
                    String key = fw + "x" + fh + " option " + option + " scale " + scale;
                    if (player >= 2 && scale < 2) failures.add(key + ": dropped to scale 1");
                    int cw = ScreenScale.canvas(fw, scale);
                    int ch = ScreenScale.canvas(fh, scale);
                    int hw = RotasInventoryRenderer.targetWidth(cw);
                    int hh = RotasInventoryRenderer.targetHeight(ch);
                    RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(hw, hh);
                    for (String issue : issues(l, hw, hh, cw, ch)) failures.add(key + ": " + issue);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static int vanillaScale(int option, int w, int h) {
        int scale = 1;
        while (scale != option && scale < w && scale < h && w / (scale + 1) >= 320 && h / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    private static List<String> issues(RotasInventoryRenderer.Layout l, int hw, int hh, int cw, int ch) {
        List<String> issues = new ArrayList<>();
        if (hw > cw || hh > ch) issues.add("hub larger than canvas");
        int radius = l.portraitDiameter() / 2;
        int discTop = l.portraitCenterY() - radius;
        if (!l.compact() && discTop - RotasInventoryRenderer.LEVEL_BADGE_H < 0) issues.add("level badge clipped");
        int nameBlock = l.compact() ? RotasInventoryRenderer.NAME_BLOCK_COMPACT_H : RotasInventoryRenderer.NAME_BLOCK_H;
        if (l.nameY() + nameBlock > hh) issues.add("name clipped");
        int half = l.socketSize() / 2;
        int[][] sockets = {{l.headX(), l.headY()}, {l.chestX(), l.chestY()}, {l.legsX(), l.legsY()},
                {l.feetX(), l.feetY()}, {l.offhandX(), l.offhandY()}};
        for (int[] s : sockets) {
            if (s[0] - half < 0 || s[1] - half < 0 || s[1] + half > hh) issues.add("socket clipped");
        }
        if (l.portraitCenterX() + radius + RotasInventoryRenderer.CURIO_RESERVE > l.rightX()) issues.add("disc under card");
        if (l.tabY() < 0) issues.add("tabs clipped");
        if (l.panelY() + l.panelH() > hh) issues.add("card clipped");
        int gap = l.spacing() - l.slotBox();
        if (l.gridX() + l.slotBox() * 9 + gap * 8 > l.rightX() + l.rightW()) issues.add("bag wider than card");
        int status = l.compact() ? RotasInventoryRenderer.STATUS_COMPACT_H : RotasInventoryRenderer.STATUS_FULL_H;
        if (l.gridY() < l.statusY() + status) issues.add("bag over STATUS");
        if (l.slotBox() < 18) issues.add("slots smaller than 18px: " + l.slotBox());
        if (l.barW() < 90) issues.add("vitals narrower than 90px: " + l.barW());
        if (hw < 200 + 16) issues.add("item card wider than hub");
        return issues;
    }
}
