package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.hud.HudValueText;
import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudValueTextTest {
    /** Six pixels per character, so a column of 30 fits exactly five characters. */
    private static final ToIntFunction<String> WIDTH = text -> text.length() * 6;

    @Test void textThatFitsIsKeptWhole() {
        assertEquals("7/20", HudValueText.fit("7/20", WIDTH, 60));
    }

    @Test void aValueThatExactlyFillsItsColumnIsKept() {
        assertEquals("20/20", HudValueText.fit("20/20", WIDTH, 30));
    }

    @Test void aWideValueFallsBackToItsNumerator() {
        assertEquals("1420", HudValueText.fit("1420/1420", WIDTH, 30));
    }

    @Test void aValueWiderThanItsNumeratorIsTrimmedFromTheRight() {
        assertEquals("12", HudValueText.fit("123456", WIDTH, 12));
    }

    @Test void aLongLabelIsTrimmedToItsColumn() {
        assertEquals("HUN", HudValueText.fit("HUNGER", WIDTH, 18));
    }

    @Test void nothingIsDrawnWhenThereIsNoRoomOrNoText() {
        assertEquals("", HudValueText.fit("20/20", WIDTH, 0));
        assertEquals("", HudValueText.fit("20/20", WIDTH, -4));
        assertEquals("", HudValueText.fit("", WIDTH, 40));
        assertEquals("", HudValueText.fit(null, WIDTH, 40));
    }
}
