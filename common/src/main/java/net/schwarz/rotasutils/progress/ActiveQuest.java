package net.schwarz.rotasutils.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.schwarz.rotasutils.util.Nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** A quest a player currently has accepted, plus their per-objective counters. */
public final class ActiveQuest {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_./-]{1,96}");
    private static final int MAX_STAGE = 15;

    private final String questId;
    /** Version of the template at accept time, used to migrate on republish. */
    private int questVersion;
    private long startedAtEpochSeconds;
    /** 0 when the quest has no time limit. */
    private long deadlineEpochSeconds;
    private int[] progress;
    private boolean[] completed;
    private UUID partyId;
    /** Counts objective ticks contributed by this player for contribution-based rewards. */
    private int contribution;
    private boolean turnInReady;
    /** Set once the player has been warned that the deadline is close; never re-warns. */
    private boolean deadlineWarned;
    private int stage;
    private final Map<String, Integer> keyedProgress = new LinkedHashMap<>();
    private final Set<String> keyedComplete = new LinkedHashSet<>();

    public ActiveQuest(String questId, int questVersion, int objectiveCount) {
        this.questId = questId;
        this.questVersion = questVersion;
        this.progress = new int[objectiveCount];
        this.completed = new boolean[objectiveCount];
    }

    public String questId() {
        return questId;
    }

    public int questVersion() {
        return questVersion;
    }

    public void setQuestVersion(int questVersion) {
        this.questVersion = questVersion;
    }

    public long startedAt() {
        return startedAtEpochSeconds;
    }

    public void setStartedAt(long startedAt) {
        this.startedAtEpochSeconds = startedAt;
    }

    public long deadline() {
        return deadlineEpochSeconds;
    }

    public void setDeadline(long deadline) {
        this.deadlineEpochSeconds = deadline;
    }

    public UUID partyId() {
        return partyId;
    }

    public void setPartyId(UUID partyId) {
        this.partyId = partyId;
    }

    public int contribution() {
        return contribution;
    }

    public void addContribution(int amount) {
        this.contribution += amount;
    }

    public boolean turnInReady() {
        return turnInReady;
    }

    public void setTurnInReady(boolean turnInReady) {
        this.turnInReady = turnInReady;
    }

    public boolean deadlineWarned() {
        return deadlineWarned;
    }

    public void setDeadlineWarned(boolean deadlineWarned) {
        this.deadlineWarned = deadlineWarned;
    }

    public int objectiveCount() {
        return progress.length;
    }

    public int stage() {
        return stage;
    }

    public void setStage(int stage) {
        if (stage < 0 || stage > MAX_STAGE) {
            throw new IllegalArgumentException("Quest stage must be between 0 and " + MAX_STAGE);
        }
        this.stage = stage;
    }

    public Map<String, Integer> keyedProgress() {
        return Collections.unmodifiableMap(keyedProgress);
    }

    public int progress(String key) {
        return keyedProgress.getOrDefault(validKey(key), 0);
    }

    public void setProgress(String key, int value) {
        keyedProgress.put(validKey(key), Math.max(0, value));
    }

    public boolean isComplete(String key) {
        return keyedComplete.contains(validKey(key));
    }

    public void setComplete(String key, boolean value) {
        key = validKey(key);
        if (value) {
            keyedComplete.add(key);
        } else {
            keyedComplete.remove(key);
        }
    }

    public int progress(int index) {
        return index >= 0 && index < progress.length ? progress[index] : 0;
    }

    public void setProgress(int index, int value) {
        if (index >= 0 && index < progress.length) {
            progress[index] = Math.max(0, value);
        }
    }

    public boolean isComplete(int index) {
        return index >= 0 && index < completed.length && completed[index];
    }

    public void setComplete(int index, boolean value) {
        if (index >= 0 && index < completed.length) {
            completed[index] = value;
        }
    }

    /**
     * Grows or shrinks the counters to match a republished template, keeping the
     * counters that still line up. Player progress is never silently discarded.
     */
    public void resize(int newCount) {
        if (newCount == progress.length) {
            return;
        }
        int[] newProgress = new int[newCount];
        boolean[] newCompleted = new boolean[newCount];
        int shared = Math.min(newCount, progress.length);
        System.arraycopy(progress, 0, newProgress, 0, shared);
        System.arraycopy(completed, 0, newCompleted, 0, shared);
        progress = newProgress;
        completed = newCompleted;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("quest", questId);
        tag.putInt("version", questVersion);
        tag.putLong("started", startedAtEpochSeconds);
        tag.putLong("deadline", deadlineEpochSeconds);
        tag.putIntArray("progress", progress);
        byte[] flags = new byte[completed.length];
        for (int i = 0; i < completed.length; i++) {
            flags[i] = (byte) (completed[i] ? 1 : 0);
        }
        tag.putByteArray("completed", flags);
        if (partyId != null) {
            tag.putUUID("party", partyId);
        }
        tag.putInt("contribution", contribution);
        tag.putBoolean("turn_in", turnInReady);
        tag.putBoolean("warned", deadlineWarned);
        if (stage != 0) {
            tag.putInt("stage", stage);
        }
        if (!keyedProgress.isEmpty()) {
            tag.put("key_progress", Nbt.saveStringIntMap(keyedProgress));
        }
        if (!keyedComplete.isEmpty()) {
            tag.put("key_complete", Nbt.saveStrings(keyedComplete));
        }
        return tag;
    }

    public static ActiveQuest load(CompoundTag tag) {
        int[] progress = tag.getIntArray("progress");
        ActiveQuest quest = new ActiveQuest(tag.getString("quest"), tag.getInt("version"), progress.length);
        System.arraycopy(progress, 0, quest.progress, 0, progress.length);
        byte[] flags = tag.getByteArray("completed");
        for (int i = 0; i < Math.min(flags.length, quest.completed.length); i++) {
            quest.completed[i] = flags[i] != 0;
        }
        quest.startedAtEpochSeconds = tag.getLong("started");
        quest.deadlineEpochSeconds = tag.getLong("deadline");
        if (tag.hasUUID("party")) {
            quest.partyId = tag.getUUID("party");
        }
        quest.contribution = tag.getInt("contribution");
        quest.turnInReady = tag.getBoolean("turn_in");
        quest.deadlineWarned = tag.getBoolean("warned");
        if (tag.contains("stage")) {
            quest.setStage(tag.getInt("stage"));
        }
        for (Map.Entry<String, Integer> entry : Nbt.loadStringIntMap(tag, "key_progress").entrySet()) {
            quest.setProgress(entry.getKey(), entry.getValue());
        }
        for (String key : Nbt.loadStrings(tag, "key_complete")) {
            quest.setComplete(key, true);
        }
        return quest;
    }

    private static String validKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid objective key: " + key);
        }
        return key;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static ActiveQuest read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(tag == null ? new CompoundTag() : tag);
    }
}
