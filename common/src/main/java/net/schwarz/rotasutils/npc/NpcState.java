package net.schwarz.rotasutils.npc;

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

    public String marker() {
        return switch (this) {
            case QUEST_AVAILABLE -> "!";
            case READY_TO_TURN_IN -> "?";
            case QUEST_ACTIVE, OBJECTIVE_COMPLETE -> "...";
            case REQUIREMENTS_NOT_MET -> "x";
            default -> "";
        };
    }

    public int markerColor() {
        return switch (this) {
            case QUEST_AVAILABLE -> 0xFFFFC25E;
            case READY_TO_TURN_IN -> 0xFF86C05C;
            case QUEST_ACTIVE, OBJECTIVE_COMPLETE -> 0xFFC9B895;
            case REQUIREMENTS_NOT_MET -> 0xFFE2695C;
            default -> 0xFFC9B895;
        };
    }

    public NpcState best(NpcState other) {
        return other.ordinal() > ordinal() ? other : this;
    }
}
