package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestRepublishTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void removingTheFirstObjectiveKeepsProgressOnTheRest() {
        var quest = new QuestDef("q");
        quest.objectives().add(new Objective(ObjectiveType.TALK_NPC));
        quest.objectives().add(new Objective(ObjectiveType.KILL_MOB));
        quest.freezeObjectiveKeys();
        var active = new ActiveQuest("q", 1, 2);
        ObjectiveEngine.migrate(active, quest);
        active.setProgress(1, 1);
        active.setComplete(1, true);

        var edited = QuestDef.load(quest.save());
        edited.objectives().remove(0);
        edited.objectives().add(new Objective(ObjectiveType.BREAK_BLOCK));
        edited.freezeObjectiveKeys();
        var reloaded = ActiveQuest.load(active.save());
        ObjectiveEngine.migrate(reloaded, edited);

        assertTrue(reloaded.isComplete(0), "the kill stays done");
        assertFalse(reloaded.isComplete(1), "the new objective starts fresh");
        assertNotEquals(edited.objectives().get(0).key(), edited.objectives().get(1).key());
    }
}
