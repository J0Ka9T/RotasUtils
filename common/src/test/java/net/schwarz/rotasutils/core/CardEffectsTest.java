package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.stat.CharacterStat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cards: the effect lines an administrator writes, and what the shipped set promises. */
class CardEffectsTest {
    @Test void aFlatLineAndAPercentLineBothRead() {
        CharacterStat.Effect flat = CardEffects.parse("minecraft:generic.armor 3");
        assertNotNull(flat);
        assertEquals("minecraft:generic.armor", flat.attribute());
        assertEquals(3, flat.perPoint(), 1e-9);
        assertSame(CharacterStat.Operation.ADD, flat.operation());
        assertFalse(flat.percent());

        CharacterStat.Effect percent = CardEffects.parse("minecraft:generic.attack_damage 0.08 percent");
        assertNotNull(percent);
        assertSame(CharacterStat.Operation.MULTIPLY_BASE, percent.operation());
        assertTrue(percent.percent());
        assertEquals(0.08, percent.perPoint(), 1e-9);
    }

    @Test void aLineThatWouldGiveACardTooMuchPowerIsRejected() {
        assertNull(CardEffects.parse("rotas:defense 5"), "a card may not touch the logical combat values");
        assertNull(CardEffects.parse("minecraft:generic.follow_range 10"), "an attribute off the list is refused");
        assertNull(CardEffects.parse("minecraft:generic.armor"), "a line needs an amount");
        assertNull(CardEffects.parse("minecraft:generic.armor lots"));
        assertNull(CardEffects.parse("minecraft:generic.armor 3 sideways"), "only add or percent");
        assertNull(CardEffects.parse("minecraft:generic.armor 0"), "a card that does nothing is not a card");
        assertNull(CardEffects.parse("minecraft:generic.armor 99999"));
        assertNull(CardEffects.parse(""));
        assertNull(CardEffects.parse(null));
    }

    @Test void onlyTheFirstFewLinesOfACardCount() {
        String[] lines = {
                "minecraft:generic.armor 1",
                "minecraft:generic.luck 1",
                "minecraft:generic.max_health 0.01 percent",
                "minecraft:generic.movement_speed 0.01 percent",
                "minecraft:generic.attack_damage 1",
        };
        assertEquals(CardEffects.MAX_LINES, CardEffects.parseAll(lines).size());
        assertEquals(2, CardEffects.parseAll(new String[]{"minecraft:generic.armor 1", "nonsense", "minecraft:generic.luck 1"}).size(),
                "a mistyped line is skipped, the rest of the card still works");
    }

    @Test void aParsedLineDescribesItselfTheWayAPlayerReadsIt() {
        CharacterStat.Effect percent = CardEffects.parse("minecraft:generic.attack_damage 0.08 percent");
        assertEquals("+8% ATK", CardEffects.describe(percent, key -> "ATK"));
        CharacterStat.Effect flat = CardEffects.parse("minecraft:generic.armor 3");
        assertEquals("+3 DEF", CardEffects.describe(flat, key -> "DEF"));
    }

    @Test void theShippedCardsAreRareReadableAndAllValid() {
        SeasonRules rules = new SeasonRules().sanitize();
        assertTrue(rules.cards.enabled);
        assertTrue(rules.cards.maxSockets >= 1);
        assertFalse(rules.cards.entries.isEmpty());
        rules.cards.entries.forEach((id, card) -> {
            assertFalse(card.name.isBlank(), id + " needs a name");
            assertTrue(List.of("ANY", "WEAPON", "ARMOR").contains(card.fits), id + " has a strange fit");
            assertFalse(CardEffects.parseAll(card.effects).isEmpty(), id + " must actually do something");
            double chance = card.chance >= 0 ? card.chance : rules.cards.dropChance;
            assertTrue(chance <= 0.05, id + " must stay a rare find");
        });
    }

    @Test void handEditedCardsAreClampedAndAnUnknownFitReadsAsAny() {
        SeasonRules loaded = SeasonRules.fromJson("{\"cards\":{\"maxSockets\":99,\"dropChance\":5,\"entries\":{"
                + "\"rotas:test\":{\"name\":\"x\",\"fits\":\"hat\",\"chance\":9,\"effects\":[\"minecraft:generic.luck 1\"]}}}}");
        assertEquals(8, loaded.cards.maxSockets);
        assertEquals(1.0, loaded.cards.dropChance, 1e-9);
        SeasonRules.CardDef card = loaded.cards.entries.get("rotas:test");
        assertNotNull(card);
        assertEquals("ANY", card.fits);
        assertEquals(1.0, card.chance, 1e-9);
    }
}
