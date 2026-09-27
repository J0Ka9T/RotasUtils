package net.schwarz.rotasutils.core;

import java.util.Locale;

/**
 * What a zone is for. The type is a label plus a one-click preset ({@link ZonePresets}); every setting a
 * preset fills in stays editable afterwards, so a type never locks a zone's behaviour.
 */
public enum ZoneType {
    CUSTOM("Custom", "Your own settings."),
    TOWN("Town", "Safe hub: no hostile spawns and new mobs are not leveled."),
    DUNGEON("Dungeon", "Own mobs only, minibosses, no elytra or ender pearls in."),
    BOSS_ARENA("Boss arena", "Boss spawn point, no natural spawns, no escaping by air or pearl."),
    PVP_ARENA("PvP arena", "PvP on, inventory kept and no XP lost on death.");

    private final String label;
    private final String description;

    ZoneType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public static ZoneType parse(String value, ZoneType fallback) {
        try {
            return value == null || value.isBlank() ? fallback : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return fallback;
        }
    }
}
