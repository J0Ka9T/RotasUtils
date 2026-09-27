package net.schwarz.rotasutils.sky;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EldritchSkyTransitionTest {
    @Test
    void offSnapshotStaysZero() {
        assertEquals(0f, EldritchSkyTransition.Snapshot.off().opennessAt(1000, 0f));
    }

    @Test
    void openingReachesOneAtOpeningTicks() {
        EldritchSkyTransition.Snapshot open = EldritchSkyTransition.toggle(
                EldritchSkyTransition.Snapshot.off(), 100L, 42L);
        assertEquals(EldritchSkyTransition.State.OPENING, open.state);
        assertEquals(0f, open.opennessAt(100L, 0f), 1e-4);
        assertEquals(1f, open.opennessAt(100L + EldritchSkyTransition.OPENING_TICKS, 0f), 1e-4);
        assertEquals(EldritchSkyTransition.State.ACTIVE,
                open.settle(100L + EldritchSkyTransition.OPENING_TICKS).state);
    }

    @Test
    void closingReachesZero() {
        EldritchSkyTransition.Snapshot active = new EldritchSkyTransition.Snapshot(
                EldritchSkyTransition.State.ACTIVE, 0L, 1f, 5L);
        EldritchSkyTransition.Snapshot closing = EldritchSkyTransition.toggle(active, 500L, 1L);
        assertEquals(EldritchSkyTransition.State.CLOSING, closing.state);
        assertEquals(0f, closing.opennessAt(500L + EldritchSkyTransition.CLOSING_TICKS, 0f), 1e-4);
        assertEquals(EldritchSkyTransition.State.OFF,
                closing.settle(500L + EldritchSkyTransition.CLOSING_TICKS).state);
    }

    @Test
    void midOpeningReversalPreservesOpenness() {
        EldritchSkyTransition.Snapshot opening = EldritchSkyTransition.toggle(
                EldritchSkyTransition.Snapshot.off(), 0L, 99L);
        long mid = EldritchSkyTransition.OPENING_TICKS / 2;
        float midOpenness = opening.opennessAt(mid, 0f);
        EldritchSkyTransition.Snapshot reversed = EldritchSkyTransition.toggle(opening, mid, 0L);
        assertEquals(EldritchSkyTransition.State.CLOSING, reversed.state);
        assertEquals(midOpenness, reversed.opennessAt(mid, 0f), 1e-4);
        // Immediately reversing again preserves openness too.
        EldritchSkyTransition.Snapshot reversedAgain = EldritchSkyTransition.toggle(reversed, mid, 0L);
        assertEquals(EldritchSkyTransition.State.OPENING, reversedAgain.state);
        assertEquals(midOpenness, reversedAgain.opennessAt(mid, 0f), 1e-4);
        assertEquals(opening.seed, reversedAgain.seed);
    }

    @Test
    void roundTripsThroughNbt() {
        EldritchSkyTransition.Snapshot snap = new EldritchSkyTransition.Snapshot(
                EldritchSkyTransition.State.OPENING, 123L, 0.42f, 987654321L);
        CompoundTag tag = snap.save();
        EldritchSkyTransition.Snapshot loaded = EldritchSkyTransition.Snapshot.load(tag);
        assertEquals(snap.state, loaded.state);
        assertEquals(snap.referenceTick, loaded.referenceTick);
        assertEquals(snap.opennessAtReference, loaded.opennessAtReference, 1e-6);
        assertEquals(snap.seed, loaded.seed);
    }

    @Test
    void reopeningFromOffPicksFreshSeed() {
        EldritchSkyTransition.Snapshot open1 = EldritchSkyTransition.toggle(
                EldritchSkyTransition.Snapshot.off(), 0L, 111L);
        assertEquals(111L, open1.seed);
        EldritchSkyTransition.Snapshot closing = EldritchSkyTransition.toggle(
                open1.settle(EldritchSkyTransition.OPENING_TICKS), EldritchSkyTransition.OPENING_TICKS, 0L);
        EldritchSkyTransition.Snapshot off = closing.settle(
                EldritchSkyTransition.OPENING_TICKS + EldritchSkyTransition.CLOSING_TICKS);
        assertEquals(EldritchSkyTransition.State.OFF, off.state);
        EldritchSkyTransition.Snapshot open2 = EldritchSkyTransition.toggle(off, 10_000L, 222L);
        assertEquals(222L, open2.seed);
        assertNotEquals(open1.seed, open2.seed);
    }
}
