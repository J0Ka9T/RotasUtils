package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDialogueGuideTest {
    @Test void everyDialogueFieldHasItsOwnNameKey() {
        for (String field : NpcDialogueGuide.fields()) {
            assertEquals("rotasutils.dialogue.field." + field, NpcDialogueGuide.labelKey(field));
        }
    }

    @Test void fieldsWeDoNotOwnFallBackToTheEditorInsteadOfInventingAKey() {
        assertEquals("", NpcDialogueGuide.labelKey("something_new"));
        assertEquals("", NpcDialogueGuide.labelKey(null));
        assertEquals("", NpcDialogueGuide.labelKey(""));
    }

    @Test void aCleanTemplateReportsNothing() {
        assertEquals(List.of(), NpcDialogueGuide.problems(NpcDialogueTemplates.of(
                NpcDialogueTemplates.Template.GREETER)));
        assertEquals(0L, NpcDialogueGuide.blocking(NpcDialogueTemplates.of(
                NpcDialogueTemplates.Template.QUEST_GIVER)));
    }

    @Test void aButtonThatOpensAMissingStepIsBlocking() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        document.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject().addProperty("next", "nowhere");
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.missing_next", true, "nowhere"));
    }

    @Test void anOpeningStepThatDoesNotExistIsBlocking() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        document.addProperty("start", "missing_opening");
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.missing_start", true, "missing_opening"));
    }

    @Test void aStepNothingLinksToIsAWarning() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        document.getAsJsonArray("nodes").add(JsonParser.parseString(
                "{\"id\":\"orphan\",\"lines\":[\"Nobody can hear me.\"],\"choices\":[]}").getAsJsonObject());
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.unreachable", false, "orphan"));
        assertEquals(0L, NpcDialogueGuide.blocking(document));
    }

    @Test void anUnlabelledButtonAndASilentStepAreWarnings() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        document.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject().addProperty("text", "");
        document.getAsJsonArray("nodes").get(1).getAsJsonObject().add("lines", new JsonArray());
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.empty_button", false, "hello"));
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.silent_step", false, "who_are_you"));
        assertEquals(0L, NpcDialogueGuide.blocking(document));
    }

    @Test void aDialogueWithNoStepsSaysSo() {
        JsonObject document = new JsonObject();
        document.add("nodes", new JsonArray());
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.no_steps", false));
    }

    @Test void aSchemaErrorBecomesABlockingProblemInsteadOfAnException() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GIFT_RECEIVER);
        document.getAsJsonArray("gifts").get(0).getAsJsonObject()
                .getAsJsonArray("items").set(0, new com.google.gson.JsonPrimitive("not a valid id"));
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.invalid", true));
    }

    @Test void switchingTheConversationOffIsReported() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        document.addProperty("dialogue_enabled", false);
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.switched_off", false));
    }

    @Test void twoStepsSharingAnIdIsBlocking() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        document.getAsJsonArray("nodes").add(JsonParser.parseString(
                "{\"id\":\"hello\",\"lines\":[\"Me too.\"],\"choices\":[]}").getAsJsonObject());
        assertTrue(hasProblem(document, "rotasutils.dialogue.problem.duplicate_id", true, "hello"));
    }

    @Test void aMissingDocumentIsReportedRatherThanCrashing() {
        assertTrue(hasProblem(null, "rotasutils.dialogue.problem.no_dialogue", false));
        assertEquals(0L, NpcDialogueGuide.blocking(null));
    }

    @Test void theEditorNamesStepsAndButtonsRatherThanRawPaths() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        assertEquals("rotasutils.dialogue.btn.conversation", NpcDialogueGuide.describe(document, List.of()).key());

        NpcDialogueGuide.Crumb step = NpcDialogueGuide.describe(document, List.of("nodes", "0"));
        assertEquals("rotasutils.dialogue.crumb.step", step.key());
        assertEquals(List.of("hello"), step.args());

        NpcDialogueGuide.Crumb button = NpcDialogueGuide.describe(document, List.of("nodes", "0", "choices", "0"));
        assertEquals("rotasutils.dialogue.crumb.button", button.key());
        assertEquals(List.of("ท่านเป็นใคร?"), button.args());

        NpcDialogueGuide.Crumb field = NpcDialogueGuide.describe(document, List.of("nodes", "0", "lines"));
        assertEquals("rotasutils.dialogue.field.lines", field.key());

        NpcDialogueGuide.Crumb unknown = NpcDialogueGuide.describe(document, List.of("nodes", "0", "mystery"));
        assertEquals("rotasutils.dialogue.crumb.field", unknown.key());
        assertEquals(List.of("mystery"), unknown.args());
    }

    @Test void aBrokenPathStillNamesWhatItCan() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        assertEquals("rotasutils.dialogue.crumb.step",
                NpcDialogueGuide.describe(document, List.of("nodes", "9")).key());
        assertEquals("rotasutils.dialogue.crumb.entry",
                NpcDialogueGuide.describe(document, List.of("nodes", "0", "lines", "3")).key());
    }

    @Test void theTrailRunsFromTheRootToTheCurrentPosition() {
        JsonObject document = NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER);
        List<NpcDialogueGuide.Crumb> trail = NpcDialogueGuide.trail(document,
                List.of("nodes", "0", "choices", "0", "action"));
        assertEquals(6, trail.size());
        assertEquals("rotasutils.dialogue.btn.conversation", trail.get(0).key());
        assertEquals("rotasutils.dialogue.crumb.step", trail.get(2).key());
        assertEquals(List.of("hello"), trail.get(2).args());
        assertEquals("rotasutils.dialogue.crumb.button", trail.get(4).key());
        assertEquals("rotasutils.dialogue.field.action", trail.get(5).key());
        assertEquals(List.of(), NpcDialogueGuide.trail(document, List.of()));
    }

    @Test void theShapeCountsTheConversation() {
        NpcDialogueGuide.Shape greeter = NpcDialogueGuide.shape(
                NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER));
        assertEquals(2, greeter.steps());
        assertEquals("hello", greeter.opening());
        assertEquals(3, greeter.buttons());
        assertEquals(0, greeter.gifts());

        NpcDialogueGuide.Shape gifts = NpcDialogueGuide.shape(
                NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GIFT_RECEIVER));
        assertEquals(1, gifts.gifts());
    }

    /** True when the document reports this problem key and severity, carrying every given argument. */
    private static boolean hasProblem(JsonObject document, String key, boolean blocking, String... args) {
        outer:
        for (NpcDialogueGuide.Problem problem : NpcDialogueGuide.problems(document)) {
            if (!problem.key().equals(key) || problem.blocking() != blocking) {
                continue;
            }
            for (String arg : args) {
                if (!problem.args().contains(arg)) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
