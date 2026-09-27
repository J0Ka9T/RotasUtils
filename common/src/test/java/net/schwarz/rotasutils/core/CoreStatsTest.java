package net.schwarz.rotasutils.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.level.StatRules;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.server.CharacterStatService;
import net.schwarz.rotasutils.server.ProgressService;
import net.schwarz.rotasutils.stat.CoreStat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rebuilt level and stat system: four fixed stats, flat points per level, one EXP formula. */
class CoreStatsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void pointsAreStartPlusFlatPerLevel() {
        StatRules rules = new StatRules();
        assertEquals(3, rules.pointsAt(1));
        assertEquals(21, rules.pointsAt(10));
        assertEquals(201, rules.pointsAt(100));
        assertTrue(rules.pointsAt(100) >= 2 * rules.maxPerStat, "level 100 fills two stats");
        assertTrue(rules.pointsAt(100) < 3 * rules.maxPerStat, "but not three, so players choose");
    }

    @Test
    void levelPointsAreGrantedOnceAndNeverTakenBack() {
        RotasData data = new RotasData();
        PlayerProgress progress = data.progress(UUID.randomUUID());
        assertEquals(3, CharacterStatService.grantLevelPoints(progress, data), "a new character starts with 3");
        assertEquals(0, CharacterStatService.grantLevelPoints(progress, data));
        progress.setLevel(10);
        assertEquals(18, CharacterStatService.grantLevelPoints(progress, data));
        progress.setLevel(5);
        assertEquals(0, CharacterStatService.grantLevelPoints(progress, data), "lowering a level keeps the points");
        assertEquals(21, progress.rpg().statPoints());
    }

    @Test
    void everyPointIsWorthTheSameAndTheTextSaysSo() {
        StatRules rules = new StatRules();
        assertEquals("+1% พลังโจมตี", CoreStat.STR.describe(rules, 1));
        assertEquals("+50% พลังโจมตี", CoreStat.STR.describe(rules, 50));
        assertEquals("+10% ความเร็วโจมตี, +4% โอกาสหลบ", CoreStat.AGI.describe(rules, 20));
    }

    /** At the cap every stat roughly doubles what it governs, so no single stat is the only right answer. */
    @Test
    void everyStatIsWorthAboutTheSameAtTheCap() {
        StatRules rules = new StatRules();
        int cap = rules.maxPerStat;
        double str = 1 + rules.strAttack * cap;
        double vit = 1 + rules.vitHealth * cap;
        double intel = 1 + rules.intMagic * cap;
        // Attack speed is damage over time; dodge is hits that never land.
        double agi = (1 + rules.agiAttackSpeed * cap) / (1 - rules.agiDodge * cap);
        double best = Math.max(Math.max(str, vit), Math.max(intel, agi));
        double worst = Math.min(Math.min(str, vit), Math.min(intel, agi));
        assertEquals(2.0, str, 1e-9);
        assertTrue(best / worst < 1.1, "no stat may be worth 10% more than another");
    }

    @Test
    void refundGivesEveryPointBack() {
        RotasData data = new RotasData();
        PlayerProgress progress = data.progress(UUID.randomUUID());
        progress.rpg().addStatPoints(10);
        progress.rpg().allocate(CoreStat.STR.id(), 4);
        progress.rpg().allocate(CoreStat.VIT.id(), 2);
        assertEquals(6, CharacterStatService.refund(progress, data));
        assertEquals(10, progress.rpg().statPoints());
        assertEquals(0, CharacterStatService.allocated(progress, CoreStat.STR));
    }

    @Test
    void newSystemWipesLevelsStatsAndSkillsOnce() {
        RotasData data = new RotasData();
        PlayerProgress veteran = data.progress(UUID.randomUUID());
        veteran.setLevel(60);
        veteran.setXp(500);
        veteran.addTotalXp(1_000_000);
        veteran.addSkillPoints(12);
        veteran.setSkillRank("warrior/slash", 3);
        veteran.rpg().addStatPoints(40);
        veteran.rpg().allocate(CoreStat.STR.id(), 25);
        veteran.rpg().currency("rotas:gold", 999);

        assertEquals(1, ProgressService.migrateStatSystem(data));
        assertEquals(1, veteran.level());
        assertEquals(0, veteran.xp());
        assertEquals(0, veteran.totalXp());
        assertEquals(0, veteran.skillPoints());
        assertEquals(0, veteran.skillRank("warrior/slash"));
        assertEquals(0, CharacterStatService.allocated(veteran, CoreStat.STR));
        assertEquals(3, veteran.rpg().statPoints(), "back to the starting points");
        assertEquals(999, veteran.rpg().currency("rotas:gold"), "money is kept");

        veteran.setLevel(5);
        assertEquals(0, ProgressService.migrateStatSystem(data), "the wipe runs once");
        assertEquals(5, veteran.level());
    }

    @Test
    void theCurveComesFromTheSeasonRulesOnly() {
        LevelConfig config = new LevelConfig();
        assertEquals(25, config.curve().xpToNext(1));
        long toMax = config.curve().totalXpTo(100);
        assertTrue(toMax > 12_000_000 && toMax < 14_000_000, "about 13M EXP to level 100: " + toMax);

        SeasonRules rules = config.season().copy();
        rules.mainBaseXp = 100;
        rules.mainMaxLevel = 50;
        config.setSeason(rules);
        assertEquals(100, config.curve().xpToNext(1));
        assertEquals(Long.MAX_VALUE, config.curve().xpToNext(50));

        LevelConfig restored = LevelConfig.load(config.save());
        assertEquals(50, restored.curve().maxLevel());
        assertEquals(100, restored.curve().xpToNext(1));
    }

    @Test
    void oldSeasonFilesLoadAndGetTheNewStatBlock() {
        SeasonRules rules = SeasonRules.fromJson("{\"diminishingStep\":25,\"dailyCapFloor\":150000,"
                + "\"classSynergy\":{},\"stats\":{\"pointsPerLevel\":4}}");
        assertEquals(4, rules.stats.pointsPerLevel);
        assertEquals(100, rules.stats.maxPerStat);
        assertNotNull(SeasonRules.fromJson("{}").stats);
    }

    @Test
    void onlyTheFourStatsExist() {
        assertEquals(4, CoreStat.ALL.size());
        assertEquals(CoreStat.INT, CoreStat.byId("rotas:int"));
        assertNull(CoreStat.byId("rotas:strength"));
    }
}
