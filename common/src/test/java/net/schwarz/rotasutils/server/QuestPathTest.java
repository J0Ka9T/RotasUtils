package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.quest.reward.RewardType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestPathTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static QuestDef quest() {
        var quest = new QuestDef("thief");
        quest.objectives().add(new Objective(ObjectiveType.TALK_NPC));
        var catchThief = new Objective(ObjectiveType.DIALOGUE_CHOICE);
        catchThief.setOpens("Catch ");
        var helpThief = new Objective(ObjectiveType.DIALOGUE_CHOICE);
        helpThief.setOpens("help");
        var investigate = new Objective(ObjectiveType.TALK_NPC);
        investigate.setPath("help");
        quest.objectives().add(catchThief);
        quest.objectives().add(helpThief);
        quest.objectives().add(investigate);
        var bounty = new Reward(RewardType.CURRENCY);
        bounty.setPath("catch");
        quest.rewards().add(bounty);
        return QuestDef.load(quest.save());
    }

    @Test void choosingAPathLocksTheOtherChoiceAndItsObjectives() {
        var quest = quest();
        var active = new ActiveQuest("thief", 1, 4);
        assertEquals("", QuestService.chosenPath(quest, active));
        assertFalse(ObjectiveEngine.stepUnlocked(quest, active, quest.objectives().get(3), 3), "path waits for its choice");
        active.setComplete(0, true);
        assertFalse(QuestService.allRequiredComplete(quest, active), "a choice is still required");

        active.setComplete(1, true);
        assertEquals("catch", QuestService.chosenPath(quest, active));
        assertFalse(ObjectiveEngine.stepUnlocked(quest, active, quest.objectives().get(2), 2), "other choice closes");
        assertFalse(ObjectiveEngine.stepUnlocked(quest, active, quest.objectives().get(3), 3), "help path stays locked");
        assertTrue(QuestService.allRequiredComplete(quest, active), "catch path needs nothing else");
        assertEquals("catch", quest.rewards().get(0).path());
    }

    @Test void helpPathRequiresItsOwnObjective() {
        var quest = quest();
        var active = new ActiveQuest("thief", 1, 4);
        active.setComplete(0, true);
        active.setComplete(2, true);
        assertEquals("help", QuestService.chosenPath(quest, active));
        assertTrue(ObjectiveEngine.stepUnlocked(quest, active, quest.objectives().get(3), 3));
        assertFalse(QuestService.allRequiredComplete(quest, active));
        active.setComplete(3, true);
        assertTrue(QuestService.allRequiredComplete(quest, active));
    }

    @Test void separateQuestionsOnSeparateStepsAreAllRequired() {
        var quest = new QuestDef("three_npcs");
        for (int step = 0; step < 3; step++) {
            var answer = new Objective(ObjectiveType.DIALOGUE_CHOICE);
            answer.setOpens("npc" + step);
            answer.setStep(step);
            quest.objectives().add(answer);
        }
        quest = QuestDef.load(quest.save());
        var active = new ActiveQuest("three_npcs", 1, 3);
        active.setComplete(0, true);
        assertFalse(QuestService.allRequiredComplete(quest, active), "two NPCs still to talk to");
        assertTrue(QuestService.onPath(quest, active, 1), "answering one NPC must not close another NPC's question");
        assertTrue(ObjectiveEngine.stepUnlocked(quest, active, quest.objectives().get(1), 1));
        active.setComplete(1, true);
        active.setComplete(2, true);
        assertTrue(QuestService.allRequiredComplete(quest, active));
        assertEquals(java.util.Set.of("npc0", "npc1", "npc2"), QuestService.chosenPaths(quest, active));
    }
}
