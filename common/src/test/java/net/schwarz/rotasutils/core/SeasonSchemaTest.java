package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonSchemaTest {
    private static JsonObject root() {
        return JsonParser.parseString(new SeasonRules().toJson()).getAsJsonObject();
    }

    @Test
    void knowsCollections() {
        assertTrue(SeasonSchema.isEntryCollection(List.of("daily", "tiers")));
        assertTrue(SeasonSchema.isEntryCollection(List.of("worldEvents", "types")));
        assertTrue(SeasonSchema.isMap(List.of("cards", "entries")));
        assertFalse(SeasonSchema.isEntryCollection(List.of("refine", "chances")));
        assertFalse(SeasonSchema.isEntryCollection(List.of("farming")));
        assertTrue(SeasonSchema.isEntry(List.of("events", "rules", "0")));
    }

    @Test
    void addAndRemoveSurviveParse() {
        JsonObject root = root();
        int tiers = root.getAsJsonObject("daily").getAsJsonArray("tiers").size();
        assertNotNull(SeasonSchema.add(root, List.of("daily", "tiers"), null));
        assertNotNull(SeasonSchema.add(root, List.of("cards", "entries"), "rotas:test_card"));
        assertNotNull(SeasonSchema.add(root, List.of("worldEvents", "types"), "blood_moon"));
        SeasonRules parsed = SeasonRules.fromJson(root.toString());
        assertEquals(tiers + 1, parsed.daily.tiers.length);
        assertTrue(parsed.cards.entries.containsKey("rotas:test_card"));
        assertTrue(parsed.worldEvents.types.containsKey("blood_moon"));

        assertTrue(SeasonSchema.remove(root, List.of("worldEvents", "types", "blood_moon")));
        assertTrue(SeasonSchema.remove(root, List.of("daily", "tiers", "0")));
        parsed = SeasonRules.fromJson(root.toString());
        assertFalse(parsed.worldEvents.types.containsKey("blood_moon"));
        assertEquals(tiers, parsed.daily.tiers.length);
    }

    @Test
    void emptyMapGetsClassDefaults() {
        JsonObject root = root();
        root.getAsJsonObject("worldEvents").add("types", new JsonObject());
        SeasonSchema.add(root, List.of("worldEvents", "types"), "fresh");
        var fresh = root.getAsJsonObject("worldEvents").getAsJsonObject("types").getAsJsonObject("fresh");
        assertTrue(fresh.has("weight"));
    }
}
