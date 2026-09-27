package net.schwarz.rotasutils.event;

import net.schwarz.rotasutils.quest.objective.EventKind;

import java.util.Locale;

/**
 * Every moment in the game this mod can react to.
 *
 * <p>This is the catalogue an administrator browses: one entry per thing that happens, with what the
 * server does about it. A type does not belong to one mod - {@code KILL_ENTITY} is the same event
 * whether the mob came from vanilla, Alex's Mobs or Cataclysm - so a modpack is covered by filtering a
 * type down to a namespace or an exact id rather than by shipping a hook for every mod.</p>
 *
 * <p>{@link #subject()} says what the filter on such a rule is matched against: an entity id, an item
 * id, a block id, or nothing at all.</p>
 */
public enum EventType {
    // Combat -------------------------------------------------------------------------------------
    KILL_ENTITY(Category.COMBAT, Subject.ENTITY, true),
    KILL_BOSS(Category.COMBAT, Subject.ENTITY, true),
    KILL_PLAYER(Category.COMBAT, Subject.ENTITY, true),
    PLAYER_DEATH(Category.COMBAT, Subject.ENTITY, false),
    SPELL_DAMAGE(Category.COMBAT, Subject.NONE, true),

    // Gathering and crafting ---------------------------------------------------------------------
    BLOCK_BREAK(Category.WORLD, Subject.BLOCK, true),
    BLOCK_PLACE(Category.WORLD, Subject.BLOCK, false),
    BLOCK_INTERACT(Category.WORLD, Subject.BLOCK, false),
    CRAFT_ITEM(Category.CRAFT, Subject.ITEM, true),
    SMELT_ITEM(Category.CRAFT, Subject.ITEM, true),
    USE_ITEM(Category.CRAFT, Subject.ITEM, false),
    COLLECT_ITEM(Category.CRAFT, Subject.ITEM, false),
    FISHING(Category.CRAFT, Subject.ITEM, true),

    // People -------------------------------------------------------------------------------------
    ENTITY_INTERACT(Category.PEOPLE, Subject.ENTITY, false),
    TALK_NPC(Category.PEOPLE, Subject.ENTITY, false),
    DIALOGUE_CHOICE(Category.PEOPLE, Subject.NONE, false),
    MERCHANT_TRADE(Category.PEOPLE, Subject.ITEM, true),

    // Progress -----------------------------------------------------------------------------------
    QUEST_COMPLETE(Category.PROGRESS, Subject.NONE, true),
    LEVEL_UP(Category.PROGRESS, Subject.NONE, false),
    TITLE_EARNED(Category.PROGRESS, Subject.NONE, false),
    LOCATION(Category.PROGRESS, Subject.NONE, true),
    PLAYER_LOGIN(Category.PROGRESS, Subject.NONE, false),

    // This mod's own systems ---------------------------------------------------------------------
    REFINE_SUCCESS(Category.ROTAS, Subject.ITEM, false),
    REFINE_FAIL(Category.ROTAS, Subject.ITEM, false),
    CARD_DROP(Category.ROTAS, Subject.ITEM, false),
    MONSTER_DROP(Category.ROTAS, Subject.ENTITY, false);

    /** How the catalogue is grouped in the interface. */
    public enum Category { COMBAT, WORLD, CRAFT, PEOPLE, PROGRESS, ROTAS }

    /** What a rule's filter is matched against. */
    public enum Subject { ENTITY, ITEM, BLOCK, NONE }

    private final Category category;
    private final Subject subject;
    private final boolean carriesXp;

    EventType(Category category, Subject subject, boolean carriesXp) {
        this.category = category;
        this.subject = subject;
        this.carriesXp = carriesXp;
    }

    public Category category() {
        return category;
    }

    public Subject subject() {
        return subject;
    }

    /**
     * True when experience is already granted for this event elsewhere, so a rule's multiplier has
     * something to scale. On the others a multiplier would do nothing, and the interface says so.
     */
    public boolean carriesXp() {
        return carriesXp;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Translation key of the player-facing name. */
    public String nameKey() {
        return "rotasutils.event.type." + key();
    }

    /** Translation key of the one-line explanation. */
    public String helpKey() {
        return "rotasutils.event.help." + key();
    }

    public static EventType byName(String name) {
        if (name != null) {
            for (EventType type : values()) {
                if (type.name().equalsIgnoreCase(name.trim())) {
                    return type;
                }
            }
        }
        return null;
    }

    /** The catalogue entry an experience source belongs to, or null when it has none. */
    public static EventType of(net.schwarz.rotasutils.level.XpSource source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case MOB_KILL -> KILL_ENTITY;
            case BOSS_KILL -> KILL_BOSS;
            case PLAYER_KILL -> KILL_PLAYER;
            case MINING -> BLOCK_BREAK;
            case CRAFTING -> CRAFT_ITEM;
            case SMELTING -> SMELT_ITEM;
            case FISHING -> FISHING;
            case TRADING -> MERCHANT_TRADE;
            case DISCOVERY -> LOCATION;
            case QUEST_COMPLETE, FIRST_COMPLETION, OPTIONAL_OBJECTIVE -> QUEST_COMPLETE;
            default -> null;
        };
    }

    /** The catalogue entry a quest objective event belongs to, or null when it has none. */
    public static EventType of(EventKind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case KILL_ENTITY -> KILL_ENTITY;
            case KILL_PLAYER -> KILL_PLAYER;
            case BLOCK_BREAK -> BLOCK_BREAK;
            case BLOCK_PLACE -> BLOCK_PLACE;
            case BLOCK_INTERACT -> BLOCK_INTERACT;
            case CRAFT_ITEM -> CRAFT_ITEM;
            case SMELT_ITEM -> SMELT_ITEM;
            case USE_ITEM -> USE_ITEM;
            case COLLECT_ITEM -> COLLECT_ITEM;
            case ENTITY_INTERACT -> ENTITY_INTERACT;
            case TALK_NPC -> TALK_NPC;
            case DIALOGUE_CHOICE -> DIALOGUE_CHOICE;
            case LOCATION -> LOCATION;
            case QUEST_COMPLETE -> QUEST_COMPLETE;
            case DELIVER_ITEM, ESCORT, DEFEND, CUSTOM -> null;
        };
    }
}
