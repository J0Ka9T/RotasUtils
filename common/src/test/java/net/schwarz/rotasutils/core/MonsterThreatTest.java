package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.server.MonsterThreat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterThreatTest {
    @Test void strongerMonstersNeverPayLess() {
        long normal = MonsterThreat.score(20, 4, 0, 0, 1, 0, false, 5000);
        long armored = MonsterThreat.score(80, 12, 15, 4, 20, 2, false, 5000);
        long boss = MonsterThreat.score(80, 12, 15, 4, 20, 2, true, 5000);
        assertTrue(armored > normal);
        assertTrue(boss > armored);
    }

    @Test void extremeInputsStayBoundedAndUuidVarianceIsStable() {
        assertEquals(5000, MonsterThreat.score(Double.MAX_VALUE, Double.MAX_VALUE,
                Double.MAX_VALUE, Double.MAX_VALUE, 10000, 8, true, 5000));
        java.util.UUID id = java.util.UUID.fromString("12345678-1234-5678-9abc-def012345678");
        assertEquals(MonsterThreat.levelInBand(id, 20, 40), MonsterThreat.levelInBand(id, 20, 40));
        assertTrue(MonsterThreat.levelInBand(id, 20, 40) >= 20);
        assertTrue(MonsterThreat.levelInBand(id, 20, 40) <= 40);
    }
}
