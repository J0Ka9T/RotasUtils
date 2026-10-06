package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

public record ZoneDisplay(Border border, boolean hud, boolean banner) {
    public static final ZoneDisplay DEFAULT = new ZoneDisplay(Border.AUTO, true, true);

    public enum Border {
        AUTO,
        ALWAYS,
        NEVER;

        public Border next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String label() {
            String lower = name().toLowerCase(java.util.Locale.ROOT);
            return net.schwarz.rotasutils.util.ThaiText.label("zone_border", this,
                    Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }
    }

    public ZoneDisplay {
        border = border == null ? Border.AUTO : border;
    }

    public ZoneDisplay withBorder(Border next) {
        return new ZoneDisplay(next, hud, banner);
    }

    public ZoneDisplay withHud(boolean next) {
        return new ZoneDisplay(border, next, banner);
    }

    public ZoneDisplay withBanner(boolean next) {
        return new ZoneDisplay(border, hud, next);
    }

    public boolean borderVisible(boolean lockedForViewer, ZoneDef.Danger danger) {
        return switch (border) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> lockedForViewer || danger == ZoneDef.Danger.DANGEROUS || danger == ZoneDef.Danger.DEADLY;
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("border", border.name());
        tag.putBoolean("hud", hud);
        tag.putBoolean("banner", banner);
        return tag;
    }

    public static ZoneDisplay load(CompoundTag tag) {
        return new ZoneDisplay(Nbt.readEnum(tag, "border", Border.class, Border.AUTO),
                !tag.contains("hud") || tag.getBoolean("hud"),
                !tag.contains("banner") || tag.getBoolean("banner"));
    }
}
