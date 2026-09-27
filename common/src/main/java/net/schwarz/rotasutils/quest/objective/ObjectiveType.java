package net.schwarz.rotasutils.quest.objective;

import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.List;

/**
 * Every supported objective kind together with the fields the editor should render.
 *
 * <p>Adding an objective type means adding a constant here and a case in
 * {@link net.schwarz.rotasutils.quest.ObjectiveEngine}; no screen code changes.
 */
public enum ObjectiveType {
    KILL_MOB("Kill Mobs", EventKind.KILL_ENTITY, List.of(
            new ParamSpec("entity", ParamKind.ENTITY, "Mob", "minecraft:zombie"),
            new ParamSpec("entity_tag", ParamKind.ENTITY_TAG, "Or Entity Tag", ""),
            new ParamSpec("amount", ParamKind.INT, "Required Amount", "10"),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Required Dimension", ""),
            new ParamSpec("biome", ParamKind.STRING, "Required Biome", "").asAdvanced(),
            new ParamSpec("region", ParamKind.STRING, "Required Region", "").asAdvanced(),
            new ParamSpec("weapon", ParamKind.ITEM, "Required Weapon", "").asAdvanced(),
            new ParamSpec("damage_type", ParamKind.STRING, "Required Damage Type", "").asAdvanced(),
            new ParamSpec("max_distance", ParamKind.INT, "Max Distance To Point", "0").asAdvanced(),
            new ParamSpec("named", ParamKind.STRING, "Require Mob Name", "").asAdvanced(),
            new ParamSpec("count_projectile", ParamKind.BOOL, "Count Projectile Kills", "true"),
            new ParamSpec("count_pet", ParamKind.BOOL, "Count Pet Kills", "false"),
            new ParamSpec("count_party", ParamKind.BOOL, "Count Party Kills", "true"),
            new ParamSpec("require_boss", ParamKind.BOOL, "Require Boss", "false"),
            new ParamSpec("require_quest_spawned", ParamKind.BOOL, "Require Quest Spawned Mob", "false").asAdvanced(),
            new ParamSpec("show_marker", ParamKind.BOOL, "Show Quest Marker", "true"),
            new ParamSpec("show_boss_bar", ParamKind.BOOL, "Show Boss Health Bar", "false"))),

    KILL_BOSS("Kill Boss", EventKind.KILL_ENTITY, List.of(
            new ParamSpec("entity", ParamKind.ENTITY, "Boss Entity", "minecraft:wither"),
            new ParamSpec("amount", ParamKind.INT, "Required Amount", "1"),
            new ParamSpec("named", ParamKind.STRING, "Require Boss Name", ""),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Required Dimension", ""),
            new ParamSpec("count_party", ParamKind.BOOL, "Count Party Kills", "true"),
            new ParamSpec("show_boss_bar", ParamKind.BOOL, "Show Boss Health Bar", "true"))),

    KILL_PLAYER("Kill Players", EventKind.KILL_PLAYER, List.of(
            new ParamSpec("mode", ParamKind.STRING, "Target Mode", "ANY"),
            new ParamSpec("target", ParamKind.STRING, "Specific Target", ""),
            new ParamSpec("amount", ParamKind.INT, "Required Kills", "1"),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Valid Dimension", ""),
            new ParamSpec("region", ParamKind.STRING, "Valid Region", "").asAdvanced(),
            new ParamSpec("weapon", ParamKind.ITEM, "Required Weapon", "").asAdvanced(),
            new ParamSpec("damage_type", ParamKind.STRING, "Required Damage Type", "").asAdvanced(),
            new ParamSpec("exclude_party", ParamKind.BOOL, "Exclude Party Members", "true"),
            new ParamSpec("exclude_team", ParamKind.BOOL, "Exclude Scoreboard Teammates", "true"),
            new ParamSpec("victim_cooldown", ParamKind.INT, "Same Victim Cooldown (s)", "1800"),
            new ParamSpec("min_victim_playtime", ParamKind.INT, "Min Victim Playtime (min)", "60"),
            new ParamSpec("replace_target", ParamKind.BOOL, "Replace Target If Offline", "true"))),

    DELIVER_ITEM("Give Items", EventKind.DELIVER_ITEM, List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:wheat"),
            new ParamSpec("item_tag", ParamKind.ITEM_TAG, "Or Item Tag", ""),
            new ParamSpec("amount", ParamKind.INT, "Required Quantity", "16"),
            new ParamSpec("target_kind", ParamKind.STRING, "Deliver To", "BOARD"),
            new ParamSpec("target_id", ParamKind.STRING, "Target Id", ""),
            new ParamSpec("target_pos", ParamKind.POS, "Target Position", ""),
            new ParamSpec("match_nbt", ParamKind.BOOL, "Match NBT", "false").asAdvanced(),
            new ParamSpec("match_enchantments", ParamKind.BOOL, "Match Enchantments", "false").asAdvanced(),
            new ParamSpec("match_name", ParamKind.STRING, "Match Custom Name", "").asAdvanced(),
            new ParamSpec("match_model_data", ParamKind.INT, "Match Custom Model Data", "-1").asAdvanced(),
            new ParamSpec("min_durability", ParamKind.INT, "Min Durability %", "0").asAdvanced(),
            new ParamSpec("consume", ParamKind.BOOL, "Consume Item", "true"),
            new ParamSpec("allow_partial", ParamKind.BOOL, "Allow Partial Delivery", "true"),
            new ParamSpec("show_progress", ParamKind.BOOL, "Display Progress", "true"))),

    COLLECT_ITEM("Collect Items", EventKind.COLLECT_ITEM, List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:iron_ingot"),
            new ParamSpec("item_tag", ParamKind.ITEM_TAG, "Or Item Tag", ""),
            new ParamSpec("amount", ParamKind.INT, "Required Quantity", "8"),
            new ParamSpec("consume", ParamKind.BOOL, "Consume On Turn In", "true"),
            new ParamSpec("show_progress", ParamKind.BOOL, "Display Progress", "true"))),

    TALK_NPC("Talk To NPC", EventKind.TALK_NPC, List.of(
            new ParamSpec("npc_uuid", ParamKind.NPC, "NPC (world selected)", ""),
            new ParamSpec("npc_type", ParamKind.ENTITY, "Or NPC Entity Type", ""),
            new ParamSpec("npc_name", ParamKind.STRING, "Or NPC Name", ""),
            new ParamSpec("dialogue", ParamKind.STRING, "Dialogue Id", ""),
            new ParamSpec("require_full_dialogue", ParamKind.BOOL, "Require Full Conversation", "false"),
            new ParamSpec("require_choice", ParamKind.STRING, "Require Dialogue Choice", "").asAdvanced(),
            new ParamSpec("amount", ParamKind.INT, "Times", "1"),
            new ParamSpec("show_marker", ParamKind.BOOL, "Show Quest Marker", "true"))),

    REACH_LOCATION("Reach Location", EventKind.LOCATION, List.of(
            new ParamSpec("pos", ParamKind.POS, "Target Position", ""),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Dimension", ""),
            new ParamSpec("radius", ParamKind.INT, "Radius", "8"),
            new ParamSpec("show_marker", ParamKind.BOOL, "Show Quest Marker", "true"))),

    STAY_IN_REGION("Stay In Region", EventKind.LOCATION, List.of(
            new ParamSpec("pos", ParamKind.POS, "Region Center", ""),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Dimension", ""),
            new ParamSpec("radius", ParamKind.INT, "Radius", "16"),
            new ParamSpec("amount", ParamKind.INT, "Seconds Required", "60"))),

    DEFEND_AREA("Defend Area", EventKind.DEFEND, List.of(
            new ParamSpec("pos", ParamKind.POS, "Area Center", ""),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Dimension", ""),
            new ParamSpec("radius", ParamKind.INT, "Radius", "24"),
            new ParamSpec("amount", ParamKind.INT, "Seconds To Hold", "120"),
            new ParamSpec("fail_on_leave", ParamKind.BOOL, "Fail If Player Leaves", "true"))),

    ESCORT_NPC("Escort NPC", EventKind.ESCORT, List.of(
            new ParamSpec("npc_uuid", ParamKind.NPC, "NPC (world selected)", ""),
            new ParamSpec("pos", ParamKind.POS, "Destination", ""),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Dimension", ""),
            new ParamSpec("radius", ParamKind.INT, "Arrival Radius", "6"),
            new ParamSpec("fail_on_death", ParamKind.BOOL, "Fail If NPC Dies", "true"))),

    BREAK_BLOCK("Break Blocks", EventKind.BLOCK_BREAK, List.of(
            new ParamSpec("block", ParamKind.BLOCK, "Block", "minecraft:stone"),
            new ParamSpec("amount", ParamKind.INT, "Required Amount", "32"),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Required Dimension", ""),
            new ParamSpec("tool", ParamKind.ITEM, "Required Tool", "").asAdvanced())),

    PLACE_BLOCK("Place Blocks", EventKind.BLOCK_PLACE, List.of(
            new ParamSpec("block", ParamKind.BLOCK, "Block", "minecraft:oak_planks"),
            new ParamSpec("amount", ParamKind.INT, "Required Amount", "16"),
            new ParamSpec("dimension", ParamKind.DIMENSION, "Required Dimension", ""))),

    INTERACT_BLOCK("Interact With Block", EventKind.BLOCK_INTERACT, List.of(
            new ParamSpec("block", ParamKind.BLOCK, "Block", "minecraft:lever"),
            new ParamSpec("pos", ParamKind.POS, "Specific Position", ""),
            new ParamSpec("amount", ParamKind.INT, "Times", "1"))),

    INTERACT_ENTITY("Interact With Entity", EventKind.ENTITY_INTERACT, List.of(
            new ParamSpec("entity", ParamKind.ENTITY, "Entity", "minecraft:cow"),
            new ParamSpec("amount", ParamKind.INT, "Times", "1"))),

    CRAFT_ITEM("Craft Items", EventKind.CRAFT_ITEM, List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:iron_sword"),
            new ParamSpec("amount", ParamKind.INT, "Required Amount", "1"))),

    SMELT_ITEM("Smelt Items", EventKind.SMELT_ITEM, List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:iron_ingot"),
            new ParamSpec("amount", ParamKind.INT, "Required Amount", "8"))),

    USE_ITEM("Use Items", EventKind.USE_ITEM, List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:ender_pearl"),
            new ParamSpec("amount", ParamKind.INT, "Required Amount", "1"))),

    DIALOGUE_CHOICE("Complete Dialogue Choice", EventKind.DIALOGUE_CHOICE, List.of(
            new ParamSpec("dialogue", ParamKind.STRING, "Dialogue Id", ""),
            new ParamSpec("choice", ParamKind.STRING, "Choice Id", ""))),

    COMPLETE_QUEST("Complete Another Quest", EventKind.QUEST_COMPLETE, List.of(
            new ParamSpec("quest", ParamKind.QUEST, "Quest", ""),
            new ParamSpec("amount", ParamKind.INT, "Times", "1"))),

    CUSTOM("Trigger Custom Event", EventKind.CUSTOM, List.of(
            new ParamSpec("event", ParamKind.STRING, "Event Id", "mymod:my_event"),
            new ParamSpec("amount", ParamKind.INT, "Times", "1"))),

    /** Serialized legacy name; {@link Objective#load} migrates it to {@link #CUSTOM}. */
    CUSTOM_EVENT("Trigger Custom Event", EventKind.CUSTOM, List.of(
            new ParamSpec("event", ParamKind.STRING, "Event Id", "mymod:my_event"),
            new ParamSpec("amount", ParamKind.INT, "Times", "1")));

    public static final ObjectiveType[] VALUES = java.util.Arrays.stream(values())
            .filter(type -> type != CUSTOM_EVENT)
            .toArray(ObjectiveType[]::new);

    private final String display;
    private final EventKind eventKind;
    private final List<ParamSpec> specs;

    ObjectiveType(String display, EventKind eventKind, List<ParamSpec> specs) {
        this.display = display;
        this.eventKind = eventKind;
        this.specs = specs;
    }

    public String display() {
        return net.schwarz.rotasutils.util.ThaiText.label("objective_type", this, display);
    }

    public EventKind eventKind() {
        return eventKind;
    }

    public List<ParamSpec> specs() {
        return specs;
    }
}
