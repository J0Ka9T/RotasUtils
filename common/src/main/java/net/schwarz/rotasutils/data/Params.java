package net.schwarz.rotasutils.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Typed accessor over the parameter bag carried by objectives, rewards and effects. */
public final class Params {
    private final CompoundTag tag;

    public Params(CompoundTag tag) {
        this.tag = tag;
    }

    public CompoundTag tag() {
        return tag;
    }

    public Params copy() {
        return new Params(tag.copy());
    }

    public int getInt(String key, int fallback) {
        return tag.contains(key) ? tag.getInt(key) : fallback;
    }

    public double getDouble(String key, double fallback) {
        return tag.contains(key) ? tag.getDouble(key) : fallback;
    }

    public boolean getBool(String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    public String getString(String key, String fallback) {
        return tag.contains(key) ? tag.getString(key) : fallback;
    }

    public ResourceLocation getId(String key) {
        String raw = tag.getString(key);
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }

    public void put(String key, String value) {
        tag.putString(key, value);
    }

    public void put(String key, int value) {
        tag.putInt(key, value);
    }

    public void put(String key, double value) {
        tag.putDouble(key, value);
    }

    public void put(String key, boolean value) {
        tag.putBoolean(key, value);
    }

    /** Fills any spec-declared key that is missing so editors never see a null field. */
    public void applyDefaults(List<ParamSpec> specs) {
        for (ParamSpec spec : specs) {
            if (tag.contains(spec.key())) {
                continue;
            }
            String def = spec.defaultValue();
            switch (spec.kind()) {
                case INT -> tag.putInt(spec.key(), parseInt(def));
                case DOUBLE -> tag.putDouble(spec.key(), parseDouble(def));
                case BOOL -> tag.putBoolean(spec.key(), Boolean.parseBoolean(def));
                default -> tag.putString(spec.key(), def == null ? "" : def);
            }
        }
    }

    private static int parseInt(String value) {
        try {
            return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return value == null || value.isEmpty() ? 0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
