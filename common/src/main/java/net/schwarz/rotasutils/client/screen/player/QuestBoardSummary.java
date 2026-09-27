package net.schwarz.rotasutils.client.screen.player;

import java.util.List;

/** Counts the quest states players use to decide what to open next. */
public record QuestBoardSummary(int total, int available, int active, int ready) {
    public enum State { AVAILABLE, ACTIVE, READY, LOCKED }

    public static QuestBoardSummary of(List<State> states) {
        int available = 0;
        int active = 0;
        int ready = 0;
        for (State state : states) {
            if (state == State.AVAILABLE) available++;
            if (state == State.ACTIVE || state == State.READY) active++;
            if (state == State.READY) ready++;
        }
        return new QuestBoardSummary(states.size(), available, active, ready);
    }
}
