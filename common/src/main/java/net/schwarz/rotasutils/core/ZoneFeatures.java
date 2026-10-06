package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ZoneFeatures(ZoneType type, boolean isolateMobs, List<ZoneSpawnPoint> spawnPoints,
                           ZoneMessages messages, List<ZoneEffect> effects, ZoneMovement movement,
                           ZoneDungeon dungeon, ZoneDisplay display) {
    public static final int MAX_SPAWN_POINTS = 16;
    public static final int MAX_EFFECTS = 8;
    public static final ZoneFeatures DEFAULT = new ZoneFeatures(ZoneType.CUSTOM, false, List.of(),
            ZoneMessages.NONE, List.of(), ZoneMovement.NONE, ZoneDungeon.NONE, ZoneDisplay.DEFAULT);

    public ZoneFeatures(ZoneType type, boolean isolateMobs, List<ZoneSpawnPoint> spawnPoints,
                        ZoneMessages messages, List<ZoneEffect> effects, ZoneMovement movement) {
        this(type, isolateMobs, spawnPoints, messages, effects, movement, ZoneDungeon.NONE, ZoneDisplay.DEFAULT);
    }

    public ZoneFeatures(ZoneType type, boolean isolateMobs, List<ZoneSpawnPoint> spawnPoints,
                        ZoneMessages messages, List<ZoneEffect> effects, ZoneMovement movement, ZoneDungeon dungeon) {
        this(type, isolateMobs, spawnPoints, messages, effects, movement, dungeon, ZoneDisplay.DEFAULT);
    }

    public ZoneFeatures {
        type = type == null ? ZoneType.CUSTOM : type;
        spawnPoints = List.copyOf(spawnPoints == null ? List.of() : spawnPoints);
        messages = messages == null ? ZoneMessages.NONE : messages;
        effects = List.copyOf(effects == null ? List.of() : effects);
        movement = movement == null ? ZoneMovement.NONE : movement;
        dungeon = dungeon == null ? ZoneDungeon.NONE : dungeon;
        display = display == null ? ZoneDisplay.DEFAULT : display;
        if (spawnPoints.size() > MAX_SPAWN_POINTS) {
            throw new IllegalArgumentException("A zone holds at most " + MAX_SPAWN_POINTS + " spawn points");
        }
        Set<String> ids = new HashSet<>();
        for (ZoneSpawnPoint point : spawnPoints) {
            if (!ids.add(point.id())) {
                throw new IllegalArgumentException("Duplicate spawn point id: " + point.id());
            }
        }
        if (effects.size() > MAX_EFFECTS) {
            throw new IllegalArgumentException("A zone holds at most " + MAX_EFFECTS + " effects");
        }
        Set<String> effectIds = new HashSet<>();
        for (ZoneEffect effect : effects) {
            if (!effectIds.add(effect.effect())) {
                throw new IllegalArgumentException("Effect listed twice: " + effect.effect());
            }
        }
    }

    public ZoneFeatures withType(ZoneType next) {
        return new ZoneFeatures(next, isolateMobs, spawnPoints, messages, effects, movement, dungeon, display);
    }

    public ZoneFeatures withIsolateMobs(boolean next) {
        return new ZoneFeatures(type, next, spawnPoints, messages, effects, movement, dungeon, display);
    }

    public ZoneFeatures withSpawnPoints(List<ZoneSpawnPoint> next) {
        return new ZoneFeatures(type, isolateMobs, next, messages, effects, movement, dungeon, display);
    }

    public ZoneFeatures withMessages(ZoneMessages next) {
        return new ZoneFeatures(type, isolateMobs, spawnPoints, next, effects, movement, dungeon, display);
    }

    public ZoneFeatures withEffects(List<ZoneEffect> next) {
        return new ZoneFeatures(type, isolateMobs, spawnPoints, messages, next, movement, dungeon, display);
    }

    public ZoneFeatures withMovement(ZoneMovement next) {
        return new ZoneFeatures(type, isolateMobs, spawnPoints, messages, effects, next, dungeon, display);
    }

    public ZoneFeatures withDungeon(ZoneDungeon next) {
        return new ZoneFeatures(type, isolateMobs, spawnPoints, messages, effects, movement, next, display);
    }

    public ZoneFeatures withDisplay(ZoneDisplay next) {
        return new ZoneFeatures(type, isolateMobs, spawnPoints, messages, effects, movement, dungeon, next);
    }

    public boolean dungeonRun() {
        return dungeon.enabled();
    }

    public ZoneSpawnPoint spawnPoint(String id) {
        for (ZoneSpawnPoint point : spawnPoints) {
            if (point.id().equals(id)) {
                return point;
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putBoolean("isolate_mobs", isolateMobs);
        tag.put("spawn_points", Nbt.saveList(spawnPoints, ZoneSpawnPoint::save));
        tag.put("messages", messages.save());
        tag.put("effects", Nbt.saveList(effects, ZoneEffect::save));
        tag.put("movement", movement.save());
        tag.put("dungeon", dungeon.save());
        tag.put("display", display.save());
        return tag;
    }

    public static ZoneFeatures load(CompoundTag tag) {
        return new ZoneFeatures(Nbt.readEnum(tag, "type", ZoneType.class, ZoneType.CUSTOM),
                tag.getBoolean("isolate_mobs"),
                Nbt.loadList(tag, "spawn_points", ZoneSpawnPoint::load),
                tag.contains("messages") ? ZoneMessages.load(tag.getCompound("messages")) : ZoneMessages.NONE,
                Nbt.loadList(tag, "effects", ZoneEffect::load),
                tag.contains("movement") ? ZoneMovement.load(tag.getCompound("movement")) : ZoneMovement.NONE,
                tag.contains("dungeon") ? ZoneDungeon.load(tag.getCompound("dungeon")) : ZoneDungeon.NONE,
                tag.contains("display") ? ZoneDisplay.load(tag.getCompound("display")) : ZoneDisplay.DEFAULT);
    }
}
