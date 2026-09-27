package net.schwarz.rotasutils.client.theme;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SophisticatedThemeResourcesTest {
    private static final List<String> ATLASES = List.of(
            "gui_controls.png",
            "icons.png",
            "slots_background.png",
            "storage_background_12.png",
            "storage_background_12_wider.png",
            "storage_background_9.png",
            "storage_background_9_wider.png"
    );

    @Test
    void sophisticatedCoreAtlasesExistAndStayOnTheUpstreamCanvas() throws Exception {
        for (String atlas : ATLASES) {
            String path = "/assets/sophisticatedcore/textures/gui/" + atlas;
            try (InputStream stream = SophisticatedThemeResourcesTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, "Missing Sophisticated Core theme atlas: " + path);
                BufferedImage image = ImageIO.read(stream);
                assertNotNull(image, "Unreadable PNG: " + path);
                assertEquals(256, image.getWidth(), "Wrong width: " + atlas);
                assertEquals(256, image.getHeight(), "Wrong height: " + atlas);
                assertTrue(hasVisiblePixel(image), "Atlas is fully transparent: " + atlas);
            }
        }
    }

    @Test
    void sophisticatedCoreThemeContainsRotasDarkCopperAndReadableGlyphTones() throws Exception {
        int darkMatches = 0;
        int accentMatches = 0;
        int readableMatches = 0;
        for (String atlas : ATLASES) {
            String path = "/assets/sophisticatedcore/textures/gui/" + atlas;
            try (InputStream stream = SophisticatedThemeResourcesTest.class.getResourceAsStream(path)) {
                assertNotNull(stream);
                BufferedImage image = ImageIO.read(stream);
                darkMatches += countNear(image, 0x221A12, 18) + countNear(image, 0x2C2218, 18);
                accentMatches += countNear(image, 0xC98B3D, 20) + countNear(image, 0xE3A857, 20);
                readableMatches += countNear(image, 0xEDDFC0, 20) + countNear(image, 0xB6A17C, 20);
            }
        }
        assertTrue(darkMatches > 1000, "Sophisticated theme is missing Rotas dark surfaces");
        assertTrue(accentMatches > 20, "Sophisticated theme is missing copper accent pixels");
        assertTrue(readableMatches > 20, "Sophisticated theme is missing readable cream/muted glyph pixels");
    }

    private static int countNear(BufferedImage image, int rgb, int tolerance) {
        int tr = (rgb >>> 16) & 0xFF;
        int tg = (rgb >>> 8) & 0xFF;
        int tb = rgb & 0xFF;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) == 0) continue;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                if (Math.abs(r - tr) <= tolerance && Math.abs(g - tg) <= tolerance && Math.abs(b - tb) <= tolerance) count++;
            }
        }
        return count;
    }

    private static boolean hasVisiblePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0) return true;
            }
        }
        return false;
    }
}
