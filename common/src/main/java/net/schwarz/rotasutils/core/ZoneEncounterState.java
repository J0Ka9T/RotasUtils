package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Live state of one zone spawn point, kept in world data so a restart neither duplicates a boss nor
 * skips its cooldown. Times are overworld game ticks.
 *
 * @param mob            the point's living mob, or null when none is alive
 * @param nextSpawnAt    earliest game time the next mob may appear
 * @param lastPlayerSeen last game time a player stood inside the zone, for the empty-zone reset
 */
public record ZoneEncounterState(UUID mob, long nextSpawnAt, long lastPlayerSeen) {
    public static ZoneEncounterState fresh(long now) {
        return new ZoneEncounterState(null, 0, now);
    }

    public ZoneEncounterState seen(long now) {
        return new ZoneEncounterState(mob, nextSpawnAt, now);
    }

    public ZoneEncounterState spawned(UUID next, long now) {
        return new ZoneEncounterState(next, nextSpawnAt, now);
    }

    public ZoneEncounterState died(long now, int respawnSeconds) {
        return new ZoneEncounterState(null, ZoneEncounterSchedule.respawnAt(now, respawnSeconds), lastPlayerSeen);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (mob != null) {
            tag.putUUID("mob", mob);
        }
        tag.putLong("next_spawn", nextSpawnAt);
        tag.putLong("last_seen", lastPlayerSeen);
        return tag;
    }

    public static ZoneEncounterState load(CompoundTag tag) {
        return new ZoneEncounterState(tag.hasUUID("mob") ? tag.getUUID("mob") : null,
                tag.getLong("next_spawn"), tag.getLong("last_seen"));
    }
}
