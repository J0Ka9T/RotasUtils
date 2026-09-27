package net.schwarz.rotasutils.server.fabric;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.LivingEntity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

public final class MonsterStorageImpl {
    private static final String PREFIX = "rotasutils.monster:";
    private static final int MAX_ENCODED_LENGTH = 44000;
    private static final int MAX_COMPRESSED_BYTES = 33000;
    private static final int MAX_EXPANDED_BYTES = 65536;
    private MonsterStorageImpl() { }

    public static CompoundTag read(LivingEntity entity) {
        String encoded = entity.getTags().stream().filter(value -> value.startsWith(PREFIX)).findFirst().orElse("");
        if (encoded.isEmpty()) { return new CompoundTag(); }
        if (encoded.length() > MAX_ENCODED_LENGTH) { throw new IllegalArgumentException("Monster tag exceeds storage budget"); }
        try {
            byte[] compressed = Base64.getDecoder().decode(encoded.substring(PREFIX.length()));
            if (compressed.length > MAX_COMPRESSED_BYTES) { throw new IllegalArgumentException("Monster tag exceeds compressed storage budget"); }
            byte[] expanded = expandBounded(compressed);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(expanded))) {
                CompoundTag result = NbtIo.read(input, new NbtAccounter(MAX_EXPANDED_BYTES));
                if (input.available() != 0) { throw new IllegalArgumentException("Monster tag has trailing data"); }
                return result;
            }
        } catch (IOException | RuntimeException error) {
            if (error instanceof IllegalArgumentException invalid) { throw invalid; }
            throw new IllegalArgumentException("Invalid monster tag", error);
        }
    }

    private static byte[] expandBounded(byte[] compressed) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(compressed.length * 2, MAX_EXPANDED_BYTES))) {
            byte[] buffer = new byte[4096];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read == 0) { continue; }
                total += read;
                if (total > MAX_EXPANDED_BYTES) { throw new IllegalArgumentException("Monster tag exceeds expanded storage budget"); }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    public static void write(LivingEntity entity, CompoundTag state) {
        String replacement = "";
        if (!state.isEmpty()) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream(); NbtIo.writeCompressed(state, output);
                replacement = PREFIX + Base64.getEncoder().encodeToString(output.toByteArray());
                if (output.size() > MAX_COMPRESSED_BYTES || replacement.length() > MAX_ENCODED_LENGTH) {
                    throw new IllegalArgumentException("Monster tag exceeds storage budget");
                }
            } catch (IOException error) { throw new IllegalStateException("Unable to encode monster state", error); }
        }
        var previous = entity.getTags().stream().filter(value -> value.startsWith(PREFIX)).toList();
        if (previous.isEmpty() && !replacement.isEmpty() && entity.getTags().size() >= 1024) { throw new IllegalStateException("Entity has no free tag capacity"); }
        previous.forEach(entity::removeTag);
        if (!replacement.isEmpty() && !entity.addTag(replacement)) {
            previous.forEach(entity::addTag); throw new IllegalStateException("Unable to save monster tag");
        }
    }
}
