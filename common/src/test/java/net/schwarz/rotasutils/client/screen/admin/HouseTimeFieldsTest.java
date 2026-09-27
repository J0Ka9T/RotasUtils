package net.schwarz.rotasutils.client.screen.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HouseTimeFieldsTest {
    @Test
    void convertsDaysHoursAndMinutesToMillis() {
        HouseTimeFields.Result result = HouseTimeFields.toMillis("3", "1", "2");

        assertTrue(result.valid());
        assertEquals(262_920_000L, result.millis());
        assertTrue(result.field().isEmpty());
        assertTrue(result.message().isEmpty());
    }

    @Test
    void convertsTheApprovedThreeDayInterval() {
        assertEquals(259_200_000L, HouseTimeFields.toMillis("3", "0", "0").millis());
    }

    @Test
    void rejectsNegativeInputAndIdentifiesItsField() {
        HouseTimeFields.Result result = HouseTimeFields.toMillis("1", "-1", "0");

        assertFalse(result.valid());
        assertEquals("hours", result.field());
        assertFalse(result.message().isBlank());
    }

    @Test
    void rejectsMalformedInputAndIdentifiesItsField() {
        HouseTimeFields.Result result = HouseTimeFields.toMillis("1", "two", "0");

        assertFalse(result.valid());
        assertEquals("hours", result.field());
    }

    @Test
    void rejectsArithmeticOverflowAndIdentifiesItsField() {
        HouseTimeFields.Result result = HouseTimeFields.toMillis("999999999999", "0", "0");

        assertFalse(result.valid());
        assertEquals("days", result.field());
    }

    @Test
    void decomposesMillisWithoutLosingSubDayRemainders() {
        HouseTimeFields.Parts parts = HouseTimeFields.fromMillis(3 * 86_400_000L + 2 * 3_600_000L + 17 * 60_000L + 42_000L);

        assertEquals(3L, parts.days());
        assertEquals(2L, parts.hours());
        assertEquals(17L, parts.minutes());
        assertEquals(42_000L, parts.remainderMillis());
    }

    @Test
    void rejectsNegativeMillisWhenPrefillingFields() {
        assertThrows(IllegalArgumentException.class, () -> HouseTimeFields.fromMillis(-1L));
    }
}
