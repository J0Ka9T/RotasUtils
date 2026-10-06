package net.schwarz.rotasutils.core;

import com.google.gson.JsonParser;
import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonHelpCoverageTest {
    @Test
    void everyValueHasAHint() {
        var root = JsonParser.parseString(new SeasonRules().toJson()).getAsJsonObject();
        List<String> missing = new ArrayList<>();
        for (SettingsTree.Row row : SettingsTree.rows(root)) {
            if (row.kind() == SettingsTree.Kind.GROUP || SeasonSettingsCatalog.hidden(row.path().subList(0, 1))) continue;
            if (SeasonSettingsCatalog.help(row.path()).isBlank()) missing.add(row.joined());
        }
        assertTrue(missing.isEmpty(), missing.size() + " values without a hint: " + missing);
    }

    @Test
    void wildcardHintsReachListEntries() {
        assertEquals("ยิ่งมาก ยิ่งถูกสุ่มบ่อย เทียบกับประเภทอื่น ปกติ 5-20",
                SeasonSettingsCatalog.help(List.of("worldEvents", "types", "blood_moon", "weight")));
        assertTrue(SeasonSettingsCatalog.help(List.of("daily", "tiers", "2", "reward", "gold")).contains("เงิน"));
    }
}
