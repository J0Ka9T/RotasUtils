package net.schwarz.rotasutils.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonsterStorageTest {
    @Test
    void healthRatioSurvivesVanillaLoadBeforeTransientModifiersReturn() {
        double savedRatio = MonsterStorage.healthRatio(30.0F, 100.0F);
        float primedVanillaHealth = MonsterStorage.healthAtRatio(20.0F, savedRatio);
        float restoredScaledHealth = primedVanillaHealth / 20.0F * 100.0F;

        assertEquals(0.3D, savedRatio, 0.000001D);
        assertEquals(6.0F, primedVanillaHealth, 0.0001F);
        assertEquals(30.0F, restoredScaledHealth, 0.0001F);
    }

    @Test
    void healthRatioRejectsNonFiniteInputsAndClampsCorruptValues() {
        assertEquals(0.0D, MonsterStorage.healthRatio(Float.NaN, 20.0F));
        assertEquals(0.0D, MonsterStorage.healthRatio(10.0F, 0.0F));
        assertEquals(1.0D, MonsterStorage.healthRatio(30.0F, 20.0F));
        assertEquals(0.0F, MonsterStorage.healthAtRatio(20.0F, Double.NaN));
        assertEquals(20.0F, MonsterStorage.healthAtRatio(20.0F, 2.0D));
        assertEquals(0.0F, MonsterStorage.healthAtRatio(Float.POSITIVE_INFINITY, 0.5D));
    }
}
