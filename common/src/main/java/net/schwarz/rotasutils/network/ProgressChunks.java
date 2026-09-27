package net.schwarz.rotasutils.network;

import java.io.ByteArrayOutputStream;

public final class ProgressChunks {
    public static final int CHUNK_BYTES = 32768;
    public static final int MAX_BYTES = 1048576;
    private long transfer;
    private int total;
    private int next;
    private long deadline;
    private ByteArrayOutputStream bytes;
    private final int chunkBytes;
    private final int maxBytes;

    public ProgressChunks() { this(CHUNK_BYTES, MAX_BYTES); }
    public ProgressChunks(int chunkBytes, int maxBytes) {
        if (chunkBytes < 1 || maxBytes < chunkBytes || maxBytes > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid chunk assembler limits");
        }
        this.chunkBytes = chunkBytes; this.maxBytes = maxBytes;
    }

    public byte[] accept(long id, int length, int index, byte[] chunk, long nowMillis) {
        if (length < 1 || length > maxBytes || index < 0 || chunk.length < 1 || chunk.length > chunkBytes) {
            clear(); throw new IllegalArgumentException("Progress chunk bounds exceeded");
        }
        if (index == 0) {
            transfer = id; total = length; next = 0; deadline = nowMillis + 10000;
            bytes = new ByteArrayOutputStream(length);
        }
        if (bytes == null || id != transfer || length != total || index != next || nowMillis > deadline
                || chunk.length > total - bytes.size()
                || chunk.length != Math.min(chunkBytes, total - bytes.size())) {
            clear(); throw new IllegalArgumentException("Progress chunk sequence invalid");
        }
        bytes.writeBytes(chunk); next++;
        if (bytes.size() == total) {
            byte[] result = bytes.toByteArray(); clear(); return result;
        }
        return null;
    }

    public void clear() { bytes = null; next = 0; total = 0; }
}
