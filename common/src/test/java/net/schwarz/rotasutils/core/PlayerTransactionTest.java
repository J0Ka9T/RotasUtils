package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.PlayerRecordTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTransactionTest {
    @TempDir Path temporary;

    @Test void serverReceiptsAndKernelVariablesNeverInflateClientSnapshot() {
        RotasData data = new RotasData();
        var player = data.progress(UUID.randomUUID());
        player.questVariables().put("pref.layout", "compact");
        var baseline = player.clientSnapshot();
        for (int i = 0; i < 10000; i++) {
            player.claimedRewards().add("kernel|rotas:reward/test|" + i);
            player.questVariables().put("rpg.private_" + i, "secret");
        }
        assertEquals(baseline, player.clientSnapshot());
        assertFalse(player.clientSnapshot().contains("claimed"));
        assertEquals("compact", player.clientSnapshot().getCompound("variables").getString("pref.layout"));
        assertEquals(10000, player.save().getList("claimed", 8).size());
        assertEquals(10001, player.save().getCompound("variables").size());
    }

    @Test void stateAndReceiptsSurviveActualCompressedNbtRoundTrip() throws Exception {
        RotasData data = new RotasData();
        UUID id = UUID.randomUUID();
        var player = data.progress(id);
        player.setLevel(42);
        player.questVariables().put("pref.layout", "compact");
        try (var transaction = new PlayerRecordTransaction(data, player)) {
            transaction.variable("rpg.welcome", "yes");
            transaction.unlock("rotas:quest/welcome");
            transaction.claim("kernel|rotas:reward/welcome|once");
            transaction.commit();
        }
        Path file = temporary.resolve("rotasutils_data.dat");
        NbtIo.writeCompressed(data.save(new CompoundTag()), file.toFile());
        var restored = RotasData.load(NbtIo.readCompressed(file.toFile()));
        assertEquals(42, restored.peek(id).level());
        assertEquals("compact", restored.peek(id).questVariables().get("pref.layout"));
        try (var transaction = new PlayerRecordTransaction(restored, restored.peek(id))) {
            assertEquals("yes", transaction.variable("rpg.welcome"));
            assertTrue(transaction.claimed("kernel|rotas:reward/welcome|once"));
        }
        assertTrue(restored.peek(id).unlockedQuests().contains("rotas:quest/welcome"));
    }

    @Test void rollbackNestedTransactionAndOptimisticConflictAreSafe() {
        RotasData data = new RotasData();
        var player = data.progress(UUID.randomUUID());
        try (var transaction = new PlayerRecordTransaction(data, player)) {
            transaction.variable("rpg.staged", "discarded");
            assertThrows(IllegalStateException.class, () -> new PlayerRecordTransaction(data, player));
        }
        assertFalse(player.questVariables().containsKey("rpg.staged"));
        try (var transaction = new PlayerRecordTransaction(data, player)) {
            transaction.variable("rpg.staged", "discarded");
            player.questVariables().put("external", "preserve");
            assertThrows(IllegalStateException.class, transaction::commit);
        }
        assertEquals("preserve", player.questVariables().get("external"));
        assertFalse(player.questVariables().containsKey("rpg.staged"));
    }

    @Test void preventsUseAfterCommitAndReceiptEviction() {
        RotasData data = new RotasData();
        var player = data.progress(UUID.randomUUID());
        try (var transaction = new PlayerRecordTransaction(data, player)) {
            transaction.commit();
            assertThrows(IllegalStateException.class, () -> transaction.variable("rpg.late", "bad"));
            assertThrows(IllegalStateException.class, transaction::commit);
        }
        for (int i = 0; i < 65536; i++) { player.claimedRewards().add("legacy:" + i); }
        try (var transaction = new PlayerRecordTransaction(data, player)) {
            assertThrows(IllegalStateException.class, () -> transaction.claim("kernel|rotas:r|one"));
        }
        assertEquals(65536, player.claimedRewards().size());
    }
}
