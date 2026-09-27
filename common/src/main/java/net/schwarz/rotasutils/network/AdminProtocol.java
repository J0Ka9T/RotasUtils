package net.schwarz.rotasutils.network;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Arrays;
import java.util.function.Consumer;

public final class AdminProtocol {
    public static final int CHUNK = 24 * 1024;
    public static final int MAX = 512 * 1024;
    public record Segment(long id, int total, int index, byte[] bytes) { }
    private AdminProtocol() { }

    public static Segment read(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > CHUNK + 32 || buffer.readUnsignedByte() != 1) {
            throw new IllegalArgumentException("Invalid admin packet");
        }
        long id = buffer.readLong(); int total = buffer.readVarInt(), index = buffer.readVarInt();
        byte[] bytes = buffer.readByteArray(CHUNK);
        if (buffer.isReadable() || id < 1 || total < 1 || total > MAX || index < 0) {
            throw new IllegalArgumentException("Invalid admin chunk bounds");
        }
        return new Segment(id, total, index, bytes);
    }

    public static CompoundTag decode(byte[] bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            CompoundTag tag = buffer.readNbt();
            if (tag == null || buffer.isReadable()) { throw new IllegalArgumentException("Invalid admin document"); }
            return tag;
        } finally { buffer.release(); }
    }

    public static void send(long id, CompoundTag tag, Consumer<FriendlyByteBuf> sink) {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer()); byte[] bytes;
        try {
            encoded.writeNbt(tag);
            if (encoded.readableBytes() > MAX) { throw new IllegalArgumentException("Admin response exceeds 512 KiB"); }
            bytes = new byte[encoded.readableBytes()]; encoded.readBytes(bytes);
        } finally { encoded.release(); }
        for (int offset = 0, index = 0; offset < bytes.length; offset += CHUNK, index++) {
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeByte(1); packet.writeLong(id); packet.writeVarInt(bytes.length).writeVarInt(index);
            packet.writeByteArray(Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK)));
            sink.accept(packet);
        }
    }

    public static void expected(CompoundTag request, long revision, long generation) {
        if (!request.contains("revision", 4) || !request.contains("generation", 4)
                || request.getLong("revision") != revision || request.getLong("generation") != generation) {
            throw new IllegalStateException("Editor is stale; refresh before changing content");
        }
    }
}
