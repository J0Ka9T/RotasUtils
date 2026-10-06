package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.QuestDefinitions;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.QuestStage;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class KernelQuestAdapter {
    public record Projection(QuestDef quest, QuestDefinitions.Quest source) {
        public Projection {
            Objects.requireNonNull(quest, "quest");
            Objects.requireNonNull(source, "source");
        }
    }

    private final Map<String, Projection> projections;

    public KernelQuestAdapter(ContentRegistry.Snapshot snapshot, Collection<QuestDef> canonicalQuests) {
        projections = project(snapshot, canonicalQuests);
    }

    public Map<String, Projection> projections() {
        return projections;
    }

    public Projection find(String questId) {
        return projections.get(questId);
    }

    public static Map<String, Projection> project(ContentRegistry.Snapshot snapshot,
                                                   Collection<QuestDef> canonicalQuests) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(canonicalQuests, "canonicalQuests");

        Map<String, QuestDef> canonicalById = new LinkedHashMap<>();
        for (QuestDef quest : canonicalQuests) {
            canonicalById.put(quest.id(), quest);
        }

        Map<String, Projection> projected = new LinkedHashMap<>();
        snapshot.quests().values().stream()
                .sorted(Comparator.comparing(quest -> quest.id().value()))
                .forEach(source -> {
                    String id = source.id().value();
                    QuestDef existing = canonicalById.get(id);
                    if (existing != null && !id.equals(existing.kernelOrigin())) {
                        throw new IllegalArgumentException("Quest ID collision: " + id);
                    }
                    projected.put(id, new Projection(project(source), source));
                });
        return Collections.unmodifiableMap(projected);
    }

    private static QuestDef project(QuestDefinitions.Quest source) {
        String id = source.id().value();
        QuestDef quest = new QuestDef(id);
        quest.setPublished(true);
        quest.setName(source.label());
        quest.setKernelOrigin(id);
        quest.setResetPolicy(source.reset().name());
        quest.setRepeat(repeat(source));
        quest.setCooldownSeconds(source.cooldownSeconds());
        quest.setBountyLimit(source.bountyLimit());

        for (QuestDefinitions.Stage sourceStage : source.stages()) {
            String stageKey = "s" + sourceStage.index();
            ArrayList<String> objectiveKeys = new ArrayList<>();
            int objectiveIndex = 0;
            for (QuestDefinitions.Objective sourceObjective : sourceStage.objectives()) {
                String objectiveKey = stageKey + "/o" + objectiveIndex++;
                Objective objective = new Objective(ObjectiveType.CUSTOM);
                objective.setKey(objectiveKey);
                objective.setDescription(sourceObjective.label());
                objective.params().put("event", sourceObjective.event().value());
                objective.params().put("amount", sourceObjective.count());
                objective.setKernelEvent(sourceObjective.event().value(), sourceObjective.match());
                quest.objectives().add(objective);
                objectiveKeys.add(objectiveKey);
            }

            List<QuestStage.Branch> branches = sourceStage.branches().stream()
                    .map(branch -> new QuestStage.Branch(branch.stage(), ""))
                    .toList();
            String rewardId = sourceStage.reward() == null ? "" : sourceStage.reward().value();
            quest.stages().add(new QuestStage(stageKey, sourceStage.label(), objectiveKeys, branches, rewardId));
        }
        return quest;
    }

    private static QuestDef.Repeat repeat(QuestDefinitions.Quest source) {
        return switch (source.reset()) {
            case NONE -> source.repeatable() ? QuestDef.Repeat.UNLIMITED : QuestDef.Repeat.NEVER;
            case DAILY -> QuestDef.Repeat.DAILY;
            case WEEKLY -> QuestDef.Repeat.WEEKLY;
            case COOLDOWN -> QuestDef.Repeat.COOLDOWN;
        };
    }
}
