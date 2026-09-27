package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.core.DialogueStudio;
import net.schwarz.rotasutils.core.DialogueStudioCheck;
import net.schwarz.rotasutils.quest.QuestDef;

import java.util.Locale;

@Environment(EnvType.CLIENT)
final class DialogueText {
    private static final String BASE = "rotasutils.dialogue.studio.";

    private DialogueText() {
    }

    static String t(String key, Object... args) {
        return L.t(BASE + key, args);
    }

    static String messageName(DialogueStudio.MessageLabel label) {
        return label.title().isEmpty() ? t("message_n", label.number()) : label.title();
    }

    static String outcome(DialogueStudio.Outcome outcome) {
        return t("outcome." + outcome.name().toLowerCase(Locale.ROOT));
    }

    static String outcomeWithTarget(DialogueStudio.Outcome outcome, String target) {
        return switch (outcome) {
            case ACCEPT_QUEST, TURN_IN_QUEST -> target.isBlank() ? outcome(outcome) : outcome(outcome) + ": " + quest(target);
            case TRIGGER_EVENT -> target.isBlank() ? outcome(outcome) : outcome(outcome) + ": " + target;
            default -> outcome(outcome);
        };
    }

    static String state(String state) {
        return t("state." + state);
    }

    static String repeat(String repeat) {
        return t("repeat." + repeat);
    }

    static String rewardType(String type) {
        return t("reward." + type);
    }

    static String condition(DialogueStudio.ConditionKind kind) {
        return t("condition." + kind.name().toLowerCase(Locale.ROOT));
    }

    static String quest(String id) {
        if (id == null || id.isBlank()) {
            return t("choose_quest");
        }
        QuestDef quest = ClientState.quest(id);
        return quest == null || quest.name().isBlank() ? id : quest.name();
    }

    static String issue(DialogueStudioCheck.Issue issue) {
        String text = t("issue." + issue.key(), issue.args().toArray());
        if (issue.message() < 0) {
            return text;
        }
        String where = issue.reply() < 0
                ? t("where.message", DialogueStudio.number(issue.message()))
                : t("where.reply", DialogueStudio.number(issue.message()), issue.reply() + 1);
        return where + "  " + text;
    }
}
