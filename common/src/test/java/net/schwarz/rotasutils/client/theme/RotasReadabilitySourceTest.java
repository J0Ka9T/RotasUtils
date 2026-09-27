package net.schwarz.rotasutils.client.theme;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotasReadabilitySourceTest {
    @Test
    void selectedNpcRoleUsesDarkInkInsteadOfGoldText() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/admin/NpcEditorScreen.java"));
        assertFalse(source.contains("selected ? RotasTheme.ACCENT_STRONG : Ui.TEXT_BRIGHT"));
        assertTrue(source.contains("selected ? RotasTheme.TEXT : Ui.TEXT_BRIGHT"));
    }

    @Test
    void sharedUiUsesVanillaLatinBeforeThaifixCompatibleFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/Ui.java"));
        assertTrue(source.contains("new ResourceLocation(Rotasutils.MOD_ID, \"ui_readable\")"));
        assertTrue(source.contains("withFont(UI_FONT)"));
        assertFalse(source.contains("withBold(true)"));
        assertTrue(source.contains("graphics.drawString(Minecraft.getInstance().font, component"));

        String fontDefinition = Files.readString(Path.of("src/main/resources/assets/rotasutils/font/ui_readable.json"));
        int vanillaLatin = fontDefinition.indexOf("minecraft:include/default");
        int packDefault = fontDefinition.indexOf("minecraft:default");
        int unicodeFallback = fontDefinition.indexOf("minecraft:include/unifont");
        assertTrue(vanillaLatin >= 0 && packDefault > vanillaLatin && unicodeFallback > packDefault);
        String button = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/RotasButton.java"));
        assertTrue(button.contains("Ui.readableComponent(builder.message)"));
    }

    @Test
    void mutedBodyTextIsNotRoutedThroughTheFaintestInk() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/Ui.java"));
        assertTrue(source.contains("public static final int TEXT_MUTED = RotasTheme.TEXT_MUTED;"));
        assertTrue(source.contains("public static final int TEXT_FAINT = RotasTheme.TEXT_FAINT;"));
    }

    @Test
    void dangerButtonsUseDedicatedDarkDangerInk() throws Exception {
        String ui = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/Ui.java"));
        String button = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/RotasButton.java"));
        assertTrue(ui.contains("DANGER_BORDER"));
        assertTrue(ui.contains("DANGER_TEXT"));
        assertTrue(button.contains("Ui.DANGER_BORDER"));
        assertTrue(button.contains("Ui.DANGER_TEXT"));
    }
}
