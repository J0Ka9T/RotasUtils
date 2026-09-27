package net.schwarz.rotasutils.core;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobMasteryCurve;
import net.schwarz.rotasutils.job.JobSlot;
import net.schwarz.rotasutils.job.JobArchetypes;
import net.schwarz.rotasutils.job.JobAttributeModifier;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillRules;
import net.schwarz.rotasutils.stat.CharacterStat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobsAndStatsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void treesLimitedToJobsAndRaces() {
        SkillCategory category = new SkillCategory("warrior_tree", "Warrior");
        Function<String, String> same = id -> id;
        assertNull(SkillRules.audienceBlock(category, "", List.of(), same, same));

        category.jobs().add("warrior");
        assertNotNull(SkillRules.audienceBlock(category, "", List.of(), same, same));
        assertNotNull(SkillRules.audienceBlock(category, "mage", List.of(), same, same));
        assertNull(SkillRules.audienceBlock(category, "warrior", List.of(), same, same));

        category.races().add("origins:elytrian");
        assertNotNull(SkillRules.audienceBlock(category, "warrior", List.of("origins:human"), same, same));
        assertNull(SkillRules.audienceBlock(category, "warrior", List.of("origins:elytrian"), same, same));
        assertNull(SkillRules.audienceBlock(category, List.of("mage","warrior"), List.of("origins:elytrian"), same, same));

        SkillCategory restored = SkillCategory.load(category.save());
        assertEquals(category.jobs(), restored.jobs());
        assertEquals(category.races(), restored.races());
    }

    @Test
    void jobAndStatProgressPersistButStayServerSideWhereNeeded() {
        PlayerProgress progress = new PlayerProgress(UUID.randomUUID());
        progress.setJob("warrior");
        progress.setJobChangedAt(1234);
        progress.setStatPointLevel(7);
        progress.setLastHealth(33);

        PlayerProgress restored = PlayerProgress.load(progress.save());
        assertEquals("warrior", restored.job());
        assertEquals(1234, restored.jobChangedAt());
        assertEquals(7, restored.statPointLevel());
        assertEquals(33, restored.lastHealth());

        CompoundTag client = progress.clientSnapshot();
        assertEquals("warrior", client.getString("job"));
        assertFalse(client.contains("stat_point_level"));
        assertFalse(client.contains("last_health"));
    }

    @Test
    void legacyJobMigratesToMainSlotWithoutLosingIdentity() {
        PlayerProgress legacy = new PlayerProgress(UUID.randomUUID());
        legacy.setJob("warrior");
        CompoundTag stored = legacy.save();
        stored.remove("main_job");
        stored.remove("sub_job");

        PlayerProgress restored = PlayerProgress.load(stored);
        assertEquals("warrior", restored.mainJob());
        assertEquals("warrior", restored.job());
        assertEquals("", restored.subJob());
        assertTrue(restored.rpg().learnedJobs().contains("warrior"));
    }

    @Test
    void jobSlotsCannotContainTheSameJob() {
        PlayerProgress progress = new PlayerProgress(UUID.randomUUID());
        progress.setMainJob("warrior");
        assertThrows(IllegalArgumentException.class, () -> progress.setSubJob("warrior"));
        progress.setSubJob("smith");
        assertThrows(IllegalArgumentException.class, () -> progress.setMainJob("smith"));
    }

    @Test
    void masteryCurveAwardsJobScopedPointsAndSubJobUsesReducedRate() {
        JobDef warrior = new JobDef("warrior");
        warrior.setMasteryCurve(new JobMasteryCurve(100, 1.0, 20));
        warrior.setSkillPointsPerMasteryLevel(2);
        warrior.setSubJobXpRate(0.25);
        PlayerProgress progress = new PlayerProgress(UUID.randomUUID());
        progress.setMainJob("warrior");

        var main = net.schwarz.rotasutils.server.JobService.grantMastery(progress, warrior, JobSlot.MAIN, 250);
        assertEquals(2, main.levelsGained());
        assertEquals(4, progress.rpg().jobSkillPoints("warrior"));

        progress.setMainJob("");
        progress.setSubJob("warrior");
        var sub = net.schwarz.rotasutils.server.JobService.grantMastery(progress, warrior, JobSlot.SUB, 200);
        assertEquals(50, sub.appliedXp());
    }

    @Test
    void expandedJobDefinitionRoundTrips() {
        JobDef job = new JobDef("smith");
        job.setMainAllowed(false);
        job.setSubAllowed(true);
        job.setSubJobXpRate(0.4);
        job.setSubPassiveCap(0.3);
        job.setSkillPointsPerMasteryLevel(3);
        job.trainerNpcIds().add("village_smith");
        JobDef restored = JobDef.load(job.save());
        assertEquals(job.save(), restored.save());
    }

    @Test
    void slotAssignmentHonorsEligibilityAndPreservesLearnedJobs() {
        PlayerProgress progress=new PlayerProgress(UUID.randomUUID());
        JobDef warrior=new JobDef("warrior"), smith=new JobDef("smith");
        smith.setMainAllowed(false);
        net.schwarz.rotasutils.server.JobService.assignSlot(progress,warrior,JobSlot.MAIN);
        net.schwarz.rotasutils.server.JobService.assignSlot(progress,smith,JobSlot.SUB);
        assertEquals("warrior",progress.mainJob()); assertEquals("smith",progress.subJob());
        assertTrue(progress.rpg().learnedJobs().containsAll(List.of("warrior","smith")));
        assertThrows(IllegalArgumentException.class,()->net.schwarz.rotasutils.server.JobService.assignSlot(progress,smith,JobSlot.MAIN));
    }

    @Test
    void firstJoinConfigurationRoundTripsWithNeutralLegacyDefaults() {
        LevelConfig config=new LevelConfig();
        config.setFirstJoinMode(LevelConfig.FirstJoinMode.ASSIGN);
        config.setFirstJoinMainJob("warrior"); config.setFirstJoinSubJob("smith");
        LevelConfig restored=LevelConfig.load(config.save());
        assertEquals(LevelConfig.FirstJoinMode.ASSIGN,restored.firstJoinMode());
        assertEquals("warrior",restored.firstJoinMainJob()); assertEquals("smith",restored.firstJoinSubJob());
    }

    @Test
    void diagramArchetypesSeparateCombatAndProfessionJobs() {
        assertEquals(13, JobArchetypes.all().size());
        JobDef brawler = JobArchetypes.create("brawler");
        assertNotNull(brawler);
        assertTrue(brawler.mainAllowed());
        assertFalse(brawler.subAllowed());
        for (String id : List.of("archer", "fighter", "tank", "rogue", "wizard")) {
            JobDef job = JobArchetypes.create(id);
            assertNotNull(job);
            assertTrue(job.mainAllowed());
            assertFalse(job.subAllowed());
            assertFalse(job.itemSelectors().isEmpty());
        }
        for (String id : List.of("miner", "chef", "fisher", "alchemy", "blacksmith", "farmer", "rancher")) {
            JobDef job = JobArchetypes.create(id);
            assertNotNull(job);
            assertFalse(job.mainAllowed());
            assertTrue(job.subAllowed());
            assertFalse(job.masteryActivities().isEmpty());
        }
        assertTrue(JobArchetypes.create("blacksmith").masteryActivities().contains("repair"));
        assertTrue(JobArchetypes.create("rancher").masteryActivities().contains("horse_care"));
    }

    @Test
    void jobItemAndActivityConfigurationRoundTrips() {
        JobDef rogue = JobArchetypes.create("rogue");
        JobDef restored = JobDef.load(rogue.save());
        assertEquals(rogue.itemSelectors(), restored.itemSelectors());
        assertEquals(rogue.masteryActivities(), restored.masteryActivities());
        assertEquals(rogue.save(), restored.save());
    }

    @Test
    void everyArchetypeHasMechanicalStrengthsAndWeaknesses() {
        for (JobArchetypes.Archetype archetype : JobArchetypes.all()) {
            JobDef job = JobArchetypes.create(archetype.id());
            assertTrue(job.attributeModifiers().stream().anyMatch(modifier -> modifier.amount() > 0), archetype.id());
            assertTrue(job.attributeModifiers().stream().anyMatch(modifier -> modifier.amount() < 0), archetype.id());
        }
    }

    @Test
    void combatAndMagicTemplatesUseRotasCommuAttributes() {
        assertTrue(JobArchetypes.create("fighter").attributeModifiers().stream()
                .anyMatch(modifier -> modifier.attribute().equals("epicfight:staminar")));
        assertTrue(JobArchetypes.create("tank").attributeModifiers().stream()
                .anyMatch(modifier -> modifier.attribute().equals("epicfight:stun_armor")));
        assertTrue(JobArchetypes.create("wizard").attributeModifiers().stream()
                .anyMatch(modifier -> modifier.attribute().equals("irons_spellbooks:spell_power")));
    }

    @Test
    void templatesOnlyUseVanillaOrVerifiedRotasCommuAttributes() {
        // IDs read from epic-fight-20.14.17 and irons_spellbooks-3.16.1 in the RotasCommu profile.
        java.util.Set<String> modded = java.util.Set.of(
                "epicfight:staminar", "epicfight:stamina_regen", "epicfight:impact", "epicfight:armor_negation",
                "epicfight:max_strikes", "epicfight:stun_armor", "epicfight:weight", "epicfight:execution_resistance",
                "irons_spellbooks:spell_power", "irons_spellbooks:max_mana", "irons_spellbooks:mana_regen",
                "irons_spellbooks:cooldown_reduction", "irons_spellbooks:cast_time_reduction",
                "irons_spellbooks:spell_resist", "irons_spellbooks:summon_damage", "irons_spellbooks:casting_movespeed");
        for (JobArchetypes.Archetype archetype : JobArchetypes.all()) {
            for (JobAttributeModifier modifier : JobArchetypes.create(archetype.id()).attributeModifiers()) {
                String attribute = modifier.attribute();
                assertTrue(attribute.startsWith("minecraft:generic.") || modded.contains(attribute)
                                || net.schwarz.rotasutils.server.CombatStats.logical(attribute),
                        archetype.id() + " uses unverified attribute " + attribute);
            }
        }
        assertTrue(JobArchetypes.create("fighter").attributeModifiers().stream()
                .anyMatch(modifier -> modifier.attribute().equals("irons_spellbooks:spell_power") && modifier.amount() < 0),
                "the fighter trades magic power for melee strength");
    }

    @Test
    void subJobBenefitsAndPenaltiesUseConfiguredCap() {
        JobAttributeModifier benefit = new JobAttributeModifier("Speed", "minecraft:generic.movement_speed", 0.20,
                CharacterStat.Operation.MULTIPLY_TOTAL);
        JobAttributeModifier penalty = new JobAttributeModifier("Health", "minecraft:generic.max_health", -4,
                CharacterStat.Operation.ADD);
        assertEquals(0.06, benefit.amount(JobSlot.SUB, 0.30), 0.0001);
        assertEquals(-1.2, penalty.amount(JobSlot.SUB, 0.30), 0.0001);
        assertEquals(benefit, JobAttributeModifier.load(benefit.save()));
    }

}
