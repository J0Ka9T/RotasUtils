package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ContentPacks {
    public static final int MAX_FILE_BYTES = 262_144;

    private ContentPacks() {
    }

    public static ContentRegistry.Prepared read(Path root, ContentRegistry registry) {
        List<ContentRegistry.Source> sources = new ArrayList<>();
        List<ContentRegistry.Diagnostic> errors = new ArrayList<>();
        long total = 0;
        try {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                return registry.prepare(List.of());
            }
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Pack root must be a real directory");
            }
            try (var paths = Files.walk(root, 12)) {
                List<Path> files = paths.sorted().limit(8193).toList();
                if (files.size() > 8192) {
                    throw new IOException("Pack entry budget exceeded");
                }
                int definitions = 0;
                for (Path path : files) {
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Symlink rejected: " + root.relativize(path));
                    }
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        if (root.relativize(path).getNameCount() >= 12) {
                            throw new IOException("Pack directory nesting exceeds 11 levels");
                        }
                        continue;
                    }
                    if (!path.getFileName().toString().endsWith(".json")) {
                        continue;
                    }
                    if (++definitions > 4096) {
                        throw new IOException("Definition count exceeds 4096");
                    }
                    String source = root.relativize(path).toString();
                    try {
                        Path relative = root.relativize(path);
                        if (relative.getNameCount() < 2) {
                            throw new IllegalArgumentException("Expected layer/<pack>/<definition>.json");
                        }
                        ContentRegistry.Layer layer = ContentRegistry.Layer.valueOf(
                                relative.getName(0).toString().toUpperCase(Locale.ROOT));
                        byte[] bytes;
                        try (var input = Files.newInputStream(path)) {
                            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                        }
                        total += bytes.length;
                        if (total > 16_777_216) {
                            throw new IOException("Total pack budget exceeds 16 MiB");
                        }
                        if (bytes.length > MAX_FILE_BYTES) {
                            throw new IllegalArgumentException("Definition exceeds 256 KiB");
                        }
                        sources.add(new ContentRegistry.Source(source, layer,
                                parse(new String(bytes, StandardCharsets.UTF_8))));
                    } catch (IllegalArgumentException | IOException ex) {
                        errors.add(new ContentRegistry.Diagnostic(source, ex.getMessage()));
                        if (total > 16_777_216) {
                            break;
                        }
                    }
                }
            }
        } catch (IOException ex) {
            errors.add(new ContentRegistry.Diagnostic(root.toString(), ex.getMessage()));
        }
        ContentRegistry.Prepared prepared = registry.prepare(sources);
        errors.addAll(prepared.issues());
        return errors.isEmpty() ? prepared : new ContentRegistry.Prepared(null, errors);
    }

    public static JsonObject parse(String text) throws IOException {
        if (text.length() > MAX_FILE_BYTES) {
            throw new IOException("JSON document too large");
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement result = readValue(reader, 0, new int[]{0});
            if (!result.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Expected one JSON object");
            }
            return result.getAsJsonObject();
        }
    }

    private static JsonElement readValue(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 48 || ++nodes[0] > 8192) {
            throw new IOException("JSON nesting/node budget exceeded");
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new IOException("Duplicate JSON field: " + name);
                    }
                    object.add(name, readValue(reader, depth + 1, nodes));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    array.add(readValue(reader, depth + 1, nodes));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> {
                String value = reader.nextString();
                if (value.length() > 64) {
                    throw new IOException("Numeric literal too long");
                }
                yield new JsonPrimitive(new BigDecimal(value));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IOException("Unexpected JSON token at " + reader.getPath());
        };
    }
}
