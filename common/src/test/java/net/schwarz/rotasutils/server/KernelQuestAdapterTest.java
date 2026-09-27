package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.core.ActionEngine;
import net.schwarz.rotasutils.core.ConditionEngine;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentPacks;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.ItemCatalog;
import net.schwarz.rotasutils.core.MonsterCatalog;
import net.schwarz.rotasutils.core.QuestDefinitions;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.QuestStage;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelQuestAdapterTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void projectsTwoStagesWithoutLosingKernelAuthorityMetadata() throws Exception {
        ContentRegistry.Snapshot snapshot = compiledQuestSnapshot();
        QuestDefinitions.Quest source = snapshot.quests().get(new ContentId("rotas:quest/hunt"));

        KernelQuestAdapter adapter = new KernelQuestAdapter(snapshot, List.of());
        KernelQuestAdapter.Projection projection = adapter.find("rotas:quest/hunt");
        QuestDef quest = projection.quest();

        assertSame(source, projection.source());
        assertSame(projection, adapter.projections().get("rotas:quest/hunt"));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.projections().put("rotas:quest/other", projection));
        assertEquals("rotas:quest/hunt", quest.id());
        assertEquals("rotas:quest/hunt", quest.kernelOrigin());
        assertTrue(quest.published());
        assertEquals("Village Hunt", quest.name());
        assertEquals(QuestDef.Repeat.COOLDOWN, quest.repeat());
        assertEquals(900, quest.cooldownSeconds());
        assertEquals("COOLDOWN", quest.resetPolicy());
        assertEquals(4, quest.bountyLimit());

        assertEquals(List.of("s0/o0", "s1/o0"),
                quest.objectives().stream().map(Objective::key).toList());
        Objective hunt = quest.objectives().get(0);
        assertEquals(ObjectiveType.CUSTOM, hunt.type());
        assertEquals("Defeat undead", hunt.description());
        assertEquals(3, hunt.requiredAmount());
        assertEquals("rotas:monster_defeated", hunt.params().getString("event", ""));
        assertEquals("rotas:monster_defeated", hunt.kernelEvent());
        assertEquals(Map.of(
                "event.monster_profile", "rotas:monster/undead",
                "event.weapon", "minecraft:iron_sword"), hunt.kernelMatch());

        QuestDef loaded = QuestDef.load(quest.save());
        Objective loadedHunt = loaded.objectives().get(0);
        assertEquals(ObjectiveType.CUSTOM, loadedHunt.type());
        assertEquals("CUSTOM", loadedHunt.save().getString("type"));
        assertEquals("rotas:monster_defeated", loadedHunt.kernelEvent());
        assertEquals(Map.of(
                "event.monster_profile", "rotas:monster/undead",
                "event.weapon", "minecraft:iron_sword"), loadedHunt.kernelMatch());

        Objective report = quest.objectives().get(1);
        assertEquals(ObjectiveType.CUSTOM, report.type());
        assertEquals("rotas:dialogue_complete", report.kernelEvent());
        assertEquals(Map.of("event.npc", "rotas:scout"), report.kernelMatch());

        assertEquals(List.of(
                new QuestStage("s0", "Track", List.of("s0/o0"),
                        List.of(new QuestStage.Branch(1, "")), "rotas:reward/stage"),
                new QuestStage("s1", "Report", List.of("s1/o0"), List.of(), "")
        ), quest.stages());
    }

    @Test
    void customObjectiveHasCanonicalEnumIdentityAndLoadsLegacySaves() {
        assertEquals("CUSTOM", ObjectiveType.CUSTOM.name());
        assertNotSame(ObjectiveType.CUSTOM_EVENT, ObjectiveType.CUSTOM);
        assertTrue(java.util.Arrays.asList(ObjectiveType.VALUES).contains(ObjectiveType.CUSTOM));

        Objective canonical = new Objective(ObjectiveType.CUSTOM);
        canonical.setKernelEvent("rotas:event/custom", Map.of("event.variant", "alpha"));
        CompoundTag canonicalTag = canonical.save();
        assertEquals("CUSTOM", canonicalTag.getString("type"));
        assertEquals(ObjectiveType.CUSTOM, Objective.load(canonicalTag).type());

        CompoundTag legacyTag = canonicalTag.copy();
        legacyTag.putString("type", "CUSTOM_EVENT");
        Objective loadedLegacy = Objective.load(legacyTag);
        assertEquals(ObjectiveType.CUSTOM, loadedLegacy.type());
        assertEquals("rotas:event/custom", loadedLegacy.kernelEvent());
        assertEquals(Map.of("event.variant", "alpha"), loadedLegacy.kernelMatch());
    }

    @Test
    void mapsEveryKernelResetPolicyExactly() {
        assertEquals(QuestDef.Repeat.NEVER, projectReset(QuestDefinitions.Reset.NONE, false, 0).repeat());
        assertEquals(QuestDef.Repeat.UNLIMITED, projectReset(QuestDefinitions.Reset.NONE, true, 0).repeat());
        assertEquals(QuestDef.Repeat.DAILY, projectReset(QuestDefinitions.Reset.DAILY, true, 0).repeat());
        assertEquals(QuestDef.Repeat.WEEKLY, projectReset(QuestDefinitions.Reset.WEEKLY, true, 0).repeat());
        QuestDef cooldown = projectReset(QuestDefinitions.Reset.COOLDOWN, true, 3_601);
        assertEquals(QuestDef.Repeat.COOLDOWN, cooldown.repeat());
        assertEquals(3_601, cooldown.cooldownSeconds());
    }

    @Test
    void rejectsWorldAuthoredIdCollisionWithoutMutation() throws Exception {
        ContentRegistry.Snapshot snapshot = compiledQuestSnapshot();
        QuestDef worldAuthored = new QuestDef("rotas:quest/hunt");
        worldAuthored.setName("World-authored hunt");
        CompoundTag before = worldAuthored.save().copy();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> KernelQuestAdapter.project(snapshot, List.of(worldAuthored)));

        assertEquals("Quest ID collision: rotas:quest/hunt", failure.getMessage());
        assertEquals(before, worldAuthored.save());
        assertEquals("", worldAuthored.kernelOrigin());
    }

    @Test
    void reconciliationPublishesOnlyChangedOwnedProjections() throws Exception {
        ContentRegistry.Snapshot snapshot = compiledQuestSnapshot();
        Map<String, KernelQuestAdapter.Projection> projections = KernelQuestAdapter.project(snapshot, List.of());
        RotasData data = new RotasData();
        QuestDef ordinary = new QuestDef("world:quest/local");
        data.quests().put(ordinary.id(), ordinary);

        data.reconcileKernelQuests(projections);

        assertTrue(data.isDirty());
        assertSame(ordinary, data.quest(ordinary.id()));
        assertEquals("rotas:quest/hunt", data.quest("rotas:quest/hunt").kernelOrigin());

        data.setDirty(false);
        Map<String, KernelQuestAdapter.Projection> equivalent =
                KernelQuestAdapter.project(snapshot, data.quests().values());
        data.reconcileKernelQuests(equivalent);

        assertFalse(data.isDirty(), "serialized-equivalent projections must not dirty the world store");
        assertSame(ordinary, data.quest(ordinary.id()));
    }

    @Test
    void reconciliationPreflightsEveryProjectionBeforePublishingAny() {
        QuestDefinitions.Quest first = kernelQuest("rotas:quest/a_first");
        QuestDefinitions.Quest colliding = kernelQuest("rotas:quest/z_collision");
        Map<String, KernelQuestAdapter.Projection> projections =
                KernelQuestAdapter.project(snapshotOf(first, colliding), List.of());
        RotasData data = new RotasData();
        QuestDef worldAuthored = new QuestDef("rotas:quest/z_collision");
        worldAuthored.setName("Keep this definition");
        data.quests().put(worldAuthored.id(), worldAuthored);
        CompoundTag before = data.save(new CompoundTag()).copy();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> data.reconcileKernelQuests(projections));

        assertEquals("Quest ID collision: rotas:quest/z_collision", failure.getMessage());
        assertNull(data.quest("rotas:quest/a_first"));
        assertSame(worldAuthored, data.quest("rotas:quest/z_collision"));
        assertEquals(before, data.save(new CompoundTag()));
        assertFalse(data.isDirty());
    }

    private static QuestDef projectReset(QuestDefinitions.Reset reset, boolean repeatable, int cooldownSeconds) {
        ContentId id = new ContentId("rotas:quest/reset_" + reset.name().toLowerCase());
        var objective = new QuestDefinitions.Objective("o0", new ContentId("rotas:event"), Map.of(), 1,
                ConditionEngine.ALWAYS, "Event");
        var stage = new QuestDefinitions.Stage(0, "Stage", List.of(objective), null, List.of());
        var quest = new QuestDefinitions.Quest(id, "Reset", ConditionEngine.ALWAYS, List.of(stage), reset,
                cooldownSeconds, repeatable, null, 0);
        return KernelQuestAdapter.project(snapshotOf(quest), List.of()).get(id.value()).quest();
    }

    private static QuestDefinitions.Quest kernelQuest(String id) {
        var objective = new QuestDefinitions.Objective("o0", new ContentId("rotas:event"), Map.of(), 1,
                ConditionEngine.ALWAYS, "Event");
        var stage = new QuestDefinitions.Stage(0, "Stage", List.of(objective), null, List.of());
        return new QuestDefinitions.Quest(new ContentId(id), "Quest", ConditionEngine.ALWAYS, List.of(stage),
                QuestDefinitions.Reset.NONE, 0, false, null, 0);
    }

    private static ContentRegistry.Snapshot compiledQuestSnapshot() throws Exception {
        ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));
        List<ContentRegistry.Source> sources = List.of(
                source("action/reward", "action", "{\"type\":\"currency\",\"id\":\"rotas:gold\",\"amount\":5}"),
                source("reward/stage", "reward", "{\"actions\":[\"rotas:action/reward\"]}"),
                source("reward/final", "reward", "{\"actions\":[\"rotas:action/reward\"]}"),
                source("quest/hunt", "quest", """
                        {
                          "label":"Village Hunt",
                          "requirement":{"type":"always"},
                          "reset":"COOLDOWN",
                          "cooldown_seconds":900,
                          "repeatable":true,
                          "reward":"rotas:reward/final",
                          "bounty_limit":4,
                          "stages":[
                            {
                              "label":"Track",
                              "reward":"rotas:reward/stage",
                              "objectives":[{
                                "event":"rotas:monster_defeated",
                                "match":{
                                  "event.monster_profile":"rotas:monster/undead",
                                  "event.weapon":"minecraft:iron_sword"
                                },
                                "count":3,
                                "condition":{"type":"always"},
                                "label":"Defeat undead"
                              }],
                              "branches":[{"stage":1,"condition":{"type":"always"}}]
                            },
                            {
                              "label":"Report",
                              "objectives":[{
                                "event":"rotas:dialogue_complete",
                                "match":{"event.npc":"rotas:scout"},
                                "label":"Report to the scout"
                              }]
                            }
                          ]
                        }
                        """)
        );
        ContentRegistry.Prepared prepared = registry.prepare(sources);
        assertTrue(prepared.valid(), prepared.issues().toString());
        return prepared.snapshot();
    }

    private static ContentRegistry.Source source(String id, String kind, String body) throws Exception {
        return new ContentRegistry.Source(id, ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:" + id + "\",\"kind\":\"" + kind
                        + "\",\"body\":" + body + "}"));
    }

    private static ContentRegistry.Snapshot snapshotOf(QuestDefinitions.Quest... quests) {
        Map<ContentId, QuestDefinitions.Quest> byId = new LinkedHashMap<>();
        for (QuestDefinitions.Quest quest : quests) {
            byId.put(quest.id(), quest);
        }
        return new ContentRegistry.Snapshot("test", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                MonsterCatalog.empty(), ItemCatalog.empty(), byId, Map.of());
    }
}
