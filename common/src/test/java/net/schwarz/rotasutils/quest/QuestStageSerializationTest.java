package net.schwarz.rotasutils.quest;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestStageSerializationTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stagedQuestRoundTripsWithoutLosingLegacyFields() {
        QuestDef quest = new QuestDef("rotas:quest/hunt");
        quest.setVersion(7);
        quest.setPublished(true);
        quest.setName("Village Hunt");
        quest.setDescription("Speak to the scout, then clear the ridge.");
        quest.setRepeat(QuestDef.Repeat.DAILY);
        quest.setCooldownSeconds(3_600);

        Objective talk = new Objective(ObjectiveType.TALK_NPC);
        talk.setKey("intro/talk");
        talk.setDescription("Speak to the scout");
        talk.setKernelEvent("rotas:event/talk", Map.of("npc", "rotas:scout"));
        Objective kill = new Objective(ObjectiveType.KILL_MOB);
        kill.setKey("hunt/kill");
        kill.params().put("amount", 5);
        quest.objectives().addAll(List.of(talk, kill));

        List<QuestStage> expectedStages = List.of(
                new QuestStage("intro", "Speak", List.of("intro/talk"),
                        List.of(new QuestStage.Branch(1, "rotas:condition/scout_ready")),
                        "rotas:reward/intro"),
                new QuestStage("hunt", "Hunt", List.of("hunt/kill"), List.of(), "")
        );
        quest.stages().addAll(expectedStages);
        quest.setKernelOrigin("rotas:quest/hunt");
        quest.setResetPolicy("DAILY");
        quest.setBountyLimit(25);

        QuestDef loaded = QuestDef.load(quest.save());

        assertEquals("rotas:quest/hunt", loaded.id());
        assertEquals(7, loaded.version());
        assertTrue(loaded.published());
        assertEquals("Village Hunt", loaded.name());
        assertEquals("Speak to the scout, then clear the ridge.", loaded.description());
        assertEquals(QuestDef.Repeat.DAILY, loaded.repeat());
        assertEquals(3_600, loaded.cooldownSeconds());
        assertEquals(List.of("intro/talk", "hunt/kill"),
                loaded.objectives().stream().map(Objective::key).toList());
        assertEquals("rotas:event/talk", loaded.objectives().get(0).kernelEvent());
        assertEquals(Map.of("npc", "rotas:scout"), loaded.objectives().get(0).kernelMatch());
        assertEquals(5, loaded.objectives().get(1).requiredAmount());
        assertEquals(expectedStages, loaded.stages());
        assertEquals("rotas:quest/hunt", loaded.kernelOrigin());
        assertEquals("DAILY", loaded.resetPolicy());
        assertEquals(25, loaded.bountyLimit());

        ActiveQuest active = new ActiveQuest(quest.id(), quest.version(), 2);
        active.setStartedAt(1_700_000_000L);
        active.setDeadline(1_700_003_600L);
        active.setProgress(0, 1);
        active.setComplete(0, true);
        active.setStage(1);
        active.setProgress("intro/talk", 1);
        active.setComplete("intro/talk", true);
        active.setProgress("hunt/kill", 4);
        ActiveQuest loadedActive = ActiveQuest.load(active.save());

        assertEquals(1_700_000_000L, loadedActive.startedAt());
        assertEquals(1_700_003_600L, loadedActive.deadline());
        assertEquals(1, loadedActive.progress(0));
        assertTrue(loadedActive.isComplete(0));
        assertEquals(1, loadedActive.stage());
        assertEquals(Map.of("intro/talk", 1, "hunt/kill", 4), loadedActive.keyedProgress());
        assertTrue(loadedActive.isComplete("intro/talk"));
        assertFalse(loadedActive.isComplete("hunt/kill"));
    }

    @Test
    void packetRoundTripsStagedDefinitionAndKeyedProgress() {
        QuestDef quest = new QuestDef("rotas:quest/packet");
        Objective objective = new Objective(ObjectiveType.KILL_MOB);
        objective.setKey("hunt/kill");
        objective.setKernelEvent("rotas:event/kill", Map.of("mob", "minecraft:zombie"));
        quest.objectives().add(objective);
        List<QuestStage> stages = List.of(new QuestStage("hunt", "Hunt", List.of("hunt/kill"),
                List.of(new QuestStage.Branch(0, "rotas:condition/retry")), "rotas:reward/hunt"));
        quest.stages().addAll(stages);
        quest.setKernelOrigin("rotas:quest/packet");
        quest.setResetPolicy("COOLDOWN");
        quest.setBountyLimit(100);

        FriendlyByteBuf questBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            quest.write(questBuffer);
            QuestDef decoded = QuestDef.read(questBuffer);
            assertEquals(stages, decoded.stages());
            assertEquals("hunt/kill", decoded.objectives().get(0).key());
            assertEquals("rotas:event/kill", decoded.objectives().get(0).kernelEvent());
            assertEquals(Map.of("mob", "minecraft:zombie"), decoded.objectives().get(0).kernelMatch());
            assertEquals("rotas:quest/packet", decoded.kernelOrigin());
            assertEquals("COOLDOWN", decoded.resetPolicy());
            assertEquals(100, decoded.bountyLimit());
        } finally {
            questBuffer.release();
        }

        ActiveQuest active = new ActiveQuest(quest.id(), 1, 1);
        active.setStage(1);
        active.setProgress(0, 2);
        active.setComplete(0, true);
        active.setProgress("hunt/kill", 2);
        active.setComplete("hunt/kill", true);
        FriendlyByteBuf progressBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            active.write(progressBuffer);
            ActiveQuest decoded = ActiveQuest.read(progressBuffer);
            assertEquals(1, decoded.stage());
            assertEquals(2, decoded.progress(0));
            assertTrue(decoded.isComplete(0));
            assertEquals(Map.of("hunt/kill", 2), decoded.keyedProgress());
            assertTrue(decoded.isComplete("hunt/kill"));
        } finally {
            progressBuffer.release();
        }
    }

    @Test
    void legacyTagsLoadWithGeneratedKeysWithoutRewritingNewFields() {
        QuestDef original = new QuestDef("legacy");
        original.setName("Legacy quest");
        original.objectives().add(new Objective(ObjectiveType.TALK_NPC));
        original.objectives().add(new Objective(ObjectiveType.KILL_MOB));
        CompoundTag legacyQuestTag = original.save();
        legacyQuestTag.remove("stages");
        legacyQuestTag.remove("kernel_origin");
        legacyQuestTag.remove("reset_policy");
        legacyQuestTag.remove("bounty_limit");
        for (int i = 0; i < legacyQuestTag.getList("objectives", Tag.TAG_COMPOUND).size(); i++) {
            legacyQuestTag.getList("objectives", Tag.TAG_COMPOUND).getCompound(i).remove("objective_key");
        }

        QuestDef loadedQuest = QuestDef.load(legacyQuestTag);

        assertEquals(List.of("o0", "o1"), loadedQuest.objectives().stream().map(Objective::key).toList());
        assertTrue(loadedQuest.stages().isEmpty());
        assertEquals("", loadedQuest.kernelOrigin());
        assertEquals("", loadedQuest.resetPolicy());
        assertEquals(0, loadedQuest.bountyLimit());
        CompoundTag resavedQuest = loadedQuest.save();
        assertFalse(resavedQuest.contains("stages"));
        assertFalse(resavedQuest.contains("kernel_origin"));
        assertFalse(resavedQuest.contains("reset_policy"));
        assertFalse(resavedQuest.contains("bounty_limit"));
        assertFalse(resavedQuest.getList("objectives", Tag.TAG_COMPOUND).getCompound(0).contains("objective_key"));
        assertFalse(resavedQuest.getList("objectives", Tag.TAG_COMPOUND).getCompound(1).contains("objective_key"));

        ActiveQuest originalActive = new ActiveQuest("legacy", 1, 2);
        originalActive.setProgress(0, 3);
        originalActive.setProgress(1, 7);
        originalActive.setComplete(0, true);
        CompoundTag legacyActiveTag = originalActive.save();
        legacyActiveTag.remove("stage");
        legacyActiveTag.remove("key_progress");
        legacyActiveTag.remove("key_complete");

        ActiveQuest loadedActive = ActiveQuest.load(legacyActiveTag);

        assertEquals(0, loadedActive.stage());
        assertEquals(3, loadedActive.progress(0));
        assertEquals(7, loadedActive.progress(1));
        assertTrue(loadedActive.isComplete(0));
        assertTrue(loadedActive.keyedProgress().isEmpty());
        CompoundTag resavedActive = loadedActive.save();
        assertFalse(resavedActive.contains("stage"));
        assertFalse(resavedActive.contains("key_progress"));
        assertFalse(resavedActive.contains("key_complete"));
    }

    @Test
    void stagedMetadataRejectsInvalidBoundsAndReferences() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuestStage("UPPER", "Bad", List.of("objective"), List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> new QuestStage("valid", "Bad", List.of("not valid"), List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> new QuestStage("valid", "Bad", List.of("objective"), List.of(), "x".repeat(201)));

        QuestDef duplicateKeys = new QuestDef("duplicate");
        Objective first = new Objective(ObjectiveType.TALK_NPC);
        first.setKey("same");
        Objective second = new Objective(ObjectiveType.KILL_MOB);
        second.setKey("same");
        duplicateKeys.objectives().addAll(List.of(first, second));
        duplicateKeys.stages().add(new QuestStage("one", "One", List.of("same"), List.of(), ""));
        assertThrows(IllegalArgumentException.class, duplicateKeys::save);

        QuestDef badBranch = stagedQuest(1);
        badBranch.stages().add(new QuestStage("second", "Second", List.of("o0"),
                List.of(new QuestStage.Branch(2, "")), ""));
        assertThrows(IllegalArgumentException.class, badBranch::save);

        QuestDef tooManyStages = stagedQuest(17);
        assertThrows(IllegalArgumentException.class, tooManyStages::save);

        QuestDef duplicateStages = stagedQuest(1);
        duplicateStages.stages().add(new QuestStage("s0", "Duplicate", List.of("o0"), List.of(), ""));
        assertThrows(IllegalArgumentException.class, duplicateStages::save);

        QuestDef unknownObjective = stagedQuest(1);
        unknownObjective.stages().clear();
        unknownObjective.stages().add(new QuestStage("unknown", "Unknown", List.of("missing"), List.of(), ""));
        assertThrows(IllegalArgumentException.class, unknownObjective::save);

        CompoundTag emptyStages = new QuestDef("empty-stages").save();
        emptyStages.put("stages", new ListTag());
        assertThrows(IllegalArgumentException.class, () -> QuestDef.load(emptyStages));

        QuestDef bounds = new QuestDef("bounds");
        assertThrows(IllegalArgumentException.class, () -> bounds.setBountyLimit(-1));
        assertThrows(IllegalArgumentException.class, () -> bounds.setBountyLimit(1_000_001));
        assertThrows(IllegalArgumentException.class, () -> bounds.setKernelOrigin("x".repeat(201)));
    }

    private static QuestDef stagedQuest(int count) {
        QuestDef quest = new QuestDef("staged");
        Objective objective = new Objective(ObjectiveType.TALK_NPC);
        objective.setKey("o0");
        quest.objectives().add(objective);
        for (int index = 0; index < count; index++) {
            quest.stages().add(new QuestStage("s" + index, "Stage " + index, List.of("o0"), List.of(), ""));
        }
        return quest;
    }
}
