package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.util.ThaiText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThaiTextTest {
    @Test void theBundledThaiTableIsLoadedWhateverTheGameLanguage() {
        assertTrue(ThaiText.has("rotasutils.job.requirements"));
        String text = ThaiText.get("rotasutils.job.requirements");
        assertTrue(text.codePoints().anyMatch(point -> point >= 0x0E00 && point <= 0x0E7F), "expected Thai script");
        assertFalse(ThaiText.has("block.minecraft.stone"), "vanilla keys stay with the game language");
    }

    @Test void placeholdersFollowMinecraftRules() {
        assertEquals("a 1 b 2", ThaiText.format("a %s b %s", 1, 2));
        assertEquals("2 then 1", ThaiText.format("%2$s then %1$s", 1, 2));
        assertEquals("7 levels, 100%", ThaiText.format("%d levels, 100%%", 7));
        assertEquals("only %s", ThaiText.format("only %s"), "a missing argument stays visible");
        assertEquals("no placeholders", ThaiText.format("no placeholders", 5));
    }

    @Test void interfacePhrasesTranslateExactlyAndThroughPatterns() {
        assertEquals("ยกเลิก", ThaiText.phrase("Cancel"));
        assertEquals("  ยกเลิก ", ThaiText.phrase("  Cancel "), "surrounding spaces are kept");
        assertEquals("รางวัลที่เลเวล 5", ThaiText.phrase("Rewards at level 5"));
        assertEquals("ชื่อ: Goblin Hunt", ThaiText.phrase("Name: Goblin Hunt"));
        assertEquals("ตัวคูณพลังชีวิต", ThaiText.phrase("Health multiplier"), "captured parts are translated too");
        assertEquals("ค้นหาเควส", ThaiText.phrase("Search quests"), "a lower-cased label still matches");
        assertEquals("ตั้งค่าแรงค์ S", ThaiText.phrase("S-Rank Settings"), "the more specific pattern wins");
    }

    @Test void textWithoutAPhraseEntryPassesThrough() {
        assertEquals("Goblin Hunt", ThaiText.phrase("Goblin Hunt"));
        assertEquals("กระดานเควส", ThaiText.phrase("กระดานเควส"));
        assertEquals("", ThaiText.phrase(""));
        assertNull(ThaiText.phrase(null));
        assertEquals("12 / 40", ThaiText.phrase("12 / 40"));
    }

    @Test void zoneEditorLabelsAndDynamicValuesUseThai() {
        assertEquals("กฎ: ตั้งไว้ที่นี่ 3 ข้อ", ThaiText.phrase("Rules: 3 set here"));
        assertEquals("ทางเข้า: เงื่อนไข 2 ข้อ", ThaiText.phrase("Entry lock: 2 rules"));
        assertEquals("ห้ามใช้ปีก: เปิด", ThaiText.phrase("No elytra: ON"));
        assertEquals("ม็อบ Lv 5-10  |  XP x2  |  สำหรับ Lv 3-8",
                ThaiText.phrase("Mobs Lv 5-10  |  XP x2  |  for Lv 3-8"));
    }

    @Test void unknownKeysComeBackAsTheKey() {
        assertEquals("rotasutils.nope.missing", ThaiText.t("rotasutils.nope.missing"));
        assertNull(ThaiText.get(null));
    }
}
