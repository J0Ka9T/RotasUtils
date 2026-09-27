package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class NpcConversationLayoutTest {
    private static final String LAYOUT_CLASS =
            "net.schwarz.rotasutils.client.screen.player.NpcConversationLayout";

    @Test
    void dialogueIsAWideBottomStripForTheTypicalScreenWithFiveOptions() {
        Object layout = compute(640, 360, 5);
        int dialogueX = intAccessor(layout, "dialogueX");
        int dialogueY = intAccessor(layout, "dialogueY");
        int dialogueWidth = intAccessor(layout, "dialogueWidth");
        int dialogueHeight = intAccessor(layout, "dialogueHeight");

        assertTrue(dialogueX >= 0, "dialogue must start on-screen, was x=" + dialogueX);
        assertTrue(dialogueY >= 0, "dialogue must start on-screen, was y=" + dialogueY);
        assertTrue(dialogueWidth > 0, "dialogue must have a positive width");
        assertTrue(dialogueHeight > 0, "dialogue must have a positive height");
        assertTrue(dialogueWidth >= (640 * 80) / 100,
                "dialogue should use at least 80% of the screen width, was " + dialogueWidth);
        assertTrue(dialogueX + dialogueWidth <= 640,
                "dialogue must not run off the right edge");
        assertTrue(dialogueY + dialogueHeight <= 360,
                "dialogue must not run off the bottom edge");
        assertTrue(dialogueY >= 360 / 2,
                "dialogue should touch the lower region of the screen, was y=" + dialogueY);
    }

    @Test
    void optionsStackOnTheLeftDirectlyAboveTheDialogueWithoutOverlapping() {
        Object layout = compute(640, 360, 5);
        int dialogueY = intAccessor(layout, "dialogueY");
        int optionsX = intAccessor(layout, "optionsX");
        int optionsY = intAccessor(layout, "optionsY");
        int optionsWidth = intAccessor(layout, "optionsWidth");
        int optionsHeight = intAccessor(layout, "optionsHeight");
        int optionRowHeight = intAccessor(layout, "optionRowHeight");

        assertTrue(optionsX >= 0, "options must start on-screen, was x=" + optionsX);
        assertTrue(optionsWidth > 0, "options must have a positive width");
        assertTrue(optionsHeight > 0, "options must have a positive height");
        assertTrue(optionRowHeight > 0, "option rows must have a positive height");
        assertTrue(optionsX + optionsWidth <= 640,
                "options must not run off the right edge");
        assertTrue(optionsX < 640 / 2, "options should be anchored on the left side");

        assertTrue(optionsY + optionsHeight <= dialogueY,
                "options must never overlap the dialogue");
        int gap = dialogueY - (optionsY + optionsHeight);
        assertTrue(gap <= optionRowHeight,
                "options should sit directly above the dialogue, gap was " + gap);

        assertTrue(optionsY >= 360 / 3,
                "options may rise into the center-left but should keep the upper scene open, was y=" + optionsY);
    }

    @Test
    void normalScreenKeepsRowsReadableAndDialogueWithinRangeAboveFiveOptions() {
        Object layout = compute(640, 360, 5);
        int optionRowHeight = intAccessor(layout, "optionRowHeight");
        int dialogueHeight = intAccessor(layout, "dialogueHeight");
        int dialogueY = intAccessor(layout, "dialogueY");
        int optionsY = intAccessor(layout, "optionsY");
        int optionsHeight = intAccessor(layout, "optionsHeight");

        assertTrue(optionRowHeight >= 20,
                "option rows must be at least 20px tall for readability, was " + optionRowHeight);
        assertTrue(dialogueHeight >= 60,
                "dialogue must be at least 60px tall for readability, was " + dialogueHeight);
        assertTrue(dialogueHeight <= 96,
                "dialogue must not exceed 96px so the screen stays open, was " + dialogueHeight);
        assertTrue(optionsY + optionsHeight <= dialogueY,
                "the five options must fit above the dialogue without overlap");
    }

    @Test
    void compactAndWideLayoutsStayFullyOnScreenWithPositiveDimensions() {
        for (int[] size : new int[][] {{320, 240}, {854, 480}}) {
            Object layout = compute(size[0], size[1], 5);
            String where = size[0] + "x" + size[1];

            assertPositiveBoxOnScreen(layout, "dialogue", size[0], size[1], where);
            assertPositiveBoxOnScreen(layout, "options", size[0], size[1], where);
            assertTrue(intAccessor(layout, "optionRowHeight") > 0,
                    "option rows must have a positive height at " + where);
            assertTrue(intAccessor(layout, "optionsY") + intAccessor(layout, "optionsHeight")
                            <= intAccessor(layout, "dialogueY"),
                    "options must never overlap the dialogue at " + where);
        }
    }

    private static void assertPositiveBoxOnScreen(
            Object layout, String prefix, int screenWidth, int screenHeight, String where) {
        int x = intAccessor(layout, prefix + "X");
        int y = intAccessor(layout, prefix + "Y");
        int width = intAccessor(layout, prefix + "Width");
        int height = intAccessor(layout, prefix + "Height");

        assertTrue(width > 0, prefix + " width must be positive at " + where);
        assertTrue(height > 0, prefix + " height must be positive at " + where);
        assertTrue(x >= 0, prefix + " must start on-screen at " + where);
        assertTrue(y >= 0, prefix + " must start on-screen at " + where);
        assertTrue(x + width <= screenWidth, prefix + " must fit horizontally at " + where);
        assertTrue(y + height <= screenHeight, prefix + " must fit vertically at " + where);
    }

    private static Object compute(int screenWidth, int screenHeight, int optionCount) {
        Class<?> type = layoutType();
        Method compute = method(type, "compute", int.class, int.class, int.class);
        return assertDoesNotThrow(() -> compute.invoke(null, screenWidth, screenHeight, optionCount));
    }

    private static int intAccessor(Object layout, String name) {
        Method accessor = method(layout.getClass(), name);
        Object value = assertDoesNotThrow(() -> accessor.invoke(layout));
        assertTrue(value instanceof Number, name + " should return an integer but returned " + value);
        return ((Number) value).intValue();
    }

    private static Class<?> layoutType() {
        try {
            return Class.forName(LAYOUT_CLASS);
        } catch (ClassNotFoundException missing) {
            fail("Expected production helper " + LAYOUT_CLASS
                    + " to exist for NPC conversation layout, but the class is missing.");
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
                fail(LAYOUT_CLASS + " is missing the method " + name + Arrays.toString(parameters));
                return null;
            }
        }
    }
}
