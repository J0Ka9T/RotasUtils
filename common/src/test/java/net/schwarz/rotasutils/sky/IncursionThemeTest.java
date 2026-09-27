package net.schwarz.rotasutils.sky;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IncursionThemeTest {
    @Test void everySkyVariantHasADefaultTheme() {
        SeasonRules.IncursionRules rules = new SeasonRules.IncursionRules();
        for (int variant = 0; variant <= EldritchSkyTransition.VARIANT_SKY_RAINBOW; variant++) {
            assertNotNull(rules.themes.get(IncursionService.themeKey(variant)), "variant " + variant);
        }
        assertEquals("red", IncursionService.themeKey(EldritchSkyTransition.VARIANT_SKY_RED));
        assertEquals("rainbow", IncursionService.themeKey(EldritchSkyTransition.VARIANT_FOUR_SKIES));
        assertEquals("void", IncursionService.themeKey(EldritchSkyTransition.VARIANT_VOID));
    }

    @Test void sanitizeRepairsThemes() {
        SeasonRules season = new SeasonRules();
        season.incursions.themes.get("blue").mobs = null;
        season.incursions.themes.put("broken", null);
        season.incursions.radius = 1;
        season.sanitize();
        assertEquals(0, season.incursions.themes.get("blue").mobs.length);
        assertFalse(season.incursions.themes.containsKey("broken"));
        assertEquals(16, season.incursions.radius);
    }
}
