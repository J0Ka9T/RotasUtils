package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MonsterProgressionTest {
    @Test void commonMinecraftMonstersReceiveUsefulTypes() {
        assertEquals(MonsterType.UNDEAD, MonsterTypes.infer("minecraft:skeleton"));
        assertEquals(MonsterType.ARTHROPOD, MonsterTypes.infer("minecraft:spider"));
        assertEquals(MonsterType.DRAGON, MonsterTypes.infer("minecraft:ender_dragon"));
        assertEquals(MonsterType.UNKNOWN, MonsterTypes.infer("example:unclassified"));
    }

    @Test void curvesRemainFiniteAndBoundedAtLevel999() {
        assertEquals(1, MonsterScalingService.factor(MonsterScalingService.Curve.LINEAR, 1, .05, 8), .0001);
        assertEquals(5.95, MonsterScalingService.factor(MonsterScalingService.Curve.LINEAR, 100, .05, 8), .0001);
        double high = MonsterScalingService.factor(MonsterScalingService.Curve.SOFT_EXPONENTIAL, 999, .05, 8);
        assertTrue(Double.isFinite(high));
        assertTrue(high <= 8);
    }

    @Test void scalingOrderCombinesCurveRankAndCustomLayers() {
        Map<String, MonsterDefinitions.DerivedScale> calculated = MonsterScalingService.calculate(50,
                MonsterScalingService.Curve.LINEAR, .02, 10, 1.5,
                Map.of("minecraft:generic.max_health", new MonsterDefinitions.Scale(2, 0, 4)), Map.of());
        var health = calculated.get("minecraft:generic.max_health");
        assertEquals(5.94, health.multiplier(), .0001);
        assertEquals(4, health.add(), .0001);
        assertTrue(calculated.keySet().containsAll(MonsterScalingService.SCALABLE_ATTRIBUTES));
    }
}
