package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NpcInteractionEditorTest {
    @Test void editorCanBuildAValidDialogueAndGiftWithoutJson() {
        var form = new GuidedContentDocument(NpcInteractionEditor.defaults(List.of()));
        var choices = List.of("nodes", "0", "choices");
        form.append(choices, NpcInteractionEditor.newEntry(choices, new JsonArray()));
        form.append(List.of("gifts"), NpcInteractionEditor.newEntry(List.of("gifts"), new JsonArray()));
        var rewards = List.of("gifts", "0", "rewards");
        form.append(rewards, NpcInteractionEditor.newEntry(rewards, new JsonArray()));
        var definition = NpcInteractions.parse(form.document());
        assertEquals(1, definition.nodes().get("hello").choices().size());
        assertEquals("minecraft:apple", definition.gifts().get(0).rewards().get(0).id());
        assertFalse(definition.behavior().enabled());
    }
    @Test void addingMultipleEntriesDoesNotDuplicateLocalIds() {
        var nodes = new JsonArray();
        for (int i = 0; i < 5; i++) nodes.add(NpcInteractionEditor.newEntry(List.of("nodes"), nodes));
        var document = NpcInteractionEditor.defaults(List.of());
        document.add("nodes", nodes); document.addProperty("start", "node");
        assertEquals(5, NpcInteractions.parse(document).nodes().size());
        assertEquals(6, NpcInteractionEditor.options(List.of("nodes", "0", "choices", "0", "next"), document).size());
    }
}
