package net.schwarz.rotasutils.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SophisticatedThemeTest {
    @Test
    void remapsOnlySophisticatedDefaultDarkText() {
        assertEquals(0xEDDFC0, SophisticatedTheme.remapDefaultTextColor(0x404040));
        assertEquals(0xFFFFFF, SophisticatedTheme.remapDefaultTextColor(0xFFFFFF));
        assertEquals(0xFF5555, SophisticatedTheme.remapDefaultTextColor(0xFF5555));
        assertEquals(0xC98B3D, SophisticatedTheme.remapDefaultTextColor(0xC98B3D));
    }
}
