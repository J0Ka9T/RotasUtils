package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonSettingsEditorTest {
    private static JsonObject defaults() {
        return JsonParser.parseString(new SeasonRules().toJson()).getAsJsonObject();
    }

    @Test void everySeasonSettingHasASection() {
        List<String> orphans = new ArrayList<>();
        for (String key : defaults().keySet()) {
            List<String> path = List.of(key);
            if (SeasonSettingsCatalog.hidden(path)) continue;
            if (SeasonSettingsCatalog.sectionOf(path).equals(SeasonSettingsCatalog.OTHER)) orphans.add(key);
        }
        assertTrue(orphans.isEmpty(), "add these to a section in SeasonSettingsCatalog: " + orphans);
    }

    @Test void everyFixedSettingHasAName() {
        List<String> unnamed = new ArrayList<>();
        for (SettingsTree.Row row : SettingsTree.rows(defaults())) {
            List<String> path = row.path();
            if (SeasonSettingsCatalog.hidden(path.subList(0, 1))) continue;
            if (isMapEntry(path)) continue;
            if (!SeasonSettingsCatalog.named(path)) unnamed.add(row.joined());
        }
        assertTrue(unnamed.isEmpty(), "name these in SeasonSettingsCatalog: " + unnamed);
    }

    private static boolean isMapEntry(List<String> path) {
        String joined = String.join(".", path);
        if (joined.startsWith("classSynergy.")) return true;
        for (String map : List.of("rankExp.", "rankThresholds.", "rankPerks.", "classSynergy.", "drops.ranks.",
                "drops.grades.", "nemesis.epithets.", "worldEvents.types.", "cards.entries.")) {
            String prefix = map.substring(0, map.length() - 1);
            int depth = prefix.split("\\.").length;
            if (joined.startsWith(map) && path.size() == depth + 1) return true;
        }
        return false;
    }

    @Test void nothingIsHidden() {
        assertFalse(SeasonSettingsCatalog.hidden(List.of("stats")));
        assertFalse(SeasonSettingsCatalog.hidden(List.of("farming")));
    }

    @Test void nestedValuesAreListedAndTyped() {
        JsonObject root = defaults();
        List<SettingsTree.Row> rows = SettingsTree.rows(root);
        assertTrue(rows.contains(new SettingsTree.Row(List.of("farming", "comboCap"), SettingsTree.Kind.INT)));
        assertTrue(rows.contains(new SettingsTree.Row(List.of("farming", "eliteChance"), SettingsTree.Kind.DECIMAL)));
        assertTrue(rows.contains(new SettingsTree.Row(List.of("cards", "dropChance"), SettingsTree.Kind.DECIMAL)),
                "5.0E-4 is a decimal, not a whole number");
        assertTrue(rows.contains(new SettingsTree.Row(List.of("daily", "tiers"), SettingsTree.Kind.GROUP)),
                "a list of tiers is walked into, one group per tier");
        assertTrue(rows.contains(new SettingsTree.Row(List.of("daily", "tiers", "0", "reward", "gold"), SettingsTree.Kind.INT)));
        assertTrue(rows.contains(new SettingsTree.Row(List.of("cards", "entries", "rotas:zombie", "name"), SettingsTree.Kind.TEXT)),
                "keys holding a colon stay one path step");
        assertTrue(rows.contains(new SettingsTree.Row(List.of("stats", "strAttack"), SettingsTree.Kind.DECIMAL)));
        assertTrue(rows.contains(new SettingsTree.Row(List.of("stats", "maxPerStat"), SettingsTree.Kind.INT)));
        assertTrue(rows.contains(new SettingsTree.Row(List.of("refine", "chances"), SettingsTree.Kind.LIST)));
    }

    @Test void editedValuesReachTheRules() {
        JsonObject root = defaults();
        List<String> combo = List.of("farming", "comboCap");
        assertTrue(SettingsTree.set(root, combo, SettingsTree.parse(SettingsTree.Kind.INT, " 40 ", SettingsTree.get(root, combo))));
        List<String> gold = List.of("daily", "tiers", "0", "reward", "gold");
        assertTrue(SettingsTree.set(root, gold, SettingsTree.parse(SettingsTree.Kind.INT, "999", null)));
        List<String> chances = List.of("refine", "chances");
        SettingsTree.set(root, chances, SettingsTree.parseList("1, 1, 0.9, 0.8, 0.5, 0.4, 0.3, 0.2, 0.1, 0.05",
                SettingsTree.get(root, chances)));

        SeasonRules rules = SeasonRules.fromJson(root.toString());
        assertEquals(40, rules.farming.comboCap);
        assertEquals(999, rules.daily.tiers[0].reward.gold);
        assertEquals(0.9, rules.refine.chances[2], 1e-9);
        assertFalse(SettingsTree.set(root, List.of("farming", "noSuchKey"), new JsonPrimitive(1)),
                "the editor never invents keys");
    }

    @Test void listsKeepTheirElementType() {
        JsonArray whole = SettingsTree.parseList("10, 50, 200", JsonParser.parseString("[1,2]"));
        assertEquals("[10,50,200]", whole.toString());
        assertThrows(IllegalArgumentException.class, () -> SettingsTree.parseList("10, 2.5", JsonParser.parseString("[1,2]")));
        assertThrows(IllegalArgumentException.class, () -> SettingsTree.parseList("abc", JsonParser.parseString("[0.5]")));
        JsonArray text = SettingsTree.parseList("minecraft:zombie, minecraft:husk", JsonParser.parseString("[]"));
        assertEquals("[\"minecraft:zombie\",\"minecraft:husk\"]", text.toString());
        JsonArray withComma = SettingsTree.parseList("[\"a, b\", \"c\"]", JsonParser.parseString("[\"x\"]"));
        assertEquals(2, withComma.size());
        assertEquals("\"a, b\", c", SettingsTree.listText(withComma));
    }

    @Test void badTextIsRefusedWithAReason() {
        assertThrows(IllegalArgumentException.class, () -> SettingsTree.parse(SettingsTree.Kind.INT, "1.5", null));
        assertThrows(IllegalArgumentException.class, () -> SettingsTree.parse(SettingsTree.Kind.DECIMAL, "NaN", null));
        assertThrows(IllegalArgumentException.class, () -> SettingsTree.parse(SettingsTree.Kind.JSON, "{", null));
        JsonElement list = JsonParser.parseString("[1]");
        assertThrows(IllegalArgumentException.class, () -> SettingsTree.parse(SettingsTree.Kind.JSON, "{}", list),
                "a list stays a list");
    }

    @Test void choicesMatchWhatTheServerAccepts() {
        for (String option : SeasonSettingsCatalog.choices(List.of("refine", "onFail"))) {
            assertEquals(option, RefineMath.Fail.byKey(option).name());
        }
        assertEquals(RefineMath.Fail.values().length, SeasonSettingsCatalog.choices(List.of("refine", "onFail")).size());
    }

    @Test void aSeasonFileFitsOneServerboundPacketCompressed() {
        String json = defaults().toString();
        byte[] packed = CompressedText.compress(json);
        assertEquals(json, CompressedText.decompress(packed));
        assertTrue(packed.length < 8_000, "compressed season is " + packed.length + " bytes");
    }

    @Test void aPayloadThatInflatesTooFarIsRefused() {
        String huge = "a".repeat(CompressedText.MAX_TEXT_BYTES + 10);
        byte[] bomb = CompressedText.compress(huge);
        assertThrows(IllegalArgumentException.class, () -> CompressedText.decompress(bomb));
        assertThrows(IllegalArgumentException.class, () -> CompressedText.decompress(new byte[]{1, 2, 3, 4}));
    }
}
