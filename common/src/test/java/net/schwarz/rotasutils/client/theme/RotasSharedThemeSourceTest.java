package net.schwarz.rotasutils.client.theme;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotasSharedThemeSourceTest {
    @Test
    void adminHubDoesNotKeepTheOldDarkFallbackCard() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/admin/AdminMenuScreen.java"));

        assertFalse(source.contains("0xE8382C1F"));
        assertTrue(source.contains("Ui.PANEL_ALT"));
    }

    @Test
    void skillCategoryDefaultsFollowTheSharedThemeTokens() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/admin/SkillCategorySettingsScreen.java"));

        assertTrue(source.contains("RotasTheme.SURFACE"));
        assertTrue(source.contains("RotasTheme.ACCENT"));
        assertFalse(source.contains("parseColor(value, 0xFF2C2218)"));
        assertFalse(source.contains("parseColor(value, 0xFFC98B3D)"));
    }
}
