package net.schwarz.rotasutils.client.theme;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotasInventoryThemeSourceTest {
    @Test
    void characterHubUsesLightParchmentSlotAndTabPalette() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/inventory/RotasInventoryRenderer.java"));

        assertTrue(source.contains("0xEAF1E6D1"));
        assertTrue(source.contains("0xFFD8C2A2"));
        assertTrue(source.contains("0xFFF8EDD9"));
        assertTrue(source.contains("0xFFD6C09A"));
        assertFalse(source.contains("0xC02A2118"));
        assertFalse(source.contains("0xB81F1810"));
        assertFalse(source.contains("0xC71A140E"));
        assertFalse(source.contains("0xE04C3A20"));
    }
}
