package net.schwarz.rotasutils.npc;

/**
 * What an NPC has to say to one player right now.
 *
 * <p>Order matters: a later constant beats an earlier one when an NPC carries several
 * quests, so the marker above its head shows the most advanced thing on offer rather
 * than whichever quest happens to be first in the list.</p>
 */
public enum NpcState {
    NO_QUEST,
    QUEST_ON_COOLDOWN,
    QUEST_COMPLETE,
    QUEST_FAILED,
    REQUIREMENTS_NOT_MET,
    QUEST_ACTIVE,
    OBJECTIVE_COMPLETE,
    QUEST_AVAILABLE,
    READY_TO_TURN_IN;

    /** Marker string drawn above the head; empty means no marker. */
    public String marker() {
        return switch (this) {
            case QUEST_AVAILABLE -> "!";
            case READY_TO_TURN_IN -> "?";
            case QUEST_ACTIVE, OBJECTIVE_COMPLETE -> "...";
            case REQUIREMENTS_NOT_MET -> "x";
            default -> "";
        };
    }

    /** Marker colour, matching the quest board status inks. */
    public int markerColor() {
        return switch (this) {
            case QUEST_AVAILABLE -> 0xFFFFC25E;
            case READY_TO_TURN_IN -> 0xFF86C05C;
            case QUEST_ACTIVE, OBJECTIVE_COMPLETE -> 0xFFC9B895;
            case REQUIREMENTS_NOT_MET -> 0xFFE2695C;
            default -> 0xFFC9B895;
        };
    }

    /** The more advanced of two states, used when one NPC carries several quests. */
    public NpcState best(NpcState other) {
        return other.ordinal() > ordinal() ? other : this;
    }
}
