package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonPresetPreviewTest {
    private static JsonObject defaults() {
        return JsonParser.parseString(new SeasonRules().toJson()).getAsJsonObject();
    }

    @Test
    void everyPreviewedSectionRendersWithDefaults() {
        SeasonRules rules = new SeasonRules();
        for (String section : new String[]{"leveling", "monster", "party", "stats", "pvp", "refine",
                "farming", "worldEvents", "horse"}) {
            assertFalse(SeasonPreview.lines(rules, section).isEmpty(), section);
        }
    }

    @Test
    void presetsScaleFromDefaultsAndStayValid() {
        for (SeasonSettingsCatalog.Section section : SeasonSettingsCatalog.SECTIONS) {
            if (!SeasonPresets.has(section.id())) continue;
            for (SeasonPresets.Level level : SeasonPresets.Level.values()) {
                JsonObject draft = defaults();
                assertTrue(SeasonPresets.apply(draft, defaults(), section.id(), level) > 0, section.id());
                SeasonRules.fromJson(draft.toString()); // must still parse
            }
        }
        JsonObject easy = defaults();
        SeasonPresets.apply(easy, defaults(), "leveling", SeasonPresets.Level.EASY);
        assertTrue(SeasonRules.fromJson(easy.toString()).mainBaseXp < new SeasonRules().mainBaseXp);
        JsonObject hard = defaults();
        SeasonPresets.apply(hard, defaults(), "refine", SeasonPresets.Level.EASY);
        for (double chance : SeasonRules.fromJson(hard.toString()).refine.chances) assertTrue(chance <= 1.0);

        JsonObject normal = defaults();
        SeasonPresets.apply(normal, defaults(), "horse", SeasonPresets.Level.HARD);
        SeasonPresets.apply(normal, defaults(), "horse", SeasonPresets.Level.NORMAL);
        assertEquals(defaults(), normal);
    }
}
