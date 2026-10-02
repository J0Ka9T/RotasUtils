package net.schwarz.rotasutils.network;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SyncQueueTest {
    private static CompoundTag tag(int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", value);
        return tag;
    }

    @Test void anIdenticalSnapshotIsNotSentAgainUntilThePlayerIsForgotten() {
        UUID player = UUID.randomUUID();
        assertTrue(SyncQueue.changed(false, player, tag(1)), "first snapshot always goes out");
        assertFalse(SyncQueue.changed(false, player, tag(1)), "nothing changed, nothing sent");
        assertTrue(SyncQueue.changed(false, player, tag(2)), "a real change goes out");
        assertTrue(SyncQueue.changed(true, player, tag(2)), "content and kernel snapshots are tracked apart");
        SyncQueue.forget(player);
        assertTrue(SyncQueue.changed(false, player, tag(2)), "a fresh login gets everything again");
    }
}
