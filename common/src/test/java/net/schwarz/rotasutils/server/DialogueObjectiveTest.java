package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.quest.objective.*;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DialogueObjectiveTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void configuredNpcStillRequiresTheCorrectCompletedResponse() {
        UUID npc = UUID.randomUUID();
        var objective = new Objective(ObjectiveType.TALK_NPC);
        objective.params().put("npc_uuid", npc.toString());
        objective.params().put("dialogue", "hello");
        objective.params().put("require_choice", "accept");
        objective.params().put("require_full_dialogue", true);
        var event = new QuestEvent(EventKind.TALK_NPC).entityUuid(npc);
        assertNotNull(ObjectiveEngine.matches(null, null, objective, event));
        event.dialogueId("hello").choiceId("wrong").dialogueComplete(true);
        assertNotNull(ObjectiveEngine.matches(null, null, objective, event));
        event.choiceId("accept").dialogueComplete(false);
        assertNotNull(ObjectiveEngine.matches(null, null, objective, event));
        event.dialogueComplete(true);
        assertNull(ObjectiveEngine.matches(null, null, objective, event));
    }
    @Test void ordinaryTalkIsNotCountedAgainForEveryResponse() {
        var objective = new Objective(ObjectiveType.TALK_NPC);
        var event = new QuestEvent(EventKind.TALK_NPC);
        assertNull(ObjectiveEngine.matches(null, null, objective, event));
        assertNotNull(ObjectiveEngine.matches(null, null, objective, event.dialogueId("hello").choiceId("continue")));
    }
}
