package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneSummaryTest {
    @Test void aPlainZoneReadsInFewLines() {
        ZoneDef zone = ZoneDef.create("rotas:a", "Forest", "minecraft:overworld", 5, 15)
                .withArea(new ZoneArea.Sphere(0, 64, 0, 30));
        List<String> lines = ZoneSummary.lines(zone);
        assertTrue(lines.get(0).startsWith("Forest [rotas:a]"));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Mobs Lv 5-15")));
        assertTrue(lines.contains("Entry: open"));
        assertTrue(lines.contains("Rules: inherited from the world"));
    }

    @Test void overriddenRulesAndMovementAreListed() {
        ZoneDef zone = ZoneDef.create("rotas:a", "Arena", "minecraft:overworld", 1, 5)
                .withCombatRules(new ZoneCombatRules(RuleBool.of(true), null, null, null, null, RuleBool.of(true),
                        RuleDouble.of(0), null))
                .withFeatures(ZoneFeatures.DEFAULT.withMovement(new ZoneMovement(true, false, true)));
        String text = String.join("\n", ZoneSummary.lines(zone));
        assertTrue(text.contains("PvP on"));
        assertTrue(text.contains("keep inventory on"));
        assertTrue(text.contains("XP loss 0%"));
        assertTrue(text.contains("no elytra, no pearls in"));
        assertTrue(text.contains("whole dimension"));
    }
}
