package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class KernelEventBus {
    public record Event(ContentId type, String occurrence, KernelContext context) {
        public Event {
            Objects.requireNonNull(type);
            Objects.requireNonNull(occurrence);
            Objects.requireNonNull(context);
        }
    }

    private final Thread owner = Thread.currentThread();
    private final Map<ContentId, List<Consumer<Event>>> listeners = new HashMap<>();
    private final BiConsumer<Event, RuntimeException> errors;
    private int depth;
    private int dispatches;

    public KernelEventBus(BiConsumer<Event, RuntimeException> errors) {
        this.errors = Objects.requireNonNull(errors);
    }

    public AutoCloseable subscribe(ContentId type, Consumer<Event> listener) {
        checkThread();
        Objects.requireNonNull(listener);
        List<Consumer<Event>> group = listeners.computeIfAbsent(type, key -> new ArrayList<>());
        if (group.size() >= 256) {
            throw new IllegalArgumentException("Event listener budget exceeded: " + type);
        }
        group.add(listener);
        return () -> {
            checkThread();
            group.remove(listener);
            if (group.isEmpty()) {
                listeners.remove(type, group);
            }
        };
    }

    public void emit(Event event) {
        checkThread();
        if (depth == 0) {
            dispatches = 0;
        }
        if (depth >= 16 || ++dispatches > 1024) {
            throw new IllegalStateException("Recursive event budget exceeded");
        }
        depth++;
        try {
            for (Consumer<Event> listener : List.copyOf(listeners.getOrDefault(event.type(), List.of()))) {
                try {
                    listener.accept(event);
                } catch (RuntimeException ex) {
                    errors.accept(event, ex);
                }
            }
        } finally {
            depth--;
        }
    }

    public boolean hasListeners(ContentId type) {
        checkThread();
        return listeners.containsKey(type);
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Rotas event bus requires its owning server thread");
        }
    }
}
