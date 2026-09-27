package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.stat.CharacterStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a card does, written the way an administrator types it in {@code season.json}.
 *
 * <p>One line is {@code "<attribute> <amount> [add|percent]"}, for example
 * {@code "minecraft:generic.attack_damage 0.08 percent"} or {@code "minecraft:generic.armor 3"}. A card
 * only ever moves a real Minecraft attribute, so its bonus is applied by the same equipment pass that
 * already handles set bonuses and needs no state of its own.</p>
 *
 * <p>Everything here is pure text handling, so the format can be checked without a world.</p>
 */
public final class CardEffects {
    /** A card carries at most this many lines, so one card cannot become a whole build. */
    public static final int MAX_LINES = 4;
    /** Attributes a card is allowed to move. Anything else is rejected when the card is read. */
    private static final List<String> ALLOWED = List.of(
            "minecraft:generic.attack_damage",
            "minecraft:generic.attack_speed",
            "minecraft:generic.max_health",
            "minecraft:generic.armor",
            "minecraft:generic.armor_toughness",
            "minecraft:generic.movement_speed",
            "minecraft:generic.knockback_resistance",
            "minecraft:generic.luck");

    private CardEffects() {
    }

    /** True when a card may move this attribute. */
    public static boolean allowed(String attribute) {
        return attribute != null && ALLOWED.contains(attribute.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> allowedAttributes() {
        return ALLOWED;
    }

    /**
     * Reads one line. Returns null when the line is malformed or names an attribute a card may not
     * touch, so a mistyped card is skipped instead of breaking every other card on the item.
     */
    public static CharacterStat.Effect parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }
        String attribute = parts[0].toLowerCase(Locale.ROOT);
        if (!allowed(attribute)) {
            return null;
        }
        double amount;
        try {
            amount = Double.parseDouble(parts[1]);
        } catch (NumberFormatException malformed) {
            return null;
        }
        if (!Double.isFinite(amount) || amount == 0 || Math.abs(amount) > 1000) {
            return null;
        }
        boolean percent = parts.length == 3 && parts[2].equalsIgnoreCase("percent");
        if (parts.length == 3 && !percent && !parts[2].equalsIgnoreCase("add")) {
            return null;
        }
        return new CharacterStat.Effect(attribute, amount,
                percent ? CharacterStat.Operation.MULTIPLY_BASE : CharacterStat.Operation.ADD,
                percent, label(attribute), 0);
    }

    /** Every readable line of a card, in order, never more than {@link #MAX_LINES}. */
    public static List<CharacterStat.Effect> parseAll(String[] lines) {
        List<CharacterStat.Effect> effects = new ArrayList<>();
        if (lines == null) {
            return effects;
        }
        for (String line : lines) {
            if (effects.size() >= MAX_LINES) {
                break;
            }
            CharacterStat.Effect effect = parse(line);
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    /** The translation key of an attribute's player-facing name. */
    public static String label(String attribute) {
        return "rotasutils.card.attribute." + attribute.replace(':', '.').replace("minecraft.generic.", "");
    }

    /** {@code "+8% Attack Damage"}, built from a parsed line, for a tooltip. */
    public static String describe(CharacterStat.Effect effect, java.util.function.UnaryOperator<String> translate) {
        double shown = effect.percent() ? effect.perPoint() * 100 : effect.perPoint();
        String number = shown == Math.rint(shown)
                ? String.valueOf((long) shown)
                : String.format(Locale.ROOT, "%.2f", shown).replaceAll("0+$", "").replaceAll("\\.$", "");
        return (shown >= 0 ? "+" : "") + number + (effect.percent() ? "%" : "") + " "
                + translate.apply(effect.label());
    }
}
