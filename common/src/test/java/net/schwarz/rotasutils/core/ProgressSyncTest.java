package net.schwarz.rotasutils.core;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.schwarz.rotasutils.network.ProgressDelta;
import net.schwarz.rotasutils.network.ProgressChunks;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProgressSyncTest {
    private CompoundTag snapshot() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("player", UUID.randomUUID());
        tag.putLong("xp", 0); tag.putInt("level", 1); return tag;
    }

    @Test void fullDeltaDeletionDuplicateAndUnchangedFrames() {
        var sender = new ProgressDelta.Sender(); var receiver = new ProgressDelta.Receiver();
        var state = snapshot(); state.putString("removed_later", "yes");
        var full = sender.next(state, false);
        assertEquals(state, receiver.apply(full));
        assertNull(sender.next(state, false));
        state.putLong("xp", 300); state.remove("removed_later");
        var delta = sender.next(state, false);
        assertFalse(delta.getBoolean("full"));
        assertEquals(1, delta.getCompound("set").size());
        assertEquals(state, receiver.apply(delta));
        assertNull(receiver.apply(delta));
        assertNull(receiver.apply(full));
    }

    @Test void missingBaseRequiresResyncAndReconnectAcceptsNewEpoch() {
        var sender = new ProgressDelta.Sender(); var receiver = new ProgressDelta.Receiver();
        var state = snapshot(); receiver.apply(sender.next(state, false));
        state.putLong("xp", 10); sender.next(state, false);
        state.putLong("xp", 20); var missing = sender.next(state, false);
        assertThrows(IllegalArgumentException.class, () -> receiver.apply(missing));
        sender.reset(); assertEquals(state, receiver.apply(sender.next(state, false)));
        receiver.clear(); var other = new ProgressDelta.Sender();
        assertEquals(state, receiver.apply(other.next(state, false)));
    }

    @Test void deltaPayloadIsSmallComparedWithLargeProfile() {
        var sender = new ProgressDelta.Sender(); var state = snapshot();
        state.putString("quest_history", "x".repeat(20000));
        var full = sender.next(state, false); state.putLong("xp", 42);
        var delta = sender.next(state, false);
        assertTrue(bytes(delta).length < 256);
        assertTrue(bytes(full).length > 20000);
    }

    @Test void malformedDeletionListCannotAdvanceReceiver() {
        var sender = new ProgressDelta.Sender(); var receiver = new ProgressDelta.Receiver();
        var state = snapshot(); receiver.apply(sender.next(state, false));
        state.putLong("xp", 1);
        var valid = sender.next(state, false); var malformed = valid.copy();
        var list = new net.minecraft.nbt.ListTag(); list.add(net.minecraft.nbt.IntTag.valueOf(1));
        malformed.put("removed", list);
        assertThrows(IllegalArgumentException.class, () -> receiver.apply(malformed));
        assertEquals(state, receiver.apply(valid));
    }

    @Test void chunksReassembleAndRejectOversizedOutOfOrderAndExpiredTransfers() {
        byte[] source = new byte[ProgressChunks.CHUNK_BYTES * 2 + 15];
        for (int i = 0; i < source.length; i++) { source[i] = (byte) i; }
        var chunks = new ProgressChunks(); byte[] result = null;
        for (int offset = 0, index = 0; offset < source.length; offset += ProgressChunks.CHUNK_BYTES, index++) {
            result = chunks.accept(4, source.length, index,
                    Arrays.copyOfRange(source, offset, Math.min(source.length, offset + ProgressChunks.CHUNK_BYTES)), 0);
        }
        assertArrayEquals(source, result);
        assertThrows(IllegalArgumentException.class, () -> chunks.accept(5, ProgressChunks.MAX_BYTES + 1, 0, new byte[1], 0));
        assertThrows(IllegalArgumentException.class, () -> chunks.accept(5, 5, 1, new byte[5], 0));
        chunks.accept(6, source.length, 0, Arrays.copyOf(source, ProgressChunks.CHUNK_BYTES), 0);
        assertThrows(IllegalArgumentException.class, () -> chunks.accept(6, source.length, 1,
                new byte[ProgressChunks.CHUNK_BYTES], 10001));
    }

    private byte[] bytes(CompoundTag tag) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(tag); byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); return bytes;
        } finally { buffer.release(); }
    }
}
