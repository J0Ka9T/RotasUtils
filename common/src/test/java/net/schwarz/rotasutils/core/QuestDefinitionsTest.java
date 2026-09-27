package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class QuestDefinitionsTest {
    private final ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));

    private ContentRegistry.Source source(String id, String kind, String body) throws Exception {
        return new ContentRegistry.Source(id, ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:" + id + "\",\"kind\":\"" + kind + "\",\"body\":" + body + "}"));
    }

    private QuestDefinitions.Quest parse(String body) throws Exception {
        return QuestDefinitions.quest(new ContentId("rotas:quest/test"), ContentPacks.parse(body), json -> ConditionEngine.ALWAYS);
    }

    private static KernelContext context(Map<String, String> facts) {
        return new KernelContext() {
            @Override public double number(String name) { return Double.parseDouble(facts.getOrDefault(name, "0")); }
            @Override public String text(String name) { return facts.get(name); }
            @Override public boolean requirement(String type, Map<String, String> parameters) { return true; }
            @Override public Transaction begin() { throw new UnsupportedOperationException(); }
        };
    }

    @Test void objectivesMatchOnEventTypeAndFacts() throws Exception {
        var quest = parse("{\"stages\":[{\"objectives\":[{\"event\":\"rotas:monster_defeated\",\"count\":3,"
                + "\"match\":{\"event.monster_profile\":\"rotas:monster/undead\"}}]}]}");
        var objective = quest.stages().get(0).objectives().get(0);
        assertEquals(3, objective.count());
        assertTrue(objective.matches(new ContentId("rotas:monster_defeated"), Map.of("event.monster_profile", "rotas:monster/undead")));
        assertFalse(objective.matches(new ContentId("rotas:monster_defeated"), Map.of("event.monster_profile", "rotas:monster/other")));
        assertFalse(objective.matches(new ContentId("rotas:monster_defeated"), Map.of()));
        assertFalse(objective.matches(new ContentId("rotas:item_obtained"), Map.of("event.monster_profile", "rotas:monster/undead")));
    }

    @Test void stagesAdvanceSequentiallyUnlessABranchMatches() throws Exception {
        var quest = parse("{\"stages\":["
                + "{\"objectives\":[{\"event\":\"rotas:a\"}],\"branches\":[{\"stage\":2}]},"
                + "{\"objectives\":[{\"event\":\"rotas:b\"}]},"
                + "{\"objectives\":[{\"event\":\"rotas:c\"}]}]}");
        assertEquals(2, quest.stages().get(0).next(context(Map.of()), 3), "the branch wins over the next stage");
        assertEquals(2, quest.stages().get(1).next(context(Map.of()), 3));
        assertEquals(-1, quest.stages().get(2).next(context(Map.of()), 3), "the last stage completes the quest");
        assertThrows(IllegalArgumentException.class, () -> parse("{\"stages\":[{\"objectives\":[{\"event\":\"rotas:a\"}],\"branches\":[{\"stage\":5}]}]}"),
                "a branch cannot target a stage that does not exist");
        assertThrows(IllegalArgumentException.class, () -> parse("{\"stages\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"stages\":[{\"objectives\":[]}]}"));
    }

    @Test void resetWindowsAndRepeatAvailabilityFollowTheDeclaredPolicy() throws Exception {
        var once = parse("{\"stages\":[{\"objectives\":[{\"event\":\"rotas:a\"}]}]}");
        assertEquals(QuestDefinitions.Reset.NONE, once.reset());
        assertFalse(once.repeatable());
        assertTrue(once.available(0, 1000));
        assertFalse(once.available(1, 100000), "a one-shot quest never returns");

        var daily = parse("{\"stages\":[{\"objectives\":[{\"event\":\"rotas:a\"}]}],\"reset\":\"DAILY\"}");
        assertTrue(daily.repeatable(), "a resetting quest is repeatable by default");
        assertEquals(daily.window(86400), daily.window(86400 + 3600));
        assertNotEquals(daily.window(86400), daily.window(2 * 86400));
        assertFalse(daily.available(86400, 86400 + 3600), "the same day is still on cooldown");
        assertTrue(daily.available(86400, 2 * 86400), "the next day is available again");

        var cooldown = parse("{\"stages\":[{\"objectives\":[{\"event\":\"rotas:a\"}]}],\"reset\":\"COOLDOWN\",\"cooldown_seconds\":600}");
        assertFalse(cooldown.available(1000, 1500));
        assertTrue(cooldown.available(1000, 1600));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"stages\":[{\"objectives\":[{\"event\":\"rotas:a\"}]}],\"reset\":\"COOLDOWN\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"stages\":[{\"objectives\":[{\"event\":\"rotas:a\"}]}],\"reset\":\"DAILY\",\"repeatable\":false}"));
    }

    @Test void stateKeysStayInsideTheBoundedVariableNamespace() {
        String key = QuestDefinitions.key(new ContentId("rotas:quest/daily_hunt"), "stage");
        assertEquals("rpg.q.rotas.quest-daily_hunt.stage", key);
        assertTrue(key.matches("rpg\\.[a-z0-9_.-]{1,120}"), key);
        String long_ = QuestDefinitions.key(new ContentId("rotas:quest/" + "a".repeat(140)), "p.o0");
        assertTrue(long_.matches("rpg\\.[a-z0-9_.-]{1,120}"), long_);
        assertTrue(long_.length() < 40, "an oversized ID falls back to a hash");
        assertEquals(QuestDefinitions.key(new ContentId("rotas:quest/" + "a".repeat(140)), "p.o0"), long_);
    }

    @Test void questsCompileThroughTheRegistryAndRequireTheirRewards() throws Exception {
        var quest = source("quest/hunt", "quest", "{\"label\":\"Hunt\",\"reset\":\"DAILY\",\"bounty_limit\":5,"
                + "\"reward\":\"rotas:reward/hunt\",\"stages\":[{\"objectives\":[{\"event\":\"rotas:monster_defeated\",\"count\":2}]}]}");
        assertFalse(registry.prepare(List.of(quest)).valid(), "a missing reward rejects the pack");
        var action = source("action/hunt", "action", "{\"type\":\"currency\",\"id\":\"rotas:gold\",\"amount\":5}");
        var reward = source("reward/hunt", "reward", "{\"actions\":[\"rotas:action/hunt\"]}");
        var prepared = registry.prepare(List.of(quest, action, reward));
        assertTrue(prepared.valid(), prepared.issues().toString());
        var compiled = prepared.snapshot().quests().get(new ContentId("rotas:quest/hunt"));
        assertEquals(5, compiled.bountyLimit());
        assertEquals(QuestDefinitions.Reset.DAILY, compiled.reset());
        assertEquals(1, compiled.stages().size());
    }
}
