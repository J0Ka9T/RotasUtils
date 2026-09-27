package net.schwarz.rotasutils.client.screen.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcActionPresentationTest {
    @Test
    void classifiesServerActionsByPlayerIntent() {
        assertEquals(NpcActionPresentation.Kind.DIALOGUE, NpcActionPresentation.kind("choice:greeting"));
        assertEquals(NpcActionPresentation.Kind.QUEST, NpcActionPresentation.kind("quest:rotas:hunt"));
        assertEquals(NpcActionPresentation.Kind.SHOP, NpcActionPresentation.kind("shop"));
        assertEquals(NpcActionPresentation.Kind.QUEST, NpcActionPresentation.kind("board"));
        assertEquals(NpcActionPresentation.Kind.SERVICE, NpcActionPresentation.kind("crafter"));
        assertEquals(NpcActionPresentation.Kind.GIFT, NpcActionPresentation.kind("gift:apple"));
        assertEquals(NpcActionPresentation.Kind.NAVIGATION, NpcActionPresentation.kind("@journal"));
    }

    @Test
    void assignsStableGlyphsWithoutDependingOnTranslatedLabels() {
        assertEquals("?", NpcActionPresentation.glyph("choice:greeting"));
        assertEquals("!", NpcActionPresentation.glyph("quest:rotas:hunt"));
        assertEquals("$", NpcActionPresentation.glyph("shop"));
        assertEquals("#", NpcActionPresentation.glyph("board"));
        assertEquals("<", NpcActionPresentation.glyph("@leave"));
    }
}
