package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PerkRulesTest {
    private static PerkRules shipped() throws IOException {
        try (var in = PerkRulesTest.class.getResourceAsStream("/data/rotasutils/trades/perks.json")) {
            assertNotNull(in);
            return PerkRules.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test void everyRoleHasAPerkAndEveryPerkTypeIsUsed() throws IOException {
        PerkRules rules = shipped();
        for (String role : Set.of("chef", "miner", "alchemy", "rancher", "fisher", "farmer")) {
            assertFalse(rules.forRole(role).isEmpty(), role + " has no perk");
        }
        assertTrue(rules.forRole("blacksmith").isEmpty());
        assertEquals(PerkRules.TYPES, rules.perks().stream().map(PerkRules.Perk::type).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void aPerkGrowsWithLevelAndStopsAtItsCap() throws IOException {
        for (PerkRules.Perk perk : shipped().perks()) {
            assertEquals(perk.base(), perk.value(1), 1e-9, perk.type());
            assertTrue(perk.value(10) >= perk.value(1), perk.type());
            assertEquals(perk.max(), perk.value(1000), 1e-9, perk.type() + " must be capped");
            assertTrue(perk.value(0) <= perk.value(2), "level 0 never beats level 2");
        }
    }

    @Test void perksStayModestBecauseThisIsASubRole() throws IOException {
        for (PerkRules.Perk perk : shipped().perks()) {
            switch (perk.type()) {
                case "rich_veins" -> assertTrue(perk.max() <= 25, "ore doubling capped at a quarter");
                case "swift_pick" -> assertTrue(perk.max() <= 2, "at most Haste II");
                case "lucky_waters" -> assertTrue(perk.max() <= 2, "at most +2 Luck");
                case "master_brew" -> assertTrue(perk.max() <= 40);
                case "arcane_mind" -> assertTrue(perk.max() <= 80);
                case "animal_whisperer" -> assertTrue(perk.max() <= 3);
                case "green_thumb" -> assertTrue(perk.max() <= 6);
                default -> { }
            }
        }
    }

    @Test void badPerkFilesAreRefused() {
        String ok = "{\"perks\":[{\"role\":\"miner\",\"type\":\"prospect\",\"base\":6,\"perLevel\":1,\"max\":10}]}";
        assertDoesNotThrow(() -> PerkRules.parse(ok));
        assertThrows(IllegalArgumentException.class, () -> PerkRules.parse(ok.replace("prospect", "teleport")));
        assertThrows(IllegalArgumentException.class, () -> PerkRules.parse(ok.replace("\"max\":10", "\"max\":2")), "cap below base");
        assertThrows(IllegalArgumentException.class, () -> PerkRules.parse(ok.replace("\"max\":10", "\"max\":10,\"radius\":500")));
    }
}
