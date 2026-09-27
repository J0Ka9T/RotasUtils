package net.schwarz.rotasutils.quest;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Serializable stage membership and branching metadata for a canonical quest. */
public record QuestStage(String key, String label, List<String> objectiveKeys,
                         List<Branch> branches, String rewardId) {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_./-]{1,96}");
    private static final int MAX_LABEL_LENGTH = 128;
    private static final int MAX_ID_LENGTH = 200;

    public QuestStage {
        key = validKey(key, "stage key");
        label = Objects.requireNonNull(label, "label");
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Stage label exceeds " + MAX_LABEL_LENGTH + " characters");
        }
        objectiveKeys = List.copyOf(objectiveKeys);
        branches = List.copyOf(branches);
        rewardId = validId(rewardId, "reward ID");

        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        for (String objectiveKey : objectiveKeys) {
            validKey(objectiveKey, "objective key");
            if (!uniqueKeys.add(objectiveKey)) {
                throw new IllegalArgumentException("Duplicate objective key in stage: " + objectiveKey);
            }
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putString("label", label);
        tag.put("objective_keys", Nbt.saveStrings(objectiveKeys));
        tag.put("branches", Nbt.saveList(branches, Branch::save));
        if (!rewardId.isEmpty()) {
            tag.putString("reward_id", rewardId);
        }
        return tag;
    }

    public static QuestStage load(CompoundTag tag) {
        return new QuestStage(tag.getString("key"), tag.getString("label"),
                Nbt.loadStrings(tag, "objective_keys"),
                Nbt.loadList(tag, "branches", Branch::load), tag.getString("reward_id"));
    }

    static String validKey(String key, String label) {
        Objects.requireNonNull(key, label);
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + key);
        }
        return key;
    }

    static String validId(String id, String label) {
        Objects.requireNonNull(id, label);
        if (id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(label + " exceeds " + MAX_ID_LENGTH + " characters");
        }
        return id;
    }

    public record Branch(int targetStage, String conditionId) {
        public Branch {
            if (targetStage < 0) {
                throw new IllegalArgumentException("Branch target cannot be negative");
            }
            conditionId = validId(conditionId, "branch condition ID");
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("target_stage", targetStage);
            if (!conditionId.isEmpty()) {
                tag.putString("condition_id", conditionId);
            }
            return tag;
        }

        private static Branch load(CompoundTag tag) {
            return new Branch(tag.getInt("target_stage"), tag.getString("condition_id"));
        }
    }
}
