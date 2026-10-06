package net.schwarz.rotasutils.skill;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.util.Nbt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SkillEffect {
    private EffectType type;
    private final Params params;
    private String condition = "";
    private Stacking stacking = Stacking.ADD;

    public SkillEffect(EffectType type) {
        this(type, new CompoundTag());
    }

    public SkillEffect(EffectType type, CompoundTag params) {
        this.type = type;
        this.params = new Params(params);
        this.params.applyDefaults(type.specs());
    }

    public EffectType type() {
        return type;
    }

    public void setType(EffectType type) {
        this.type = type;
        this.params.applyDefaults(type.specs());
    }

    public Params params() {
        return params;
    }

    public String condition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition == null ? "" : condition;
    }

    public Stacking stacking() {
        return stacking;
    }

    public void setStacking(Stacking stacking) {
        this.stacking = stacking;
    }

    public double valueAt(int rank) {
        return valueAt(rank, 1);
    }

    public double valueAt(int rank, int level) {
        double perRank = params.getDouble("value", 0)
                + params.getDouble("per_level", 0) * Math.max(0, level - 1);
        double total = perRank * Math.max(0, rank);
        double max = params.getDouble("max", 0);
        if (!Double.isFinite(total) || !Double.isFinite(max)) {
            return 0;
        }
        return max > 0 ? Math.min(total, max) : total;
    }

    public boolean percentage() {
        return params.getBool("percent", false);
    }

    public static UUID modifierId(String nodeId, int effectIndex) {
        return UUID.nameUUIDFromBytes(("rotasutils:skill:" + nodeId + ":" + effectIndex).getBytes(StandardCharsets.UTF_8));
    }

    public SkillEffect copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.put("params", params.tag());
        tag.putString("condition", condition);
        tag.putString("stacking", stacking.name());
        return tag;
    }

    public static SkillEffect load(CompoundTag tag) {
        EffectType type = Nbt.readEnum(tag, "type", EffectType.class, EffectType.MAX_HEALTH);
        SkillEffect effect = new SkillEffect(type, tag.getCompound("params").copy());
        effect.condition = tag.getString("condition");
        effect.stacking = Nbt.readEnum(tag, "stacking", Stacking.class, Stacking.ADD);
        return effect;
    }

    public enum Stacking {
        ADD("Additive"),
        MULTIPLY_BASE("Multiply base"),
        MULTIPLY_TOTAL("Multiply total");

        public static final Stacking[] VALUES = values();
        private final String display;

        Stacking(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("effect_stacking", this, display);
        }
    }
}
