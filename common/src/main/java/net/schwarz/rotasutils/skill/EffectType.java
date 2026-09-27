package net.schwarz.rotasutils.skill;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.List;

/** Built-in skill effects. Attribute-backed entries carry the vanilla attribute they drive. */
public enum EffectType {
    MAX_HEALTH("Maximum Health", Attributes.MAX_HEALTH),
    ARMOR("Armor", Attributes.ARMOR),
    ARMOR_TOUGHNESS("Armor Toughness", Attributes.ARMOR_TOUGHNESS),
    ATTACK_DAMAGE("Attack Damage", Attributes.ATTACK_DAMAGE),
    ATTACK_SPEED("Attack Speed", Attributes.ATTACK_SPEED),
    MOVEMENT_SPEED("Movement Speed", Attributes.MOVEMENT_SPEED),
    KNOCKBACK_RESISTANCE("Knockback Resistance", Attributes.KNOCKBACK_RESISTANCE),
    LUCK("Luck", Attributes.LUCK),

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
        // Static fields cannot be read from an enum constructor, so the shared
        // attribute spec list is substituted lazily in specs().
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

    /** Non-null when this effect is applied through a vanilla attribute modifier. */
    public Attribute attribute() {
        return attribute;
    }

    public List<ParamSpec> specs() {
        return specs == null ? ATTRIBUTE_SPECS : specs;
    }

    /** Multiplier-style effects are read by gameplay code rather than applied as attributes. */
    public boolean isMultiplier() {
        return switch (this) {
            case MINING_SPEED, ROTAS_XP_MULTIPLIER, QUEST_XP_MULTIPLIER, CURRENCY_MULTIPLIER,
                 HEALING_MULTIPLIER, DAMAGE_TYPE_MULTIPLIER, QUEST_COOLDOWN_REDUCTION -> true;
            default -> false;
        };
    }
}
