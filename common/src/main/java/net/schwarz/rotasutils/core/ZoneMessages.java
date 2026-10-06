package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;

import java.util.regex.Pattern;

public record ZoneMessages(String enterTitle, String enterSubtitle, String leaveTitle, String sound) {
    public static final int MAX_TEXT = 64;
    public static final ZoneMessages NONE = new ZoneMessages("", "", "", "");
    private static final Pattern SOUND = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public ZoneMessages {
        enterTitle = text(enterTitle, "Enter title");
        enterSubtitle = text(enterSubtitle, "Enter subtitle");
        leaveTitle = text(leaveTitle, "Leave title");
        sound = sound == null ? "" : sound.trim();
        if (!sound.isEmpty() && (sound.length() > 128 || !SOUND.matcher(sound).matches())) {
            throw new IllegalArgumentException("Sound must be a sound id like minecraft:block.bell.use");
        }
    }

    public boolean hasEnter() {
        return !enterTitle.isEmpty() || !enterSubtitle.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("enter_title", enterTitle);
        tag.putString("enter_subtitle", enterSubtitle);
        tag.putString("leave_title", leaveTitle);
        tag.putString("sound", sound);
        return tag;
    }

    public static ZoneMessages load(CompoundTag tag) {
        return new ZoneMessages(tag.getString("enter_title"), tag.getString("enter_subtitle"),
                tag.getString("leave_title"), tag.getString("sound"));
    }

    private static String text(String value, String name) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > MAX_TEXT) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_TEXT + " characters");
        }
        return trimmed;
    }
}
