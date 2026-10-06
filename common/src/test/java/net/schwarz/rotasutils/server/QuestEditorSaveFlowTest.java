package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestEditorSaveFlowTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void freshQuestMatchesItsClientCopy() {
        var created = new QuestDef("quest_1");
        assertEquals(created.save(), QuestDef.load(created.save()).save(),
                "a new quest must survive save/load unchanged, or the editor baseline never matches live");
    }

    @Test void npcOnlyQuestPublishesWithoutErrors() {
        var data = new RotasData();
        data.putQuest(new QuestDef("quest_1"));
        var edited = QuestDef.load(data.quest("quest_1").save());
        edited.setName("Find the thief");
        var talk = new Objective(ObjectiveType.TALK_NPC);
        talk.params().put("npc_name", "Elder");
        edited.objectives().add(talk);
        edited.setPublished(true);

        var live = ConfigService.snapshot(data, "quest/quest_1");
        assertEquals(live, ConfigService.merge(live, data.quest("quest_1").save()), "baseline check");
        var errors = ConfigService.validate(data, "quest/quest_1", edited.save()).stream()
                .filter(issue -> issue.severity() == Validation.Severity.ERROR).toList();
        assertTrue(errors.isEmpty(), errors.toString());
    }
}
