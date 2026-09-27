package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;

public final class DialoguePreview {
    public record Reply(int index, String text, DialogueStudio.Outcome outcome, String target,
                        List<DialogueStudio.ConditionKind> conditions) { }

    public record Step(int message, String speech, List<Reply> replies) { }

    public record Result(int message, boolean ended, boolean broken, DialogueStudio.Outcome action, String target) { }

    private DialoguePreview() {
    }

    public static Step step(DialogueStudio studio, int message) {
        if (message < 0 || message >= studio.messageCount()) {
            return new Step(-1, "", List.of());
        }
        List<Reply> replies = new ArrayList<>();
        for (int r = 0; r < studio.replyCount(message); r++) {
            replies.add(new Reply(r, studio.replyText(message, r), studio.outcome(message, r),
                    studio.target(message, r), studio.conditions(message, r)));
        }
        return new Step(message, studio.messageText(message), List.copyOf(replies));
    }

    public static Result choose(DialogueStudio studio, int message, int reply) {
        DialogueStudio.Outcome outcome = studio.outcome(message, reply);
        int next = studio.nextIndex(message, reply);
        if (next == DialogueStudio.UNRESOLVED) {
            return new Result(message, true, true, null, "");
        }
        return switch (outcome) {
            case GO_TO -> new Result(next, false, false, null, "");
            case END -> new Result(message, true, false, null, "");
            case OPEN_SHOP -> new Result(message, true, false, outcome, "");
            default -> new Result(next >= 0 ? next : message, next < 0, false, outcome, studio.target(message, reply));
        };
    }
}
