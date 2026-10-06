package net.schwarz.rotasutils.quest.reward;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.util.Nbt;

public final class Reward {
    private RewardType type;
    private final Params params;
    private Mode mode = Mode.GUARANTEED;
    private int weight = 1;
    private String pool = "";
    private String path = "";

    public Reward(RewardType type) {
        this(type, new CompoundTag());
    }

    public Reward(RewardType type, CompoundTag params) {
        this.type = type;
        this.params = new Params(params);
        this.params.applyDefaults(type.specs());
    }

    public RewardType type() {
        return type;
    }

    public void setType(RewardType type) {
        this.type = type;
        this.params.applyDefaults(type.specs());
    }

    public Params params() {
        return params;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public int weight() {
        return Math.max(1, weight);
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String pool() {
        return pool;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    public String path() {
        return path;
    }

    public void setPath(String path) {
        this.path = net.schwarz.rotasutils.quest.objective.Objective.cleanPath(path);
    }

    public Reward copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.put("params", params.tag());
        tag.putString("mode", mode.name());
        tag.putInt("weight", weight);
        tag.putString("pool", pool);
        if (!path.isEmpty()) {
            tag.putString("path", path);
        }
        return tag;
    }

    public static Reward load(CompoundTag tag) {
        RewardType type = Nbt.readEnum(tag, "type", RewardType.class, RewardType.ITEM);
        Reward reward = new Reward(type, tag.getCompound("params").copy());
        reward.mode = Nbt.readEnum(tag, "mode", Mode.class, Mode.GUARANTEED);
        reward.weight = Math.max(1, tag.getInt("weight"));
        reward.pool = tag.getString("pool");
        reward.path = net.schwarz.rotasutils.quest.objective.Objective.cleanPath(tag.getString("path"));
        return reward;
    }

    public enum Mode {
        GUARANTEED("Guaranteed"),
        RANDOM_WEIGHTED("Random (weighted)"),
        PLAYER_CHOICE("Player chooses one"),
        OPTIONAL_BONUS("Optional objective bonus"),
        FIRST_COMPLETION("First completion only"),
        REPEAT_ONLY("Repeat completion only"),
        PARTY_CONTRIBUTION("Scaled by party contribution");

        public static final Mode[] VALUES = values();
        private final String display;

        Mode(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("reward_mode", this, display);
        }
    }
}
