package net.schwarz.rotasutils.client.theme;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SophisticatedThemeMixinSourceTest {
    private static final Path MIXIN_DIR = Path.of("src/main/java/net/schwarz/rotasutils/mixin/client/compat");

    @Test
    void sophisticatedThemeMixinsStayOptionalAndRenderOnly() throws Exception {
        String storage = Files.readString(MIXIN_DIR.resolve("SophisticatedStorageScreenMixin.java"));
        String settings = Files.readString(MIXIN_DIR.resolve("SophisticatedSettingsScreenMixin.java"));

        assertOptionalColorMixin(storage,
                "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase",
                "renderStorageTitle");
        assertOptionalColorMixin(settings,
                "net.p3pp3rf1y.sophisticatedcore.client.gui.SettingsScreen",
                "renderSettingsTitle");

        assertTrue(storage.contains("renderLabels") && storage.contains("m_280003_"));
        assertTrue(settings.contains("renderLabels") && settings.contains("m_280003_"));
        assertFalse(Files.exists(MIXIN_DIR.resolve("SophisticatedLabelMixin.java")),
                "constructor constant rewriting in Sophisticated Label causes a JVM VerifyError");
    }

    @Test
    void backpackCompatibilityIsThemeOnlyAndNeverChangesSophisticatedLayout() throws Exception {
        String storage = Files.readString(MIXIN_DIR.resolve("SophisticatedStorageScreenMixin.java"));

        assertFalse(storage.contains("SophisticatedBackpackLayout"));
        assertFalse(storage.contains("updateDimensionsAndSlotPositions"));
        assertFalse(storage.contains("updateStorageSlotsPositions"));
        assertFalse(storage.contains("getNumberOfVisibleRows"));
        assertFalse(storage.contains("updateInventoryScrollPanel"));
        assertFalse(storage.contains("updateTransferButtonsPositions"));
        assertFalse(storage.contains("renderBg") && storage.contains("CallbackInfo"));
        assertFalse(storage.contains("@ModifyVariable"));
        assertFalse(storage.contains("@ModifyArgs"));
        assertFalse(storage.contains("SlotAccessor"));
        assertFalse(storage.contains("rotasutils$positionWidget"));
        assertFalse(Files.exists(Path.of("src/main/java/net/schwarz/rotasutils/client/theme/SophisticatedBackpackLayout.java")));
        assertFalse(Files.exists(Path.of("src/test/java/net/schwarz/rotasutils/client/theme/SophisticatedBackpackLayoutTest.java")));
        assertFalse(storage.contains("import net.p3pp3rf1y"));
    }

    @Test
    void sophisticatedThemeMixinsAreRegisteredAsClientMixins() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/rotasutils.mixins.json"));
        assertTrue(config.contains("client.compat.SophisticatedStorageScreenMixin"));
        assertTrue(config.contains("client.compat.SophisticatedSettingsScreenMixin"));
        assertFalse(config.contains("client.compat.SophisticatedLabelMixin"));
    }

    private static void assertOptionalColorMixin(String source, String target, String method) {
        assertTrue(source.contains("@Pseudo"));
        assertTrue(source.contains("targets = \"" + target + "\""));
        assertTrue(source.contains("remap = false"));
        assertTrue(source.contains("@ModifyConstant"));
        assertTrue(source.contains("intValue = SophisticatedTheme.DEFAULT_DARK_TEXT"));
        assertTrue(source.contains("require = 0"));
        assertTrue(source.contains(method));
        assertTrue(source.contains("SophisticatedTheme.remapDefaultTextColor"));
        assertFalse(source.contains("import net.p3pp3rf1y"));
    }
}
