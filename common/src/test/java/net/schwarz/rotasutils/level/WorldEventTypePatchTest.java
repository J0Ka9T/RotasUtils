package net.schwarz.rotasutils.level;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The admin world-event forms patch one block of the season JSON; the parser must keep the result. */
class WorldEventTypePatchTest {
    private static JsonObject root() {
        return JsonParser.parseString(new SeasonRules().toJson()).getAsJsonObject();
    }

    @Test
    void addedTypeSurvivesParse() {
        JsonObject root = root();
        JsonObject def = JsonParser.parseString(new Gson().toJson(new SeasonRules.WorldEventDef())).getAsJsonObject();
        def.addProperty("name", "Blood moon");
        def.addProperty("goal", "KILL");
        def.addProperty("goalCount", 40);
        def.add("spawns", JsonParser.parseString("[\"minecraft:zombie\",\"minecraft:skeleton\"]"));
        root.getAsJsonObject("worldEvents").getAsJsonObject("types").add("blood_moon", def);

        SeasonRules rules = SeasonRules.fromJson(root.toString());
        SeasonRules.WorldEventDef parsed = rules.worldEvents.types.get("blood_moon");
        assertEquals("Blood moon", parsed.name);
        assertEquals(40, parsed.goalCount);
        assertEquals(2, parsed.spawns.length);
    }

    @Test
    void deletedTypeIsGoneAndScheduleEditsApply() {
        JsonObject root = root();
        JsonObject block = root.getAsJsonObject("worldEvents");
        String first = block.getAsJsonObject("types").keySet().iterator().next();
        block.getAsJsonObject("types").remove(first);
        block.addProperty("intervalMinutes", 15);
        block.addProperty("enabled", false);

        SeasonRules rules = SeasonRules.fromJson(root.toString());
        assertFalse(rules.worldEvents.types.containsKey(first));
        assertEquals(15, rules.worldEvents.intervalMinutes);
        assertTrue(!rules.worldEvents.enabled);
    }
}
