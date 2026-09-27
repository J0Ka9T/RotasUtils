package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.core.ResolvedZoneRules;
import net.schwarz.rotasutils.core.RuleBool;
import net.schwarz.rotasutils.core.RuleDouble;
import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.core.ZoneCombatRules;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.core.ZoneRuleResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneRuleServiceTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void pvpIsBlockedWhenEitherPlayerStandsWherePvpIsOff() {
        ResolvedZoneRules off = at(rules(RuleBool.of(false), null, null, null));
        ResolvedZoneRules on = at(rules(RuleBool.of(true), null, null, null));
        ResolvedZoneRules global = at(ZoneCombatRules.inherit());

        assertTrue(ZoneRuleService.pvpBlocked(off, on), "victim in a no-PvP zone");
        assertTrue(ZoneRuleService.pvpBlocked(on, off), "attacker shooting out of a no-PvP zone");
        assertFalse(ZoneRuleService.pvpBlocked(on, on));
        assertFalse(ZoneRuleService.pvpBlocked(global, null), "no override keeps vanilla PvP");
        assertFalse(ZoneRuleService.pvpBlocked(null, null));
    }

    @Test
    void damageMultipliersStackForVictimAndAttackerOnly() {
        ResolvedZoneRules taken = at(rules(null, RuleDouble.of(0.5), null, null));
        ResolvedZoneRules dealt = at(rules(null, null, RuleDouble.of(3), null));

        assertEquals(5f, ZoneRuleService.scaled(10f, taken, null));
        assertEquals(30f, ZoneRuleService.scaled(10f, null, dealt));
        assertEquals(15f, ZoneRuleService.scaled(10f, taken, dealt));
        assertEquals(10f, ZoneRuleService.scaled(10f, at(ZoneCombatRules.inherit()), null));
        assertEquals(0f, ZoneRuleService.scaled(10f, at(rules(null, RuleDouble.of(0), null, null)), null));
    }

    @Test
    void xpLossTakesAPercentageOfCurrentProgress() {
        assertEquals(750, ZoneRuleService.xpAfterLoss(1000, 25));
        assertEquals(1000, ZoneRuleService.xpAfterLoss(1000, 0), "PvP arenas cost nothing");
        assertEquals(0, ZoneRuleService.xpAfterLoss(1000, 100));
        assertEquals(0, ZoneRuleService.xpAfterLoss(-5, 10));
    }

    private static ZoneCombatRules rules(RuleBool pvp, RuleDouble taken, RuleDouble dealt, RuleBool keep) {
        return new ZoneCombatRules(pvp, null, taken, dealt, null, keep, null, null);
    }

    private static ResolvedZoneRules at(ZoneCombatRules rules) {
        ZoneDef zone = new ZoneDef("zone_test", "Test", OVERWORLD, List.of(new ZoneArea.Sphere(0, 64, 0, 16)),
                1, 10, 0, true).withCombatRules(rules);
        return ZoneRuleResolver.resolve(List.of(zone), OVERWORLD, 0, 64, 0);
    }
}
