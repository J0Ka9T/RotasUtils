package net.schwarz.rotasutils.level;


/** Non-quest experience sources, each independently configurable in the level manager. */
public enum XpSource {
    QUEST_COMPLETE("Quest Completion", 0, false, false),
    OPTIONAL_OBJECTIVE("Optional Objective", 0, false, false),
    FIRST_COMPLETION("First Quest Completion", 0, false, false),
    /** Combat XP is calculated from the killed entity's live combat attributes. */
    MOB_KILL("Monster Kills", 0, true, true),
    BOSS_KILL("Boss Kills", 0, true, true),
    PLAYER_KILL("Player Kills", 0, true, false),
    ASSIST("Assists", 0, true, false),
    DUNGEON_COMPLETE("Dungeon Completion", 0, false, false),
    DISCOVERY("Location Discovery", 0, false, false),
    ADVANCEMENT("Advancements", 0, false, false),
    MINING("Mining", 0, true, false),
    CRAFTING("Crafting", 0, true, false),
    SMELTING("Smelting", 0, true, false),
    FISHING("Fishing", 0, true, false),
    FARMING("Farming", 0, true, false),
    TRADING("Trading", 0, true, false),
    HEALING("Healing", 0, true, false),
    SERVER_EVENT("Server Events", 0, false, false),
    CUSTOM_API("Custom API Events", 0, false, false);

    public static final XpSource[] VALUES = values();

    private final String display;
    private final int defaultAmount;
    private final boolean antiFarmByDefault;
    private final boolean enabledByDefault;

    XpSource(String display, int defaultAmount, boolean antiFarmByDefault, boolean enabledByDefault) {
        this.display = display;
        this.defaultAmount = defaultAmount;
        this.antiFarmByDefault = antiFarmByDefault;
        this.enabledByDefault = enabledByDefault;
    }

    public String display() {
        return net.schwarz.rotasutils.util.ThaiText.label("xp_source", this, display);
    }

    public int defaultAmount() {
        return defaultAmount;
    }

    public boolean antiFarmByDefault() {
        return antiFarmByDefault;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }
}
