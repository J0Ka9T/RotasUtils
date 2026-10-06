package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDialogueTemplatesTest {
    @Test void everyTemplateIsAValidStartingDocument() {
        for (NpcDialogueTemplates.Template template : NpcDialogueTemplates.Template.values()) {
            JsonObject document = NpcDialogueTemplates.of(template);
            NpcInteractions.Definition definition = NpcInteractions.parse(document);
            assertFalse(definition.nodes().isEmpty(), template + " has no steps");
            assertTrue(definition.nodes().containsKey(definition.start()),
                    template + " starts at a missing step");
        }
    }

    @Test void everyTemplateStartsWithNothingToFix() {
        for (NpcDialogueTemplates.Template template : NpcDialogueTemplates.Template.values()) {
            List<NpcDialogueGuide.Problem> problems = NpcDialogueGuide.problems(NpcDialogueTemplates.of(template));
            assertEquals(List.of(), problems, template + " should start clean");
        }
    }

    @Test void aNullTemplateFallsBackToBlank() {
        assertEquals(NpcDialogueTemplates.of(NpcDialogueTemplates.Template.BLANK),
                NpcDialogueTemplates.of((NpcDialogueTemplates.Template) null));
    }

    @Test void templatesCoverTheCommonNpcJobs() {
        JsonObject giver = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.QUEST_GIVER);
        assertEquals("start_quest", actionType(giver, "accept"));
        assertEquals(NpcDialogueTemplates.DEFAULT_QUEST, actionTarget(giver, "accept"));

        JsonObject turnIn = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.QUEST_TURN_IN);
        assertEquals("turn_in", actionType(turnIn, "turn_in"));
        assertEquals(2, node(turnIn, "hello").getAsJsonArray("choices")
                .get(0).getAsJsonObject().getAsJsonObject("action").getAsJsonArray("rewards").size());

        JsonObject shop = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.SHOPKEEPER);
        assertEquals("shop", actionType(shop, "browse"));
    }

    @Test void theGiftTemplateDeclaresAnAcceptableGift() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GIFT_RECEIVER);
        JsonArray gifts = document.getAsJsonArray("gifts");
        assertEquals(1, gifts.size());
        JsonObject gift = gifts.get(0).getAsJsonObject();
        assertFalse(gift.getAsJsonArray("items").isEmpty());
        assertFalse(gift.getAsJsonArray("rewards").isEmpty());
        assertTrue(document.has("invalid_gift"));
    }

    @Test void templatesCarryNoUiWordingThatWouldNeedTranslating() {
        for (NpcDialogueTemplates.Template template : NpcDialogueTemplates.Template.values()) {
            String json = NpcDialogueTemplates.of(template).toString();
            assertFalse(json.contains("Greeter"), "template data must not hold UI wording");
            assertFalse(json.contains("Shopkeeper"), "template data must not hold UI wording");
            assertFalse(json.contains("template"), "template data must not hold UI wording");
        }
    }

    private static JsonObject node(JsonObject document, String id) {
        for (var element : document.getAsJsonArray("nodes")) {
            JsonObject node = element.getAsJsonObject();
            if (node.get("id").getAsString().equals(id)) {
                return node;
            }
        }
        throw new AssertionError("No such node: " + id);
    }

    private static JsonObject choice(JsonObject document, String id) {
        for (var element : document.getAsJsonArray("nodes")) {
            for (var choiceElement : element.getAsJsonObject().getAsJsonArray("choices")) {
                JsonObject choice = choiceElement.getAsJsonObject();
                if (choice.get("id").getAsString().equals(id)) {
                    return choice;
                }
            }
        }
        throw new AssertionError("No such choice: " + id);
    }

    private static String actionType(JsonObject document, String id) {
        return choice(document, id).getAsJsonObject("action").get("type").getAsString();
    }

    private static String actionTarget(JsonObject document, String id) {
        return choice(document, id).getAsJsonObject("action").get("target").getAsString();
    }
}
