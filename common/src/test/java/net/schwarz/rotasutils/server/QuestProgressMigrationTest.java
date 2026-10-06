package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.core.ConditionEngine;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.ItemCatalog;
import net.schwarz.rotasutils.core.MonsterCatalog;
import net.schwarz.rotasutils.core.QuestDefinitions;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestProgressMigrationTest {
    private static final ContentId HUNT = new ContentId("rotas:quest/hunt");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final long NOW = 1_700_000_000L;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void migratesActiveStageOnceAndNeverRewards() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        progress.setXp(40);
        progress.addRankPoints(9);
        progress.claimOnce("kernel|quest|rotas:quest/other|once");
        old(progress, HUNT, "stage", "0");
        old(progress, HUNT, "p.o0", "2");
        old(progress, HUNT, "p.o1", "2");
        Snapshot before = Snapshot.of(progress);

        var first = QuestProgressMigration.migrate(progress, hunt(), NOW);
        var second = QuestProgressMigration.migrate(progress, hunt(), NOW + 1);

        assertTrue(first.changed());
        assertTrue(first.complete());
        assertEquals(List.of(), first.diagnostics());
        assertFalse(second.changed());
        assertTrue(second.complete());

        ActiveQuest active = progress.active(HUNT.value());
        assertNotNull(active);
        assertEquals(0, active.stage());
        assertEquals(1, active.questVersion());
        assertEquals(2, active.progress("s0/o0"));
        assertFalse(active.isComplete("s0/o0"));
        assertEquals(2, active.progress("s0/o1"));
        assertTrue(active.isComplete("s0/o1"));
        assertEquals(2, active.progress(0));
        assertEquals(2, active.progress(1));
        assertTrue(active.isComplete(1));
        assertFalse(active.turnInReady());

        assertNoOldKeys(progress, HUNT);
        assertEquals("1", progress.questVariables().get(marker(HUNT)));
        before.assertNoRewardChange(progress);

        PlayerProgress reloaded = PlayerProgress.load(progress.save());
        assertEquals(2, reloaded.active(HUNT.value()).progress("s0/o1"));
        assertFalse(QuestProgressMigration.migrate(reloaded, hunt(), NOW + 2).changed());
    }

    @Test
    void laterStageMapsCurrentObjectivesAndDropsValidatedEarlierStageLeftovers() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        old(progress, HUNT, "stage", "1");
        old(progress, HUNT, "p.o0", "0");
        old(progress, HUNT, "p.o1", "2");

        var result = QuestProgressMigration.migrate(progress, hunt(), NOW);

        assertTrue(result.changed());
        assertTrue(result.complete());
        ActiveQuest active = progress.active(HUNT.value());
        assertEquals(1, active.stage());
        assertEquals(0, active.progress("s1/o0"));
        assertEquals(Map.of("s1/o0", 0), active.keyedProgress());
        assertNoOldKeys(progress, HUNT);
    }

    @Test
    void completedQuestRecordsHistoryAtOriginalTimeWithoutReceipts() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        old(progress, HUNT, "stage", "-1");
        old(progress, HUNT, "done", "1600000000");
        old(progress, HUNT, "p.o0", "1");
        Snapshot before = Snapshot.of(progress);

        var result = QuestProgressMigration.migrate(progress, hunt(), NOW);

        assertTrue(result.changed());
        assertTrue(result.complete());
        assertNull(progress.active(HUNT.value()));
        assertEquals(1, progress.completionCount(HUNT.value()));
        assertEquals(1_600_000_000L, progress.lastCompletedAt(HUNT.value()));
        assertEquals(1_600_000_900L, progress.cooldownUntil(HUNT.value()));
        assertNoOldKeys(progress, HUNT);
        before.assertNoRewardChange(progress);
        assertFalse(QuestProgressMigration.migrate(progress, hunt(), NOW + 1).changed());
        assertEquals(1, progress.completionCount(HUNT.value()));
    }

    @Test
    void abandonedStateIsClearedWithoutCanonicalState() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        old(progress, HUNT, "stage", "-1");
        old(progress, HUNT, "p.o0", "0");

        var result = QuestProgressMigration.migrate(progress, hunt(), NOW);

        assertTrue(result.changed());
        assertTrue(result.complete());
        assertNull(progress.active(HUNT.value()));
        assertEquals(0, progress.completionCount(HUNT.value()));
        assertNoOldKeys(progress, HUNT);
        assertEquals("1", progress.questVariables().get(marker(HUNT)));
    }

    @Test
    void existingCanonicalStateWinsAndValidOldKeysAreRetired() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        ActiveQuest canonical = new ActiveQuest(HUNT.value(), 1, 3);
        canonical.setStage(1);
        canonical.setProgress("s1/o0", 1);
        progress.putActive(canonical);
        CompoundTag canonicalBefore = canonical.save().copy();
        old(progress, HUNT, "stage", "0");
        old(progress, HUNT, "p.o0", "1");

        var result = QuestProgressMigration.migrate(progress, hunt(), NOW);

        assertTrue(result.changed());
        assertTrue(result.complete());
        assertEquals(canonicalBefore, progress.active(HUNT.value()).save());
        assertEquals("1", progress.questVariables().get(marker(HUNT)));
        assertNoOldKeys(progress, HUNT);
    }

    @Test
    void existingCanonicalStateWinsButMalformedOldKeysStayAndNeverRevive() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        progress.putActive(new ActiveQuest(HUNT.value(), 1, 3));
        old(progress, HUNT, "stage", "0");
        old(progress, HUNT, "p.o0", "SECRET_VALUE");

        var first = QuestProgressMigration.migrate(progress, hunt(), NOW);

        assertTrue(first.changed());
        assertFalse(first.complete());
        assertEquals(1, first.diagnostics().size());
        assertEquals("1", progress.questVariables().get(marker(HUNT)));
        assertEquals("SECRET_VALUE", progress.questVariables().get(QuestDefinitions.key(HUNT, "p.o0")));

        progress.removeActive(HUNT.value());
        var second = QuestProgressMigration.migrate(progress, hunt(), NOW + 1);

        assertFalse(second.changed());
        assertNull(progress.active(HUNT.value()), "a recorded marker must stop retained keys from reviving a quest");
    }

    @Test
    void malformedOrUnmappableStateIsRetainedWithValueFreeDiagnostics() {
        List<Map<String, String>> cases = List.of(
                Map.of("stage", "16"),
                Map.of("stage", "2"),
                Map.of("stage", "SECRET_VALUE"),
                Map.of("stage", "-3"),
                Map.of("stage", "0", "p.o0", "-2"),
                Map.of("stage", "0", "p.o0", "SECRET_VALUE"),
                Map.of("stage", "0", "p.o0", "4"),
                Map.of("stage", "1", "p.o1", "3"),
                Map.of("stage", "0", "p.o7", "1"),
                Map.of("stage", "0", "p.SECRET_VALUE", "1"),
                Map.of("stage", "0", "unexpected", "1"),
                Map.of("stage", "-1", "done", "SECRET_VALUE"),
                Map.of("stage", "-1", "done", "0"),
                Map.of("done", "1600000000"),
                Map.of("p.o0", "1"));
        for (Map<String, String> values : cases) {
            PlayerProgress progress = new PlayerProgress(PLAYER);
            values.forEach((suffix, value) -> old(progress, HUNT, suffix, value));
            Map<String, String> variablesBefore = new LinkedHashMap<>(progress.questVariables());
            progress.clearDirty();

            var result = QuestProgressMigration.migrate(progress, hunt(), NOW);

            String label = values.toString();
            assertFalse(result.changed(), label);
            assertFalse(result.complete(), label);
            assertFalse(progress.dirty(), label);
            assertEquals(1, result.diagnostics().size(), label);
            String diagnostic = result.diagnostics().get(0);
            assertTrue(diagnostic.contains(PLAYER.toString()), diagnostic);
            assertTrue(diagnostic.contains(HUNT.value()), diagnostic);
            assertTrue(diagnostic.contains("rpg.q."), diagnostic);
            assertFalse(diagnostic.contains("SECRET_VALUE") && !values.containsKey("p.SECRET_VALUE"), diagnostic);
            assertEquals(variablesBefore, progress.questVariables(), label);
            assertNull(progress.active(HUNT.value()), label);
            assertEquals(0, progress.completionCount(HUNT.value()), label);
        }
    }

    @Test
    void diagnosticsAreBoundedTo64Entries() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        QuestDefinitions.Quest[] quests = new QuestDefinitions.Quest[70];
        for (int i = 0; i < quests.length; i++) {
            quests[i] = quest(new ContentId("rotas:quest/bulk_" + i));
            old(progress, quests[i].id(), "stage", "9");
        }

        var result = QuestProgressMigration.migrate(progress, KernelQuestAdapter.project(snapshotOf(quests), List.of()), NOW);

        assertFalse(result.complete());
        assertEquals(64, result.diagnostics().size());
    }

    @Test
    void unrelatedAndUnprojectedVariablesAreIgnored() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        ContentId disabled = new ContentId("rotas:quest/disabled");
        old(progress, disabled, "stage", "4");
        progress.questVariables().put("reputation.guild", "12");
        progress.clearDirty();

        var result = QuestProgressMigration.migrate(progress, hunt(), NOW);

        assertFalse(result.changed());
        assertTrue(result.complete());
        assertFalse(progress.dirty());
        assertEquals("4", progress.questVariables().get(QuestDefinitions.key(disabled, "stage")));
        assertEquals("12", progress.questVariables().get("reputation.guild"));
    }

    @Test
    void nestedSlugPrefixesBelongToTheLongestMatchingQuest() {
        ContentId nested = new ContentId("rotas:quest/hunt.x");
        PlayerProgress progress = new PlayerProgress(PLAYER);
        old(progress, HUNT, "stage", "0");
        old(progress, nested, "stage", "1");
        old(progress, nested, "p.o0", "1");

        var result = QuestProgressMigration.migrate(progress,
                KernelQuestAdapter.project(snapshotOf(quest(HUNT), quest(nested)), List.of()), NOW);

        assertTrue(result.complete(), result.diagnostics().toString());
        assertEquals(0, progress.active(HUNT.value()).stage());
        assertEquals(1, progress.active(nested.value()).stage());
        assertEquals(1, progress.active(nested.value()).progress("s1/o0"));
        assertNoOldKeys(progress, HUNT);
    }

    @Test
    void identicalSlugsAreAmbiguousAndRetained() {
        ContentId left = new ContentId("a:b.c");
        ContentId right = new ContentId("a.b:c");
        assertEquals(QuestDefinitions.key(left, "stage"), QuestDefinitions.key(right, "stage"));
        PlayerProgress progress = new PlayerProgress(PLAYER);
        old(progress, left, "stage", "0");

        var result = QuestProgressMigration.migrate(progress,
                KernelQuestAdapter.project(snapshotOf(quest(left), quest(right)), List.of()), NOW);

        assertFalse(result.changed());
        assertFalse(result.complete());
        assertEquals(2, result.diagnostics().size());
        assertNull(progress.active(left.value()));
        assertNull(progress.active(right.value()));
        assertEquals("0", progress.questVariables().get(QuestDefinitions.key(left, "stage")));
    }

    @Test
    void removeQuestVariablesMarksDirtyOnlyWhenSomethingIsRemoved() {
        PlayerProgress progress = new PlayerProgress(PLAYER);
        progress.questVariables().put("a", "1");
        progress.clearDirty();

        progress.removeQuestVariables(List.of("missing"));
        assertFalse(progress.dirty());

        progress.removeQuestVariables(List.of("a", "missing"));
        assertTrue(progress.dirty());
        assertFalse(progress.questVariables().containsKey("a"));
    }

    private static Map<String, KernelQuestAdapter.Projection> hunt() {
        return KernelQuestAdapter.project(snapshotOf(quest(HUNT)), List.of());
    }

    private static QuestDefinitions.Quest quest(ContentId id) {
        var track = new QuestDefinitions.Objective("o0", new ContentId("rotas:monster_defeated"), Map.of(), 3,
                ConditionEngine.ALWAYS, "Track");
        var gather = new QuestDefinitions.Objective("o1", new ContentId("rotas:item_collected"), Map.of(), 2,
                ConditionEngine.ALWAYS, "Gather");
        var report = new QuestDefinitions.Objective("o0", new ContentId("rotas:dialogue_complete"), Map.of(), 1,
                ConditionEngine.ALWAYS, "Report");
        var first = new QuestDefinitions.Stage(0, "Hunt", List.of(track, gather), null, List.of());
        var second = new QuestDefinitions.Stage(1, "Report", List.of(report), null, List.of());
        return new QuestDefinitions.Quest(id, "Hunt", ConditionEngine.ALWAYS, List.of(first, second),
                QuestDefinitions.Reset.COOLDOWN, 900, true, null, 0);
    }

    private static ContentRegistry.Snapshot snapshotOf(QuestDefinitions.Quest... quests) {
        Map<ContentId, QuestDefinitions.Quest> byId = new LinkedHashMap<>();
        for (QuestDefinitions.Quest quest : quests) {
            byId.put(quest.id(), quest);
        }
        return new ContentRegistry.Snapshot("test", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                MonsterCatalog.empty(), ItemCatalog.empty(), byId, Map.of());
    }

    private static void old(PlayerProgress progress, ContentId quest, String suffix, String value) {
        progress.questVariables().put(QuestDefinitions.key(quest, suffix), value);
    }

    private static String marker(ContentId quest) {
        String hash = UUID.nameUUIDFromBytes(quest.value().getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").substring(0, 16);
        return "rpg.migration.quest." + hash;
    }

    private static void assertNoOldKeys(PlayerProgress progress, ContentId quest) {
        String prefix = QuestDefinitions.key(quest, "");
        assertTrue(progress.questVariables().keySet().stream().noneMatch(key -> key.startsWith(prefix)),
                progress.questVariables().toString());
    }

    private record Snapshot(long xp, long rankPoints, List<String> claims, int mail, CompoundTag rpg) {
        static Snapshot of(PlayerProgress progress) {
            return new Snapshot(progress.xp(), progress.rankPoints(), List.copyOf(progress.claimedRewards()),
                    progress.mailbox().size(), progress.rpg().save().copy());
        }

        void assertNoRewardChange(PlayerProgress progress) {
            assertEquals(this, of(progress));
        }
    }
}
