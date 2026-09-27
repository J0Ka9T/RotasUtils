package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobSlot;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.npc.NpcServiceDef;
import net.schwarz.rotasutils.server.CrafterService;
import net.schwarz.rotasutils.server.ProductionService;
import net.schwarz.rotasutils.server.ProductionService.Access;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SubRoleAccessTest {
    private static final JobDef.ProductionEntry.Activity CRAFT = JobDef.ProductionEntry.Activity.CRAFT;

    private static JobDef job(String id, String... rows) {
        JobDef job = new JobDef(id);
        job.setName(id);
        for (String row : rows) job.production().add(JobDef.ProductionEntry.decode(row));
        return job;
    }

    private static ProductionService.Target target(String id, String... tags) {
        Set<String> inTags = Set.of(tags);
        return new ProductionService.Target() {
            @Override public String id() { return id; }
            @Override public boolean inTag(ResourceLocation tag) { return inTags.contains(tag.toString()); }
        };
    }

    private static Access access(List<JobDef> jobs, String subJob, int level, ProductionService.Target target, int minLocked) {
        return ProductionService.access(jobs, subJob, job -> level, CRAFT, target, minLocked);
    }

    @Test void itemsOutsideEveryTableAreFree() {
        List<JobDef> jobs = List.of(job("blacksmith", "CRAFT minecraft:iron_sword 1"));
        assertEquals(Access.State.FREE, access(jobs, "", 1, target("minecraft:torch"), 1).state());
    }

    @Test void onlyTheSubRoleAtItsLevelMayTakeAResult() {
        List<JobDef> jobs = List.of(job("blacksmith", "CRAFT minecraft:iron_sword 1", "CRAFT minecraft:diamond_sword 10"));
        assertEquals(Access.State.GRANTED, access(jobs, "blacksmith", 1, target("minecraft:iron_sword"), 1).state());
        Access stranger = access(jobs, "chef", 20, target("minecraft:iron_sword"), 1);
        assertTrue(stranger.locked(), "a starter row is sealed to the role");
        assertEquals("blacksmith", stranger.jobName());
        assertEquals(1, stranger.level());
        Access apprentice = access(jobs, "blacksmith", 9, target("minecraft:diamond_sword"), 1);
        assertTrue(apprentice.locked());
        assertEquals(10, apprentice.level());
    }

    @Test void starterRowsCanBeLeftOpen() {
        List<JobDef> jobs = List.of(job("blacksmith", "CRAFT minecraft:iron_sword 1", "CRAFT minecraft:diamond_sword 10"));
        assertEquals(Access.State.FREE, access(jobs, "", 1, target("minecraft:iron_sword"), 2).state());
        assertTrue(access(jobs, "", 1, target("minecraft:diamond_sword"), 2).locked());
    }

    @Test void anItemInTwoTablesOpensForEitherRole() {
        List<JobDef> jobs = List.of(job("farmer", "CRAFT minecraft:hay_block 3"), job("rancher", "CRAFT minecraft:hay_block 2"));
        var hay = target("minecraft:hay_block");
        assertEquals(Access.State.GRANTED, access(jobs, "farmer", 3, hay, 1).state());
        assertEquals(Access.State.GRANTED, access(jobs, "rancher", 2, hay, 1).state());
        Access chef = access(jobs, "chef", 20, hay, 1);
        assertEquals("rancher", chef.jobName(), "a stranger is pointed at the easiest role");
        assertEquals(2, chef.level());
        Access farmer = access(jobs, "farmer", 2, hay, 1);
        assertEquals("farmer", farmer.jobName(), "a role player is told about their own role");
        assertEquals(3, farmer.level());
    }

    @Test void disabledJobsSealNothing() {
        JobDef blacksmith = job("blacksmith", "CRAFT minecraft:iron_sword 1");
        blacksmith.setEnabled(false);
        assertEquals(Access.State.FREE, access(List.of(blacksmith), "", 1, target("minecraft:iron_sword"), 1).state());
    }

    @Test void tagRowsSealEveryTaggedItem() {
        List<JobDef> jobs = List.of(job("miner", "MINE #minecraft:iron_ores 5"));
        var ore = target("minecraft:deepslate_iron_ore", "minecraft:iron_ores");
        Access access = ProductionService.access(jobs, "", job -> 1, JobDef.ProductionEntry.Activity.MINE, ore, 2);
        assertTrue(access.locked());
        assertEquals(Access.State.FREE, ProductionService.access(jobs, "", job -> 1,
                JobDef.ProductionEntry.Activity.MINE, target("minecraft:stone"), 2).state());
    }

    @Test void roleRulesTravelAndClamp() {
        LevelConfig config = new LevelConfig();
        config.season().gatherDropChance = 0.5;
        config.season().crafterDailyLimit = 9;
        LevelConfig loaded = LevelConfig.load(config.save());
        assertEquals(0.5, loaded.season().gatherDropChance, 1e-9);
        assertEquals(9, loaded.season().crafterDailyLimit);

        SeasonRules bad = SeasonRules.fromJson("{\"gatherDropChance\":7,\"gatherMaxCount\":0,\"crafterFee\":[1]}");
        assertEquals(1.0, bad.gatherDropChance, 1e-9);
        assertEquals(1, bad.gatherMaxCount);
        assertArrayEquals(new long[]{80, 400, 1200, 3000}, bad.crafterFee);

        SeasonRules old = SeasonRules.fromJson("{\"enabled\":true}");
        assertTrue(old.lockSmelting && old.lockBrewing && old.lockStarterRows, "older season files get the gates");
        assertEquals(5, old.crafterDailyLimit);
        assertEquals(1200, new SeasonRules().crafterFeeFor(1));
        assertEquals(9000, new SeasonRules().crafterFeeFor(9), "tiers past D charge the D fee");
    }

    @Test void artisanServiceKeepsItsLevel() {
        NpcServiceDef service = new NpcServiceDef(NpcServiceDef.Type.CRAFTER, "chef", "", JobSlot.SUB, 9);
        assertEquals(service, NpcServiceDef.load(service.save()));
        CompoundTag old = service.save();
        old.remove("level");
        assertEquals(0, NpcServiceDef.load(old).level(), "services saved before artisans make every level");
    }

    @Test void dailyLimitCountsDown() {
        assertEquals(-1, CrafterService.remaining(0, 3));
        assertEquals(2, CrafterService.remaining(5, 3));
        assertEquals(0, CrafterService.remaining(5, 8));
    }
}
