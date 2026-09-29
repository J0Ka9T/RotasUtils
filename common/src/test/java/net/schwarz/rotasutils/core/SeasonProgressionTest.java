package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobMasteryCurve;
import net.schwarz.rotasutils.level.LevelConfig;
import net.schwarz.rotasutils.level.LevelCurve;
import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeasonProgressionTest {
    @Test void defaultCurveIsTheSeasonPowerCurve() {
        LevelCurve curve = new LevelCurve();
        assertEquals(25, curve.xpToNext(1));
        assertEquals(3147, curve.xpToNext(10));
    }

    @Test void oldWorldCurvesAreReplacedByTheSeasonRules() {
        CompoundTag tag = new LevelConfig().save();
        CompoundTag oldCurve = new CompoundTag();
        oldCurve.putString("preset", "COMMUNITY_RPG");
        oldCurve.putLong("base_xp", 333);
        tag.put("curve", oldCurve);
        assertEquals(184, LevelConfig.load(tag).curve().xpToNext(1), "the stored curve is ignored");
    }

    @Test void seasonRulesTravelWithTheLevelConfig() {
        LevelConfig config = new LevelConfig();
        config.season().horse.pullCost = 640;
        config.season().partyLevelReach = 7;
        LevelConfig loaded = LevelConfig.load(config.save());
        assertEquals(640, loaded.season().horse.pullCost);
        assertEquals(7, loaded.season().partyLevelReach);
    }

    @Test void subJobSeasonCurveCapsAtTwenty() {
        JobMasteryCurve curve = JobMasteryCurve.season(new SeasonRules());
        assertEquals(90, curve.xpForNextLevel(1));
        assertEquals(313, curve.xpForNextLevel(2));
        assertEquals(20, curve.maxLevel());
        long total = 0;
        for (int level = 1; level < 20; level++) total += curve.xpForNextLevel(level);
        assertEquals(20, curve.levelAt(total));
        assertEquals(19, curve.levelAt(total - 1));
        assertEquals(curve, JobMasteryCurve.load(curve.save()));
        assertEquals(0, new JobMasteryCurve(100, 1.15, 100).exponent(), 1e-9);
    }

    @Test void productionTableRoundTripsAndRejectsBadRows() {
        JobDef job = new JobDef("chef");
        job.setProductionXpRate(0.144);
        job.production().add(JobDef.ProductionEntry.decode("CRAFT minecraft:bread 1"));
        job.production().add(JobDef.ProductionEntry.decode("smelt #minecraft:fishes 8"));
        JobDef loaded = JobDef.load(job.save());
        assertEquals(0.144, loaded.productionXpRate(), 1e-9);
        assertEquals(2, loaded.production().size());
        assertEquals(JobDef.ProductionEntry.Activity.SMELT, loaded.production().get(1).activity());
        assertEquals("SMELT #minecraft:fishes 8", loaded.production().get(1).encode());
        assertThrows(IllegalArgumentException.class, () -> JobDef.ProductionEntry.decode("CRAFT bread"));
        assertThrows(IllegalArgumentException.class, () -> JobDef.ProductionEntry.decode("CRAFT Not An Id 3"));
    }

    @Test void chefNeedsTwentyFiveTierADishesForTheFirstLevel() {
        SeasonRules rules = new SeasonRules();
        double perDish = rules.tierXp[0] * 0.144;
        assertEquals(25, Math.ceil(90 / perDish - 1e-9), 1e-9);
    }

    @Test void kernelQuestTypeIsOptionalAndValidated() {
        String base = "{\"stages\":[{\"objectives\":[{\"event\":\"rotas:monster_defeated\"}]}]";
        var plain = QuestDefinitions.quest(new ContentId("rotas:quest/a"),
                com.google.gson.JsonParser.parseString(base + "}").getAsJsonObject(), json -> ConditionEngine.ALWAYS);
        assertEquals("", plain.type());
        var main = QuestDefinitions.quest(new ContentId("rotas:quest/b"),
                com.google.gson.JsonParser.parseString(base + ",\"type\":\"MAIN\"}").getAsJsonObject(), json -> ConditionEngine.ALWAYS);
        assertEquals("MAIN", main.type());
        assertThrows(IllegalArgumentException.class, () -> QuestDefinitions.quest(new ContentId("rotas:quest/c"),
                com.google.gson.JsonParser.parseString(base + ",\"type\":\"EPIC\"}").getAsJsonObject(), json -> ConditionEngine.ALWAYS));
    }
}
