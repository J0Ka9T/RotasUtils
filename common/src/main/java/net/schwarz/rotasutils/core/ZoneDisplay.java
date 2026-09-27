package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

/**
 * How a zone presents itself to players.
 *
 * @param border when players see the zone's border in the world as they approach it
 * @param hud    whether the zone shows in the player's zone chip while they stand in it
 * @param banner whether crossing into the zone shows the automatic entry banner (a zone with its own
 *               enter title shows that title instead)
 */
public record ZoneDisplay(Border border, boolean hud, boolean banner) {
    public static final ZoneDisplay DEFAULT = new ZoneDisplay(Border.AUTO, true, true);

    public enum Border {
        /** Shown when the zone is locked for the player or marked Dangerous or Deadly. */
        AUTO,
        /** Always shown to players who come near it. */
        ALWAYS,
        /** Never shown to players (administrators still see it with the Zone Wand). */
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

    /** True when players near this zone should see its border, given their lock state and its danger. */
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
