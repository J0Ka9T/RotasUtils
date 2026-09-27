package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure ordered monster scaling. Entity mutation remains in the server-side MonsterService. */
public final class MonsterScalingService {
    public enum Curve { LINEAR, SOFT_EXPONENTIAL, CUSTOM_CURVE }

    public static final Set<String> SCALABLE_ATTRIBUTES = Set.of(
            "minecraft:generic.max_health", "minecraft:generic.attack_damage", "minecraft:generic.armor",
            "minecraft:generic.armor_toughness", "minecraft:generic.movement_speed",
            "minecraft:generic.knockback_resistance", "minecraft:generic.follow_range");

    private MonsterScalingService() { }

    public static double factor(Curve curve, int level, double growth, double maximum) {
        int safeLevel = MonsterLevels.configured(level);
        double cap = Double.isFinite(maximum) ? Math.max(1, Math.min(10_000, maximum)) : 1;
        double linear = 1 + Math.max(0, safeLevel - 1) * Math.max(0, growth);
        double value = switch (curve == null ? Curve.LINEAR : curve) {
            case LINEAR -> linear;
            case SOFT_EXPONENTIAL -> 1 + (cap - 1) * (1 - Math.exp(-Math.max(0, growth) * (safeLevel - 1)));
            case CUSTOM_CURVE -> linear;
        };
        return Math.max(0, Math.min(cap, Double.isFinite(value) ? value : cap));
    }

    public static Map<String, MonsterDefinitions.DerivedScale> calculate(int level, Curve curve, double growth,
            double maximum, double rankMultiplier, Map<String, MonsterDefinitions.Scale> custom,
            Map<String, MonsterDefinitions.Scale> affixes) {
        double levelFactor = factor(curve, level, growth, maximum);
        double rank = Double.isFinite(rankMultiplier) ? Math.max(0, Math.min(100, rankMultiplier)) : 1;
        Map<String, MonsterDefinitions.Scale> base = new LinkedHashMap<>();
        SCALABLE_ATTRIBUTES.forEach(attribute -> base.put(attribute, new MonsterDefinitions.Scale(levelFactor * rank, 0, 0)));
        List<Map<String, MonsterDefinitions.Scale>> layers = new ArrayList<>();
        layers.add(base); layers.add(custom == null ? Map.of() : custom); layers.add(affixes == null ? Map.of() : affixes);
        return MonsterDefinitions.derive(1, layers);
    }
}
