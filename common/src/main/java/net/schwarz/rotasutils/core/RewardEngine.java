package net.schwarz.rotasutils.core;

import java.util.List;
import java.util.Objects;

public final class RewardEngine {
    public enum Result { GRANTED, ALREADY_CLAIMED, CONDITION_FAILED }

    public record Reward(ContentId id, ConditionEngine.Condition condition, List<ActionEngine.Action> actions) {
        public Reward {
            Objects.requireNonNull(id);
            Objects.requireNonNull(condition);
            actions = List.copyOf(actions);
            if (actions.isEmpty() || actions.size() > 128) {
                throw new IllegalArgumentException("Reward requires 1..128 actions");
            }
        }
    }

    private final ActionEngine actions;

    public RewardEngine(ActionEngine actions) {
        this.actions = Objects.requireNonNull(actions);
    }

    public Result grant(Reward reward, String occurrence, KernelContext context) {
        if (occurrence == null || !occurrence.matches("[a-zA-Z0-9_./:-]{1,200}")) {
            throw new IllegalArgumentException("Invalid server-issued reward occurrence");
        }
        String receipt = "kernel|" + reward.id() + "|" + occurrence;
        try (KernelContext.Transaction transaction = context.begin()) {
            transaction.seed(receipt);
            if (transaction.claimed(receipt)) {
                return Result.ALREADY_CLAIMED;
            }
            if (!reward.condition().test(context)) {
                return Result.CONDITION_FAILED;
            }
            actions.stage(reward.actions(), transaction);
            transaction.claim(receipt);
            transaction.commit();
            return Result.GRANTED;
        }
    }
}
