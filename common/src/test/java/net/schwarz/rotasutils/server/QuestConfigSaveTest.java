package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.quest.QuestDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestConfigSaveTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static boolean hasError(RotasData data, QuestDef quest) {
        return ConfigService.validate(data, "quest/" + quest.id(), quest.save()).stream()
                .anyMatch(issue -> issue.severity() == Validation.Severity.ERROR);
    }

    @Test void incompleteDraftSavesButCannotPublish() {
        var data = new RotasData();
        var quest = new QuestDef("q1");
        assertFalse(hasError(data, quest), "an unfinished draft must still save");
        quest.setPublished(true);
        assertTrue(hasError(data, quest), "publishing still requires a name and objectives");
    }

    @Test void removedKeysDoNotComeBackFromAnOlderAppliedCopy() {
        var data = new RotasData();
        var quest = new QuestDef("q1");
        data.putQuest(quest);
        var older = quest.save();
        older.put("stages", new ListTag());
        data.configHistory().stage("actor", "quest/q1", quest.save(), older, -1);
        data.configHistory().applied("actor", "quest/q1", "test");
        assertFalse(ConfigService.snapshot(data, "quest/q1").contains("stages"));
    }
}
