package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcInteractionsTest {
    private NpcInteractions.Definition parse(String body) throws Exception {
        return NpcInteractions.parse(ContentPacks.parse(body));
    }

    @Test void parsesBranchingTreeAndGiftSelectors() throws Exception {
        var definition = parse("""
            {"start":"hello","nodes":[
              {"id":"hello","lines":["Hello","What brings you here?"],"choices":[
                {"id":"work","text":"Work","next":"work","when":{"quest":"intro","state":"not_started"}}]},
              {"id":"work","lines":["Bring three apples."],"choices":[]}],
             "gifts":[{"id":"fruit","items":["minecraft:apple","#minecraft:flowers"],"count":3,
               "repeat":"daily","rewards":[{"type":"currency","id":"rotas:gold","amount":5}]}]}
            """);
        assertEquals(2, definition.nodes().size());
        assertEquals(2, definition.gifts().get(0).items().size());
        assertEquals(3, definition.gifts().get(0).count());
    }

    @Test void rejectsDanglingNodeAndDuplicateChoices() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"start":"a","nodes":[{"id":"a","lines":["x"],"choices":[{"id":"go","text":"Go","next":"missing"}]}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"start":"a","nodes":[{"id":"a","lines":["x"],"choices":[{"id":"x","text":"One"},{"id":"x","text":"Two"}]}]}
            """));
    }

    @Test void rejectsUnsafeRewardsAndInvalidCooldowns() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"gifts":[{"id":"g","items":["minecraft:apple"],"repeat":"cooldown","cooldown_seconds":-1,"rewards":[]}]}
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            {"gifts":[{"id":"g","items":["minecraft:apple"],"rewards":[{"type":"command","id":"op @s","amount":1}]}]}
            """));
    }

    @Test void repeatWindowsAreExplicitAndSurviveStoredTimestamp() {
        assertTrue(NpcInteractions.available("daily", 0, null, 86_399));
        assertFalse(NpcInteractions.available("daily", 0, "86398", 86_399));
        assertTrue(NpcInteractions.available("daily", 0, "86398", 86_400));
        assertFalse(NpcInteractions.available("once", 0, "100", 999_999));
        assertFalse(NpcInteractions.available("cooldown", 60, "100", 159));
        assertTrue(NpcInteractions.available("cooldown", 60, "100", 160));
        assertFalse(NpcInteractions.available("daily", 0, "broken", 160));
        assertTrue(NpcInteractions.available("unlimited", 0, "100", 100));
    }

    @Test void hiddenLegacyQuestsCanBeUnlockedByTheirExistingIds() throws Exception {
        var definition = parse("""
                {"gifts":[{"id":"intro","items":["minecraft:apple"],"rewards":[
                {"type":"quest_unlock","id":"meet_the_clerk","amount":1}]}]}
                """);
        assertEquals("meet_the_clerk", definition.gifts().get(0).rewards().get(0).id());
    }
}
