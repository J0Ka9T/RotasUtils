package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigHistoryTest {
    private CompoundTag value(int n) { var tag = new CompoundTag(); tag.putInt("value", n); return tag; }

    @Test void draftSurvivesRestartAndDoesNotChangeLive() {
        var history = new ConfigHistory(() -> {});
        history.stage("a", "settings", value(1), value(2), -1);
        var restored = ConfigHistory.load(history.save(), () -> {});
        assertEquals(value(2), restored.draft("a", "settings").value());
        assertEquals(value(1), restored.draft("a", "settings").base());
        assertNull(restored.draft("b", "settings"));
    }

    @Test void staleGenerationAndChangedLiveRejectWithoutLosingDraft() {
        var history = new ConfigHistory(() -> {});
        history.stage("a", "settings", value(1), value(2), -1);
        assertThrows(IllegalStateException.class, () -> history.stage("a", "settings", value(1), value(3), -1));
        assertThrows(IllegalStateException.class, () -> history.check("a", "settings", value(9), 0));
        assertEquals(value(2), history.draft("a", "settings").value());
        history.check("a", "settings", value(1), 0);
        history.applied("a", "settings", "test");
        assertNull(history.draft("a", "settings"));
        assertEquals(1, history.revisions("settings").size());
    }

    @Test void returnedDraftCannotMutateStoredValue() {
        var history = new ConfigHistory(() -> {});
        history.stage("a", "settings", value(1), value(2), -1);
        history.draft("a", "settings").value().putInt("value", 99);
        assertEquals(2, history.draft("a", "settings").value().getInt("value"));
    }

    @Test void restageRebasesOnTheNewLiveSnapshot() {
        var history = new ConfigHistory(() -> {});
        history.stage("a", "settings", value(1), value(2), -1);
        history.stage("a", "settings", value(7), value(8), 0);
        assertEquals(value(8), history.draft("a", "settings").value());
        assertEquals(value(7), history.draft("a", "settings").base());
        history.check("a", "settings", value(7), 1);
        history.applied("a", "settings", "test");
        assertNull(history.draft("a", "settings"));
    }

    @Test void unconditionalDiscardClearsAStuckDraft() {
        var history = new ConfigHistory(() -> {});
        history.stage("a", "settings", value(1), value(2), -1);
        history.discard("a", "settings");
        assertNull(history.draft("a", "settings"));
        history.discard("a", "settings");
    }
}
