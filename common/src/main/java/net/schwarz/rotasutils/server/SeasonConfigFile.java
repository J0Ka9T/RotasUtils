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

/**
 * {@code config/rotasutils/season.json}: every season number in one file the team can edit between playtests.
 * A file on disk wins at server start and on {@code /rotas season reload}; without one, the world's copy is
 * written out so there is always something to edit.
 */
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

    /** Reads the file into the world config. Returns null on success, or the reason it failed. */
    public static String reload(MinecraftServer server, RotasData data) {
        Path file = path(server);
        try {
            if (!Files.exists(file)) {
                write(server, data.levelConfig().season());
                return null;
            }
            SeasonRules rules = SeasonRules.fromJson(Files.readString(file, StandardCharsets.UTF_8));
            data.levelConfig().setSeason(rules);
            // Cards are read from this file, so the shared display table follows every reload.
            CardService.refreshIndex(data);
            data.setDirty();
            // Write back the sanitized copy so out-of-range values the admin typed are visible as corrected.
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
