package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.compat.CuriosServerCompat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuriosUnequipGateTest {
    @Test void removalIsAllowedOnlyWhenEveryCuriosRuleAgrees() {
        assertTrue(CuriosServerCompat.allowsUnequip(false, true, false));
    }

    @Test void bindingCurseBlocksRemoval() {
        assertFalse(CuriosServerCompat.allowsUnequip(true, true, false));
    }

    @Test void aCurioThatRefusesUnequipBlocksRemoval() {
        assertFalse(CuriosServerCompat.allowsUnequip(false, false, false));
    }

    @Test void aDeniedUnequipEventBlocksRemoval() {
        assertFalse(CuriosServerCompat.allowsUnequip(false, true, true));
    }

    @Test void everyRuleIsNecessary() {
        assertFalse(CuriosServerCompat.allowsUnequip(true, false, true));
        assertFalse(CuriosServerCompat.allowsUnequip(true, false, false));
        assertFalse(CuriosServerCompat.allowsUnequip(true, true, true));
        assertFalse(CuriosServerCompat.allowsUnequip(false, false, true));
    }
}
