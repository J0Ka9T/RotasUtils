package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneFeatures;
import net.schwarz.rotasutils.core.ZoneMessages;
import net.schwarz.rotasutils.core.ZoneMovement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZonePresenceServiceTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void enteringShowsTheNewZonesTitleWithItsSound() {
        ZoneDef crypt = zone("zone_crypt", new ZoneMessages("The Crypt", "Keep quiet", "", "minecraft:block.bell.use"),
                ZoneMovement.NONE);

        var titles = ZonePresenceService.titles(null, crypt);

        assertEquals(new ZonePresenceService.Titles("The Crypt", "Keep quiet", "minecraft:block.bell.use"), titles);
    }

    @Test
    void leavingShowsTheOldZonesLeaveTitleUnlessTheNextZoneHasAnEntryTitle() {
        ZoneDef crypt = zone("zone_crypt", new ZoneMessages("The Crypt", "", "Fresh air", ""), ZoneMovement.NONE);
        ZoneDef forest = zone("zone_forest", ZoneMessages.NONE, ZoneMovement.NONE);
        ZoneDef town = zone("zone_town", new ZoneMessages("Rivertown", "", "", ""), ZoneMovement.NONE);

        assertEquals(new ZonePresenceService.Titles("Fresh air", "", ""), ZonePresenceService.titles(crypt, null));
        assertEquals(new ZonePresenceService.Titles("Fresh air", "", ""), ZonePresenceService.titles(crypt, forest),
                "stepping out of a nested zone into its parent");
        assertEquals("Rivertown", ZonePresenceService.titles(crypt, town).title());
        assertNull(ZonePresenceService.titles(forest, null));
        assertNull(ZonePresenceService.titles(null, forest));
    }

    @Test
    void pearlsCannotEnterOrLeaveANoPearlZone() {
        ZoneDef arena = zone("zone_arena", ZoneMessages.NONE, new ZoneMovement(false, false, true));
        ZoneDef field = zone("zone_field", ZoneMessages.NONE, ZoneMovement.NONE);

        assertTrue(ZonePresenceService.pearlRuleBlocks(arena, null), "into the arena");
        assertTrue(ZonePresenceService.pearlRuleBlocks(field, arena), "out of the arena");
        assertTrue(ZonePresenceService.pearlRuleBlocks(arena, arena));
        assertFalse(ZonePresenceService.pearlRuleBlocks(field, field));
        assertFalse(ZonePresenceService.pearlRuleBlocks(null, null));
    }

    private static ZoneDef zone(String id, ZoneMessages messages, ZoneMovement movement) {
        return ZoneDef.create(id, id.substring(5), OVERWORLD, 1, 10)
                .withFeatures(ZoneFeatures.DEFAULT.withMessages(messages).withMovement(movement));
    }
}
