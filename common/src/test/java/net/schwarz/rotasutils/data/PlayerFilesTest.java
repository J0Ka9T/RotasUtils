package net.schwarz.rotasutils.data;

import net.schwarz.rotasutils.progress.PlayerProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFilesTest {
    @TempDir
    Path dir;

    @Test
    void writtenPlayersComeBackAndOverwriteInPlace() throws IOException {
        PlayerFiles files = new PlayerFiles(dir.resolve("players"));
        PlayerProgress player = new RotasData().progress(UUID.randomUUID());
        player.claimOnce("quest:a:0");
        files.write(player);
        player.claimOnce("quest:b:0");
        files.write(player);

        Map<UUID, PlayerProgress> loaded = files.loadAll();

        assertEquals(1, loaded.size());
        assertTrue(loaded.get(player.playerId()).claimedRewards().contains("quest:b:0"));
    }

    @Test
    void oneCorruptFileCostsOnlyThatPlayer() throws IOException {
        PlayerFiles files = new PlayerFiles(dir.resolve("players"));
        PlayerProgress good = new RotasData().progress(UUID.randomUUID());
        files.write(good);
        Path bad = dir.resolve("players").resolve(UUID.randomUUID() + ".dat");
        Files.writeString(bad, "not nbt");

        Map<UUID, PlayerProgress> loaded = files.loadAll();

        assertEquals(1, loaded.size());
        assertTrue(loaded.containsKey(good.playerId()));
        assertFalse(Files.exists(bad));
    }

    @Test
    void missingDirectoryLoadsNothing() {
        assertTrue(new PlayerFiles(dir.resolve("absent")).loadAll().isEmpty());
    }
}
