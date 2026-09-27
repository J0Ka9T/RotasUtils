package net.schwarz.rotasutils.client.theme;

import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.Ui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RotasThemePaletteTest {
    @Test
    void defaultThemeUsesLightParchmentSurfacesAndDarkInk() {
        assertEquals(0xFFF5E7D0, RotasTheme.PANEL);
        assertEquals(0xFFF2E3CA, RotasTheme.SURFACE);
        assertEquals(0xFFF8EDD9, RotasTheme.SURFACE_HIGH);
        assertEquals(0xFFEEDDC2, RotasTheme.TRACK);
        assertEquals(0xFF51351F, RotasTheme.TEXT);
        assertEquals(0xFF76583A, RotasTheme.TEXT_MUTED);
        assertEquals(0xFF9B8062, RotasTheme.TEXT_FAINT);
    }

    @Test
    void bordersControlsAndSelectionUseWarmPaperHierarchy() {
        assertEquals(0xFFD0B594, RotasTheme.PANEL_BORDER);
        assertEquals(0xFFD8C2A2, RotasTheme.SEPARATOR);
        assertEquals(0xFFE8D5B7, RotasTheme.CONTROL);
        assertEquals(0xFFF0DFC3, RotasTheme.CONTROL_HOVER);
        assertEquals(0xFFDCC29D, RotasTheme.CONTROL_PRESSED);
        assertEquals(0xFFE4D5BF, RotasTheme.CONTROL_DISABLED);
        assertEquals(0xFFD6C09A, RotasTheme.ACCENT_WASH);
    }

    @Test
    void semanticAccentsRemainDistinctOnTheLightTheme() {
        assertEquals(0xFFC5A175, RotasTheme.ACCENT);
        assertEquals(0xFFD2B184, RotasTheme.ACCENT_STRONG);
        assertEquals(0xFF718A68, RotasTheme.GOOD);
        assertEquals(0xFFB48A4C, RotasTheme.WARN);
        assertEquals(0xFFA35F52, RotasTheme.BAD);
        assertNotEquals(Ui.TEXT, Ui.PANEL);
        assertNotEquals(Ui.GOOD, Ui.WARN);
        assertNotEquals(Ui.WARN, Ui.BAD);
    }
}
