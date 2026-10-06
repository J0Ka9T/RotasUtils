package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.npc.NpcDef;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcEditingTest {
    @Test
    void progressionServicesAndCombatBuildRoundTrip() {
        NpcDef npc=new NpcDef("mentor");
        npc.services().add(new net.schwarz.rotasutils.npc.NpcServiceDef(net.schwarz.rotasutils.npc.NpcServiceDef.Type.JOB_MASTER,"warrior","",net.schwarz.rotasutils.job.JobSlot.MAIN));
        npc.setBuild(new net.schwarz.rotasutils.npc.NpcBuild(net.schwarz.rotasutils.npc.NpcBuild.LevelMode.FIXED,25,1.2,"guild",24,"rotas:guard",java.util.Map.of("rotas:strength",8),java.util.Map.of("shield_wall",2)));
        NpcDef restored=NpcDef.load(npc.save());
        assertEquals(npc.save(),restored.save());
        assertEquals(1,restored.services().size());
        assertTrue(restored.combatCapable());
    }

    @Test
    void jobMasterNpcCanOfferDifferentMainAndSubJobs() {
        NpcDef npc = new NpcDef("guild_master");
        npc.setRole(NpcDef.Role.JOB_MASTER);
        npc.services().add(new net.schwarz.rotasutils.npc.NpcServiceDef(
                net.schwarz.rotasutils.npc.NpcServiceDef.Type.JOB_MASTER, "fighter", "", net.schwarz.rotasutils.job.JobSlot.MAIN));
        npc.services().add(new net.schwarz.rotasutils.npc.NpcServiceDef(
                net.schwarz.rotasutils.npc.NpcServiceDef.Type.JOB_MASTER, "blacksmith", "", net.schwarz.rotasutils.job.JobSlot.SUB));

        assertEquals("fighter", JobService.offeredJob(npc, net.schwarz.rotasutils.job.JobSlot.MAIN));
        assertEquals("blacksmith", JobService.offeredJob(npc, net.schwarz.rotasutils.job.JobSlot.SUB));
        assertEquals(NpcDef.Role.JOB_MASTER, NpcDef.load(npc.save()).role());
    }
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void voiceRoundTripsAndFallsBackToGreetingVoice() {
        NpcDef npc = new NpcDef("talker");
        npc.setVoice("greeting", "minecraft:entity.villager.yes");
        npc.setVoicePitch(500);
        NpcDef restored = NpcDef.load(npc.save());
        assertEquals("minecraft:entity.villager.yes", restored.voice("greeting"));
        assertEquals(200, restored.voicePitch());
        assertNotNull(restored.voiceSound("farewell"));
        assertNull(new NpcDef("mute").voiceSound("greeting"));
        assertNull(new NpcDef("bad").voiceSound("greeting"));
    }

    @Test void nonfiniteReachCannotDisableDistanceChecks() {
        NpcDef npc = new NpcDef("test");
        assertThrows(IllegalArgumentException.class, () -> npc.setInteractionDistance(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> npc.setInteractionDistance(Double.POSITIVE_INFINITY));
        assertEquals(6, npc.interactionDistance());
    }

    @Test void corruptStoredReachIsRestoredToSafeDefault() {
        var tag = new NpcDef("test").save();
        tag.putDouble("distance", Double.NaN);
        assertEquals(6, NpcDef.load(tag).interactionDistance());
    }

    @Test void simpleShopTradesAndMobOptionsSurviveSaveAndLoad() {
        NpcDef npc = new NpcDef("shop");
        assertFalse(npc.hasShop());
        npc.trades().add(new NpcDef.Trade(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3),
                net.minecraft.world.item.ItemStack.EMPTY,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 99)));
        npc.setStandStill(true);
        npc.setInvulnerable(true);
        npc.setShowName(true);
        NpcDef loaded = NpcDef.load(npc.save());
        assertTrue(loaded.hasShop());
        assertEquals(1, loaded.trades().size());
        var trade = loaded.trades().get(0);
        assertEquals(net.minecraft.world.item.Items.EMERALD, trade.costA().getItem());
        assertEquals(3, trade.costA().getCount());
        assertTrue(trade.costB().isEmpty());
        assertEquals(64, trade.result().getCount(), "counts are clamped to the stack size");
        assertTrue(loaded.standStill());
        assertTrue(loaded.invulnerable());
        assertTrue(loaded.showName());
    }

    @Test void merchantWithoutTradesReportsAProblem() {
        NpcDef npc = new NpcDef("shop");
        npc.setRole(NpcDef.Role.MERCHANT);
        assertTrue(npc.problems().stream().anyMatch(problem -> problem.contains("รายการแลกเปลี่ยน")));
    }

    @Test void talkAndEscortExposeWorldSelectionInsteadOfTypedUuid() {
        for (var type : new ObjectiveType[]{ObjectiveType.TALK_NPC, ObjectiveType.ESCORT_NPC}) {
            assertEquals(ParamKind.NPC, type.specs().stream().filter(s -> s.key().equals("npc_uuid")).findFirst().orElseThrow().kind());
        }
    }
}
