package net.schwarz.rotasutils.core;

import java.util.List;

public final class ReloadState {
    private final Thread owner = Thread.currentThread();
    private volatile ContentRegistry.Snapshot active = ContentRegistry.Snapshot.empty();
    private List<ContentRegistry.Diagnostic> issues = List.of();
    private long revision;

    public ContentRegistry.Snapshot active() {
        return active;
    }

    public long revision() {
        return revision;
    }

    public List<ContentRegistry.Diagnostic> issues() {
        return issues;
    }

    public boolean apply(ContentRegistry.Prepared prepared) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Reload publication requires the server thread");
        }
        issues = prepared.issues();
        if (!prepared.valid()) {
            return false;
        }
        if (!active.hash().equals(prepared.snapshot().hash())) {
            active = prepared.snapshot();
            revision++;
        }
        return true;
    }

    public void restore(ContentRegistry.Prepared prepared, long durableRevision) {
        if (Thread.currentThread() != owner || !prepared.valid() || durableRevision < revision) {
            throw new IllegalStateException("Invalid durable revision publication");
        }
        active = prepared.snapshot(); revision = durableRevision; issues = List.of();
    }
}
