package net.schwarz.rotasutils.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConfigFiles {
    private ConfigFiles() { }

    public static JsonObject document(String domain, CompoundTag value) {
        var json = new JsonObject(); json.addProperty("schema", 1); json.addProperty("domain", domain);
        json.add("body", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, value)); return json;
    }

    public static CompoundTag body(JsonObject document) {
        KernelJson.integer(document, "schema", 1, 1);
        if (!document.has("body") || !document.get("body").isJsonObject()) { throw new IllegalArgumentException("body: expected an object"); }
        var converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, document.get("body"));
        if (!(converted instanceof CompoundTag tag)) { throw new IllegalArgumentException("body: expected configuration object"); }
        return tag;
    }

    public static Path export(Path root, List<ContentRegistry.Source> sources, Map<String, CompoundTag> legacy) throws IOException {
        Path exports = safeDirectory(root, "exports");
        Path target = exports.resolve(java.time.Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectory(target);
        int index = 0;
        for (var source : sources) {
            Path directory = safeDirectory(target, "packs/" + source.layer().name().toLowerCase(java.util.Locale.ROOT) + "/snapshot");
            write(directory.resolve(String.format("%04d.json", index++)), source.document());
        }
        Path legacyRoot = safeDirectory(target, "configuration");
        index = 0;
        for (var entry : new java.util.TreeMap<>(legacy).entrySet()) {
            write(legacyRoot.resolve(String.format("%04d.json", index++)), document(entry.getKey(), entry.getValue()));
        }
        return target;
    }

    public static Map<String, CompoundTag> readLegacy(Path root) throws IOException {
        Path directory = root.resolve("configuration");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) { return Map.of(); }
        safeDirectory(root, "configuration");
        Map<String, CompoundTag> documents = new java.util.TreeMap<>(); long total = 0;
        try (var paths = Files.walk(directory, 2)) {
            var files = paths.sorted().limit(1026).toList();
            if (files.size() > 1025) { throw new IOException("Too many configuration files (maximum 1024)"); }
            for (Path file : files) {
                if (Files.isSymbolicLink(file)) { throw new IOException("Symbolic links are not supported: " + file.getFileName()); }
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) { continue; }
                if (!file.toString().endsWith(".json")) { continue; }
                byte[] bytes;
                try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(ContentPacks.MAX_FILE_BYTES + 1); }
                total += bytes.length;
                if (bytes.length > ContentPacks.MAX_FILE_BYTES || total > 4 * 1024 * 1024) { throw new IOException("Configuration import exceeds file or total budget"); }
                var json = ContentPacks.parse(new String(bytes, StandardCharsets.UTF_8));
                String domain = KernelJson.string(json, "domain");
                if (documents.putIfAbsent(domain, body(json)) != null) { throw new IOException("Duplicate configuration domain: " + domain); }
            }
        }
        return Map.copyOf(documents);
    }

    public static Path safeDirectory(Path root, String relative) throws IOException {
        Path absolute = root.toAbsolutePath().normalize(); Path target = absolute.resolve(relative).normalize();
        if (!target.startsWith(absolute)) { throw new IOException("Path leaves the configuration directory"); }
        Path cursor = absolute.getRoot();
        for (Path part : target) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) { throw new IOException("Symbolic links are not supported in configuration paths"); }
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Expected directory: " + cursor); }
        }
        Files.createDirectories(target); return target;
    }

    private static void write(Path file, JsonObject document) throws IOException {
        byte[] bytes = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ContentPacks.MAX_FILE_BYTES) { throw new IOException("Exported definition exceeds 256 KiB"); }
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
