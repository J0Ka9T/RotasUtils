package net.schwarz.rotasutils.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class CompressedText {
    public static final int MAX_TEXT_BYTES = 1 << 20;

    private CompressedText() {
    }

    public static byte[] compress(String text) {
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 4));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static String decompress(byte[] data) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length * 4));
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("truncated payload");
                }
                if (out.size() + count > MAX_TEXT_BYTES) throw new IllegalArgumentException("payload too large");
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException invalid) {
            throw new IllegalArgumentException("invalid payload", invalid);
        } finally {
            inflater.end();
        }
    }
}
