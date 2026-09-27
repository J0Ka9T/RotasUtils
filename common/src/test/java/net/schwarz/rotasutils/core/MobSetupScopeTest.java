package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobSetupScopeTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test void aZoneVersionOutranksTheGlobalSetupOnlyInsideItsZones() {
        MobSetupForm global = MobSetupForm.create("minecraft:zombie");
        assertFalse(global.zoneScoped());
        assertEquals(MobSetupForm.GLOBAL_PRIORITY, global.priority());

        MobSetupForm crypt = global.copyForZone();
        assertTrue(crypt.scopeZones().isEmpty(), "a copy starts with no zone chosen");
        crypt.toggleScopeZone("zone_crypt");
        crypt.setBaseXp(500);
        assertTrue(crypt.zoneScoped());
        assertFalse(crypt.usesOtherSelectors(), "zones are shown by the editor, not flagged as hidden selectors");

        var globalProfile = MonsterDefinitions.profile(new ContentId("rotas:monster/zombie"), global.body());
        var cryptProfile = MonsterDefinitions.profile(new ContentId("rotas:monster/zombie_2"), crypt.body());
        assertEquals(Set.of("zone_crypt"), cryptProfile.selector().regions(), "plain zone ids are accepted");
        assertTrue(cryptProfile.priority() > globalProfile.priority());

        var catalog = new MonsterCatalog(Map.of(globalProfile.id(), globalProfile, cryptProfile.id(), cryptProfile),
                Map.of(), Map.of(), Map.of());
        List<MonsterDefinitions.Profile> order = catalog.candidates("minecraft:zombie");
        assertEquals(cryptProfile.id(), order.get(0).id(), "the zone version is tried first");
        assertTrue(cryptProfile.selector().matches("minecraft:zombie", Set.of(), "", OVERWORLD, "NATURAL", "zone_crypt"));
        assertFalse(cryptProfile.selector().matches("minecraft:zombie", Set.of(), "", OVERWORLD, "NATURAL", ""),
                "outside the zone the zone version does not apply");
        assertTrue(globalProfile.selector().matches("minecraft:zombie", Set.of(), "", OVERWORLD, "NATURAL", ""),
                "the global setup still covers everywhere else");
    }

    @Test void clearingTheZonesMakesTheSetupGlobalAgain() {
        MobSetupForm form = MobSetupForm.create("minecraft:husk");
        form.toggleScopeZone("zone_desert");
        form.toggleScopeZone("zone_oasis");
        assertEquals(List.of("zone_desert", "zone_oasis"), form.scopeZones());
        assertEquals(MobSetupForm.ZONE_PRIORITY, form.priority());
        form.setScopeZones(List.of());
        assertFalse(form.zoneScoped());
        assertEquals(MobSetupForm.GLOBAL_PRIORITY, form.priority());
        assertTrue(MonsterDefinitions.profile(new ContentId("rotas:monster/husk"), form.body()).selector().regions().isEmpty());
    }

    @Test void malformedZoneIdsAreRejected() {
        MobSetupForm form = MobSetupForm.create("minecraft:zombie");
        form.toggleScopeZone("Not A Zone");
        assertThrows(IllegalArgumentException.class,
                () -> MonsterDefinitions.profile(new ContentId("rotas:monster/zombie"), form.body()));
    }
}
