package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.compat.EasyNpcCompat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyNpcCompatTest {
    @Test void everyNamespaceEasyNpcHasShippedUnderIsRecognised() {
        assertTrue(EasyNpcCompat.isEasyNpcNamespace("easy_npc"));
        assertTrue(EasyNpcCompat.isEasyNpcNamespace("easynpc"));
        assertTrue(EasyNpcCompat.isEasyNpcNamespace("easy_npc_bundle"));
    }

    @Test void unrelatedAndMissingNamespacesAreNotEasyNpcs() {
        assertFalse(EasyNpcCompat.isEasyNpcNamespace("minecraft"));
        assertFalse(EasyNpcCompat.isEasyNpcNamespace("rotasutils"));
        assertFalse(EasyNpcCompat.isEasyNpcNamespace(""));
        assertFalse(EasyNpcCompat.isEasyNpcNamespace(null));
    }
}
