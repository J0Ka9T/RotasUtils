package net.schwarz.rotasutils.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.progress.PlayerProgress;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class PlayerFiles {
    private static final String SUFFIX = ".dat";

    private final Path dir;

    PlayerFiles(Path dir) {
        this.dir = dir;
    }

    Map<UUID, PlayerProgress> loadAll() {
        Map<UUID, PlayerProgress> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return loaded;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + SUFFIX)) {
            for (Path file : files) {
                try {
                    PlayerProgress progress = PlayerProgress.load(NbtIo.readCompressed(file.toFile()));
                    loaded.put(progress.playerId(), progress);
                } catch (IOException | RuntimeException e) {
                    quarantine(file, e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("RotasUtils could not list " + dir, e);
        }
        return loaded;
    }

    void write(PlayerProgress progress) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(progress.playerId() + SUFFIX);
        Path temp = dir.resolve(progress.playerId() + SUFFIX + ".tmp");
        CompoundTag tag = progress.save();
        NbtIo.writeCompressed(tag, temp.toFile());
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantine(Path file, Exception cause) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, aside);
        } catch (IOException moveFailure) {
            Rotasutils.LOG.error("RotasUtils could not move aside {}", file, moveFailure);
        }
        Rotasutils.LOG.error("RotasUtils could not read player file {}; kept as {}", file, aside, cause);
    }
}
