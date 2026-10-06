package net.schwarz.rotasutils.server;

import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.EventKind;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class QuestInventory {
    private QuestInventory() {}

    static boolean refresh(QuestDef quest, ActiveQuest active, List<ItemStack> stacks) {
        ObjectiveEngine.migrate(active, quest);
        int[] remaining = counts(stacks);
        boolean changed = false;
        for (int index : collectionOrder(quest)) {
            Objective objective = quest.objectives().get(index);
            if (alternativeDone(quest, active, index)) {
                continue;
            }
            int amount = 0;
            if (ObjectiveEngine.stepUnlocked(quest, active, objective, index)) {
                amount = allocate(objective, stacks, remaining, objective.params().getBool("consume", true));
            }
            boolean complete = amount >= objective.requiredAmount();
            if (active.progress(index) != amount || active.isComplete(index) != complete) {
                active.setProgress(index, amount);
                active.setComplete(index, complete);
                changed = true;
            }
        }
        boolean ready = QuestService.allRequiredComplete(quest, active);
        if (active.turnInReady() != ready) {
            active.setTurnInReady(ready);
            changed = true;
        }
        return changed;
    }

    static boolean consume(QuestDef quest, ActiveQuest active, List<ItemStack> stacks) {
        int[] remaining = counts(stacks);
        for (int index : collectionOrder(quest)) {
            Objective objective = quest.objectives().get(index);
            if ((objective.optional() && !active.isComplete(index)) || !QuestService.onPath(quest, active, index)) {
                continue;
            }
            int amount = allocate(objective, stacks, remaining, objective.params().getBool("consume", true));
            if (amount < objective.requiredAmount()) {
                return false;
            }
        }
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            stack.shrink(stack.getCount() - remaining[slot]);
        }
        return true;
    }

    private static boolean alternativeDone(QuestDef quest, ActiveQuest active, int index) {
        String group = quest.objectives().get(index).alternativeGroup();
        if (group.isBlank()) return false;
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective other = quest.objectives().get(i);
            if (i != index && group.equals(other.alternativeGroup()) && active.isComplete(i)
                    && other.type() != ObjectiveType.COLLECT_ITEM) {
                return true;
            }
        }
        return false;
    }

    private static int[] counts(List<ItemStack> stacks) {
        int[] counts = new int[stacks.size()];
        for (int slot = 0; slot < stacks.size(); slot++) {
            counts[slot] = stacks.get(slot).getCount();
        }
        return counts;
    }

    private static List<Integer> collectionOrder(QuestDef quest) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (quest.objectives().get(i).type() == ObjectiveType.COLLECT_ITEM) {
                indices.add(i);
            }
        }
        indices.sort(Comparator.comparingInt((Integer i) -> quest.objectives().get(i).step())
                .thenComparing(i -> quest.objectives().get(i).optional())
                .thenComparing(i -> !quest.objectives().get(i).params().getString("item_tag", "").isEmpty()));
        return indices;
    }

    private static int allocate(Objective objective, List<ItemStack> stacks, int[] remaining, boolean consume) {
        if (objective.params().getId("item") == null && objective.params().getString("item_tag", "").isEmpty()) {
            return 0;
        }
        int needed = objective.requiredAmount();
        int amount = 0;
        for (int slot = 0; slot < stacks.size() && amount < needed; slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty() || ObjectiveEngine.matches(null, null, objective,
                    new QuestEvent(EventKind.COLLECT_ITEM).stack(stack)) != null) {
                continue;
            }
            int taken = Math.min(needed - amount, consume ? remaining[slot] : stack.getCount());
            amount += taken;
            if (consume) {
                remaining[slot] -= taken;
            }
        }
        return amount;
    }
}
