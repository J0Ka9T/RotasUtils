package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.stat.CharacterStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CardEffects {
    public static final int MAX_LINES = 4;
    private static final List<String> ALLOWED = List.of(
            "minecraft:generic.attack_damage",
            "minecraft:generic.attack_speed",
            "minecraft:generic.max_health",
            "minecraft:generic.armor",
            "minecraft:generic.armor_toughness",
            "minecraft:generic.movement_speed",
            "minecraft:generic.knockback_resistance",
            "minecraft:generic.luck",
            "rotas:armor_pen",
            "rotas:cooldown_reduction",
            "rotas:drop_rate",
            "rotas:life_steal",
            "rotas:damage_reduction",
            "rotas:stamina_regen");

    private CardEffects() {
    }

    public static boolean allowed(String attribute) {
        return attribute != null && ALLOWED.contains(attribute.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> allowedAttributes() {
        return ALLOWED;
    }

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

    public static String label(String attribute) {
        return "rotasutils.card.attribute." + attribute.replace(':', '.').replace("minecraft.generic.", "");
    }

    public static String describe(CharacterStat.Effect effect, java.util.function.UnaryOperator<String> translate) {
        double shown = effect.percent() ? effect.perPoint() * 100 : effect.perPoint();
        String number = shown == Math.rint(shown)
                ? String.valueOf((long) shown)
                : String.format(Locale.ROOT, "%.2f", shown).replaceAll("0+$", "").replaceAll("\\.$", "");
        return (shown >= 0 ? "+" : "") + number + (effect.percent() ? "%" : "") + " "
                + translate.apply(effect.label());
    }
}
