package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.event.EventRules;
import net.schwarz.rotasutils.event.EventType;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.level.XpSource;
import net.schwarz.rotasutils.quest.objective.EventKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventCatalogTest {
    private record Rule(EventType type, String filter) {
    }

    private static Rule best(List<Rule> rules, EventType type, String subject) {
        return EventRules.best(rules, Rule::type, Rule::filter, type, subject);
    }

    @Test void theMostSpecificRuleAnswers() {
        List<Rule> rules = List.of(
                new Rule(EventType.KILL_ENTITY, ""),
                new Rule(EventType.KILL_ENTITY, "alexsmobs:*"),
                new Rule(EventType.KILL_ENTITY, "alexsmobs:bone_serpent"));

        assertEquals("alexsmobs:bone_serpent", best(rules, EventType.KILL_ENTITY, "alexsmobs:bone_serpent").filter());
        assertEquals("alexsmobs:*", best(rules, EventType.KILL_ENTITY, "alexsmobs:crocodile").filter());
        assertEquals("", best(rules, EventType.KILL_ENTITY, "minecraft:zombie").filter());
    }

    @Test void aRuleOfAnotherTypeNeverAnswers() {
        List<Rule> rules = List.of(new Rule(EventType.CRAFT_ITEM, "minecraft:*"));
        assertNull(best(rules, EventType.KILL_ENTITY, "minecraft:zombie"));
    }

    @Test void specificityIsExactThenNamespaceThenAnything() {
        assertEquals(3, EventRules.specificity("minecraft:zombie", "minecraft:zombie"));
        assertEquals(2, EventRules.specificity("minecraft:*", "minecraft:zombie"));
        assertEquals(1, EventRules.specificity("", "minecraft:zombie"));
        assertEquals(1, EventRules.specificity("*", "anything"));
        assertEquals(0, EventRules.specificity("alexsmobs:*", "minecraft:zombie"));
        assertEquals(0, EventRules.specificity("minecraft:zombie", "minecraft:skeleton"));
        assertEquals(2, EventRules.specificity("MINECRAFT:*", "minecraft:zombie"), "case does not matter");
    }

    @Test void onlyFiltersThisMatcherUnderstandsAreAccepted() {
        assertTrue(EventRules.validFilter(""));
        assertTrue(EventRules.validFilter("*"));
        assertTrue(EventRules.validFilter("alexsmobs:*"));
        assertTrue(EventRules.validFilter("minecraft:zombie"));
        assertFalse(EventRules.validFilter(":*"));
        assertFalse(EventRules.validFilter("a:b:*"));
        assertFalse(EventRules.validFilter("no_colon"));
        assertFalse(EventRules.validFilter("UPPER:CASE"));
    }

    @Test void awardAppliesTheMultiplierThenTheFlatBonus() {
        assertEquals(150, EventRules.award(100, 1.5, 0));
        assertEquals(120, EventRules.award(100, 1.0, 20));
        assertEquals(0, EventRules.award(100, 0, 0), "a zero multiplier switches the pay off");
        assertEquals(20, EventRules.award(-5, 2, 20), "a negative base cannot subtract");
        assertEquals(100, EventRules.award(100, Double.NaN, 0), "a broken multiplier leaves the amount alone");
    }

    @Test void aCooldownOnlyBlocksInsideItsWindow() {
        assertTrue(EventRules.offCooldown(1000, 0, 60), "a rule that never fired is ready");
        assertTrue(EventRules.offCooldown(1000, 900, 0), "no cooldown means always ready");
        assertFalse(EventRules.offCooldown(1000, 990, 60));
        assertTrue(EventRules.offCooldown(1000, 940, 60));
    }

    @Test void everyEventKindAndXpSourceLandsSomewhereOrNowhereOnPurpose() {
        for (EventKind kind : EventKind.values()) {
            EventType type = EventType.of(kind);
            boolean expected = switch (kind) {
                case DELIVER_ITEM, ESCORT, DEFEND, CUSTOM -> false;
                default -> true;
            };
            assertEquals(expected, type != null, kind + " mapping");
        }
        assertSame(EventType.KILL_BOSS, EventType.of(XpSource.BOSS_KILL));
        assertSame(EventType.BLOCK_BREAK, EventType.of(XpSource.MINING));
        assertNull(EventType.of(XpSource.CUSTOM_API), "a source with no catalogue entry stays unmapped");
    }

    @Test void aNewWorldHasOnePlainRulePerEventAndChangesNothing() {
        SeasonRules rules = new SeasonRules().sanitize();
        assertTrue(rules.events.enabled);
        assertEquals(EventType.values().length, rules.events.rules.length,
                "the catalogue starts complete, one line per event");
        for (SeasonRules.EventRule rule : rules.events.rules) {
            assertNotNull(rule.eventType());
            assertEquals("", rule.filter);
            assertTrue(rule.enabled);
            assertEquals(1.0, rule.xpMultiplier, 1e-9, "a fresh catalogue must not change the balance");
            assertEquals(0, rule.xpFlat);
            assertEquals(0, rule.gold);
            assertEquals("OFF", rule.announce);
        }
    }

    @Test void handEditedRulesAreClampedAndUnknownOnesDropped() {
        SeasonRules loaded = SeasonRules.fromJson("{\"events\":{\"enabled\":true,\"rules\":["
                + "{\"type\":\"KILL_ENTITY\",\"filter\":\"ALEXSMOBS:*\",\"xpMultiplier\":9999,\"gold\":-5,"
                + "\"announce\":\"shout\",\"cooldownSeconds\":999999},"
                + "{\"type\":\"NOT_A_REAL_EVENT\",\"filter\":\"x:*\"},"
                + "{\"type\":\"CRAFT_ITEM\",\"filter\":\"not a filter\"}]}}");

        SeasonRules.EventRule alex = null;
        SeasonRules.EventRule craft = null;
        for (SeasonRules.EventRule rule : loaded.events.rules) {
            if (rule.eventType() == EventType.KILL_ENTITY && rule.filter.equals("alexsmobs:*")) alex = rule;
            if (rule.eventType() == EventType.CRAFT_ITEM && rule.filter.isEmpty()) craft = rule;
        }
        assertNotNull(alex, "a filter is normalised to lower case, not dropped");
        assertEquals(1000, alex.xpMultiplier, 1e-9);
        assertEquals(0, alex.gold);
        assertEquals("OFF", alex.announce, "an announcement nobody understands reads as off");
        assertEquals(86400, alex.cooldownSeconds);
        assertNotNull(craft, "an unreadable filter falls back to the plain entry");

        for (SeasonRules.EventRule rule : loaded.events.rules) {
            assertNotNull(rule.eventType(), "a rule naming an unknown event is dropped");
        }
        for (EventType type : EventType.values()) {
            boolean plain = false;
            for (SeasonRules.EventRule rule : loaded.events.rules) {
                if (rule.eventType() == type && rule.filter.isEmpty()) {
                    plain = true;
                    break;
                }
            }
            assertTrue(plain, type + " must always keep a plain entry to fall back to");
        }
    }

    @Test void namespacesAreListedOnceAndSorted() {
        List<String> namespaces = EventRules.namespaces(List.of(
                "minecraft:zombie", "alexsmobs:crocodile", "minecraft:skeleton", "broken", "alexsmobs:bone_serpent"));
        assertEquals(List.of("alexsmobs", "minecraft"), namespaces);
    }
}
