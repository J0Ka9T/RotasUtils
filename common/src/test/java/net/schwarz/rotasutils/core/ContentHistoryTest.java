package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.RotasData;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ContentHistoryTest {
    private final ContentRegistry registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));

    private ContentRegistry.Prepared content(int value) throws Exception {
        return registry.prepare(List.of(new ContentRegistry.Source("server/test.json", ContentRegistry.Layer.SERVER,
                ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:test\",\"kind\":\"action\",\"body\":{\"type\":\"stat_points\",\"amount\":" + value + "}}"))));
    }

    @Test void revisionsAndPrivateDraftsPersistInExistingWorldStore() throws Exception {
        var data = new RotasData(); var history = data.contentHistory();
        history.commit(content(2), 0, "console", "bootstrap", 10);
        history.begin("admin-a"); history.begin("admin-b");
        history.put("admin-a", content(3).snapshot().definitions().values().iterator().next().source().document());
        var loaded = RotasData.load(data.save(new CompoundTag())).contentHistory();
        assertEquals(1, loaded.revision());
        assertNotEquals(loaded.draft("admin-a").documents(), loaded.draft("admin-b").documents());
        assertEquals(List.of("rotas:test"), loaded.preview("admin-a").changed());
        assertTrue(registry.prepare(loaded.sources(loaded.draft("admin-a").documents())).valid());
    }

    @Test void invalidDraftCannotApplyOrChangeLiveRevision() throws Exception {
        var history = new ContentHistory(() -> { }); history.commit(content(2), 0, "console", "bootstrap", 1);
        history.begin("admin");
        history.put("admin", ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:bad\",\"kind\":\"reward\",\"body\":{\"actions\":[\"rotas:missing\"]}}"));
        var prepared = registry.prepare(history.sources(history.draft("admin").documents()));
        assertFalse(prepared.valid());
        assertThrows(IllegalArgumentException.class, () -> history.applyDraft("admin", 1, prepared, 2));
        assertEquals(1, history.revision()); assertNotNull(history.draft("admin"));
    }

    @Test void concurrentEditorsAndMutationDuringValidationFailClosed() throws Exception {
        var history = new ContentHistory(() -> { }); history.commit(content(2), 0, "console", "bootstrap", 1);
        history.begin("a"); history.begin("b");
        history.put("a", content(3).snapshot().definitions().values().iterator().next().source().document());
        var prepared = registry.prepare(history.sources(history.draft("a").documents()));
        assertEquals(2, history.applyDraft("a", 1, prepared, 2));
        assertNull(history.draft("a"));
        var stale = registry.prepare(history.sources(history.draft("b").documents()));
        assertThrows(IllegalStateException.class, () -> history.applyDraft("b", 0, stale, 3));
        history.begin("a"); var captured = registry.prepare(history.sources(history.draft("a").documents()));
        history.remove("a", new ContentId("rotas:test"));
        assertThrows(IllegalStateException.class, () -> history.applyDraft("a", 0, captured, 4));
        assertEquals(2, history.revision());
    }

    @Test void rollbackCreatesNewRevisionAndRemainsActiveAfterRestart() throws Exception {
        var history = new ContentHistory(() -> { }); var first = content(2); var second = content(3);
        history.commit(first, 0, "console", "bootstrap", 1); history.commit(second, 1, "console", "reload", 2);
        var restored = registry.prepare(history.sources(history.revision(1).documents()));
        assertEquals(3, history.commit(restored, 2, "admin", "rollback:1", 3));
        var loaded = ContentHistory.load(history.save(), () -> { });
        assertEquals(3, loaded.revision()); assertEquals(first.snapshot().hash(), loaded.current().hash());
        assertEquals("rollback:1", loaded.current().reason());
        assertEquals(2, loaded.current().parent());
        assertEquals(3, loaded.commit(restored, 3, "admin", "reload", 4));
    }

    @Test void retainedHistoryIsBoundedAndDocumentsAreDeduplicated() throws Exception {
        var history = new ContentHistory(() -> { });
        for (int i = 0; i < 100; i++) { history.commit(content(i % 2 + 1), i, "console", "reload", i); }
        assertEquals(ContentHistory.MAX_REVISIONS, history.revisions().size());
        assertEquals(2, history.save().getList("documents", 10).size());
        assertThrows(IllegalArgumentException.class, () -> history.revision(1));
        assertEquals(100, ContentHistory.load(history.save(), () -> { }).revision());
    }

    @Test void corruptDocumentCannotSilentlyReplaceSavedContent() throws Exception {
        var history = new ContentHistory(() -> { }); history.commit(content(2), 0, "console", "bootstrap", 1);
        var tag = history.save(); tag.getList("documents", 10).getCompound(0).putByteArray("body", "{}".getBytes());
        assertThrows(IllegalArgumentException.class, () -> ContentHistory.load(tag, () -> { }));
        var future = history.save(); future.putInt("schema", 99);
        assertThrows(IllegalArgumentException.class, () -> ContentHistory.load(future, () -> { }));
    }
}
