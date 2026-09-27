package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueStudioTest {
    private static final DialogueStudioCheck.Context ELENA =
            new DialogueStudioCheck.Context(Set.of("rotas:goblin_hunt"), true);

    private static JsonObject town() {
        return JsonParser.parseString("""
                {"start":"greeting","nodes":[
                  {"id":"greeting","lines":["Welcome to Ashford.","Mind the gate."],"choices":[
                    {"id":"town","text":"Tell me more","next":"town"},
                    {"id":"work","text":"Do you have any work?","next":"offer"},
                    {"id":"shop","text":"Show me your shop","next":"","action":{"type":"shop","repeat":"unlimited"}},
                    {"id":"bye","text":"Goodbye","next":""}]},
                  {"id":"town","lines":["There are monsters beyond the eastern gate.","Be careful."],"choices":[
                    {"id":"back","text":"Back","next":"greeting"}]},
                  {"id":"offer","lines":["Clear the goblins off the road."],"choices":[
                    {"id":"accept","text":"I will do it","next":"greeting",
                     "when":{"quest":"rotas:goblin_hunt","state":"not_started"},
                     "action":{"type":"start_quest","target":"rotas:goblin_hunt"}},
                    {"id":"decline","text":"Not now","next":"greeting"}]}]}
                """).getAsJsonObject();
    }

    private static List<DialogueStudioCheck.Issue> issues(DialogueStudio studio, String key) {
        return DialogueStudioCheck.run(studio, ELENA).stream().filter(issue -> issue.key().equals(key)).toList();
    }

    @Test void messageNamesComeFromTheFirstSpokenLineThenANumber() {
        DialogueStudio studio = new DialogueStudio(town());
        assertEquals("01", studio.label(0).number());
        assertEquals("Welcome to Ashford.", studio.label(0).title());
        assertEquals("There are monsters beyond the eastern gate.", studio.label(1).title());

        int blank = studio.addMessage("");
        assertEquals("04", studio.label(blank).number());
        assertEquals("", studio.label(blank).title());

        studio.setMessageText(blank, "\n   \n   Beware   the\tnight  \nsecond line");
        assertEquals("Beware the night", studio.label(blank).title());

        studio.setMessageText(blank, "x".repeat(200));
        String title = studio.label(blank).title();
        assertTrue(title.length() <= DialogueStudio.MAX_TITLE);
        assertTrue(title.endsWith("…"));
    }

    @Test void messageIdsMapToReadableDestinationsAndBack() {
        DialogueStudio studio = new DialogueStudio(town());
        List<DialogueStudio.MessageLabel> destinations = studio.labels();
        assertEquals(3, destinations.size());
        assertEquals(2, studio.indexOf("offer"));
        assertEquals(-1, studio.indexOf("nowhere"));
        assertEquals(1, studio.nextIndex(0, 0));
        assertEquals(2, studio.nextIndex(0, 1));
        assertEquals(DialogueStudio.END, studio.nextIndex(0, 3));
        assertEquals(0, studio.startIndex());

        studio.goTo(0, 3, 2);
        assertEquals("offer", studio.nextId(0, 3));
        assertEquals(DialogueStudio.Outcome.GO_TO, studio.outcome(0, 3));
    }

    @Test void addingAMessageCreatesAUniqueNodeTheServerAccepts() {
        DialogueStudio studio = new DialogueStudio(town());
        int first = studio.addMessage("Hello again.");
        int second = studio.addMessage("And again.");
        assertEquals(3, first);
        assertEquals(4, second);
        assertNotEquals(studio.messageId(first), studio.messageId(second));
        assertEquals("Hello again.", studio.messageText(first));
        NpcInteractions.Definition definition = NpcInteractions.parse(studio.document());
        assertEquals(5, definition.nodes().size());
        assertEquals(List.of("Hello again."), definition.nodes().get(studio.messageId(first)).lines());
    }

    @Test void theFirstMessageOfAnEmptyConversationBecomesTheOpening() {
        DialogueStudio studio = new DialogueStudio(JsonParser.parseString("{\"start\":\"\",\"nodes\":[]}").getAsJsonObject());
        int index = studio.addMessage("Hi.");
        assertEquals(0, studio.startIndex());
        assertEquals(studio.messageId(index), NpcInteractions.parse(studio.document()).start());
    }

    @Test void addingAReplyLinksToTheNextMessageWhenThereIsOne() {
        DialogueStudio studio = new DialogueStudio(town());
        int reply = studio.addReply(1, "Continue");
        assertEquals("Continue", studio.replyText(1, reply));
        assertEquals(DialogueStudio.Outcome.GO_TO, studio.outcome(1, reply));
        assertEquals(2, studio.nextIndex(1, reply));

        int last = studio.addReply(2, "Continue");
        assertEquals(DialogueStudio.Outcome.END, studio.outcome(2, last));

        int again = studio.addReply(1, "Continue");
        assertNotEquals(studio.replyId(1, reply), studio.replyId(1, again));
        assertEquals(3, NpcInteractions.parse(studio.document()).nodes().get("town").choices().size());
    }

    @Test void replyDestinationsMapOntoTheExistingActionEngine() {
        DialogueStudio studio = new DialogueStudio(town());
        assertEquals(DialogueStudio.Outcome.OPEN_SHOP, studio.outcome(0, 2));
        assertEquals(DialogueStudio.Outcome.ACCEPT_QUEST, studio.outcome(2, 0));

        studio.setOutcome(0, 3, DialogueStudio.Outcome.OPEN_SHOP);
        assertEquals("shop", actionType(studio, 0, 3));

        studio.setOutcome(0, 3, DialogueStudio.Outcome.TURN_IN_QUEST);
        studio.setTarget(0, 3, "rotas:goblin_hunt");
        assertEquals("turn_in", actionType(studio, 0, 3));
        assertEquals(DialogueStudio.Outcome.TURN_IN_QUEST, studio.outcome(0, 3));

        studio.setNext(0, 3, 1);
        assertEquals(1, studio.nextIndex(0, 3));
        assertEquals(DialogueStudio.Outcome.TURN_IN_QUEST, studio.outcome(0, 3));

        studio.goTo(0, 2, 1);
        assertEquals("none", actionType(studio, 0, 2));
        assertEquals(DialogueStudio.Outcome.GO_TO, studio.outcome(0, 2));

        studio.setOutcome(0, 2, DialogueStudio.Outcome.END);
        assertEquals("", studio.nextId(0, 2));
        assertEquals(DialogueStudio.Outcome.END, studio.outcome(0, 2));

        studio.setOutcome(0, 2, DialogueStudio.Outcome.GIVE_REWARD);
        assertEquals(0, studio.addReward(0, 2));
        studio.setRewardType(0, 2, 0, "xp");
        studio.setRewardAmount(0, 2, 0, 25);
        NpcInteractions.Choice parsed = NpcInteractions.parse(studio.document()).nodes().get("greeting").choices().get(2);
        assertEquals("reward", parsed.action().type());
        assertEquals(new NpcInteractions.Grant("xp", "minecraft:apple", 25), parsed.action().rewards().get(0));
        assertTrue(DialogueStudioCheck.run(studio, ELENA).stream().noneMatch(DialogueStudioCheck.Issue::error));
    }

    @Test void deletingALinkedMessageLeavesVisibleUnresolvedReferences() {
        DialogueStudio studio = new DialogueStudio(town());
        assertEquals(List.of(new DialogueStudio.ReplyRef(0, 0)), studio.referencesTo(1));
        studio.deleteMessage(1);

        assertEquals(2, studio.messageCount());
        assertEquals("town", studio.nextId(0, 0));
        assertEquals(DialogueStudio.UNRESOLVED, studio.nextIndex(0, 0));
        List<DialogueStudioCheck.Issue> broken = issues(studio, "unresolved_destination");
        assertEquals(1, broken.size());
        assertTrue(broken.get(0).error());
        assertEquals(0, broken.get(0).message());
        assertEquals(0, broken.get(0).reply());
        assertThrows(IllegalArgumentException.class, () -> NpcInteractions.parse(studio.document()));
    }

    @Test void deletingTheOpeningMessageDoesNotSilentlyPickAnotherOne() {
        JsonObject document = town();
        document.remove("start");
        DialogueStudio studio = new DialogueStudio(document);
        studio.deleteMessage(0);
        assertEquals(-1, studio.startIndex());
        assertEquals(1, issues(studio, "missing_start").size());
        studio.setStart(0);
        assertEquals(0, studio.startIndex());
        assertTrue(issues(studio, "missing_start").isEmpty());
    }

    @Test void validationCatchesAMissingDestination() {
        JsonObject document = town();
        document.getAsJsonArray("nodes").get(2).getAsJsonObject().getAsJsonArray("choices")
                .get(1).getAsJsonObject().addProperty("next", "deleted_long_ago");
        DialogueStudio studio = new DialogueStudio(document);
        List<DialogueStudioCheck.Issue> broken = issues(studio, "unresolved_destination");
        assertEquals(1, broken.size());
        assertEquals(2, broken.get(0).message());
        assertEquals(1, broken.get(0).reply());
        assertTrue(issues(studio, "invalid").isEmpty());
    }

    @Test void validationAcceptsAKnownValidBranchingConversation() {
        assertEquals(List.of(), DialogueStudioCheck.run(new DialogueStudio(town()), ELENA));
        for (NpcDialogueTemplates.Template template : NpcDialogueTemplates.Template.values()) {
            DialogueStudio studio = new DialogueStudio(NpcDialogueTemplates.of(template));
            assertTrue(DialogueStudioCheck.run(studio, DialogueStudioCheck.Context.unknown()).stream()
                    .noneMatch(DialogueStudioCheck.Issue::error), template + " should have no errors");
        }
    }

    @Test void validationCatchesBrokenConditionsActionsAndEmptyReplies() {
        DialogueStudio studio = new DialogueStudio(town());
        studio.setReplyText(1, 0, "   ");
        assertEquals(1, issues(studio, "empty_reply").size());

        studio.addCondition(1, 0, DialogueStudio.ConditionKind.QUEST);
        assertEquals(List.of(DialogueStudio.ConditionKind.QUEST), studio.conditions(1, 0));
        assertEquals(1, issues(studio, "condition_needs_quest").size());
        studio.setConditionQuest(1, 0, "rotas:goblin_hunt");
        assertTrue(issues(studio, "condition_needs_quest").isEmpty());

        studio.addCondition(1, 0, DialogueStudio.ConditionKind.FLAG);
        studio.setFlag(1, 0, "not a flag");
        assertEquals(1, issues(studio, "bad_flag").size());
        studio.removeCondition(1, 0, DialogueStudio.ConditionKind.FLAG);
        assertTrue(issues(studio, "bad_flag").isEmpty());

        studio.addCondition(1, 0, DialogueStudio.ConditionKind.LEVEL);
        assertEquals(1, studio.minLevel(1, 0));
        studio.setMinLevel(1, 0, 10);
        NpcInteractions.Condition when = NpcInteractions.parse(studio.document()).nodes().get("town").choices().get(0).when();
        assertEquals(10, when.level());
        assertEquals("not_started", when.state());

        studio.setOutcome(1, 0, DialogueStudio.Outcome.ACCEPT_QUEST);
        assertEquals(1, issues(studio, "action_needs_quest").size());
        studio.setTarget(1, 0, "rotas:somebody_elses_quest");
        assertTrue(issues(studio, "action_needs_quest").isEmpty());
        assertEquals(1, issues(studio, "quest_not_offered").size());
        assertFalse(issues(studio, "quest_not_offered").get(0).error());

        studio.setOutcome(1, 0, DialogueStudio.Outcome.TRIGGER_EVENT);
        studio.setTarget(1, 0, "Not An Id");
        assertEquals(1, issues(studio, "bad_event").size());

        studio.setMessageText(1, "1\n2\n3\n4\n5\n6\n7\n8\n9");
        assertEquals(1, issues(studio, "too_many_lines").size());
    }

    @Test void aConversationWithoutAnOpeningIsReported() {
        DialogueStudio studio = new DialogueStudio(town());
        JsonObject document = studio.document();
        document.addProperty("start", "");
        studio.replace(document);
        assertEquals(-1, studio.startIndex());
        assertEquals(1, issues(studio, "no_start").size());
    }

    @Test void untouchedConversationsRoundTripExactly() {
        JsonObject original = town();
        DialogueStudio studio = new DialogueStudio(original);
        studio.label(0);
        studio.conditions(2, 0);
        studio.outcome(0, 2);
        studio.referencesTo(0);
        DialogueStudioCheck.run(studio, ELENA);
        assertEquals(original, studio.document());

        studio.setMessageText(1, studio.messageText(1));
        studio.setReplyText(0, 0, studio.replyText(0, 0));
        assertEquals(original, studio.document());
        assertEquals(NpcInteractions.parse(original), NpcInteractions.parse(studio.document()));
    }

    @Test void existingConversationsWithoutOptionalFieldsStayReadable() {
        JsonObject legacy = JsonParser.parseString("""
                {"nodes":[
                  {"id":"hello","lines":["Hello"],"choices":[{"id":"go","text":"Go","next":"work"},{"id":"end","text":"Bye"}]},
                  {"id":"work","lines":["Bring three apples."]}],
                 "gifts":[{"id":"fruit","items":["minecraft:apple"],"rewards":[]}],
                 "behavior":{"enabled":true,"movement":"wander"}}
                """).getAsJsonObject();
        DialogueStudio studio = new DialogueStudio(legacy);
        assertEquals(0, studio.startIndex());
        assertEquals(DialogueStudio.Outcome.GO_TO, studio.outcome(0, 0));
        assertEquals(DialogueStudio.Outcome.END, studio.outcome(0, 1));
        assertEquals(0, studio.replyCount(1));
        assertEquals(List.of(), studio.conditions(0, 0));
        assertEquals("once", studio.repeat(0, 0));
        assertTrue(DialogueStudioCheck.run(studio, ELENA).stream().noneMatch(DialogueStudioCheck.Issue::error));

        int reply = studio.addReply(1, "Back");
        studio.goTo(1, reply, 0);
        NpcInteractions.Definition definition = NpcInteractions.parse(studio.document());
        assertEquals("hello", definition.nodes().get("work").choices().get(0).next());
        assertEquals(1, definition.gifts().size());
        assertEquals("wander", definition.behavior().movement());
    }

    @Test void rawJsonAdvancedModeStaysCompatible() {
        DialogueStudio studio = new DialogueStudio(town());
        String raw = studio.json();
        JsonObject reparsed = JsonParser.parseString(raw).getAsJsonObject();
        assertNull(DialogueStudio.structuralProblem(reparsed));
        assertEquals(studio.document(), reparsed);
        assertEquals(NpcInteractions.parse(town()), NpcInteractions.parse(reparsed));

        assertNotNull(DialogueStudio.structuralProblem(JsonParser.parseString("{\"nodes\":\"oops\"}").getAsJsonObject()));
        assertNotNull(DialogueStudio.structuralProblem(JsonParser.parseString("{\"nodes\":[{\"lines\":[]}]}").getAsJsonObject()));
        assertNotNull(DialogueStudio.structuralProblem(JsonParser.parseString(
                "{\"nodes\":[{\"id\":\"a\",\"choices\":[{\"id\":\"b\",\"when\":3}]}]}").getAsJsonObject()));

        reparsed.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("lines").set(0,
                new com.google.gson.JsonPrimitive("Edited by hand."));
        studio.replace(reparsed);
        assertEquals("Edited by hand.", studio.label(0).title());
    }

    @Test void messagesCanBeDuplicatedAndReorderedWithoutBreakingLinks() {
        DialogueStudio studio = new DialogueStudio(town());
        int copy = studio.duplicateMessage(1);
        assertEquals(2, copy);
        assertNotEquals("town", studio.messageId(copy));
        assertEquals(studio.messageText(1), studio.messageText(copy));
        assertEquals(1, studio.nextIndex(0, 0));

        int moved = studio.moveMessage(3, -3);
        assertEquals(0, moved);
        assertEquals("offer", studio.messageId(0));
        assertEquals(0, studio.nextIndex(1, 1));
        assertEquals(1, studio.startIndex());
        NpcInteractions.parse(studio.document());

        int reply = studio.moveReply(1, 3, -3);
        assertEquals(0, reply);
        assertEquals("Goodbye", studio.replyText(1, 0));
        studio.deleteReply(1, 0);
        assertEquals(3, studio.replyCount(1));
    }

    @Test void templatesCanBePointedAtTheNpcsOwnQuest() {
        DialogueStudio studio = new DialogueStudio(NpcDialogueTemplates.of(NpcDialogueTemplates.Template.QUEST_GIVER));
        studio.retargetQuest(NpcDialogueTemplates.DEFAULT_QUEST, "rotas:goblin_hunt");
        assertFalse(studio.json().contains(NpcDialogueTemplates.DEFAULT_QUEST));
        assertTrue(DialogueStudioCheck.run(studio, ELENA).isEmpty());
    }

    @Test void applyingATemplateKeepsGiftsSettingsAndMovement() {
        JsonObject document = town();
        document.add("gifts", JsonParser.parseString(
                "[{\"id\":\"fruit\",\"items\":[\"minecraft:apple\"],\"rewards\":[]}]").getAsJsonArray());
        document.addProperty("shop_enabled", false);
        document.add("behavior", JsonParser.parseString(
                "{\"enabled\":true,\"movement\":\"patrol\",\"patrol\":[{\"x\":1,\"y\":64,\"z\":2}]}").getAsJsonObject());
        DialogueStudio studio = new DialogueStudio(document);
        studio.applyTemplate(NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GREETER));

        NpcInteractions.Definition definition = NpcInteractions.parse(studio.document());
        assertEquals("hello", definition.start());
        assertEquals(2, definition.nodes().size());
        assertEquals("fruit", definition.gifts().get(0).id());
        assertFalse(definition.shop());
        assertEquals("patrol", definition.behavior().movement());

        DialogueStudio giftless = new DialogueStudio(town());
        giftless.applyTemplate(NpcDialogueTemplates.of(NpcDialogueTemplates.Template.GIFT_RECEIVER));
        assertEquals(1, NpcInteractions.parse(giftless.document()).gifts().size());
    }

    @Test void previewWalksBranchesWithoutPerformingActions() {
        DialogueStudio studio = new DialogueStudio(town());
        DialoguePreview.Step opening = DialoguePreview.step(studio, studio.startIndex());
        assertEquals("Welcome to Ashford.\nMind the gate.", opening.speech());
        assertEquals(4, opening.replies().size());

        DialoguePreview.Result toTown = DialoguePreview.choose(studio, 0, 0);
        assertEquals(1, toTown.message());
        assertFalse(toTown.ended());
        assertNull(toTown.action());

        DialoguePreview.Result accept = DialoguePreview.choose(studio, 2, 0);
        assertEquals(DialogueStudio.Outcome.ACCEPT_QUEST, accept.action());
        assertEquals("rotas:goblin_hunt", accept.target());
        assertEquals(0, accept.message());

        DialoguePreview.Result shop = DialoguePreview.choose(studio, 0, 2);
        assertEquals(DialogueStudio.Outcome.OPEN_SHOP, shop.action());
        assertTrue(shop.ended());

        assertTrue(DialoguePreview.choose(studio, 0, 3).ended());
        assertEquals(List.of(DialogueStudio.ConditionKind.QUEST), DialoguePreview.step(studio, 2).replies().get(0).conditions());

        studio.deleteMessage(1);
        assertTrue(DialoguePreview.choose(studio, 0, 0).broken());
    }

    private static String actionType(DialogueStudio studio, int message, int reply) {
        return studio.document().getAsJsonArray("nodes").get(message).getAsJsonObject()
                .getAsJsonArray("choices").get(reply).getAsJsonObject()
                .getAsJsonObject("action").get("type").getAsString();
    }
}
