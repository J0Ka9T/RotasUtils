package net.schwarz.rotasutils.server;

import net.minecraft.server.MinecraftServer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.SeasonRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SeasonConfigFile {
    private SeasonConfigFile() {
    }

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve("config").resolve(Rotasutils.MOD_ID).resolve("season.json");
    }

    public static void load(MinecraftServer server, RotasData data) {
        String error = reload(server, data);
        if (error != null) {
            Rotasutils.LOG.error("season.json was not applied, the world copy stays active: {}", error);
        }
    }

    public static String reload(MinecraftServer server, RotasData data) {
        Path file = path(server);
        try {
            if (!Files.exists(file)) {
                write(server, data.levelConfig().season());
                return null;
            }
            SeasonRules rules = SeasonRules.fromJson(Files.readString(file, StandardCharsets.UTF_8));
            data.levelConfig().setSeason(rules);
            CardService.refreshIndex(data);
            data.setDirty();
            write(server, data.levelConfig().season());
            return null;
        } catch (IOException | RuntimeException failure) {
            return failure.getMessage() == null ? failure.toString() : failure.getMessage();
        }
    }

    public static void write(MinecraftServer server, SeasonRules rules) throws IOException {
        Path file = path(server);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling("season.json.tmp");
        Files.writeString(temp, rules.toJson(), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
