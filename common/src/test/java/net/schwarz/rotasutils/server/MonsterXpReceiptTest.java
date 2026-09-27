package net.schwarz.rotasutils.server;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.RotasData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterXpReceiptTest {
    @Test
    void duplicateMonsterReceiptIsRejectedForTheSameRecipient() {
        var progress = new RotasData().progress(UUID.randomUUID());
        UUID monsterId = UUID.randomUUID();

        assertTrue(ProgressService.claimMonsterXpReceipt(progress, monsterId));
        assertFalse(ProgressService.claimMonsterXpReceipt(progress, monsterId));
        assertEquals(1, progress.claimedRewards().size());
    }

    @Test
    void fullReceiptBudgetFailsClosedWithoutEviction() {
        var progress = new RotasData().progress(UUID.randomUUID());
        for (int i = 0; i < 65_536; i++) {
            progress.claimedRewards().add("existing:" + i);
        }

        assertFalse(ProgressService.claimMonsterXpReceipt(progress, UUID.randomUUID()));
        assertEquals(65_536, progress.claimedRewards().size());
        assertTrue(progress.claimedRewards().contains("existing:0"));
    }

    @Test
    void sameMonsterCanBeClaimedByDifferentRecipients() {
        RotasData data = new RotasData();
        var first = data.progress(UUID.randomUUID());
        var second = data.progress(UUID.randomUUID());
        UUID monsterId = UUID.randomUUID();

        assertTrue(ProgressService.claimMonsterXpReceipt(first, monsterId));
        assertTrue(ProgressService.claimMonsterXpReceipt(second, monsterId));
        assertEquals(1, first.claimedRewards().size());
        assertEquals(1, second.claimedRewards().size());
    }

    @Test
    void monsterReceiptSurvivesSavedDataRoundTrip() {
        RotasData data = new RotasData();
        UUID playerId = UUID.randomUUID();
        UUID monsterId = UUID.randomUUID();
        assertTrue(ProgressService.claimMonsterXpReceipt(data.progress(playerId), monsterId));

        RotasData restored = RotasData.load(data.save(new CompoundTag()));

        assertFalse(ProgressService.claimMonsterXpReceipt(restored.peek(playerId), monsterId));
        assertEquals(1, restored.peek(playerId).claimedRewards().size());
    }
}
