package net.schwarz.rotasutils.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.skill.SkillNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SkillProgressionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void categoryPointsSaturateWithoutLosingTheBalance() {
        var p = new PlayerProgress(UUID.randomUUID());
        p.addCategoryPoints("smith", Integer.MAX_VALUE); p.addCategoryPoints("smith", 1);
        assertEquals(Integer.MAX_VALUE, p.categoryPoints("smith"));
    }
    @Test void escalatingCostCannotWrapIntoAFreePurchase() {
        var node = new SkillNode("smith.master", "smith");
        node.setCostPerRank(Integer.MAX_VALUE); node.setCostIncrement(10);
        assertEquals(Integer.MAX_VALUE, node.costForRank(2));
    }
    @Test void rootMarkerDoesNotBypassAnExplicitPrerequisite() {
        var data = new net.schwarz.rotasutils.data.RotasData();
        var node = new SkillNode("master", "smith"); node.setRoot(true);
        node.connections().add(new net.schwarz.rotasutils.skill.SkillConnection("missing"));
        assertNotNull(net.schwarz.rotasutils.server.SkillService.connectionsSatisfied(data,
                new PlayerProgress(UUID.randomUUID()), node));
    }
    @Test void deletedPrerequisiteCannotBeSatisfiedByAnOldRank() {
        var data = new net.schwarz.rotasutils.data.RotasData();
        var node = new SkillNode("master", "smith");
        node.connections().add(new net.schwarz.rotasutils.skill.SkillConnection("missing"));
        var p = new PlayerProgress(UUID.randomUUID()); p.setSkillRank("missing", 2);
        assertNotNull(net.schwarz.rotasutils.server.SkillService.connectionsSatisfied(data, p, node));
    }
    @Test void refundsUsePersistedPurchasePoolsAndPricesAfterNodeDeletion() {
        var p = new PlayerProgress(UUID.randomUUID()); p.addSkillPoints(10); p.addCategoryPoints("smith", 10);
        p.spendSkillPoints(2); p.recordSkillPurchase("strike", "warrior", "", 2); p.setSkillRank("strike", 1);
        p.spendCategoryPoints("smith", 3); p.recordSkillPurchase("forge", "smith", "smith", 3); p.setSkillRank("forge", 1);
        p = PlayerProgress.load(p.save());
        assertEquals(3, net.schwarz.rotasutils.server.SkillService.refund(p, new net.schwarz.rotasutils.data.RotasData(), "smith"));
        assertEquals(10, p.categoryPoints("smith")); assertEquals(8, p.skillPoints()); assertEquals(1, p.skillRank("strike"));
        assertEquals(2, net.schwarz.rotasutils.server.SkillService.refund(p, new net.schwarz.rotasutils.data.RotasData(), null));
        assertEquals(10, p.skillPoints()); assertEquals(10, p.totalPointsEarned());
        assertEquals(0, net.schwarz.rotasutils.server.SkillService.refund(p, new net.schwarz.rotasutils.data.RotasData(), null));
    }
    @Test void nodeLevelsScaleByPurchasedRankAndRoundTrip() {
        var node = new SkillNode("master", "smith"); node.setMinLevel(5); node.setLevelIncrement(3);
        node = SkillNode.load(node.save());
        assertEquals(5, node.requiredLevel(0)); assertEquals(11, node.requiredLevel(2));
    }
    @Test void effectScalingUsesLevelAndRankAndCap() {
        var effect = new net.schwarz.rotasutils.skill.SkillEffect(net.schwarz.rotasutils.skill.EffectType.MAX_HEALTH);
        effect.params().tag().putDouble("value", 2); effect.params().tag().putDouble("per_level", 0.5);
        effect.params().tag().putDouble("max", 10);
        assertEquals(8, effect.valueAt(2, 5)); assertEquals(10, effect.valueAt(2, 100));
    }
    @Test void exclusiveBranchesCannotBeBoughtInReverseOrder() {
        var first = new SkillNode("first", "job"); first.exclusiveWith().add("second");
        var second = new SkillNode("second", "job");
        var nodes = java.util.Map.of(first.id(), first, second.id(), second);
        net.schwarz.rotasutils.skill.SkillRules.mirrorExclusives(nodes);
        var progress = new PlayerProgress(UUID.randomUUID()); progress.setSkillRank(first.id(), 1);
        assertNotNull(net.schwarz.rotasutils.skill.SkillRules.connectionsSatisfied(nodes::get, progress, second));
    }
    @Test void paidBalancesAndRanksSyncWithoutSendingRefundReceipts() {
        var p = new PlayerProgress(UUID.randomUUID()); p.addSkillPoints(8); p.addCategoryPoints("smith", 12);
        p.spendCategoryPoints("smith", 3); p.setSkillRank("forge", 1); p.recordSkillPurchase("forge", "smith", "smith", 3);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            p.write(buffer); var client = PlayerProgress.read(buffer);
            assertEquals(8, client.skillPoints()); assertEquals(9, client.categoryPoints("smith"));
            assertEquals(1, client.skillRank("forge")); assertTrue(client.skillPurchases("forge").isEmpty());
            assertEquals(1, PlayerProgress.load(p.save()).skillPurchases("forge").size());
        } finally { buffer.release(); }
    }
}
