package net.schwarz.rotasutils.skill;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.List;

public enum EffectType {
    MAX_HEALTH("Maximum Health", Attributes.MAX_HEALTH),
    ARMOR("Armor", Attributes.ARMOR),
    ARMOR_TOUGHNESS("Armor Toughness", Attributes.ARMOR_TOUGHNESS),
    ATTACK_DAMAGE("Attack Damage", Attributes.ATTACK_DAMAGE),
    ATTACK_SPEED("Attack Speed", Attributes.ATTACK_SPEED),
    MOVEMENT_SPEED("Movement Speed", Attributes.MOVEMENT_SPEED),
    KNOCKBACK_RESISTANCE("Knockback Resistance", Attributes.KNOCKBACK_RESISTANCE),
    LUCK("Luck", Attributes.LUCK),

    CRIT_CHANCE("Critical Chance", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "1"))),
    CRIT_DAMAGE("Critical Damage", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "5"))),
    DEFENSE_RATING("Defense", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Value Per Rank", "1"))),
    DODGE_CHANCE("Dodge Chance", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "1"))),
    MAGIC_POWER_BONUS("Magic Power", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "2"))),
    HEALTH_REGEN("Health Regeneration", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "0.1"))),
    ARMOR_PEN("Armor Penetration", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "1"))),
    COOLDOWN_REDUCTION("Cooldown Reduction", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "1"))),
    DROP_RATE("Drop Rate", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "1"))),
    LIFE_STEAL("Life Steal", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "0.5"))),
    DAMAGE_REDUCTION("Damage Reduction", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "1"))),
    STAMINA_REGEN("Stamina Regeneration", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "2"))),

    MINING_SPEED("Mining Speed", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "5"))),
    ROTAS_XP_MULTIPLIER("RotasUtils Experience Multiplier", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "5"))),
    QUEST_XP_MULTIPLIER("Quest Experience Multiplier", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "5"))),
    CURRENCY_MULTIPLIER("Currency Multiplier", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "5"))),
    HEALING_MULTIPLIER("Healing Multiplier", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "5"))),
    DAMAGE_TYPE_MULTIPLIER("Damage Type Multiplier", null, List.of(
            new ParamSpec("damage_type", ParamKind.STRING, "Damage Type", ""),
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "5"))),
    QUEST_COOLDOWN_REDUCTION("Repeat Cooldown Reduction", null, List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Percent Per Rank", "10"))),

    POTION_EFFECT("Permanent Potion Effect", null, List.of(
            new ParamSpec("effect", ParamKind.EFFECT, "Effect", "minecraft:speed"),
            new ParamSpec("amplifier", ParamKind.INT, "Amplifier Per Rank", "0"))),
    CUSTOM_ATTRIBUTE("Custom Attribute", null, List.of(
            new ParamSpec("attribute", ParamKind.ATTRIBUTE, "Attribute", ""),
            new ParamSpec("value", ParamKind.DOUBLE, "Value Per Rank", "1"))),

    UNLOCK_RANK("Unlock Rank Clearance", null, List.of(
            new ParamSpec("rank", ParamKind.RANK, "Rank", "E"))),
    UNLOCK_CATEGORY("Unlock Skill Category", null, List.of(
            new ParamSpec("category", ParamKind.CATEGORY, "Category", ""))),
    UNLOCK_QUEST("Unlock Quest", null, List.of(
            new ParamSpec("quest", ParamKind.QUEST, "Quest", ""))),
    UNLOCK_QUEST_CATEGORY("Unlock Quest Category", null, List.of(
            new ParamSpec("category", ParamKind.STRING, "Quest Category", ""))),
    REVEAL_HIDDEN_QUESTS("Reveal Hidden Quests", null, List.of()),
    REVEAL_HIDDEN_OBJECTIVES("Reveal Hidden Objectives", null, List.of()),
    UNLOCK_RECIPE("Unlock Recipe", null, List.of(
            new ParamSpec("recipe", ParamKind.STRING, "Recipe Id", ""))),
    PERMISSION("Grant Permission Tag", null, List.of(
            new ParamSpec("permission", ParamKind.STRING, "Permission", ""))),
    ITEM_REWARD("Grant Item Once", null, List.of(
            new ParamSpec("item", ParamKind.ITEM, "Item", "minecraft:bread"),
            new ParamSpec("amount", ParamKind.INT, "Amount", "1"))),
    COMMAND("Run Command On Unlock", null, List.of(
            new ParamSpec("command", ParamKind.COMMAND, "Command", ""))),
    CUSTOM_API("Custom API Effect", null, List.of(
            new ParamSpec("key", ParamKind.STRING, "Effect Key", ""),
            new ParamSpec("value", ParamKind.DOUBLE, "Value Per Rank", "1")));

    public static final EffectType[] VALUES = values();

    private static final List<ParamSpec> ATTRIBUTE_SPECS = List.of(
            new ParamSpec("value", ParamKind.DOUBLE, "Value Per Rank", "1"),
            new ParamSpec("per_level", ParamKind.DOUBLE, "Extra Per Level Per Rank", "0").asAdvanced(),
            new ParamSpec("percent", ParamKind.BOOL, "Percentage Instead Of Flat", "false"),
            new ParamSpec("max", ParamKind.DOUBLE, "Maximum Total (0 = none)", "0").asAdvanced());

    private final String display;
    private final Attribute attribute;
    private final List<ParamSpec> specs;

    EffectType(String display, Attribute attribute) {
        this(display, attribute, null);
    }

    EffectType(String display, Attribute attribute, List<ParamSpec> specs) {
        this.display = display;
        this.attribute = attribute;
        this.specs = specs;
    }

    public String display() {
        return net.schwarz.rotasutils.util.ThaiText.label("effect_type", this, display);
    }

    public Attribute attribute() {
        return attribute;
    }

    public List<ParamSpec> specs() {
        return specs == null ? ATTRIBUTE_SPECS : specs;
    }

    public String combatStat() {
        return switch (this) {
            case CRIT_CHANCE -> net.schwarz.rotasutils.server.CombatStats.CRIT_CHANCE;
            case CRIT_DAMAGE -> net.schwarz.rotasutils.server.CombatStats.CRIT_DAMAGE;
            case DEFENSE_RATING -> net.schwarz.rotasutils.server.CombatStats.DEFENSE;
            case DODGE_CHANCE -> net.schwarz.rotasutils.server.CombatStats.EVASION;
            case MAGIC_POWER_BONUS -> net.schwarz.rotasutils.server.CombatStats.MAGIC_POWER;
            case HEALTH_REGEN -> net.schwarz.rotasutils.server.CombatStats.REGEN;
            case ARMOR_PEN -> net.schwarz.rotasutils.server.CombatStats.ARMOR_PEN;
            case COOLDOWN_REDUCTION -> net.schwarz.rotasutils.server.CombatStats.COOLDOWN_REDUCTION;
            case DROP_RATE -> net.schwarz.rotasutils.server.CombatStats.DROP_RATE;
            case LIFE_STEAL -> net.schwarz.rotasutils.server.CombatStats.LIFE_STEAL;
            case DAMAGE_REDUCTION -> net.schwarz.rotasutils.server.CombatStats.DAMAGE_REDUCTION;
            case STAMINA_REGEN -> net.schwarz.rotasutils.server.CombatStats.STAMINA_REGEN;
            default -> null;
        };
    }

    public boolean isMultiplier() {
        return switch (this) {
            case MINING_SPEED, ROTAS_XP_MULTIPLIER, QUEST_XP_MULTIPLIER, CURRENCY_MULTIPLIER,
                 HEALING_MULTIPLIER, DAMAGE_TYPE_MULTIPLIER, QUEST_COOLDOWN_REDUCTION,
                 CRIT_CHANCE, CRIT_DAMAGE, DODGE_CHANCE, MAGIC_POWER_BONUS, HEALTH_REGEN,
                 ARMOR_PEN, COOLDOWN_REDUCTION, DROP_RATE, LIFE_STEAL, DAMAGE_REDUCTION, STAMINA_REGEN -> true;
            default -> false;
        };
    }
}
