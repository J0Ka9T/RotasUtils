package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RED contract for the future {@code NpcDialogueScreen.presentationLayout(int, int, int)} helper.
 *
 * <p>The screen must expose a pure layout decision so the presentation can be reasoned about
 * without instantiating the screen: given the viewport and how many options the server offered,
 * it returns the shared {@code NpcConversationLayout}. Reflection keeps this test compiling
 * before the method exists, so the missing method is the only thing that fails.</p>
 */
class NpcDialoguePresentationTest {
    private static final String SCREEN_CLASS =
            "net.schwarz.rotasutils.client.screen.player.NpcDialogueScreen";
    private static final int SCREEN_WIDTH = 640;
    private static final int SCREEN_HEIGHT = 360;
    private static final int OPTION_COUNT = 5;

    @Test
    void wideDialogueSitsLowWithLeftAnchoredOptionsAboveIt() {
        Object layout = presentationLayout(SCREEN_WIDTH, SCREEN_HEIGHT, OPTION_COUNT);

        int dialogueX = intAccessor(layout, "dialogueX");
        int dialogueY = intAccessor(layout, "dialogueY");
        int dialogueWidth = intAccessor(layout, "dialogueWidth");
        int dialogueHeight = intAccessor(layout, "dialogueHeight");
        int optionsX = intAccessor(layout, "optionsX");
        int optionsY = intAccessor(layout, "optionsY");
        int optionsWidth = intAccessor(layout, "optionsWidth");
        int optionsHeight = intAccessor(layout, "optionsHeight");
        int optionRowHeight = intAccessor(layout, "optionRowHeight");

        assertTrue(dialogueWidth >= (SCREEN_WIDTH * 80) / 100,
                "dialogue should use at least 80% of the screen width, was " + dialogueWidth);
        assertTrue(Math.abs(dialogueX - (SCREEN_WIDTH - dialogueWidth) / 2) <= 1,
                "dialogue should be centered instead of stuck to a screen edge");
        assertTrue(dialogueY >= SCREEN_HEIGHT / 2,
                "dialogue should sit in the lower half of the screen, was y=" + dialogueY);
        assertTrue(dialogueHeight >= 64 && dialogueHeight <= SCREEN_HEIGHT / 3,
                "dialogue card should stay compact, was height=" + dialogueHeight);
        assertTrue(optionsX < SCREEN_WIDTH / 2,
                "options should be anchored on the left side, was x=" + optionsX);
        assertTrue(optionsWidth >= (SCREEN_WIDTH * 30) / 100 && optionsWidth <= SCREEN_WIDTH / 2,
                "options should be a compact left column, was width=" + optionsWidth);
        assertTrue(dialogueY - (optionsY + optionsHeight) >= 8,
                "options need breathing room above the dialogue card");
        assertTrue(optionRowHeight >= 22 && optionRowHeight <= 28,
                "option rows should read like compact RPG choices, was " + optionRowHeight);
    }

    @Test
    void cinematicDialogueUsesOpaqueHighContrastPaletteAndSharedThaiTextPath() throws Exception {
        String dialogue = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/player/NpcDialogueScreen.java"));
        String conversation = Files.readString(Path.of("src/main/java/net/schwarz/rotasutils/client/screen/player/NpcConversationScreen.java"));

        assertTrue(dialogue.contains("0xE619120C"));
        assertTrue(dialogue.contains("0xFFC89C62"));
        assertTrue(dialogue.contains("0xFFF2E2C8"));
        assertTrue(dialogue.contains("0xFFE2C18E"));
        assertFalse(dialogue.contains("hovered ? Ui.TEXT_BRIGHT : option.color()"));
        assertTrue(dialogue.contains("Ui.textWidth"));

        assertTrue(conversation.contains("0xE619120C"));
        assertTrue(conversation.contains("0xFFF2E2C8"));
        assertTrue(conversation.contains("0xFFE2C18E"));
        assertTrue(conversation.contains("Ui.textWidth"));
        assertFalse(conversation.contains("Ui.SCRIM_TOP, Ui.SCRIM_BOTTOM"));
    }

    private static Object presentationLayout(int width, int height, int optionCount) {
        Class<?> screen = screenType();
        Method method = method(screen, "presentationLayout", int.class, int.class, int.class);
        return assertDoesNotThrow(() -> method.invoke(null, width, height, optionCount));
    }

    private static int intAccessor(Object layout, String name) {
        Method accessor = method(layout.getClass(), name);
        Object value = assertDoesNotThrow(() -> accessor.invoke(layout));
        assertTrue(value instanceof Number, name + " should return an integer but returned " + value);
        return ((Number) value).intValue();
    }

    private static Class<?> screenType() {
        try {
            return Class.forName(SCREEN_CLASS);
        } catch (ClassNotFoundException missing) {
            fail("Expected production screen " + SCREEN_CLASS
                    + " to exist so its presentation layout can be tested, but the class is missing.");
            return null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method found = type.getMethod(name, parameters);
            found.setAccessible(true);
            return found;
        } catch (NoSuchMethodException notPublic) {
            try {
                Method found = type.getDeclaredMethod(name, parameters);
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException missing) {
                fail(SCREEN_CLASS + " is missing the method " + name + Arrays.toString(parameters));
                return null;
            }
        }
    }
}
