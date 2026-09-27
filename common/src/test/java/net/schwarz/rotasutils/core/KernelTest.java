package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class KernelTest {
    private final ActionEngine actions = new ActionEngine(Map.of());
    private final ConditionEngine conditions = new ConditionEngine(Map.of());
    private final ContentRegistry registry = new ContentRegistry(conditions, actions);
    @TempDir Path temporary;

    private JsonObject json(String value) throws Exception {
        return ContentPacks.parse(value);
    }

    private ContentRegistry.Source source(String file, ContentRegistry.Layer layer, String id, String kind,
                                          String body) throws Exception {
        return new ContentRegistry.Source(file, layer, json("{\"schema\":1,\"id\":\"" + id
                + "\",\"kind\":\"" + kind + "\",\"body\":" + body + "}"));
    }

    private ContentRegistry.Source action(String file, ContentRegistry.Layer layer, String value) throws Exception {
        return source(file, layer, "rotas:action/test", "action",
                "{\"type\":\"set_variable\",\"key\":\"rpg.test\",\"value\":\"" + value + "\"}");
    }

    @Test void rejectsMalformedAndTraversalIds() {
        for (String id : List.of("plain", "Rotas:quest/a", "rotas:../a", "rotas:a//b", "rotas:a/", "rotas:")) {
            assertThrows(IllegalArgumentException.class, () -> new ContentId(id), id);
        }
        assertEquals("rotas:quest/example", new ContentId("rotas:quest/example").toString());
    }

    @Test void duplicateIdsAtSamePriorityFailEvenWithOverride() throws Exception {
        var result = registry.prepare(List.of(action("one", ContentRegistry.Layer.CORE, "1"),
                action("two", ContentRegistry.Layer.CORE, "2"), action("fix", ContentRegistry.Layer.HOTFIX, "3")));
        assertFalse(result.valid());
        assertTrue(result.issues().toString().contains("Duplicate ID"));
    }

    @Test void overridePriorityAndSourceAreDeterministic() throws Exception {
        var low = action("low", ContentRegistry.Layer.CORE, "low");
        var high = action("high", ContentRegistry.Layer.HOTFIX, "high");
        var first = registry.prepare(List.of(high, low));
        var second = registry.prepare(List.of(low, high));
        assertTrue(first.valid(), first.issues().toString());
        assertEquals(first.snapshot().hash(), second.snapshot().hash());
        assertEquals("high", first.snapshot().definitions().get(new ContentId("rotas:action/test")).source().name());
    }

    @Test void detectsMissingTypedAndCyclicReferences() throws Exception {
        var missing = source("missing", ContentRegistry.Layer.CORE, "rotas:condition/a", "condition",
                "{\"type\":\"ref\",\"id\":\"rotas:missing\"}");
        assertFalse(registry.prepare(List.of(missing)).valid());
        var wrongType = source("wrong", ContentRegistry.Layer.CORE, "rotas:condition/a", "condition",
                "{\"type\":\"ref\",\"id\":\"rotas:action/test\"}");
        assertTrue(registry.prepare(List.of(wrongType, action("a", ContentRegistry.Layer.CORE, "x")))
                .issues().toString().contains("Expected CONDITION"));
        var cycle = source("cycle", ContentRegistry.Layer.CORE, "rotas:condition/a", "condition",
                "{\"type\":\"ref\",\"id\":\"rotas:condition/a\"}");
        assertTrue(registry.prepare(List.of(cycle)).issues().toString().contains("Cycle"));
    }

    @Test void disabledContentIsRetainedButCannotBeReferenced() throws Exception {
        var disabled = new ContentRegistry.Source("disabled", ContentRegistry.Layer.CORE,
                json("{\"schema\":1,\"id\":\"rotas:disabled\",\"kind\":\"action\",\"enabled\":false}"));
        var prepared = registry.prepare(List.of(disabled));
        assertTrue(prepared.valid());
        assertEquals(1, prepared.snapshot().definitions().size());
        assertTrue(prepared.snapshot().actions().isEmpty());
        var reward = source("reward", ContentRegistry.Layer.CORE, "rotas:reward/a", "reward",
                "{\"actions\":[\"rotas:disabled\"]}");
        assertFalse(registry.prepare(List.of(disabled, reward)).valid());
    }

    @Test void rejectsFutureSchemaAndUnknownFieldsWithoutMutatingSource() throws Exception {
        var original = action("a", ContentRegistry.Layer.CORE, "x");
        var future = original.document();
        future.addProperty("schema", 2);
        assertFalse(registry.prepare(List.of(new ContentRegistry.Source("future", ContentRegistry.Layer.CORE, future))).valid());
        future.addProperty("schema", 1);
        future.addProperty("typo", true);
        assertFalse(registry.prepare(List.of(new ContentRegistry.Source("typo", ContentRegistry.Layer.CORE, future))).valid());
        assertTrue(registry.prepare(List.of(original)).valid());
        assertFalse(original.document().has("typo"));
    }

    @Test void composesAndOrNotAtLeastAndRejectsAmbiguousEmptyGroups() throws Exception {
        var context = new MemoryContext();
        String yes = "{\"type\":\"number\",\"name\":\"player.level\",\"op\":\"ge\",\"value\":10}";
        String no = "{\"type\":\"not\",\"children\":[" + yes + "]}";
        for (String type : List.of("and", "all", "or", "any", "at_least")) {
            String count = type.equals("at_least") ? ",\"count\":1" : "";
            var compiled = conditions.compile(json("{\"type\":\"" + type + "\",\"children\":[" + yes + "," + no + "]" + count + "}"), id -> null);
            assertEquals(!type.equals("and") && !type.equals("all"), compiled.test(context), type);
        }
        assertThrows(IllegalArgumentException.class, () -> conditions.compile(json("{\"type\":\"not\",\"children\":[]}"), id -> null));
    }

    @Test void missingFactInsideNotStillFailsClosed() throws Exception {
        var condition = conditions.compile(json("{\"type\":\"not\",\"children\":[{\"type\":\"number\",\"name\":\"missing\",\"op\":\"eq\",\"value\":1}]}"), id -> null);
        assertThrows(IllegalArgumentException.class, () -> condition.test(new MemoryContext()));
    }

    @Test void conditionsShortCircuitAndRespectDepthLimit() throws Exception {
        var condition = conditions.compile(json("{\"type\":\"or\",\"children\":[{\"type\":\"always\"},{\"type\":\"number\",\"name\":\"missing\",\"op\":\"eq\",\"value\":1}]}"), id -> null);
        assertTrue(condition.test(new MemoryContext()));
        String nested = "{\"type\":\"always\"}";
        for (int i = 0; i < 34; i++) {
            nested = "{\"type\":\"not\",\"children\":[" + nested + "]}";
        }
        String finalNested = nested;
        assertThrows(Exception.class, () -> conditions.compile(json(finalNested), id -> null));
    }

    @Test void actionValidationRejectsUnknownUnsafeAndFractionalInputs() {
        for (String invalid : List.of("{\"type\":\"run_command\",\"command\":\"op test\"}",
                "{\"type\":\"set_variable\",\"key\":\"pref.admin\",\"value\":\"true\"}",
                "{\"type\":\"add_variable\",\"key\":\"rpg.test\",\"amount\":1.5}")) {
            assertThrows(IllegalArgumentException.class, () -> actions.compile(json(invalid)));
        }
    }

    @Test void rewardOnceSurvivesReconstructedContextAndDoesNotReroll() throws Exception {
        var context = new MemoryContext();
        var reward = reward(List.of(actions.compile(json("{\"type\":\"add_variable\",\"key\":\"rpg.count\",\"amount\":5}"))));
        var engine = new RewardEngine(actions);
        assertEquals(RewardEngine.Result.GRANTED, engine.grant(reward, "boss:uuid:1", context));
        var restored = new MemoryContext(context);
        assertEquals(RewardEngine.Result.ALREADY_CLAIMED, engine.grant(reward, "boss:uuid:1", restored));
        assertEquals("5", restored.variables.get("rpg.count"));
        assertEquals(RewardEngine.Result.GRANTED, engine.grant(reward, "boss:uuid:2", restored));
        assertEquals("10", restored.variables.get("rpg.count"));
    }

    @Test void failedStagingAndFailedCommitHaveNoPartialStateOrReceipt() throws Exception {
        var context = new MemoryContext();
        var set = actions.compile(json("{\"type\":\"set_variable\",\"key\":\"rpg.test\",\"value\":\"yes\"}"));
        ActionEngine.Action failure = tx -> { throw new IllegalArgumentException("rejected"); };
        var engine = new RewardEngine(actions);
        assertThrows(IllegalArgumentException.class, () -> engine.grant(reward(List.of(set, failure)), "first", context));
        assertTrue(context.variables.isEmpty());
        assertTrue(context.receipts.isEmpty());
        context.failCommit = true;
        assertThrows(IllegalStateException.class, () -> engine.grant(reward(List.of(set)), "first", context));
        assertTrue(context.variables.isEmpty());
        assertTrue(context.receipts.isEmpty());
        context.failCommit = false;
        assertEquals(RewardEngine.Result.GRANTED, engine.grant(reward(List.of(set)), "first", context));
    }

    @Test void falseConditionAndOverflowDoNotConsumeReceipt() throws Exception {
        var context = new MemoryContext();
        var add = actions.compile(json("{\"type\":\"add_variable\",\"key\":\"rpg.count\",\"amount\":1}"));
        var engine = new RewardEngine(actions);
        var gated = new RewardEngine.Reward(new ContentId("rotas:reward/test"), c -> false, List.of(add));
        assertEquals(RewardEngine.Result.CONDITION_FAILED, engine.grant(gated, "one", context));
        context.variables.put("rpg.count", Long.toString(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> engine.grant(reward(List.of(add)), "one", context));
        assertTrue(context.receipts.isEmpty());
        assertEquals(Long.toString(Long.MAX_VALUE), context.variables.get("rpg.count"));
    }

    @Test void failedReloadRetainsExactSnapshotAndRevision() throws Exception {
        var state = new ReloadState();
        var good = registry.prepare(List.of(action("a", ContentRegistry.Layer.CORE, "x")));
        assertTrue(state.apply(good));
        assertEquals(1, state.revision());
        assertTrue(state.apply(good));
        assertEquals(1, state.revision());
        var before = state.active();
        assertFalse(state.apply(registry.prepare(List.of(action("a", ContentRegistry.Layer.CORE, "x"),
                action("b", ContentRegistry.Layer.CORE, "y")))));
        assertSame(before, state.active());
        assertEquals(1, state.revision());
        assertFalse(state.issues().isEmpty());
    }

    @Test void packFilesReportIndependentErrorsAndKeepSources() throws Exception {
        Path layer = Files.createDirectories(temporary.resolve("server/test"));
        Files.writeString(layer.resolve("one.json"), action("x", ContentRegistry.Layer.SERVER, "x").document().toString());
        Files.writeString(layer.resolve("bad.json"), "{\"schema\":1,\"schema\":2}");
        var result = ContentPacks.read(temporary, registry);
        assertFalse(result.valid());
        assertTrue(result.issues().toString().contains("bad.json"));
        Files.delete(layer.resolve("bad.json"));
        assertTrue(ContentPacks.read(temporary, registry).valid());
    }

    @Test void rejectsLenientJsonAndExcessiveInput() {
        for (String invalid : List.of("{a:1}", "{\"a\":1,\"a\":2}", "{\"a\":NaN}", "{} {}", "/* x */{}")) {
            assertThrows(Exception.class, () -> ContentPacks.parse(invalid), invalid);
        }
        assertThrows(Exception.class, () -> ContentPacks.parse(" ".repeat(ContentPacks.MAX_FILE_BYTES + 1)));
    }

    @Test void eventErrorsAreIsolatedAndUnsubscribeWorks() throws Exception {
        List<String> seen = new ArrayList<>();
        var bus = new KernelEventBus((event, error) -> seen.add("error:" + error.getMessage()));
        var id = new ContentId("rotas:player_login");
        bus.subscribe(id, event -> { throw new IllegalStateException("bad adapter"); });
        var subscription = bus.subscribe(id, event -> seen.add("good"));
        bus.emit(new KernelEventBus.Event(id, "first", new MemoryContext()));
        assertEquals(List.of("error:bad adapter", "good"), seen);
        subscription.close();
        seen.clear();
        bus.emit(new KernelEventBus.Event(id, "second", new MemoryContext()));
        assertEquals(List.of("error:bad adapter"), seen);
    }

    @Test void eventRecursionAndWrongThreadAreBounded() throws Exception {
        List<RuntimeException> errors = new ArrayList<>();
        var bus = new KernelEventBus((event, error) -> errors.add(error));
        var id = new ContentId("rotas:loop");
        var event = new KernelEventBus.Event(id, "one", new MemoryContext());
        bus.subscribe(id, bus::emit);
        bus.emit(event);
        assertEquals(1, errors.size());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try { bus.emit(event); } catch (IllegalStateException ex) { failure.set(ex); }
        });
        other.start();
        other.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    @Test void compilesDataDrivenRewardAndEventIndex() throws Exception {
        var reward = source("reward", ContentRegistry.Layer.CORE, "rotas:reward/test", "reward",
                "{\"actions\":[\"rotas:action/test\"]}");
        var rule = source("rule", ContentRegistry.Layer.CORE, "rotas:rule/welcome", "rule",
                "{\"event\":\"rotas:player_login\",\"scope\":\"once\",\"reward\":\"rotas:reward/test\"}");
        var result = registry.prepare(List.of(rule, reward, action("action", ContentRegistry.Layer.CORE, "hello")));
        assertTrue(result.valid(), result.issues().toString());
        var compiled = result.snapshot().rules().get(new ContentId("rotas:player_login")).get(0);
        var context = new MemoryContext();
        assertEquals(RewardEngine.Result.GRANTED, new RewardEngine(actions).grant(compiled.reward(), "welcome", context));
        assertEquals("hello", context.variables.get("rpg.test"));
    }

    @Test void sharedReferenceDagCannotCauseExponentialEvaluation() throws Exception {
        List<ContentRegistry.Source> definitions = new ArrayList<>();
        definitions.add(source("base", ContentRegistry.Layer.CORE, "rotas:condition/c0", "condition", "{\"type\":\"always\"}"));
        for (int i = 1; i < 20; i++) {
            String ref = "{\"type\":\"ref\",\"id\":\"rotas:condition/c" + (i - 1) + "\"}";
            definitions.add(source("c" + i, ContentRegistry.Layer.CORE, "rotas:condition/c" + i, "condition",
                    "{\"type\":\"and\",\"children\":[" + ref + "," + ref + "]}"));
        }
        var prepared = registry.prepare(definitions);
        assertTrue(prepared.valid(), prepared.issues().toString());
        var condition = prepared.snapshot().conditions().get(new ContentId("rotas:condition/c19"));
        assertThrows(IllegalArgumentException.class, () -> condition.test(new MemoryContext()));
        assertTrue(prepared.snapshot().conditions().get(new ContentId("rotas:condition/c0")).test(new MemoryContext()));
    }

    private RewardEngine.Reward reward(List<ActionEngine.Action> steps) {
        return new RewardEngine.Reward(new ContentId("rotas:reward/test"), ConditionEngine.ALWAYS, steps);
    }

    static final class MemoryContext implements KernelContext {
        Map<String, String> variables = new HashMap<>();
        Set<String> receipts = new HashSet<>();
        Set<String> unlocks = new HashSet<>();
        boolean failCommit;
        boolean busy;

        MemoryContext() {
        }

        MemoryContext(MemoryContext saved) {
            variables.putAll(saved.variables);
            receipts.addAll(saved.receipts);
            unlocks.addAll(saved.unlocks);
        }

        public double number(String name) {
            return name.equals("player.level") ? 10 : Double.NaN;
        }

        public String text(String name) {
            return variables.get(name);
        }

        public boolean requirement(String type, Map<String, String> parameters) {
            throw new IllegalArgumentException("No adapter");
        }

        public Transaction begin() {
            if (busy) { throw new IllegalStateException("Nested transaction"); }
            busy = true;
            Map<String, String> draft = new HashMap<>(variables);
            Set<String> claims = new HashSet<>(receipts);
            Set<String> unlocked = new HashSet<>(unlocks);
            return new Transaction() {
                public String variable(String key) { return draft.get(key); }
                public void variable(String key, String value) { draft.put(key, value); }
                public void unlock(String id) { unlocked.add(id); }
                public boolean claimed(String receipt) { return claims.contains(receipt); }
                public void claim(String receipt) { claims.add(receipt); }
                public void commit() {
                    if (failCommit) { throw new IllegalStateException("Commit failed"); }
                    variables = draft;
                    receipts = claims;
                    unlocks = unlocked;
                }
                public void close() { busy = false; }
            };
        }
    }
}
