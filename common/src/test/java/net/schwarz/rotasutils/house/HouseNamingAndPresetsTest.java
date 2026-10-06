package net.schwarz.rotasutils.house;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseNamingAndPresetsTest {
    @Test
    void aDisplayNameBecomesAValidId() {
        assertEquals("riverside_cottage", HouseNaming.slug("Riverside Cottage"));
        assertEquals("lot_12.b-3", HouseNaming.slug("  Lot 12.B-3!! "));
        assertEquals("house", HouseNaming.slug("   "));
        assertEquals("house", HouseNaming.slug("บ้านริมน้ำ"), "a name with no usable letters falls back");
        assertTrue(HouseNaming.slug("x".repeat(200)).matches("[a-z0-9_.-]{1,64}"));
    }

    @Test
    void theIdIsMadeUniqueAgainstExistingHouses() {
        assertEquals("riverside", HouseNaming.uniqueId("Riverside", List.of("other")));
        assertEquals("riverside_2", HouseNaming.uniqueId("Riverside", List.of("riverside")));
        assertEquals("riverside_3", HouseNaming.uniqueId("Riverside", List.of("riverside", "riverside_2")));
        String longTaken = "y".repeat(64);
        String made = HouseNaming.uniqueId(longTaken, List.of(longTaken));
        assertTrue(made.length() <= 64 && !made.equals(longTaken) && made.matches("[a-z0-9_.-]{1,64}"), made);
    }

    @Test
    void theNextHouseInARowIsSuggested() {
        assertEquals("Riverside 2", HouseNaming.next("Riverside 1"));
        assertEquals("Lot 10", HouseNaming.next("Lot 09"));
        assertEquals("Cottage 2", HouseNaming.next("Cottage"));
        assertEquals("", HouseNaming.next("  "));
        assertEquals("6 2", HouseNaming.next("6"), "a bare number alone is treated as a name");
    }

    @Test
    void everyPresetIsAValidConfigurationAndKeepsTheCurrencyAndTiers() {
        HouseConfig base = HouseConfig.defaults();
        for (HouseConfigPresets.Preset p : HouseConfigPresets.ALL) {
            HouseConfig made = p.applyTo(base);
            assertEquals(base.currency(), made.currency());
            assertEquals(base.tiers(), made.tiers());
            assertTrue(HouseConfigPresets.matches(p, made));
        }
    }

    @Test
    void presetsCycleAndStandardIsTheShippedDefault() {
        HouseConfig base = HouseConfig.defaults();
        assertTrue(HouseConfigPresets.matches(HouseConfigPresets.find("standard").orElseThrow(), base), "the shipped defaults are 'standard'");
        assertEquals("strict", HouseConfigPresets.after(base).id());
        assertEquals("relaxed", HouseConfigPresets.after(HouseConfigPresets.find("monthly").orElseThrow().applyTo(base)).id());
        HouseConfig odd = HouseConfigPresets.find("standard").orElseThrow().applyTo(base);
        odd = new HouseConfig(odd.currency(), odd.paymentIntervalMillis() + 60_000, odd.reminderLeadMillis(), odd.graceMillis(), odd.buyoutMultiplier(),
                odd.baseMemberLimit(), odd.memberSlotPrice(), odd.maxPurchasedMemberSlots(), odd.tiers(), 0);
        assertEquals("relaxed", HouseConfigPresets.after(odd).id(), "custom rules start the cycle");
        assertFalse(HouseConfigPresets.find("nope").isPresent());
    }
}
