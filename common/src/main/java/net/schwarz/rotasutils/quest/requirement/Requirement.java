package net.schwarz.rotasutils.quest.requirement;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.util.Nbt;

public final class Requirement {
    private RequirementType type;
    private final Params params;
    private boolean recommendationOnly;

    public Requirement(RequirementType type) {
        this(type, new CompoundTag());
    }

    public Requirement(RequirementType type, CompoundTag params) {
        this.type = type;
        this.params = new Params(params);
        this.params.applyDefaults(type.specs());
    }

    public RequirementType type() {
        return type;
    }

    public void setType(RequirementType type) {
        this.type = type;
        this.params.applyDefaults(type.specs());
    }

    public Params params() {
        return params;
    }

    public boolean recommendationOnly() {
        return recommendationOnly;
    }

    public void setRecommendationOnly(boolean recommendationOnly) {
        this.recommendationOnly = recommendationOnly;
    }

    public String summary() {
        StringBuilder out = new StringBuilder();
        for (ParamSpec spec : type.specs()) {
            String text;
            if (spec.kind() == ParamSpec.ParamKind.BOOL) {
                if (!params.getBool(spec.key(), false)) {
                    continue;
                }
                text = spec.key();
            } else {
                String value = switch (spec.kind()) {
                    case INT -> Integer.toString(params.getInt(spec.key(), 0));
                    case DOUBLE -> Double.toString(params.getDouble(spec.key(), 0));
                    default -> params.getString(spec.key(), "");
                };
                if (value.isEmpty()) {
                    continue;
                }
                text = spec.key() + "=" + value;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(text);
        }
        return out.toString();
    }

    public Requirement copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.put("params", params.tag());
        tag.putBoolean("recommend", recommendationOnly);
        return tag;
    }

    public static Requirement load(CompoundTag tag) {
        RequirementType type = Nbt.readEnum(tag, "type", RequirementType.class, RequirementType.MIN_LEVEL);
        Requirement requirement = new Requirement(type, tag.getCompound("params").copy());
        requirement.recommendationOnly = tag.getBoolean("recommend");
        return requirement;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Requirement requirement
                && type == requirement.type
                && recommendationOnly == requirement.recommendationOnly
                && params.tag().equals(requirement.params.tag());
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, recommendationOnly, params.tag().toString());
    }
}
