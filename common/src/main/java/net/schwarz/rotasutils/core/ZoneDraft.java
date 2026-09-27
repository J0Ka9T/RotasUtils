package net.schwarz.rotasutils.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Mutable client/editor history around immutable zone candidates. */
public final class ZoneDraft {
    public static final int HISTORY_LIMIT = 50;
    private final long baseRevision;
    private final Deque<ZoneDef> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private ZoneDef candidate;
    public ZoneDraft(ZoneDef live) { candidate=live; baseRevision=live.revision(); }
    public ZoneDef candidate() { return candidate; }
    public long baseRevision() { return baseRevision; }
    public int undoSize() { return undo.size(); }
    public int redoSize() { return redo.size(); }
    public void replace(ZoneDef next) {
        if (!candidate.id().equals(next.id())) throw new IllegalArgumentException("A zone draft cannot change its id");
        if (candidate.equals(next)) return;
        if (undo.size()==HISTORY_LIMIT) undo.removeFirst();
        undo.addLast(candidate); candidate=next; redo.clear();
    }
    public boolean undo() { if (undo.isEmpty()) return false; redo.addLast(candidate); candidate=undo.removeLast(); return true; }
    public boolean redo() { if (redo.isEmpty()) return false; undo.addLast(candidate); candidate=redo.removeLast(); return true; }
}
