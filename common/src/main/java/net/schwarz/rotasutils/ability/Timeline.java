package net.schwarz.rotasutils.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One clock for everything an ability does. Events sit at timestamps in seconds; a driver advances
 * the clock and every event whose time was crossed fires once, in order. The server drives one of
 * these for gameplay, the client another for presentation, and both read the same
 * {@link RedTimings}-style constants, so a cutscene never drifts from the gameplay it stages.
 */
public final class Timeline<C> {
    /** A named moment on the timeline. */
    public record Event<C>(double time, String name, Consumer<C> action) {
    }

    private final List<Event<C>> events = new ArrayList<>();

    /** Adds an event at {@code seconds}; events at the same time keep the order they were added in. */
    public Timeline<C> at(double seconds, String name, Consumer<C> action) {
        int i = events.size();
        while (i > 0 && events.get(i - 1).time() > seconds) {
            i--;
        }
        events.add(i, new Event<>(seconds, name, action));
        return this;
    }

    /** Fires every event with {@code previous < time <= now}. The first advance passes {@code -1}. */
    public void advance(double previous, double now, C context) {
        for (Event<C> event : events) {
            if (event.time() > previous && event.time() <= now) {
                event.action().accept(context);
            }
        }
    }

    public List<Event<C>> events() {
        return List.copyOf(events);
    }

    public double last() {
        return events.isEmpty() ? 0 : events.get(events.size() - 1).time();
    }
}
