package net.schwarz.rotasutils.quest.requirement;

import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.List;

public enum RequirementType {
    MIN_LEVEL("Minimum Level", List.of(
            new ParamSpec("level", ParamKind.INT, "Level", "1"))),
    MAX_LEVEL("Maximum Level", List.of(
            new ParamSpec("level", ParamKind.INT, "Level", "100"))),
    RANK_CLEARANCE("Rank Clearance", List.of(
            new ParamSpec("rank", ParamKind.RANK, "Required Rank", "F"))),
    QUEST_COMPLETED("Quest Completed", List.of(
            new ParamSpec("quest", ParamKind.QUEST, "Quest", ""),
            new ParamSpec("times", ParamKind.INT, "Times", "1"))),
    RANK_QUESTS_COMPLETED("Quests Of Rank Completed", List.of(
            new ParamSpec("rank", ParamKind.RANK, "Rank", "C"),
            new ParamSpec("times", ParamKind.INT, "Amount", "5"))),
    SKILL_UNLOCKED("Skill Unlocked", List.of(
            new ParamSpec("skill", ParamKind.SKILL, "Skill", ""))),
    SKILL_RANK("Skill Rank", List.of(
            new ParamSpec("skill", ParamKind.SKILL, "Skill", ""),
            new ParamSpec("rank", ParamKind.INT, "Minimum Rank", "1"))),
    POINTS_SPENT("Total Points Spent", List.of(
            new ParamSpec("points", ParamKind.INT, "Points", "5"))),
    CATEGORY_POINTS_SPENT("Points Spent In Category", List.of(
            new ParamSpec("category", ParamKind.CATEGORY, "Category", ""),
            new ParamSpec("points", ParamKind.INT, "Points", "5"))),
    CONNECTED_SKILLS("Connected Skills Unlocked", List.of(
            new ParamSpec("amount", ParamKind.INT, "Amount", "1"))),
    HAS_ITEM("Has Item", List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:paper"),
            new ParamSpec("amount", ParamKind.INT, "Amount", "1"),
            new ParamSpec("consume", ParamKind.BOOL, "Consume As Entry Cost", "false"))),
    FACTION("Faction Membership", List.of(
            new ParamSpec("faction", ParamKind.STRING, "Faction", ""))),
    REPUTATION("Faction Reputation", List.of(
            new ParamSpec("faction", ParamKind.STRING, "Faction", ""),
            new ParamSpec("amount", ParamKind.INT, "Minimum Reputation", "0"))),
    PERMISSION("Permission", List.of(
            new ParamSpec("permission", ParamKind.STRING, "Permission Node", ""),
            new ParamSpec("op_level", ParamKind.INT, "Fallback Op Level", "2"))),
    ADVANCEMENT("Advancement", List.of(
            new ParamSpec("advancement", ParamKind.STRING, "Advancement Id", ""))),
    SCOREBOARD("Scoreboard Value", List.of(
            new ParamSpec("objective", ParamKind.STRING, "Objective", ""),
            new ParamSpec("amount", ParamKind.INT, "Minimum Value", "0"))),
    DIMENSION("In Dimension", List.of(
            new ParamSpec("dimension", ParamKind.DIMENSION, "Dimension", "minecraft:overworld"))),
    MOD_LOADED("Mod Installed", List.of(
            new ParamSpec("mod", ParamKind.STRING, "Mod Id", ""))),
    PRESTIGE("Prestige Level", List.of(
            new ParamSpec("amount", ParamKind.INT, "Prestige", "1")));

    public static final RequirementType[] VALUES = values();

    private final String display;
    private final List<ParamSpec> specs;

    RequirementType(String display, List<ParamSpec> specs) {
        this.display = display;
        this.specs = specs;
    }

    public String display() {
        return net.schwarz.rotasutils.util.ThaiText.label("requirement_type", this, display);
    }

    public List<ParamSpec> specs() {
        return specs;
    }
}
