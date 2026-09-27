package net.schwarz.rotasutils.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EldritchSkyVariantTest {
    @Test void fourSkiesVariantSurvivesSaveAndReload() {
        var opening = EldritchSkyTransition.toggle(EldritchSkyTransition.Snapshot.off(), 120L, 81L,
                EldritchSkyTransition.VARIANT_FOUR_SKIES);
        var loaded = EldritchSkyTransition.Snapshot.load(opening.save());
        assertEquals(EldritchSkyTransition.VARIANT_FOUR_SKIES, loaded.variant);
        assertEquals(81L, loaded.seed);
        assertEquals(EldritchSkyTransition.State.ACTIVE,
                loaded.settle(120L + EldritchSkyTransition.OPENING_TICKS).state);
    }

    @Test void aFreshSkyTakesTheRequestedVariantAndKeepsItThroughSaveAndSettle() {
        EldritchSkyTransition.Snapshot open = EldritchSkyTransition.toggle(
                EldritchSkyTransition.Snapshot.off(), 100L, 9L, EldritchSkyTransition.VARIANT_HERALD);
        assertEquals(EldritchSkyTransition.VARIANT_HERALD, open.variant);
        assertEquals(EldritchSkyTransition.VARIANT_HERALD, EldritchSkyTransition.Snapshot.load(open.save()).variant);
        EldritchSkyTransition.Snapshot active = open.settle(100L + EldritchSkyTransition.OPENING_TICKS);
        assertEquals(EldritchSkyTransition.State.ACTIVE, active.state);
        assertEquals(EldritchSkyTransition.VARIANT_HERALD, active.variant);
    }

    @Test void closingKeepsTheVariantEvenWhenAnotherSigilIsUsed() {
        EldritchSkyTransition.Snapshot open = EldritchSkyTransition.toggle(
                EldritchSkyTransition.Snapshot.off(), 0L, 1L, EldritchSkyTransition.VARIANT_HERALD);
        EldritchSkyTransition.Snapshot closing = EldritchSkyTransition.toggle(open, 50L, 2L, EldritchSkyTransition.VARIANT_SKY);
        assertEquals(EldritchSkyTransition.State.CLOSING, closing.state);
        assertEquals(EldritchSkyTransition.VARIANT_HERALD, closing.variant);
    }

    @Test void oldSavesLoadAsThePlainSky() {
        var tag = new EldritchSkyTransition.Snapshot(EldritchSkyTransition.State.ACTIVE, 0L, 1f, 3L).save();
        tag.remove("variant");
        assertEquals(EldritchSkyTransition.VARIANT_SKY, EldritchSkyTransition.Snapshot.load(tag).variant);
    }

    @Test void onlyTheCrimsonVariantsAreRedAndOnlyTheHeraldsHaveAFigure() {
        assertFalse(EldritchSkyTransition.red(EldritchSkyTransition.VARIANT_SKY));
        assertFalse(EldritchSkyTransition.red(EldritchSkyTransition.VARIANT_HERALD));
        assertTrue(EldritchSkyTransition.red(EldritchSkyTransition.VARIANT_HERALD_RED));
        assertTrue(EldritchSkyTransition.red(EldritchSkyTransition.VARIANT_SKY_RED));
        assertTrue(EldritchSkyTransition.herald(EldritchSkyTransition.VARIANT_HERALD_RED));
        assertFalse(EldritchSkyTransition.herald(EldritchSkyTransition.VARIANT_SKY_RED));
        var tag = EldritchSkyTransition.toggle(EldritchSkyTransition.Snapshot.off(), 0L, 5L,
                EldritchSkyTransition.VARIANT_SKY_RED).save();
        assertEquals(EldritchSkyTransition.VARIANT_SKY_RED, EldritchSkyTransition.Snapshot.load(tag).variant);
    }
}
