package net.schwarz.rotasutils.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Timeline<C> {
    public record Event<C>(double time, String name, Consumer<C> action) {
    }

    private final List<Event<C>> events = new ArrayList<>();

    public Timeline<C> at(double seconds, String name, Consumer<C> action) {
        int i = events.size();
        while (i > 0 && events.get(i - 1).time() > seconds) {
            i--;
        }
        events.add(i, new Event<>(seconds, name, action));
        return this;
    }

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
