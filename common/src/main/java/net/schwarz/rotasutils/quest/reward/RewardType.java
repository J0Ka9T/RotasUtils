package net.schwarz.rotasutils.quest.reward;

import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.List;

/** Everything a quest, level-up or skill node can hand out. */
public enum RewardType {
    ROTAS_XP("RotasUtils Experience", List.of(
            new ParamSpec("amount", ParamKind.INT, "Amount", "100"),
            new ParamSpec("scale_with_rank", ParamKind.BOOL, "Scale With Danger Rank", "true"),
            new ParamSpec("scale_with_level", ParamKind.BOOL, "Scale With Player Level", "false"))),
    SKILL_POINT("Skill Point", List.of(
            new ParamSpec("amount", ParamKind.INT, "Amount", "1"))),
    CATEGORY_POINT("Pufferfish Category Point", List.of(
            new ParamSpec("category", ParamKind.STRING, "Pufferfish Category ID", ""),
            new ParamSpec("amount", ParamKind.INT, "Amount", "1"))),
    ITEM("Item", List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:diamond"),
            new ParamSpec("amount", ParamKind.INT, "Amount", "1"))),
    CURRENCY("Currency", List.of(
            new ParamSpec("objective", ParamKind.STRING, "Scoreboard Objective", "coins"),
            new ParamSpec("amount", ParamKind.INT, "Amount", "100"))),
    VANILLA_XP("Legacy XP (converted to Rotas XP)", List.of(
            new ParamSpec("amount", ParamKind.INT, "Amount", "50"))),
    SCOREBOARD("Scoreboard Value", List.of(
            new ParamSpec("objective", ParamKind.STRING, "Objective", ""),
            new ParamSpec("amount", ParamKind.INT, "Add Amount", "1"))),
    RANK_CLEARANCE("Rank Clearance", List.of(
            new ParamSpec("rank", ParamKind.RANK, "Rank", "E"))),
    UNLOCK_QUEST("Unlock Quest", List.of(
            new ParamSpec("quest", ParamKind.QUEST, "Quest", ""))),
    UNLOCK_SKILL("Unlock Pufferfish Skill", List.of(
            new ParamSpec("category", ParamKind.STRING, "Pufferfish Category ID", ""),
            new ParamSpec("skill", ParamKind.STRING, "Skill ID", ""))),
    UNLOCK_CATEGORY("Unlock Pufferfish Category", List.of(
            new ParamSpec("category", ParamKind.STRING, "Pufferfish Category ID", ""))),
    POTION_EFFECT("Potion Effect", List.of(
            new ParamSpec("effect", ParamKind.EFFECT, "Effect", "minecraft:regeneration"),
            new ParamSpec("duration", ParamKind.INT, "Duration (s)", "30"),
            new ParamSpec("amplifier", ParamKind.INT, "Amplifier", "0"))),
    TELEPORT("Teleport", List.of(
            new ParamSpec("pos", ParamKind.POS, "Destination", ""),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Dimension", ""))),
    REPUTATION("Faction Reputation", List.of(
            new ParamSpec("faction", ParamKind.STRING, "Faction", ""),
            new ParamSpec("amount", ParamKind.INT, "Amount", "10"))),
    TITLE("Title", List.of(
            new ParamSpec("title", ParamKind.STRING, "Title", ""))),
    PRESTIGE("Prestige", List.of(
            new ParamSpec("amount", ParamKind.INT, "Amount", "1"))),
    COMMAND("Run Command", List.of(
            new ParamSpec("command", ParamKind.COMMAND, "Command (@p = player)", "say hello"),
            new ParamSpec("as_server", ParamKind.BOOL, "Run As Server", "true"))),
    QUEST_VARIABLE("Quest Variable", List.of(
            new ParamSpec("key", ParamKind.STRING, "Key", ""),
            new ParamSpec("value", ParamKind.STRING, "Value", "")));

    public static final RewardType[] VALUES = values();

    private final String display;
    private final List<ParamSpec> specs;

    RewardType(String display, List<ParamSpec> specs) {
        this.display = display;
        this.specs = specs;
    }

    public String display() {
        return net.schwarz.rotasutils.util.ThaiText.label("reward_type", this, display);
    }

    public List<ParamSpec> specs() {
        return specs;
    }
}
