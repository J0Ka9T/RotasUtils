package net.schwarz.rotasutils.server;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestObjectiveReliabilityTest {
    private QuestDef collectQuest(int amount) {
        var quest = new QuestDef("collect");
        var objective = new Objective(ObjectiveType.COLLECT_ITEM);
        objective.params().put("item", "minecraft:iron_ingot");
        objective.params().put("amount", amount);
        quest.objectives().add(objective);
        return quest;
    }
    @Test void droppingCollectedItemsRevokesReadinessAndProgress() {
        var quest = collectQuest(8);
        var active = new ActiveQuest(quest.id(), 1, 1);
        assertTrue(QuestInventory.refresh(quest, active, java.util.List.of(new ItemStack(Items.IRON_INGOT, 8))));
        assertTrue(active.turnInReady());
        QuestInventory.refresh(quest, active, java.util.List.of(new ItemStack(Items.IRON_INGOT, 3)));
        assertFalse(active.turnInReady());
        assertFalse(active.isComplete(0));
        assertEquals(3, active.progress(0));
    }
    @Test void duplicateCostsCannotSpendTheSameStackTwiceAndFailureConsumesNothing() {
        var quest = collectQuest(8);
        quest.objectives().add(quest.objectives().get(0).copy());
        var active = new ActiveQuest(quest.id(), 1, 2);
        var stack = new ItemStack(Items.IRON_INGOT, 8);
        var inventory = java.util.List.of(stack);
        QuestInventory.refresh(quest, active, inventory);
        assertFalse(QuestInventory.consume(quest, active, inventory));
        assertEquals(8, stack.getCount());
    }
    @Test void matchingInventoryIsConsumedExactlyOnce() {
        var quest = collectQuest(8);
        var active = new ActiveQuest(quest.id(), 1, 1);
        var stack = new ItemStack(Items.IRON_INGOT, 10);
        var inventory = java.util.List.of(stack);
        QuestInventory.refresh(quest, active, inventory);
        assertTrue(QuestInventory.consume(quest, active, inventory));
        assertEquals(2, stack.getCount());
        assertFalse(QuestInventory.consume(quest, active, inventory));
        assertEquals(2, stack.getCount());
    }
    @Test void laterCollectStepStaysLockedUntilTheEarlierTalkCompletes() {
        var quest = collectQuest(8);
        quest.setObjectiveMode(QuestDef.ObjectiveMode.SEQUENTIAL);
        quest.objectives().get(0).setStep(1);
        quest.objectives().add(0, new Objective(ObjectiveType.TALK_NPC));
        var active = new ActiveQuest(quest.id(), 1, 2);
        var inventory = java.util.List.of(new ItemStack(Items.IRON_INGOT, 8));
        QuestInventory.refresh(quest, active, inventory);
        assertEquals(0, active.progress(1));
        active.setComplete(0, true);
        QuestInventory.refresh(quest, active, inventory);
        assertTrue(active.turnInReady());
    }
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void malformedItemSelectorCannotMatchAnyItem() {
        var objective = new Objective(ObjectiveType.COLLECT_ITEM);
        objective.params().put("item", "INVALID ITEM");
        assertNotNull(ObjectiveEngine.matches(null, null, objective,
                new QuestEvent(EventKind.COLLECT_ITEM).stack(new ItemStack(Items.STONE))));
    }
    @Test void malformedTagCannotFallBackToTheDefaultItem() {
        var objective = new Objective(ObjectiveType.COLLECT_ITEM);
        objective.params().put("item_tag", "INVALID TAG");
        assertNotNull(ObjectiveEngine.matches(null, null, objective,
                new QuestEvent(EventKind.COLLECT_ITEM).stack(new ItemStack(Items.IRON_INGOT))));
    }
}

