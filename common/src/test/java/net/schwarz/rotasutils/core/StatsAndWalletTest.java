package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.PlayerRecordTransaction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StatsAndWalletTest {
    @Test void statPipelineComposesThenClampsAndRejectsNonfiniteInputs() {
        var stat = new StatDefinition(new ContentId("rotas:strength"), 10, 2, 0, 1000,
                "", 1, StatDefinition.Operation.ADD);
        assertEquals(33.75, stat.calculate(3, 1, List.of(
                new StatDefinition.Modifier(3, StatDefinition.Operation.ADD),
                new StatDefinition.Modifier(.5, StatDefinition.Operation.MULTIPLY_BASE),
                new StatDefinition.Modifier(.25, StatDefinition.Operation.MULTIPLY_TOTAL))));
        assertEquals(1000, stat.calculate(1000, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> stat.calculate(1, Double.NaN, List.of()));
    }

    @Test void statDefinitionsShareTheRegistryAndRejectBadRanges() throws Exception {
        var registry = new ContentRegistry(new ConditionEngine(Map.of()), new ActionEngine(Map.of()));
        var json = ContentPacks.parse("{\"schema\":1,\"id\":\"rotas:stat/vitality\",\"kind\":\"stat\",\"body\":{\"base\":0,\"per_level\":1,\"min\":0,\"max\":100}}");
        var prepared = registry.prepare(List.of(new ContentRegistry.Source("stat.json", ContentRegistry.Layer.SERVER, json)));
        assertTrue(prepared.valid(), prepared.issues().toString());
        assertEquals(1, prepared.snapshot().stats().size());
        json.getAsJsonObject("body").addProperty("max", -1);
        assertFalse(registry.prepare(List.of(new ContentRegistry.Source("bad", ContentRegistry.Layer.SERVER, json))).valid());
    }

    @Test void walletReputationAndReceiptCommitTogetherAndPersist() {
        var data = new RotasData(); var id = UUID.randomUUID(); var player = data.progress(id);
        player.questVariables().put("reputation.rotas:empire", "50");
        try (var tx = new PlayerRecordTransaction(data, player)) {
            tx.currency("rotas:gold", 500); tx.reputation("rotas:empire", -10); tx.statPoints(3);
            tx.claim("kernel|rotas:reward/starter|one"); tx.commit();
        }
        var restored = RotasData.load(data.save(new net.minecraft.nbt.CompoundTag())).peek(id);
        assertEquals(500, restored.rpg().currency("rotas:gold"));
        assertEquals(40, restored.reputation("rotas:empire"));
        assertEquals(3, restored.rpg().statPoints());
        assertTrue(restored.claimedRewards().contains("kernel|rotas:reward/starter|one"));
    }

    @Test void failedWalletStagingCannotLeakEarlierGrantsOrConsumeReceipt() {
        var data = new RotasData(); var player = data.progress(UUID.randomUUID());
        try (var tx = new PlayerRecordTransaction(data, player)) {
            tx.currency("rotas:gold", 500); tx.statPoints(3);
            assertThrows(IllegalArgumentException.class, () -> tx.currency("rotas:gold", -501));
        }
        assertEquals(0, player.rpg().currency("rotas:gold")); assertEquals(0, player.rpg().statPoints());
        try (var tx = new PlayerRecordTransaction(data, player)) {
            tx.currency("rotas:gold", 500);
            player.rpg().stat("rotas:strength", 1);
            assertThrows(IllegalStateException.class, tx::commit);
        }
        assertEquals(0, player.rpg().currency("rotas:gold"));
    }
}
