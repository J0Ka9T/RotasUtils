package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;

import java.util.regex.Pattern;

public record ZoneEffect(String effect, int amplifier) {
    public static final int MAX_AMPLIFIER = 4;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public ZoneEffect {
        if (effect == null || effect.length() > 128 || !ID.matcher(effect).matches()) {
            throw new IllegalArgumentException("Effect must be an effect id like minecraft:night_vision");
        }
        if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
            throw new IllegalArgumentException("Effect level must be 1.." + (MAX_AMPLIFIER + 1));
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("effect", effect);
        tag.putInt("amplifier", amplifier);
        return tag;
    }

    public static ZoneEffect load(CompoundTag tag) {
        return new ZoneEffect(tag.getString("effect"), tag.getInt("amplifier"));
    }
}
