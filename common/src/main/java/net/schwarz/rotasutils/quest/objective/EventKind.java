package net.schwarz.rotasutils.quest.objective;

/**
 * The game event an objective listens to.
 *
 * <p>Objectives are bucketed by kind at accept time so a fired event only walks the
 * handful of objectives that could possibly care about it. Nothing scans every quest
 * or every player per tick.
 */
public enum EventKind {
    KILL_ENTITY,
    KILL_PLAYER,
    DELIVER_ITEM,
    COLLECT_ITEM,
    TALK_NPC,
    LOCATION,
    BLOCK_BREAK,
    BLOCK_PLACE,
    BLOCK_INTERACT,
    ENTITY_INTERACT,
    CRAFT_ITEM,
    SMELT_ITEM,
    USE_ITEM,
    QUEST_COMPLETE,
    DIALOGUE_CHOICE,
    ESCORT,
    DEFEND,
    CUSTOM
}
