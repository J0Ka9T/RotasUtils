package net.schwarz.rotasutils.quest.objective;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.util.Nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** One configured objective inside a quest definition. */
public final class Objective {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_./-]{1,96}");
    private static final int MAX_ID_LENGTH = 200;

    private ObjectiveType type;
    private String description = "";
    private final Params params;
    private String key = "";
    private boolean saveKey;
    private String kernelEvent = "";
    private final Map<String, String> kernelMatch = new LinkedHashMap<>();

    private boolean optional;
    private boolean hidden;
    private boolean partyShared = true;
    /** Sequential quests only complete objectives in ascending step order. */
    private int step;
    /** Objectives sharing an alternative group complete each other. */
    private String alternativeGroup = "";
    private int timeLimitSeconds;

    public Objective(ObjectiveType type) {
        this(type, new CompoundTag());
    }

    public Objective(ObjectiveType type, CompoundTag params) {
        this.type = type;
        this.params = new Params(params);
        this.params.applyDefaults(type.specs());
    }

    public ObjectiveType type() {
        return type;
    }

    /** Switching type keeps any parameter the new type also declares. */
    public void setType(ObjectiveType type) {
        this.type = type;
        this.params.applyDefaults(type.specs());
    }

    public Params params() {
        return params;
    }

    public String key() {
        return key;
    }

    public void setKey(String key) {
        this.key = validKey(key);
        this.saveKey = true;
    }

    /** Assigns an index-derived key to old objectives without changing their serialized shape. */
    public void useDefaultKey(String key) {
        if (!saveKey) {
            this.key = validKey(key);
        }
    }

    public String kernelEvent() {
        return kernelEvent;
    }

    public Map<String, String> kernelMatch() {
        return Collections.unmodifiableMap(kernelMatch);
    }

    public void setKernelEvent(String eventId, Map<String, String> match) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(match, "match");
        if (eventId.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Kernel event ID exceeds " + MAX_ID_LENGTH + " characters");
        }
        kernelEvent = eventId;
        kernelMatch.clear();
        for (Map.Entry<String, String> entry : match.entrySet()) {
            String fact = Objects.requireNonNull(entry.getKey(), "kernel match key");
            String expected = Objects.requireNonNull(entry.getValue(), "kernel match value");
            kernelMatch.put(fact, expected);
        }
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean optional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public boolean hidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean partyShared() {
        return partyShared;
    }

    public void setPartyShared(boolean partyShared) {
        this.partyShared = partyShared;
    }

    public int step() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String alternativeGroup() {
        return alternativeGroup;
    }

    public void setAlternativeGroup(String alternativeGroup) {
        this.alternativeGroup = alternativeGroup;
    }

    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public int requiredAmount() {
        return Math.max(1, params.getInt("amount", 1));
    }

    public Objective copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putString("desc", description);
        tag.put("params", params.tag());
        tag.putBoolean("optional", optional);
        tag.putBoolean("hidden", hidden);
        tag.putBoolean("party_shared", partyShared);
        tag.putInt("step", step);
        tag.putString("alt_group", alternativeGroup);
        tag.putInt("time_limit", timeLimitSeconds);
        if (saveKey) {
            tag.putString("objective_key", key);
        }
        if (!kernelEvent.isEmpty()) {
            tag.putString("kernel_event", kernelEvent);
            CompoundTag matches = new CompoundTag();
            for (Map.Entry<String, String> entry : kernelMatch.entrySet()) {
                matches.putString(entry.getKey(), entry.getValue());
            }
            tag.put("kernel_match", matches);
        }
        return tag;
    }

    public static Objective load(CompoundTag tag) {
        ObjectiveType type = "CUSTOM_EVENT".equals(tag.getString("type"))
                ? ObjectiveType.CUSTOM
                : Nbt.readEnum(tag, "type", ObjectiveType.class, ObjectiveType.KILL_MOB);
        Objective objective = new Objective(type, tag.getCompound("params").copy());
        objective.description = tag.getString("desc");
        objective.optional = tag.getBoolean("optional");
        objective.hidden = tag.getBoolean("hidden");
        objective.partyShared = !tag.contains("party_shared") || tag.getBoolean("party_shared");
        objective.step = tag.getInt("step");
        objective.alternativeGroup = tag.getString("alt_group");
        objective.timeLimitSeconds = tag.getInt("time_limit");
        if (tag.contains("objective_key")) {
            objective.setKey(tag.getString("objective_key"));
        }
        if (tag.contains("kernel_event")) {
            CompoundTag matches = tag.getCompound("kernel_match");
            Map<String, String> kernelMatch = new LinkedHashMap<>();
            for (String fact : matches.getAllKeys()) {
                kernelMatch.put(fact, matches.getString(fact));
            }
            objective.setKernelEvent(tag.getString("kernel_event"), kernelMatch);
        }
        return objective;
    }

    private static String validKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid objective key: " + key);
        }
        return key;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static Objective read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(tag == null ? new CompoundTag() : tag);
    }

    /** Fallback line shown when the admin left the description empty. */
    public String displayText() {
        if (!description.isBlank()) {
            return description;
        }
        return type.display() + " x" + requiredAmount();
    }
}
