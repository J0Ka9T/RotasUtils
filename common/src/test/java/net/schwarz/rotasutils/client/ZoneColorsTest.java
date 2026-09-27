package net.schwarz.rotasutils.client;

import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.core.ZoneDef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZoneColorsTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void nestedAndTouchingZonesNeverShareAColour() {
        ZoneDef forest = sphere("zone_forest", 0, 0, 200);
        ZoneDef crypt = sphere("zone_crypt", 20, 20, 16);
        ZoneDef village = sphere("zone_village", 210, 0, 40);

        Map<String, Integer> colours = ZoneColors.assign(List.of(forest, crypt, village));

        assertNotEquals(colours.get("zone_forest"), colours.get("zone_crypt"), "zone inside a zone");
        assertNotEquals(colours.get("zone_forest"), colours.get("zone_village"), "zones sharing a border");
    }

    @Test
    void farApartZonesMayReuseAColourAndTheResultIsStable() {
        ZoneDef west = sphere("zone_a", -5000, 0, 20);
        ZoneDef east = sphere("zone_b", 5000, 0, 20);

        Map<String, Integer> first = ZoneColors.assign(List.of(west, east));
        Map<String, Integer> again = ZoneColors.assign(List.of(east, west));

        assertEquals(first, again, "input order must not change colours");
        assertEquals(first.get("zone_a"), first.get("zone_b"), "nothing to tell apart, so the first colour is reused");
    }

    @Test
    void otherDimensionsDoNotTakeColours() {
        ZoneDef overworld = sphere("zone_a", 0, 0, 20);
        ZoneDef nether = new ZoneDef("zone_b", "b", "minecraft:the_nether",
                List.of(new ZoneArea.Sphere(0, 64, 0, 20)), 1, 10, 0, true);

        Map<String, Integer> colours = ZoneColors.assign(List.of(overworld, nether));

        assertEquals(colours.get("zone_a"), colours.get("zone_b"));
    }

    @Test
    void crowdedAreasStillGetValidColours() {
        List<ZoneDef> zones = new ArrayList<>();
        for (int i = 0; i < ZoneColors.PALETTE.length + 3; i++) {
            zones.add(sphere("zone_" + (char) ('a' + i), i, 0, 30));
        }

        Map<String, Integer> colours = ZoneColors.assign(zones);

        assertEquals(zones.size(), colours.size());
        colours.values().forEach(index -> assertTrue(index >= 0 && index < ZoneColors.PALETTE.length));
        assertEquals(ZoneColors.PALETTE.length, colours.values().stream().distinct().count(),
                "every colour is used before any repeats among touching zones");
    }

    private static ZoneDef sphere(String id, int x, int z, int radius) {
        return new ZoneDef(id, id, OVERWORLD, List.of(new ZoneArea.Sphere(x, 64, z, radius)), 1, 10, 0, true);
    }
}
